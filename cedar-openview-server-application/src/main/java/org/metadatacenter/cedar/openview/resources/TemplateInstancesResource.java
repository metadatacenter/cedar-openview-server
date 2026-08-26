package org.metadatacenter.cedar.openview.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.id.CedarTemplateInstanceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.mongo.MongoUtils;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;

@Path("/template-instances")
@Produces(MediaType.APPLICATION_JSON)
public class TemplateInstancesResource extends AbstractOpenViewResource {

  private static TemplateInstanceService<String, JsonNode> templateInstanceService;

  public TemplateInstancesResource(CedarConfig cedarConfig, TemplateInstanceService<String, JsonNode> templateInstanceService) {
    super(cedarConfig);
    TemplateInstancesResource.templateInstanceService = templateInstanceService;
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findTemplateInstance(@PathParam(PP_ID) String id) throws CedarException {
    CedarTemplateInstanceId iid = CedarTemplateInstanceId.build(id);
    Response response = lookupId(iid, CedarResourceType.INSTANCE);
    if (response.getStatus() != CedarResponseStatus.OK.getStatusCode()) {
      return response;
    } else {
      JsonNode templateInstance;
      try {
        templateInstance = templateInstanceService.findTemplateInstance(id);
      } catch (IOException e) {
        return artifactStoreUnavailable(iid, e);
      }
      if (templateInstance == null) {
        return CedarResponse.notFound()
            .id(id)
            .errorKey(CedarErrorKey.TEMPLATE_INSTANCE_NOT_FOUND)
            .errorMessage("The template instance can not be found by id:" + id)
            .build();
      } else {
        MongoUtils.removeIdField(templateInstance);
        return Response.ok().entity(templateInstance).build();
      }
    }

  }

}
