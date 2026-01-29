package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.Getter;

/**
 * Enum representing the supported download formats.
 */
@Getter
public enum DownloadFormat {
    CSV("text/csv", ".csv"),
    JSON("application/json", ".json");

    private final String contentType;
    private final String fileExtension;

    DownloadFormat(String contentType, String fileExtension) {
        this.contentType = contentType;
        this.fileExtension = fileExtension;
    }

}

