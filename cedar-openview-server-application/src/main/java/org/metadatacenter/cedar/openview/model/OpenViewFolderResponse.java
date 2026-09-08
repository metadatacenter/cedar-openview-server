package org.metadatacenter.cedar.openview.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.util.FolderServerNodeContext;

import java.util.List;
import java.util.Map;

/** Public, caller-independent response for an anonymously readable folder. */
@Schema(name = "OpenViewFolderResponse")
public class OpenViewFolderResponse {

  private final NodeListRequest request;
  private final long totalCount;
  private final long currentOffset;
  private final Map<String, String> paging;
  private final List<OpenViewResourceSummary> resources;
  private final List<OpenViewPathSegment> pathInfo;

  public OpenViewFolderResponse(NodeListRequest request, long totalCount, Map<String, String> paging,
                                List<FolderServerResourceExtract> resources,
                                List<FolderServerResourceExtract> pathInfo) {
    this.request = request;
    this.totalCount = totalCount;
    this.currentOffset = request.getOffset();
    this.paging = paging;
    this.resources = resources.stream().map(OpenViewResourceSummary::from).toList();
    this.pathInfo = pathInfo.stream().map(OpenViewPathSegment::from).toList();
  }

  public NodeListRequest getRequest() {
    return request;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public long getCurrentOffset() {
    return currentOffset;
  }

  public Map<String, String> getPaging() {
    return paging;
  }

  public List<OpenViewResourceSummary> getResources() {
    return resources;
  }

  public List<OpenViewPathSegment> getPathInfo() {
    return pathInfo;
  }

  public NodeListQueryType getNodeListQueryType() {
    return NodeListQueryType.FOLDER_CONTENT;
  }

  @JsonProperty("@context")
  public Map<String, String> getContext() {
    return FolderServerNodeContext.getContext();
  }
}
