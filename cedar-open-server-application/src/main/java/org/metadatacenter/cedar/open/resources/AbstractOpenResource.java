package org.metadatacenter.cedar.open.resources;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.basic.FolderServerResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.http.CedarResponse;

import javax.ws.rs.core.Response;

public abstract class AbstractOpenResource extends CedarMicroserviceResource {

  public AbstractOpenResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  protected Response lookupId(String id, CedarNodeType nodeType) throws CedarException {
    CedarRequestContext c = buildAnonymousRequestContext();

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerResource folderServerResource = folderSession.findResourceById(id);

    if (folderServerResource == null) {
      String alternateId = linkedDataUtil.getLinkedDataId(nodeType, id);
      folderServerResource = folderSession.findResourceById(alternateId);
    }

    if (folderServerResource != null && folderServerResource.isOpen() != null && folderServerResource.isOpen()) {
      return Response.ok().build();
    } else {
      return CedarResponse.notFound().id(id).build();
    }
  }

}
