package dev.itobey.adapter.api.fddb.exporter.adapter;

import dev.itobey.adapter.api.fddb.exporter.config.FddbFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * API for FDDB.info
 */
@FeignClient(name = "fddbApi", url = "${fddb-exporter.fddb.url}", configuration = FddbFeignConfig.class)
public interface FddbApi {

    /**
     * API call to get the diary containing all necessary data.
     *
     * @param from      beginning of timeframe for search of data
     * @param to        end of timeframe for search of data
     * @return the HTML response containing the data
     */
    @GetMapping("/db/i18n/myday20/?lang=en&q={to}&p={from}")
    String getDiary(
            @RequestParam("from") long from,
            @RequestParam("to") long to
    );
}
