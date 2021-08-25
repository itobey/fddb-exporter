package com.itobey.adapter.api.fddb.exporter.adapter;

import feign.Headers;
import feign.Param;
import feign.RequestLine;

/**
 * API for FDDB.info
 */
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
    @RequestLine("GET /db/i18n/myday20/?lang=de&q={TO}&p={FROM}")
    @Headers({"Authorization: Basic {BASICAUTH}", "Cookie: fddb={COOKIE}"})
    String getDiary(@Param("FROM") long from, @Param("TO") long to, @Param("BASICAUTH") String basicauth, @Param("COOKIE") String cookie);

}
