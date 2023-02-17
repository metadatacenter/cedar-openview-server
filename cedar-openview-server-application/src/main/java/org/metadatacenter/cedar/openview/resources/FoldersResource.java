package org.metadatacenter.cedar.openview.resources;

import com.codahale.metrics.annotation.Timed;
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

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.constant.CedarQueryParameters.*;

@Path("/folders")
@Produces(MediaType.APPLICATION_JSON)
public class FoldersResource extends AbstractOpenViewResource {

  public FoldersResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findFolder(@PathParam(PP_ID) String id,
                             @QueryParam(QP_RESOURCE_TYPES) Optional<String> resourceTypes,
                             @QueryParam(QP_VERSION) Optional<String> versionParam,
                             @QueryParam(QP_PUBLICATION_STATUS) Optional<String> publicationStatusParam,
                             @QueryParam(QP_SORT) Optional<String> sortParam,
                             @QueryParam(QP_LIMIT) Optional<Integer> limitParam,
                             @QueryParam(QP_OFFSET) Optional<Integer> offsetParam) throws CedarException {

    UserService userService = CedarDataServices.getNeoUserService();
    CedarRequestContext c = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
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
      ResourcePermissionServiceSession permissionSession = CedarDataServices.getResourcePermissionServiceSession(c);
      List<FolderServerResourceExtract> pathInfo = PathInfoBuilder.getResourcePathExtract(c, folderSession,
          permissionSession, folder);
      if (!folder.isOpen()) {
        boolean foundOpenParent = false;
        for (FolderServerResourceExtract parent : pathInfo) {
          if (parent.getIsOpen() != null && parent.getIsOpen()) {
            foundOpenParent = true;
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
