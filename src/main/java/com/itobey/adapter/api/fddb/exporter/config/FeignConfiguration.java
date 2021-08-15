package com.itobey.adapter.api.fddb.exporter.config;

import com.itobey.adapter.api.fddb.exporter.adapter.FddbApi;
import feign.Feign;
import feign.okhttp.OkHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The configuration for feign.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class FeignConfiguration {

    /**
     * Create the API for FDDB.
     *
     * @return the @{@link FddbApi}
     */
    @Bean
    public FddbApi createFddbApi() {
        return Feign.builder()
                .client(new OkHttpClient())
                .decode404()
                .target(FddbApi.class, "https://fddb.info");
    }

}
