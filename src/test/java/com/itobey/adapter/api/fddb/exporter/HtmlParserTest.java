package com.itobey.adapter.api.fddb.exporter;

import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import com.itobey.adapter.api.fddb.exporter.service.HtmlParser;
import org.apache.http.auth.AuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Test for @{@link HtmlParser}
 */
public class HtmlParserTest {

    @Test
    public void testHtml() throws IOException, AuthenticationException {
        ClassPathResource classPathResource = new ClassPathResource("response.html");
        String html = Files.readString(classPathResource.getFile().toPath(), StandardCharsets.ISO_8859_1);

        HtmlParser htmlParser = new HtmlParser();

        FddbData dataFromResponse = htmlParser.getDataFromResponse(html);

        System.out.println(dataFromResponse);
        //TODO
    }

    // TODO test for unauthenticated

}
