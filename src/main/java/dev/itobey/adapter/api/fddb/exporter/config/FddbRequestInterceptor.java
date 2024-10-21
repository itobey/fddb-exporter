package dev.itobey.adapter.api.fddb.exporter.config;

import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@RequiredArgsConstructor
public class FddbRequestInterceptor implements RequestInterceptor {

    private final FddbExporterProperties properties;

    private String fddbCookie;

    @Override
    public void apply(RequestTemplate template) {
        if (template.feignTarget().url().startsWith(properties.getFddb().getUrl())) {
            if (fddbCookie == null) {
                fddbCookie = login(template.feignTarget().url());
            }
            String password = properties.getFddb().getPassword();
            String username = properties.getFddb().getUsername();
            template.header("Cookie", "fddb=" + fddbCookie);
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            template.header("Authorization", "Basic " + auth);
        }
    }

    private String login(String baseUrl) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("loginemailorusername", properties.getFddb().getUsername());
        map.add("loginpassword", properties.getFddb().getPassword());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/db/i18n/account/?lang=de&action=login",
                request,
                String.class
        );

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("fddb=")) {
                    return cookie.split(";")[0].substring(5);
                }
            }
        }
        throw new AuthenticationException("Login to FDDB not successful, please check credentials");
    }
}