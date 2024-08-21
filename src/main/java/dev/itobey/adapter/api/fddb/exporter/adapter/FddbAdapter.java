package dev.itobey.adapter.api.fddb.exporter.adapter;

import dev.itobey.adapter.api.fddb.exporter.config.ConfigProperties;
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
    private final ConfigProperties configProperties;

    /**
     * Retrieve the diary containing all information to a specific timeframe.
     *
     * @return
     */
    public String retrieveDataToTimeframe(Timeframe timeframe) {
        log.debug("retrieving fddb data for timeframe {}", timeframe);
        ConfigProperties.Fddb fddb = configProperties.getFddb();
        String basicauthHeader = "Basic " + fddb.getBasicauth();
        String cookieHeader = "fddb=" + fddb.getCookie();
        return fddbApi.getDiary(timeframe.getFrom(), timeframe.getTo(), basicauthHeader, cookieHeader);
    }

}
