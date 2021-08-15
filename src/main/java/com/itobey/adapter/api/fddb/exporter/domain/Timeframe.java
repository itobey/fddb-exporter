package com.itobey.adapter.api.fddb.exporter.domain;

import com.itobey.adapter.api.fddb.exporter.adapter.FddbApi;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Contains the timeframe used to connect to the @{@link FddbApi}.
 */
@RequiredArgsConstructor
@Builder
@Data
public class Timeframe {

    private final long from;
    private final long to;

}
