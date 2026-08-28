package org.metadatacenter.cedar.openview.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.service.TemplateService;
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

@Path("/templates")
@Produces(MediaType.APPLICATION_JSON)
public class TemplatesResource extends AbstractOpenViewResource {

  private static TemplateService<String, JsonNode> templateService;

  public TemplatesResource(CedarConfig cedarConfig, TemplateService<String, JsonNode> templateService) {
    super(cedarConfig);
    TemplatesResource.templateService = templateService;
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findTemplate(@PathParam(PP_ID) String id) throws CedarException {
    CedarTemplateId tid =  CedarTemplateId.build(id);
    Response response = lookupId(tid, CedarResourceType.TEMPLATE);
    if (response.getStatus() != CedarResponseStatus.OK.getStatusCode()) {
      return response;
    } else {
      JsonNode template;
      try {
        template = templateService.findTemplate(id);
      } catch (IOException e) {
        return artifactStoreUnavailable(tid, e);
      }
      if (template == null) {
        return CedarResponse.notFound()
            .id(id)
            .errorKey(CedarErrorKey.TEMPLATE_NOT_FOUND)
            .errorMessage("The template can not be found by id:" + id)
            .build();
      } else {
        MongoUtils.removeIdField(template);
        return Response.ok().entity(template).build();
      }
    }
  }

}
