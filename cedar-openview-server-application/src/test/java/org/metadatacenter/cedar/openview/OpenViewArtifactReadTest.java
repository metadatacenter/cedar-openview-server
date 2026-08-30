package org.metadatacenter.cedar.openview;

import com.fasterxml.jackson.databind.JsonNode;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.EmbeddedCedarMongo;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Exercises an anonymous OpenView read through both the graph and Mongo persistence layers. */
class OpenViewArtifactReadTest {

  static {
    EmbeddedCedarMongo.startAndRedirectEnvironment(Map.of(
        "CEDAR_OPENVIEW_HTTP_PORT", "19031",
        "CEDAR_OPENVIEW_ADMIN_PORT", "19131",
        "CEDAR_OPENVIEW_STOP_PORT", "19231",
        "CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startAndRedirectEnvironment();
  }

  private static final DropwizardTestSupport<OpenViewServerConfiguration> SERVER =
      new DropwizardTestSupport<>(OpenViewServerApplication.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static String templateId;

  @BeforeAll
  static void startServer() throws Exception {
    SERVER.before();
    CedarConfig cedarConfig = CedarConfig.getInstance(
        CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_OPENVIEW));
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    EmbeddedCedarNeo4j.seed(cedarConfig);

    CedarRequestContext context = CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig));
    FolderServiceSession folderSession = CedarDataServices.getInstance().getFolderServiceSession(context);
    FolderServerTemplate graphTemplate = new FolderServerTemplate();
    templateId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE);
    graphTemplate.setId(templateId);
    graphTemplate.setName("OpenView readable template");
    graphTemplate.setDescription("Anonymous success-path fixture");
    graphTemplate.setVersion("1.0.0");
    graphTemplate.setPublicationStatus("bibo:draft");
    graphTemplate.setLatestVersion(true);
    graphTemplate.setLatestDraftVersion(true);
    graphTemplate.setLatestPublishedVersion(false);
    Assertions.assertNotNull(folderSession.createResourceAsChildOfId(
        graphTemplate, folderSession.findHomeFolderOf().getResourceId()));
    CedarArtifactId artifactId = CedarArtifactId.build(templateId, CedarResourceType.TEMPLATE);
    Assertions.assertTrue(folderSession.setOpen(artifactId));

    com.mongodb.client.MongoClient mongoClient =
        CedarDataServices.getInstance().getMongoClientFactoryForDocuments().getClient();
    org.metadatacenter.config.MongoConfig mongoConfig = cedarConfig.getArtifactServerConfig();
    mongoClient.getDatabase(mongoConfig.getDatabaseName())
        .getCollection(mongoConfig.getMongoCollectionName(CedarResourceType.TEMPLATE))
        .insertOne(new Document("_id", "private-mongo-id")
            .append("@id", templateId)
            .append("schema:name", "OpenView readable template"));
  }

  @AfterAll
  static void stopServer() {
    SERVER.after();
  }

  @Test
  void anonymousReadReturnsOpenArtifactWithoutMongoId() throws Exception {
    String encodedId = URLEncoder.encode(templateId, StandardCharsets.UTF_8);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + SERVER.getLocalPort() + "/templates/" + encodedId))
        .GET()
        .build();

    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    Assertions.assertEquals(200, response.statusCode(), response.body());
    JsonNode artifact = JsonMapper.MAPPER.readTree(response.body());
    Assertions.assertEquals(templateId, artifact.path("@id").asText());
    Assertions.assertEquals("OpenView readable template", artifact.path("schema:name").asText());
    Assertions.assertTrue(artifact.path("_id").isMissingNode(), response.body());
  }
}
