package org.metadatacenter.cedar.openview.resources;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.id.CedarElementId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.service.TemplateElementService;
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

@Path("/template-elements")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Template elements")
public class TemplateElementsResource extends AbstractOpenViewResource {

  private static TemplateElementService<String, JsonNode> templateElementService;

  public TemplateElementsResource(CedarConfig cedarConfig, TemplateElementService<String, JsonNode> templateElementService) {
    super(cedarConfig);
    TemplateElementsResource.templateElementService = templateElementService;
  }

  @GET
  @Timed
  @Path("/{id}")
  @Operation(summary = "Get an open template element",
      description = "Return a template element that is open to everyone. An artifact is served when it is marked open, or when it sits under a folder that is. No credentials are involved: this server exists to hand out open artifacts anonymously, which is what makes a published CEDAR artifact citable. "
          + "Mongo's internal `_id` is removed before the artifact is returned.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The template element"),
      @ApiResponse(responseCode = "401",
          description = "The template element exists but is not open, and neither is any folder above it"),
      @ApiResponse(responseCode = "404", description = "No such template element"),
      @ApiResponse(responseCode = "503",
          description = "The artifact store could not be reached after the template element was found to be open")
  })
  public Response findTemplateElement(
      @Parameter(description = "Artifact identifier. Either the bare identifier or the full IRI is "
          + "accepted; a bare one is resolved to the IRI before lookup.", required = true)
      @PathParam(PP_ID) String id) throws CedarException {
    CedarElementId eid = CedarElementId.build(id);
    Response response = lookupId(eid, CedarResourceType.ELEMENT);
    if (response.getStatus() != CedarResponseStatus.OK.getStatusCode()) {
      return response;
    } else {
      JsonNode templateElement;
      try {
        templateElement = templateElementService.findTemplateElement(id);
      } catch (IOException e) {
        return artifactStoreUnavailable(eid, e);
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
