package org.metadatacenter.cedar.open.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.id.CedarElementId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.service.TemplateElementService;
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

@Path("/template-elements")
@Produces(MediaType.APPLICATION_JSON)
public class TemplateElementsResource extends AbstractOpenResource {

  private static TemplateElementService<String, JsonNode> templateElementService;

  public TemplateElementsResource(CedarConfig cedarConfig, TemplateElementService<String, JsonNode> templateElementService) {
    super(cedarConfig);
    this.templateElementService = templateElementService;
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findTemplateElement(@PathParam(PP_ID) String id) throws CedarException {
    CedarElementId eid = CedarElementId.build(id);
    Response response = lookupId(eid, CedarResourceType.ELEMENT);
    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
      return response;
    } else {
      JsonNode templateElement;
      try {
        templateElement = templateElementService.findTemplateElement(id);
      } catch (IOException e) {
        return CedarResponse.internalServerError()
            .id(id)
            .errorKey(CedarErrorKey.TEMPLATE_ELEMENT_NOT_FOUND)
            .errorMessage("The template element can not be found by id:" + id)
            .exception(e)
            .build();
      }
      if (templateElement == null) {
        return CedarResponse.notFound()
            .id(id)
            .errorKey(CedarErrorKey.TEMPLATE_ELEMENT_NOT_FOUND)
            .errorMessage("The template element can not be found by id:" + id)
            .build();
      } else {
        MongoUtils.removeIdField(templateElement);
        return Response.ok().entity(templateElement).build();
      }
    }
  }

}
