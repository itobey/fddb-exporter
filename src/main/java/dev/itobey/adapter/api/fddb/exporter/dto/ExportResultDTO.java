package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExportResultDTO {
    private List<String> successfulDays;
    private List<String> unsuccessfulDays;
}