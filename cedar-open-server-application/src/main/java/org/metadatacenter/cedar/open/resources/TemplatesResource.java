package org.metadatacenter.cedar.open.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.mongo.MongoUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;

@Path("/templates")
@Produces(MediaType.APPLICATION_JSON)
public class TemplatesResource extends AbstractOpenResource {

  private static TemplateService<String, JsonNode> templateService;

  public TemplatesResource(CedarConfig cedarConfig, TemplateService<String, JsonNode> templateService) {
    super(cedarConfig);
    this.templateService = templateService;
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findTemplate(@PathParam(PP_ID) String id) throws CedarException {
    Response response = lookupId(id, CedarResourceType.TEMPLATE);
    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
      return response;
    } else {
      JsonNode template;
      try {
        template = templateService.findTemplate(id);
      } catch (IOException e) {
        return CedarResponse.internalServerError()
            .id(id)
            .errorKey(CedarErrorKey.TEMPLATE_NOT_FOUND)
            .errorMessage("The template can not be found by id:" + id)
            .exception(e)
            .build();
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
