package com.itobey.adapter.api.fddb.exporter.adapter;

import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface FddbApi {

    @RequestLine("GET /db/i18n/myday20/?lang=de&q={TO}&p={FROM}")
    @Headers({"Authorization: Basic {BASICAUTH}", "Cookie: fddb={COOKIE}"})
//    @Headers({"Authorization: Basic ZW1ldEBwb3N0ZW8uZGU6QzJiTVdndnlhcHlEMHhxMA==", "Cookie: fddb=2354689%2CRKZ0IGKJP2YOR8J5"})
    String getDiary(@Param("FROM") long from, @Param("TO") long to, @Param("BASICAUTH") String basicauth, @Param("COOKIE") String cookie);

}
