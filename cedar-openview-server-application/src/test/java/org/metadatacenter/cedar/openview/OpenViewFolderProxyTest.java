package org.metadatacenter.cedar.openview;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.*;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boots the real OpenView application against a controlled resource HTTP peer, which owns the folder
 * openness decision and the listing. OpenView relays it, forwarding only the parameters its route
 * declares and no credential, and moves the listing's paging links onto its own URL.
 */
@Tag("integration")
class OpenViewFolderProxyTest {
  private static final String FOLDER_UUID = "11111111-2222-3333-4444-555555555555";
  private static HttpServer resource;
  private static final List<URI> requests = new ArrayList<>();
  private static final List<Map<String, List<String>>> headers = new ArrayList<>();
  private static final AtomicInteger status = new AtomicInteger(200);
  private static volatile String body;

  static {
    try {
      resource = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      resource.createContext("/", exchange -> {
        requests.add(exchange.getRequestURI());
        headers.add(new HashMap<>(exchange.getRequestHeaders()));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Retry-After", "17");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        exchange.sendResponseHeaders(status.get(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      resource.start();
      EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of(
          "CEDAR_OPENVIEW_HTTP_PORT", "0", "CEDAR_OPENVIEW_ADMIN_PORT", "0",
          "CEDAR_RESOURCE_SERVER_HOST", "127.0.0.1",
          "CEDAR_RESOURCE_HTTP_PORT", Integer.toString(resource.getAddress().getPort()),
          "CEDAR_MONGO_PORT", "1", "CEDAR_REDIS_PERSISTENT_PORT", "1"));
    } catch (Exception e) { throw new ExceptionInInitializerError(e); }
  }

  private static final DropwizardTestSupport<OpenViewServerConfiguration> SERVER =
      new DropwizardTestSupport<>(OpenViewServerApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));
  private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @BeforeAll static void start() throws Exception { SERVER.before(); }
  @AfterAll static void stop() { SERVER.after(); resource.stop(0); }

  @Test void relaysTheListingWithOnlyDeclaredParametersAndItsOwnPagingLinks() throws Exception {
    status.set(200);
    String upstreamBase = "http://127.0.0.1:" + resource.getAddress().getPort() + "/open/folders/" + FOLDER_UUID;
    String normalized = "resource_types=template&version=all&publication_status=all&sort=name";
    body = "{\"totalCount\":3,\"currentOffset\":1,\"nodeListQueryType\":\"folder-content\","
        + "\"resources\":[{\"@id\":\"https://repo.metadatacenter.orgx/templates/t\",\"resourceType\":\"template\","
        + "\"schema:name\":\"Open\"}],\"pathInfo\":[{\"schema:name\":\"Ancestor\"}],"
        + "\"request\":{\"limit\":1,\"offset\":1},"
        + "\"paging\":{\"first\":\"" + upstreamBase + "?" + normalized + "&offset=0&limit=1\","
        + "\"next\":\"" + upstreamBase + "?" + normalized + "&offset=2&limit=1\"},"
        + "\"@context\":{\"schema\":\"http://schema.org/\"}}";

    for (String id : List.of(FOLDER_UUID, "https://repo.metadatacenter.orgx/folders/" + FOLDER_UUID)) {
      String path = "/folders/" + URLEncoder.encode(id, StandardCharsets.UTF_8);
      int before = requests.size();
      var response = get(path + "?resource_types=template&sort=name&limit=1&offset=1"
          + "&url=http://untrusted.example/&q=secret&page=2", true);
      assertEquals(200, response.statusCode(), response.body());
      assertEquals(List.of("no-store"), response.headers().allValues("Cache-Control"));
      assertEquals(before + 1, requests.size());

      URI upstream = requests.get(before);
      assertEquals("/open/folders/" + FOLDER_UUID, upstream.getRawPath());
      Map<String, String> forwarded = new TreeMap<>();
      for (String pair : upstream.getRawQuery().split("&")) {
        String[] parts = pair.split("=", 2);
        forwarded.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
      }
      assertEquals(Map.of("resource_types", "template", "sort", "name", "limit", "1", "offset", "1"), forwarded,
          "Only the declared parameters travel, never the caller's raw query");
      assertTrue(headers.get(before).keySet().stream().noneMatch(name ->
          name.equalsIgnoreCase("Authorization") || name.equalsIgnoreCase("Cookie")
              || name.equalsIgnoreCase("X-CEDAR-Artifact-Service-Key")));

      JsonNode relayed = JsonMapper.STRICT_MAPPER.readTree(response.body());
      String own = "http://localhost:" + SERVER.getLocalPort() + path;
      assertEquals(own + "?" + normalized + "&offset=0&limit=1", relayed.path("paging").path("first").asText());
      assertEquals(own + "?" + normalized + "&offset=2&limit=1", relayed.path("paging").path("next").asText());
      assertEquals(3, relayed.path("totalCount").asInt());
      assertEquals("Open", relayed.path("resources").path(0).path("schema:name").asText());
      assertEquals("Ancestor", relayed.path("pathInfo").path(0).path("schema:name").asText());
      assertFalse(response.body().contains("127.0.0.1"), "No link may name the resource server");
    }
  }

  @Test void relaysRefusalsUnchangedWithoutFallbackOrRetry() throws Exception {
    body = "{\"status\":\"UNAUTHORIZED\",\"paging\":{\"first\":\"http://127.0.0.1/should-stay\"}}";
    for (int code : List.of(400, 401, 404, 429, 503, 302)) {
      status.set(code);
      int before = requests.size();
      var response = get("/folders/" + FOLDER_UUID, false);
      assertEquals(code, response.statusCode(), response.body());
      assertEquals(body, response.body(), "Only a listing is rewritten");
      assertEquals("17", response.headers().firstValue("Retry-After").orElseThrow());
      assertEquals(List.of("no-store"), response.headers().allValues("Cache-Control"));
      assertEquals(before + 1, requests.size(), "Do not retry responses or follow redirects");
    }
  }

  @Test void aListingThatIsNotJsonIsRelayedAsItCame() throws Exception {
    status.set(200);
    for (String unchanged : List.of("not json", "[1,2]", "{\"totalCount\":0}", "{\"paging\":\"none\"}")) {
      body = unchanged;
      var response = get("/folders/" + FOLDER_UUID, false);
      assertEquals(200, response.statusCode());
      assertEquals(unchanged, response.body(), "Nothing to rewrite, so nothing is changed");
    }
  }

  /** A link that carries no query, or cannot be read, still points here; one that is not text is left alone. */
  @Test void everyReadableLinkPointsHereWhateverItsShape() throws Exception {
    status.set(200);
    body = "{\"paging\":{\"first\":\"http://127.0.0.1:1/open/folders/x\",\"last\":\"http://bad host/?a=b\","
        + "\"count\":7}}";
    String path = "/folders/" + FOLDER_UUID;
    var response = get(path, false);
    assertEquals(200, response.statusCode(), response.body());
    JsonNode paging = JsonMapper.STRICT_MAPPER.readTree(response.body()).path("paging");
    String own = "http://localhost:" + SERVER.getLocalPort() + path;
    assertEquals(own, paging.path("first").asText());
    assertEquals(own, paging.path("last").asText());
    assertEquals(7, paging.path("count").asInt());
  }

  private static HttpResponse<String> get(String path, boolean credentials) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + SERVER.getLocalPort() + path));
    if (credentials) request.header("Authorization", "apiKey deliberately-invalid")
        .header("Cookie", "session=private").header("X-CEDAR-Artifact-Service-Key", "caller-secret");
    return CLIENT.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
  }
}
