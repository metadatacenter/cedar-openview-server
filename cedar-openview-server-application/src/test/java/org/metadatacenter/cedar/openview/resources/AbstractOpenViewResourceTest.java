package org.metadatacenter.cedar.openview.resources;

import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarTemplateId;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AbstractOpenViewResourceTest {

  @Test
  void artifactStoreFailureIsAClientSafeServiceUnavailableResponse() {
    CedarTemplateId id = CedarTemplateId.build(
        "https://repo.metadatacenter.org/templates/11111111-2222-3333-4444-555555555555");

    Response response = AbstractOpenViewResource.artifactStoreUnavailable(
        id, new IOException("Connect to mongodb://secret-host:27017 failed"));

    assertEquals(503, response.getStatus());
    Map<?, ?> entity = (Map<?, ?>) response.getEntity();
    assertEquals("Artifact store is unavailable", entity.get("errorMessage"));
    assertEquals(id, entity.get("parameters") instanceof Map<?, ?> parameters ? parameters.get("id") : null);
    assertNotNull(entity.get("errorId"), "server logs need a correlation id for the hidden exception");
    assertFalse(entity.toString().contains("secret-host"), "the response must not expose the Mongo endpoint");
    assertFalse(entity.toString().contains("IOException"), "the response must not expose the exception type");
  }
}
