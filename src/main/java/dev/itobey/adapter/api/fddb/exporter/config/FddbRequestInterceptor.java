package dev.itobey.adapter.api.fddb.exporter.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RequiredArgsConstructor
public class FddbRequestInterceptor implements RequestInterceptor {

    private final FddbExporterProperties properties;
    private final FddbSession fddbSession;

    @Override
    public void apply(RequestTemplate template) {
        if (template.feignTarget().url().startsWith(properties.getFddb().getUrl())) {
            String password = properties.getFddb().getPassword();
            String username = properties.getFddb().getUsername();
            template.header("Cookie", "fddb=" + fddbSession.getCookie(template.feignTarget().url()));
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            template.header("Authorization", "Basic " + auth);
        }
    }
}
