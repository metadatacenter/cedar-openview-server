package org.metadatacenter.cedar.openview.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;

/** The complete public representation of one child in an OpenView folder listing. */
@Schema(name = "OpenViewResourceSummary")
public class OpenViewResourceSummary {

  private final String id;
  private final CedarResourceType resourceType;
  private final String name;

  private OpenViewResourceSummary(String id, CedarResourceType resourceType, String name) {
    this.id = id;
    this.resourceType = resourceType;
    this.name = name;
  }

  public static OpenViewResourceSummary from(FolderServerResourceExtract resource) {
    return new OpenViewResourceSummary(resource.getId(), resource.getType(), resource.getName());
  }

  @JsonProperty("@id")
  public String getId() {
    return id;
  }

  public CedarResourceType getResourceType() {
    return resourceType;
  }

  @JsonProperty("schema:name")
  public String getName() {
    return name;
  }
}
