package org.metadatacenter.cedar.openview.resources;

import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.core.Response;
import java.io.IOException;

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
    CedarError error = (CedarError) response.getEntity();
    assertEquals("Artifact store is unavailable", error.errorMessage);
    assertEquals(id, error.parameters.get("id"));
    assertNotNull(error.errorId, "server logs need a correlation id for the hidden exception");
    String rendered = JsonMapper.MAPPER.valueToTree(error).toString();
    assertFalse(rendered.contains("secret-host"), "the response must not expose the Mongo endpoint");
    assertFalse(rendered.contains("IOException"), "the response must not expose the exception type");
  }
}
