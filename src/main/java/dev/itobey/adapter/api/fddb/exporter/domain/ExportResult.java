package dev.itobey.adapter.api.fddb.exporter.domain;

import lombok.Data;

import java.util.List;

@Data
public class ExportResult {
    private List<String> successfulDays;
    private List<String> unsuccessfulDays;
}