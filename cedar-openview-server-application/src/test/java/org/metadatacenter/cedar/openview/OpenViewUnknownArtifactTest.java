package org.metadatacenter.cedar.openview;

import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * The open-view server's failure path. Every one of its endpoints is anonymous by design — it
 * serves artifacts that have been made open, so it builds an anonymous request context and asserts
 * no login — which makes "rejects an unauthenticated request" the wrong contract to pin here. What
 * matters instead is that an artifact the server should not serve is refused rather than leaked.
 *
 * <p>An artifact absent from the graph is the reachable half of that: it answers 404. The other
 * half, an artifact present but not open, needs a seeded graph and belongs with the sharing tests.
 *
 * <p>The graph is an in-process Neo4j, so no external service is involved. Mongo is never reached:
 * the id lookup fails before the artifact body is fetched.
 */
public class OpenViewUnknownArtifactTest {

  static {
    // Must run before the test support boots the server, which reads the Neo4j and port env vars.
    // Ports are distinct from the dev server and from every other booting test class. Redis goes to
    // a dead port: no endpoint under test depends on a live Redis.
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of(
        "CEDAR_OPENVIEW_HTTP_PORT", "19030",
        "CEDAR_OPENVIEW_ADMIN_PORT", "19130",
        "CEDAR_OPENVIEW_STOP_PORT", "19230",
        "CEDAR_REDIS_PERSISTENT_PORT", "1"));
  }

  private static final DropwizardTestSupport<OpenViewServerConfiguration> SERVER =
      new DropwizardTestSupport<>(OpenViewServerApplication.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  /** A syntactically valid artifact UUID that was never created. */
  private static final String ABSENT_UUID = "11111111-2222-3333-4444-555555555555";

  @BeforeAll
  public static void startServer() throws Exception {
    SERVER.before();
  }

  @AfterAll
  public static void stopServer() {
    SERVER.after();
  }

  @Test
  public void anAbsentTemplateIsNotFound() throws Exception {
    assertNotFound("/templates/" + ABSENT_UUID);
  }

  @Test
  public void anAbsentElementIsNotFound() throws Exception {
    assertNotFound("/template-elements/" + ABSENT_UUID);
  }

  @Test
  public void anAbsentFieldIsNotFound() throws Exception {
    assertNotFound("/template-fields/" + ABSENT_UUID);
  }

  @Test
  public void anAbsentInstanceIsNotFound() throws Exception {
    assertNotFound("/template-instances/" + ABSENT_UUID);
  }

  /**
   * A 404 alone would not distinguish the artifact being absent from the route being absent, so the
   * body has to carry the server's own not-found response rather than Jersey's empty one.
   */
  private void assertNotFound(String path) throws Exception {
    HttpResponse<String> response = CLIENT.send(
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + SERVER.getLocalPort() + path))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    Assertions.assertEquals(404, response.statusCode(), path + " should answer 404");
    Assertions.assertTrue(response.body().contains(ABSENT_UUID),
        path + " answered 404 without naming the artifact, so the route may simply be missing: "
            + response.body());
  }

}
