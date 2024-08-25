package dev.itobey.adapter.api.fddb.exporter.adapter;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * API for FDDB.info
 */
@FeignClient(name = "fddbApi", url = "https://fddb.info")
public interface FddbApi {

    /**
     * API call to get the diary containing all necessary data.
     *
     * @param from      beginning of timeframe for search of data
     * @param to        end of timeframe for search of data
     * @param basicauth the basic auth base64 string
     * @param cookie    the necessary cookie, see README.md
     * @return the HTML response containing the data
     */
    @GetMapping("/db/i18n/myday20/?lang=en&q={q}&p={p}")
    String getDiary(
            @RequestParam("p") long from,
            @RequestParam("q") long to,
            @RequestHeader("Authorization") String basicauth,
            @RequestHeader("Cookie") String cookie
    );
}
