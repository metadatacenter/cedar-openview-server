package org.metadatacenter.cedar.openview.resources;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.cedar.openview.model.OpenViewFolderResponse;
import org.metadatacenter.cedar.util.dw.AnonymousAccess;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.NodeListUtil;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.LinkHeaderUtil;
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
  @AnonymousAccess
  @Operation(summary = "List the contents of an open folder",
      description = "Return an anonymous public projection of what an open folder holds. Opening a "
          + "folder makes its whole descendant subtree readable through OpenView. The breadcrumb "
          + "contains ancestor names only; child summaries contain only their identifier, type, and "
          + "name. No ACL, provenance, timestamp, DOI, or caller-specific permission fields are returned.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The folder's contents and its path",
          content = @Content(schema = @Schema(ref = "#/components/schemas/OpenViewFolderResponse"))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "A paging, sort, or filter parameter is not valid"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)),
          description = "The folder exists but is not open, and neither is any folder above it"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "No such folder")
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

    CedarRequestContext c = buildAnonymousRequestContext();
    FolderServiceSession folderSession = dataServices.getFolderServiceSession(c);
    CedarFolderId fid = CedarFolderId.build(id);

    FolderServerFolder folder;
    folder = folderSession.findFolderById(fid);
    if (folder == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.FOLDER_NOT_FOUND)
          .message("The folder can not be found by id:" + id)
          .build();
    } else {
      List<FolderServerResourceExtract> pathInfo = folderSession.findNodePathExtract(folder);
      boolean hasOpenAncestor = pathInfo.stream().anyMatch(path -> Boolean.TRUE.equals(path.getIsOpen()));
      if (!folder.isOpen() && !hasOpenAncestor) {
        return CedarResponse.unauthorized()
            .id(id)
            .build();
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

      NodeListRequest request = NodeListUtil.buildNodeListRequest(pagedSortedTypedQuery);
      List<FolderServerResourceExtract> resources = folderSession.findFolderContentsExtract(fid, request);
      long total = folderSession.findFolderContentsCount(fid, request);
      OpenViewFolderResponse r = new OpenViewFolderResponse(request, total,
          LinkHeaderUtil.getPagingLinkHeaders(absoluteURI.toString(), total, request.getLimit(), request.getOffset()),
          resources, pathInfo);

      return Response.ok().entity(r).build();
    }
  }
}
