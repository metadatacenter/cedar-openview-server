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
@Tag(name = "Templates")
public class TemplatesResource extends AbstractOpenViewResource {

  private static TemplateService<String, JsonNode> templateService;

  public TemplatesResource(CedarConfig cedarConfig, TemplateService<String, JsonNode> templateService) {
    super(cedarConfig);
    TemplatesResource.templateService = templateService;
  }

  @GET
  @Timed
  @Path("/{id}")
  @Operation(summary = "Get an open template",
      description = "Return a template that is open to everyone. An artifact is served when it is marked open, or when it sits under a folder that is. No credentials are involved: this server exists to hand out open artifacts anonymously, which is what makes a published CEDAR artifact citable. "
          + "Mongo's internal `_id` is removed before the artifact is returned.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The template"),
      @ApiResponse(responseCode = "401",
          description = "The template exists but is not open, and neither is any folder above it"),
      @ApiResponse(responseCode = "404", description = "No such template"),
      @ApiResponse(responseCode = "503",
          description = "The artifact store could not be reached after the template was found to be open")
  })
  public Response findTemplate(
      @Parameter(description = "Artifact identifier. Either the bare identifier or the full IRI is "
          + "accepted; a bare one is resolved to the IRI before lookup.", required = true)
      @PathParam(PP_ID) String id) throws CedarException {
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
