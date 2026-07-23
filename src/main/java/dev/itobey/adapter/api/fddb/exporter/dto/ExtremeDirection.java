package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether extreme days should be the top or the bottom of the ranking.
 */
@Schema(description = "Direction for extreme day lookups")
public enum ExtremeDirection {

    HIGHEST,
    LOWEST
}
