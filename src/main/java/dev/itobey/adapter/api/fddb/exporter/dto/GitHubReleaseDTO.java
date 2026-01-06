package dev.itobey.adapter.api.fddb.exporter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.ZonedDateTime;

/**
 * DTO representing a GitHub release.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubReleaseDTO {

    @JsonProperty("tag_name")
    private String tagName;

    @JsonProperty("name")
    private String name;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("published_at")
    private ZonedDateTime publishedAt;

    @JsonProperty("prerelease")
    private boolean prerelease;

    @JsonProperty("draft")
    private boolean draft;
}
