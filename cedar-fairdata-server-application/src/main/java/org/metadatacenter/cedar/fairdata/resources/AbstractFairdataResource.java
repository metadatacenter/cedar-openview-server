package org.metadatacenter.cedar.fairdata.resources;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.folderserver.basic.FolderServerResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.http.CedarResponse;

import javax.ws.rs.core.Response;

public abstract class AbstractFairdataResource extends CedarMicroserviceResource {

  public AbstractFairdataResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  protected Response lookupId(String id) throws CedarException {
    CedarRequestContext c = buildAnonymousRequestContext();

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerResource folderServerResource = folderSession.findResourceById(id);
    if (folderServerResource == null) {
      return CedarResponse.notFound().id(id).build();
    } else if (folderServerResource.isPublic() != null && folderServerResource.isPublic()) {
      return Response.ok().build();
    } else {
      return CedarResponse.unauthorized()
          .errorKey(CedarErrorKey.RESOURCE_NOT_PUBLIC)
          .errorMessage("The requested resource is not publicly available!").build();
    }
  }


}
