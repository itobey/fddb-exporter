package dev.itobey.adapter.api.fddb.exporter.dto;

import dev.itobey.adapter.api.fddb.exporter.adapter.FddbApi;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Contains the timeframe used to connect to the @{@link FddbApi}.
 */
@RequiredArgsConstructor
@Builder
@Data
public class TimeframeDTO {

    private final long from;
    private final long to;

}
