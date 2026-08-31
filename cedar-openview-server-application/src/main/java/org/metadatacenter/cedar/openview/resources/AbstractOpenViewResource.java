package org.metadatacenter.cedar.openview.resources;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.http.CedarResponse;

import jakarta.ws.rs.core.Response;
import java.io.IOException;

public abstract class AbstractOpenViewResource extends CedarMicroserviceResource {


  public AbstractOpenViewResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  public AbstractOpenViewResource(CedarConfig cedarConfig, CedarDataServices dataServices) {
    super(cedarConfig, dataServices);
  }

  protected Response lookupId(CedarArtifactId artifactId, CedarResourceType resourceType) {
    CedarRequestContext c = buildAnonymousRequestContext();

    FolderServiceSession folderSession = dataServices.getFolderServiceSession(c);

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

  /**
   * Mongo is the source of the artifact body after the graph has established that it is open. A
   * transport/storage failure at that point is temporary dependency unavailability, not evidence
   * that the artifact is absent and not an unexplained OpenView defect.
   */
  protected static Response artifactStoreUnavailable(CedarArtifactId artifactId, IOException exception) {
    return CedarResponse.status(CedarResponseStatus.SERVICE_UNAVAILABLE)
        .id(artifactId)
        .errorMessage("Artifact store is unavailable")
        .exception(exception)
        .build();
  }

}
