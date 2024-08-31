package dev.itobey.adapter.api.fddb.exporter.adapter;

import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Adapter to the FDDB API.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FddbAdapter {

    private final FddbApi fddbApi;

    public String retrieveDataToTimeframe(Timeframe timeframe) {
        log.debug("retrieving fddb data for timeframe {}", timeframe);
        return fddbApi.getDiary(timeframe.getFrom(), timeframe.getTo());
    }

}
