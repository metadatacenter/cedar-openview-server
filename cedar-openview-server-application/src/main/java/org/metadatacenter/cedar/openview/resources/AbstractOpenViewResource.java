package org.metadatacenter.cedar.openview.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.metadatacenter.util.http.ResponseRelay;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;

/** Preserves OpenView URLs while resource owns every openness decision, for artifacts and folders alike. */
public abstract class AbstractOpenViewResource extends CedarMicroserviceResource {
  public AbstractOpenViewResource(CedarConfig config) {
    super(config);
  }

  public AbstractOpenViewResource(CedarConfig config, CedarDataServices dataServices) {
    super(config, dataServices);
  }

  protected Response resolveArtifact(String id, CedarResourceType type) throws CedarException {
    String url = microserviceUrlUtil.getResource().getOpenArtifact(type, id);
    try (ClassicHttpResponse upstream = fetch(url)) {
      return relayed(upstream).build();
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("Downstream service is unavailable", e);
    }
  }

  /**
   * Relays the resource server's open folder listing. The caller's query is not forwarded as sent:
   * {@code query} holds only the parameters the route declares, already bound to their types. The
   * listing's paging links name the resource server, so on a 200 each is moved onto this request's
   * own path, keeping the normalized query the resource server wrote.
   */
  protected Response resolveFolder(String id, Map<String, String> query) throws CedarException {
    StringJoiner parameters = new StringJoiner("&");
    query.forEach((name, value) -> parameters.add(
        URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
    String url = microserviceUrlUtil.getResource().getOpenArtifact(CedarResourceType.FOLDER, id)
        + (query.isEmpty() ? "" : "?" + parameters);
    try (ClassicHttpResponse upstream = fetch(url)) {
      Response.ResponseBuilder result = relayed(upstream);
      if (upstream.getCode() == 200 && upstream.getEntity() != null) {
        byte[] rewritten = withOwnPagingLinks(EntityUtils.toByteArray(upstream.getEntity()));
        if (rewritten != null) result.entity(rewritten);
      }
      return result.build();
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("Downstream service is unavailable", e);
    }
  }

  /**
   * Deliberately a credential-free request. Do not forward Authorization, cookies, service keys, the
   * caller's raw query, or a caller-selected destination.
   */
  private static ClassicHttpResponse fetch(String url) throws IOException {
    Request request = Request.get(url).setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    return HttpTimeouts.ANONYMOUS_INTERACTIVE.execute(request);
  }

  /** Openness can be revoked at any moment, so nothing relayed from here may be cached. */
  private static Response.ResponseBuilder relayed(ClassicHttpResponse upstream) throws IOException {
    return ResponseRelay.responseBuilder(upstream).type(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CACHE_CONTROL, null).header(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  /** The body with every paging link moved onto this request's path, or null to relay it unchanged. */
  private byte[] withOwnPagingLinks(byte[] body) {
    JsonNode tree;
    try {
      tree = JsonMapper.TOLERANT_MAPPER.readTree(body);
    } catch (IOException e) {
      return null;
    }
    if (!(tree instanceof ObjectNode object) || !(object.get("paging") instanceof ObjectNode paging)) {
      return null;
    }
    String ownPath = uriInfo.getAbsolutePath().toString();
    Iterator<Map.Entry<String, JsonNode>> links = paging.fields();
    while (links.hasNext()) {
      Map.Entry<String, JsonNode> link = links.next();
      if (!link.getValue().isTextual()) continue;
      String rawQuery;
      try {
        rawQuery = URI.create(link.getValue().textValue()).getRawQuery();
      } catch (IllegalArgumentException e) {
        rawQuery = null;
      }
      link.setValue(object.textNode(rawQuery == null ? ownPath : ownPath + "?" + rawQuery));
    }
    try {
      return JsonMapper.TOLERANT_MAPPER.writeValueAsBytes(object);
    } catch (IOException e) {
      return null;
    }
  }
}
