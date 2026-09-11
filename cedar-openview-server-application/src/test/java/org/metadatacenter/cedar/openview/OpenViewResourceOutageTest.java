package org.metadatacenter.cedar.openview;

import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.*;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;

import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenViewResourceOutageTest {
  static {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of(
        "CEDAR_OPENVIEW_HTTP_PORT", "0", "CEDAR_OPENVIEW_ADMIN_PORT", "0", "CEDAR_OPENVIEW_STOP_PORT", "0",
        "CEDAR_RESOURCE_SERVER_HOST", "127.0.0.1", "CEDAR_RESOURCE_HTTP_PORT", "1",
        "CEDAR_MONGO_PORT", "1", "CEDAR_REDIS_PERSISTENT_PORT", "1"));
  }
  private static final DropwizardTestSupport<OpenViewServerConfiguration> SERVER =
      new DropwizardTestSupport<>(OpenViewServerApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));
  @BeforeAll static void start() throws Exception { SERVER.before(); }
  @AfterAll static void stop() { SERVER.after(); }

  @Test void resourceOutageIsSanitizedAndNeverFallsBackToMongo() throws Exception {
    var client = HttpClient.newHttpClient();
    for (String type : List.of("templates", "template-elements", "template-fields", "template-instances")) {
      var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + SERVER.getLocalPort()
          + "/" + type + "/11111111-2222-3333-4444-555555555555")).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(503, response.statusCode(), response.body());
      assertTrue(response.body().contains("Downstream service is unavailable"), response.body());
      assertFalse(response.body().contains("127.0.0.1"), response.body());
      assertFalse(response.body().contains("ConnectException"), response.body());
      assertFalse(response.body().contains("MongoDB"), response.body());
    }
  }
}
