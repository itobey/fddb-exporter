package dev.itobey.adapter.api.fddb.exporter.dto;

/** One completed day, or a terminal event, in a newline-delimited export stream. */
public record ExportProgressDTO(String date, boolean successful, boolean complete, String error) {
}
