package org.metadatacenter.cedar.openview.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;

/** An ancestor name exposed for the OpenView breadcrumb, with no identifier or private metadata. */
@Schema(name = "OpenViewPathSegment")
public class OpenViewPathSegment {

  private final String name;

  private OpenViewPathSegment(String name) {
    this.name = name;
  }

  public static OpenViewPathSegment from(FolderServerResourceExtract resource) {
    return new OpenViewPathSegment(resource.getName());
  }

  @JsonProperty("schema:name")
  public String getName() {
    return name;
  }
}
