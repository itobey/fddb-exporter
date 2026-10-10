package dev.itobey.adapter.api.fddb.exporter.adapter;

import dev.itobey.adapter.api.fddb.exporter.config.FddbSession;
import dev.itobey.adapter.api.fddb.exporter.dto.TimeframeDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbParserService;
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
    private final FddbParserService fddbParserService;
    private final FddbSession fddbSession;

    public String retrieveDataToTimeframe(TimeframeDTO timeframeDTO) {
        log.debug("retrieving fddb data for timeframe {}", timeframeDTO);
        String html = fddbApi.getDiary(timeframeDTO.getFrom(), timeframeDTO.getTo());

        // fddb.info answers a request with an expired session with the logged-out page and HTTP 200, so
        // nothing below this would notice. Drop the cached cookie and try once more with a fresh login -
        // without this a session that expires on a long-running container makes every export fail with
        // "please check credentials" until someone restarts the application.
        if (fddbParserService.isLoggedOut(html)) {
            log.info("FDDB session appears to have expired - logging in again and retrying");
            fddbSession.invalidate();
            html = fddbApi.getDiary(timeframeDTO.getFrom(), timeframeDTO.getTo());
        }
        return html;
    }

}
