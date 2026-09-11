package org.metadatacenter.cedar.openview.resources;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.util.http.HttpTimeouts;

import java.io.IOException;

/** Preserves OpenView URLs while resource owns every artifact openness decision. */
public abstract class AbstractOpenViewResource extends CedarMicroserviceResource {
  public AbstractOpenViewResource(CedarConfig config) {
    super(config);
  }

  public AbstractOpenViewResource(CedarConfig config, CedarDataServices dataServices) {
    super(config, dataServices);
  }

  protected Response resolveArtifact(String id, CedarResourceType type) throws CedarException {
    String url = microserviceUrlUtil.getResource().getOpenArtifact(type, id);
    // Deliberately construct a credential-free request. Do not forward Authorization, cookies,
    // service keys, query parameters, or a caller-selected destination.
    Request request = Request.get(url).setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    try (ClassicHttpResponse upstream = HttpTimeouts.ANONYMOUS_INTERACTIVE.execute(request)) {
      Response.ResponseBuilder result = Response.status(upstream.getCode()).type(MediaType.APPLICATION_JSON)
          .header(HttpHeaders.CACHE_CONTROL, "no-store");
      if (upstream.getEntity() != null) result.entity(EntityUtils.toByteArray(upstream.getEntity()));
      return result.build();
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("Downstream service is unavailable", e);
    }
  }
}
