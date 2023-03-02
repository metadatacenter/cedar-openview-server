package org.metadatacenter.cedar.openview.resources;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.http.CedarResponse;

import javax.ws.rs.core.Response;

public abstract class AbstractOpenViewResource extends CedarMicroserviceResource {

  public AbstractOpenViewResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  protected Response lookupId(CedarArtifactId artifactId, CedarResourceType resourceType) {
    CedarRequestContext c = buildAnonymousRequestContext();

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerArtifact folderServerResource = folderSession.findArtifactById(artifactId);

    if (folderServerResource == null) {
      String alternateId = linkedDataUtil.getLinkedDataId(resourceType, artifactId.getId());
      CedarArtifactId aid = CedarArtifactId.build(alternateId, resourceType);
      folderServerResource = folderSession.findArtifactById(aid);
    }

    if (folderServerResource == null) {
      return CedarResponse.notFound().id(artifactId).build();
    } else {
      if (folderServerResource.isOpen()) {
        return Response.ok().build();
      } else {
        if (folderSession.isArtifactOpenImplicitly(artifactId)) {
          return Response.ok().build();
        } else {
          return CedarResponse.unauthorized().id(artifactId).build();
        }
      }
    }
  }

}
