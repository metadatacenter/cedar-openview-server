package org.metadatacenter.cedar.openview.resources;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.artifact.SchemaArtifactDocument;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarResourceType;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;

@Path("/template-elements")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Template elements")
public class TemplateElementsResource extends AbstractOpenViewResource {

  public TemplateElementsResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("/{id}")
  @Operation(summary = "Get an open template element",
      description = "Return a template element that is open to everyone. An artifact is served when it is marked open, or when it sits under a folder that is. No credentials are involved: this server exists to hand out open artifacts anonymously, which is what makes a published CEDAR artifact citable. "
          + "Artifact storage's internal `_id` is removed before the artifact is returned.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The template element",
          content = @Content(schema = @Schema(implementation = SchemaArtifactDocument.class))),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)),
          description = "The template element exists but is not open, and neither is any folder above it"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "No such template element"),
      @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = CedarError.class)),
          description = "A required backend is unavailable")
  })
  public Response findTemplateElement(
      @Parameter(description = "Artifact identifier. Either the bare identifier or the full IRI is "
          + "accepted; a bare one is resolved to the IRI before lookup.", required = true)
      @PathParam(PP_ID) String id) throws CedarException {
    return resolveArtifact(id, CedarResourceType.ELEMENT);
  }

}
