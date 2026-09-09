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
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Exercises an anonymous OpenView read through both the graph and Mongo persistence layers. */
class OpenViewArtifactReadTest {

  static {
    EmbeddedCedarMongo.startAndRedirectEnvironment(Map.of(
        "CEDAR_OPENVIEW_HTTP_PORT", "0",
        "CEDAR_OPENVIEW_ADMIN_PORT", "0",
        "CEDAR_OPENVIEW_STOP_PORT", "0",
        "CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startAndRedirectEnvironment();
  }

  private static final DropwizardTestSupport<OpenViewServerConfiguration> SERVER =
      new DropwizardTestSupport<>(OpenViewServerApplication.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static String templateId;
  private static String inheritedOpenFolderId;
  private static String inheritedOpenTemplateId;
  private static String closedFolderId;

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
    CedarFolderId homeFolderId = folderSession.findHomeFolderOf().getResourceId();
    Assertions.assertNotNull(folderSession.createResourceAsChildOfId(graphTemplate, homeFolderId));
    CedarArtifactId artifactId = CedarArtifactId.build(templateId, CedarResourceType.TEMPLATE);
    Assertions.assertTrue(folderSession.setOpen(artifactId));

    FolderServerFolder openAncestor = createFolder(cedarConfig, folderSession, homeFolderId,
        "OpenView public ancestor");
    Assertions.assertTrue(folderSession.setOpen(openAncestor.getResourceId()));
    FolderServerFolder inheritedOpenFolder = createFolder(cedarConfig, folderSession,
        openAncestor.getResourceId(), "OpenView inherited public child");
    inheritedOpenFolderId = inheritedOpenFolder.getId();

    FolderServerTemplate inheritedOpenTemplate = new FolderServerTemplate();
    inheritedOpenTemplateId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE);
    inheritedOpenTemplate.setId(inheritedOpenTemplateId);
    inheritedOpenTemplate.setName("OpenView inherited public template");
    inheritedOpenTemplate.setDescription("This private graph field must not appear in OpenView");
    inheritedOpenTemplate.setVersion("1.0.0");
    inheritedOpenTemplate.setPublicationStatus("bibo:draft");
    inheritedOpenTemplate.setLatestVersion(true);
    inheritedOpenTemplate.setLatestDraftVersion(true);
    inheritedOpenTemplate.setLatestPublishedVersion(false);
    Assertions.assertNotNull(folderSession.createResourceAsChildOfId(
        inheritedOpenTemplate, inheritedOpenFolder.getResourceId()));

    closedFolderId = createFolder(cedarConfig, folderSession, homeFolderId,
        "OpenView closed folder").getId();

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
    JsonNode artifact = JsonMapper.STRICT_MAPPER.readTree(response.body());
    Assertions.assertEquals(templateId, artifact.path("@id").asText());
    Assertions.assertEquals("OpenView readable template", artifact.path("schema:name").asText());
    Assertions.assertTrue(artifact.path("_id").isMissingNode(), response.body());
  }

  @Test
  void anonymousFolderReadUsesInheritedOpennessAndOnlyThePublicProjection() throws Exception {
    HttpResponse<String> response = getFolder(inheritedOpenFolderId);

    Assertions.assertEquals(200, response.statusCode(), response.body());
    JsonNode body = JsonMapper.STRICT_MAPPER.readTree(response.body());
    Assertions.assertEquals(1, body.path("totalCount").asInt(), response.body());
    Assertions.assertEquals("folder-content", body.path("nodeListQueryType").asText(), response.body());

    JsonNode resource = body.path("resources").path(0);
    Assertions.assertEquals(inheritedOpenTemplateId, resource.path("@id").asText(), response.body());
    Assertions.assertEquals("template", resource.path("resourceType").asText(), response.body());
    Assertions.assertEquals("OpenView inherited public template", resource.path("schema:name").asText(),
        response.body());
    Assertions.assertEquals(Set.of("@id", "resourceType", "schema:name"), fieldNames(resource), response.body());

    Assertions.assertTrue(body.path("pathInfo").isArray(), response.body());
    Assertions.assertTrue(body.path("pathInfo").size() > 1, response.body());
    for (JsonNode segment : body.path("pathInfo")) {
      Assertions.assertEquals(Set.of("schema:name"), fieldNames(segment), response.body());
    }
    Assertions.assertTrue(response.body().contains("OpenView public ancestor"), response.body());
    Assertions.assertTrue(response.body().contains("OpenView inherited public child"), response.body());
    Assertions.assertFalse(response.body().contains("activeUserCanRead"), response.body());
    Assertions.assertFalse(response.body().contains("currentUserPermissions"), response.body());
    Assertions.assertFalse(response.body().contains("ownedBy"), response.body());
    Assertions.assertFalse(response.body().contains("This private graph field"), response.body());
  }

  @Test
  void anonymousFolderReadRefusesAClosedTree() throws Exception {
    HttpResponse<String> response = getFolder(closedFolderId);
    Assertions.assertEquals(401, response.statusCode(), response.body());
  }

  private static FolderServerFolder createFolder(CedarConfig cedarConfig, FolderServiceSession folderSession,
                                                  CedarFolderId parentId, String name) {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setName(name);
    folder.setDescription("Private fixture description");
    CedarFolderId id = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    FolderServerFolder created = folderSession.createFolderAsChildOfId(folder, parentId, id);
    Assertions.assertNotNull(created);
    return created;
  }

  private static HttpResponse<String> getFolder(String folderId) throws Exception {
    String encodedId = URLEncoder.encode(folderId, StandardCharsets.UTF_8);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + SERVER.getLocalPort() + "/folders/" + encodedId))
        .GET()
        .build();
    return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
