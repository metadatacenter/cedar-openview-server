package org.metadatacenter.cedar.openview;

import com.sun.net.httpserver.HttpServer;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.*;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Boots the real OpenView application with no artifact store and a controlled resource HTTP peer. */
class OpenViewProxyTest {
  private static HttpServer resource;
  private static final List<String> paths = new ArrayList<>();
  private static final List<Map<String, List<String>>> headers = new ArrayList<>();
  private static final AtomicInteger status = new AtomicInteger(200);
  private static final String BODY = "{\"@id\":\"https://repo.example/templates/test\",\"schema:name\":\"Open artifact\"}";
  static {
    try {
      resource = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      resource.createContext("/", exchange -> {
        paths.add(exchange.getRequestURI().getRawPath());
        headers.add(new HashMap<>(exchange.getRequestHeaders()));
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (status.get() == 302) exchange.getResponseHeaders().set("Location", "/must-not-follow");
        exchange.sendResponseHeaders(status.get(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
      });
      resource.start();
      EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of(
          "CEDAR_OPENVIEW_HTTP_PORT", "0", "CEDAR_OPENVIEW_ADMIN_PORT", "0", "CEDAR_OPENVIEW_STOP_PORT", "0",
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

  @Test void allFamiliesPreserveIdentifiersAndNeverForwardCredentials() throws Exception {
    status.set(200);
    for (String type : List.of("templates", "template-elements", "template-fields", "template-instances")) {
      for (String id : List.of("11111111-2222-3333-4444-555555555555",
          "https://repo.metadatacenter.orgx/" + type + "/11111111-2222-3333-4444-555555555555")) {
        String path = "/" + type + "/" + URLEncoder.encode(id, StandardCharsets.UTF_8);
        int before = paths.size();
        var response = get(path + "?compact=true&url=http://untrusted.example/", true);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(BODY, response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
        assertEquals(before + 1, paths.size());
        assertEquals("/open" + path, paths.get(before));
        assertTrue(headers.get(before).keySet().stream().noneMatch(name ->
            name.equalsIgnoreCase("Authorization") || name.equalsIgnoreCase("Cookie")
                || name.equalsIgnoreCase("X-CEDAR-Artifact-Service-Key")));
      }
    }
  }

  @Test void forwardsDenialMissingAndUnavailableResponsesWithoutFallbackOrRetry() throws Exception {
    for (int code : List.of(401, 404, 503, 302)) {
      status.set(code);
      int before = paths.size();
      var response = get("/templates/missing", false);
      assertEquals(code, response.statusCode(), response.body());
      assertEquals(BODY, response.body());
      assertEquals(before + 1, paths.size(), "Do not retry responses or follow redirects");
    }
  }

  private static HttpResponse<String> get(String path, boolean credentials) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + SERVER.getLocalPort() + path));
    if (credentials) request.header("Authorization", "apiKey deliberately-invalid")
        .header("Cookie", "session=private").header("X-CEDAR-Artifact-Service-Key", "caller-secret");
    return CLIENT.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
  }
}
