package org.metadatacenter.cedar.openview.resources;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.bridge.PathInfoBuilder;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.cache.user.ProvenanceNameUtil;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.NodeListUtil;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.PagedSortedTypedQuery;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.constant.CedarQueryParameters.*;

@Path("/folders")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Folders")
public class FoldersResource extends AbstractOpenViewResource {

  public FoldersResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("/{id}")
  @Operation(summary = "List the contents of an open folder",
      description = "Return what an open folder holds, with the path back to the workspace root. "
          + "A folder is served when it is marked open, or when a folder above it is. No credentials "
          + "are involved: this server exists to hand out open content anonymously.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The folder's contents and its path"),
      @ApiResponse(responseCode = "400", description = "A paging, sort, or filter parameter is not valid"),
      @ApiResponse(responseCode = "401",
          description = "The folder exists but is not open, and neither is any folder above it"),
      @ApiResponse(responseCode = "404", description = "No such folder")
  })
  public Response findFolder(
      @Parameter(description = "Folder identifier.", required = true)
      @PathParam(PP_ID) String id,
      @Parameter(description = "Comma-separated artifact types to include, in place of all of them.")
      @QueryParam(QP_RESOURCE_TYPES) Optional<String> resourceTypes,
      @Parameter(description = "Which versions to include: `latest`, `latest-published`, `latest-draft`, or `all`.")
      @QueryParam(QP_VERSION) Optional<String> versionParam,
      @Parameter(description = "Filter by publication status: `draft` or `published`.")
      @QueryParam(QP_PUBLICATION_STATUS) Optional<String> publicationStatusParam,
      @Parameter(description = "Comma-separated sort fields; a leading `-` reverses one.")
      @QueryParam(QP_SORT) Optional<String> sortParam,
      @Parameter(description = "Maximum number of entries to return.")
      @QueryParam(QP_LIMIT) Optional<Integer> limitParam,
      @Parameter(description = "Number of entries to skip before the first one returned.")
      @QueryParam(QP_OFFSET) Optional<Integer> offsetParam) throws CedarException {

    UserService userService = dataServices.getNeoUserService();
    CedarRequestContext c = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    FolderServiceSession folderSession = dataServices.getFolderServiceSession(c);
    CedarFolderId fid = CedarFolderId.build(id);

    FolderServerFolder folder;
    folder = folderSession.findFolderById(fid);
    if (folder == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.FOLDER_NOT_FOUND)
          .errorMessage("The folder can not be found by id:" + id)
          .build();
    } else {
      ResourcePermissionServiceSession permissionSession = dataServices.getResourcePermissionServiceSession(c);
      List<FolderServerResourceExtract> pathInfo = PathInfoBuilder.getResourcePathExtract(c, folderSession,
          permissionSession, folder);
      if (!folder.isOpen()) {
        boolean foundOpenParent = false;
        for (FolderServerResourceExtract parent : pathInfo) {
          if (parent.getIsOpen() != null && parent.getIsOpen()) {
            foundOpenParent = true;
            break;
          }
        }
        if (!foundOpenParent) {
          return CedarResponse.unauthorized()
              .id(id)
              .build();
        }
      }

      PagedSortedTypedQuery pagedSortedTypedQuery = new PagedSortedTypedQuery(
          cedarConfig.getResourceRESTAPI().getPagination())
          .resourceTypes(resourceTypes)
          .version(versionParam)
          .publicationStatus(publicationStatusParam)
          .sort(sortParam)
          .limit(limitParam)
          .offset(offsetParam);
      pagedSortedTypedQuery.validate();

      UriBuilder builder = uriInfo.getAbsolutePathBuilder();
      URI absoluteURI = builder
          .queryParam(QP_RESOURCE_TYPES, pagedSortedTypedQuery.getResourceTypesAsString())
          .queryParam(QP_VERSION, pagedSortedTypedQuery.getVersionAsString())
          .queryParam(QP_PUBLICATION_STATUS, pagedSortedTypedQuery.getPublicationStatusAsString())
          .queryParam(QP_SORT, pagedSortedTypedQuery.getSortListAsString())
          .build();

      FolderServerNodeListResponse r = NodeListUtil.findFolderContents(cedarConfig, folderSession, fid,
          absoluteURI.toString(), pathInfo, pagedSortedTypedQuery);

      ProvenanceNameUtil.addProvenanceDisplayNames(r);

      return Response.ok().entity(r).build();
    }
  }
}
