package org.metadatacenter.cedar.openview.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.id.CedarFieldId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.service.TemplateFieldService;
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

@Path("/template-fields")
@Produces(MediaType.APPLICATION_JSON)
public class TemplateFieldsResource extends AbstractOpenViewResource {

  private static TemplateFieldService<String, JsonNode> templateFieldService;

  public TemplateFieldsResource(CedarConfig cedarConfig, TemplateFieldService<String, JsonNode> templateFieldService) {
    super(cedarConfig);
    TemplateFieldsResource.templateFieldService = templateFieldService;
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findTemplateField(@PathParam(PP_ID) String id) throws CedarException {
    CedarFieldId fid = CedarFieldId.build(id);
    Response response = lookupId(fid, CedarResourceType.FIELD);
    if (response.getStatus() != CedarResponseStatus.OK.getStatusCode()) {
      return response;
    } else {
      JsonNode templateField;
      try {
        templateField = templateFieldService.findTemplateField(id);
      } catch (IOException e) {
        return CedarResponse.internalServerError()
            .id(id)
            .errorKey(CedarErrorKey.TEMPLATE_FIELD_NOT_FOUND)
            .errorMessage("The template field can not be found by id:" + id)
            .exception(e)
            .build();
      }
      if (templateField == null) {
        return CedarResponse.notFound()
            .id(id)
            .errorKey(CedarErrorKey.TEMPLATE_FIELD_NOT_FOUND)
            .errorMessage("The template field can not be found by id:" + id)
            .build();
      } else {
        MongoUtils.removeIdField(templateField);
        return Response.ok().entity(templateField).build();
      }
    }
  }

}
