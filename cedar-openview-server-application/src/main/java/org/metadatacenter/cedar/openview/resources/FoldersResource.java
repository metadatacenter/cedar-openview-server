package org.metadatacenter.cedar.openview.resources;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.util.http.CedarError;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.constant.CedarQueryParameters.*;

@Path("/folders")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Folders")
public class FoldersResource extends AbstractOpenViewResource {

  public FoldersResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("/{id}")
  @Operation(summary = "List the contents of an open folder",
      description = "Return an anonymous public projection of what an open folder holds. A folder is listed when "
          + "it is open itself or when a folder above it is, so opening a folder makes its whole descendant "
          + "subtree readable. The resource server decides openness and builds the listing on its "
          + "`/open/folders` route; this route relays that answer under OpenView's URL, with its paging links "
          + "pointing back here. No credentials are involved, and none are forwarded. The breadcrumb "
          + "contains ancestor names only; child summaries contain only their identifier, type, and "
          + "name, and for a field, element or template its version and publication status. No ACL, provenance, timestamp, DOI, or caller-specific permission fields are returned.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The folder's contents and its path",
          content = @Content(schema = @Schema(ref = "#/components/schemas/OpenViewFolderResponse"))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "A paging, sort, or filter parameter is not valid"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)),
          description = "The folder exists but is not open, and neither is any folder above it"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "No such folder"),
      @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = CedarError.class)),
          description = "A required backend is unavailable")
  })
  public Response findFolder(
      @Parameter(description = "Folder identifier. Either the bare identifier or the full IRI is accepted.",
          required = true)
      @PathParam(PP_ID) String id,
      @Parameter(description = "Comma-separated artifact types to include, in place of all of them.")
      @QueryParam(QP_RESOURCE_TYPES) Optional<String> resourceTypes,
      @Parameter(description = "Which versions to include: `latest`, `latest-published`, `latest-draft`, or `all`.")
      @QueryParam(QP_VERSION) Optional<String> versionParam,
      @Parameter(description = "Filter by publication status: `draft` or `published`.")
      @QueryParam(QP_PUBLICATION_STATUS) Optional<String> publicationStatusParam,
      @Parameter(description = "Comma-separated sort fields; a leading `-` reverses one.")
      @QueryParam(QP_SORT) Optional<String> sortParam,
      @Parameter(description = "How many entries to return, from 1 to 500. Defaults to 100.")
      @QueryParam(QP_LIMIT) Optional<Integer> limitParam,
      @Parameter(description = "Number of entries to skip before the first one returned.")
      @QueryParam(QP_OFFSET) Optional<Integer> offsetParam) throws CedarException {
    // Only the parameters this route declares are forwarded, and only as values it has already bound.
    Map<String, String> query = new LinkedHashMap<>();
    resourceTypes.ifPresent(v -> query.put(QP_RESOURCE_TYPES, v));
    versionParam.ifPresent(v -> query.put(QP_VERSION, v));
    publicationStatusParam.ifPresent(v -> query.put(QP_PUBLICATION_STATUS, v));
    sortParam.ifPresent(v -> query.put(QP_SORT, v));
    limitParam.ifPresent(v -> query.put(QP_LIMIT, Integer.toString(v)));
    offsetParam.ifPresent(v -> query.put(QP_OFFSET, Integer.toString(v)));
    return resolveFolder(id, query);
  }
}
