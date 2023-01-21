package com.itobey.adapter.api.fddb.exporter;

import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import com.itobey.adapter.api.fddb.exporter.service.HtmlParser;
import lombok.SneakyThrows;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for @{@link HtmlParser}
 */
public class HtmlParserTest {

    @Test
    @SneakyThrows
    public void getDataFromResponse_whenResponseValid_shouldReturnData() {
        // given
        ClassPathResource classPathResource = new ClassPathResource("validResponse.html");
        String html = Files.readString(classPathResource.getFile().toPath(), StandardCharsets.ISO_8859_1);
        HtmlParser htmlParser = new HtmlParser();
        // when
        FddbData dataFromResponse = htmlParser.getDataFromResponse(html);
        // then
        assertEquals(2170, dataFromResponse.getKcal());
        assertEquals(149, dataFromResponse.getFat());
        assertEquals(99, dataFromResponse.getCarbs());
        assertEquals(41, dataFromResponse.getSugar());
        assertEquals(91, dataFromResponse.getProtein());
        assertEquals(30, dataFromResponse.getFiber());
        assertNull(dataFromResponse.getDate());
    }

    @Test
    @SneakyThrows
    public void getDataFromResponse_whenUnauthenticated_shouldReturnData() {
        // given
        ClassPathResource classPathResource = new ClassPathResource("unauthenticated.html");
        String html = Files.readString(classPathResource.getFile().toPath(), StandardCharsets.ISO_8859_1);
        HtmlParser htmlParser = new HtmlParser();
        // when; then
        Exception exception = assertThrows(AuthenticationException.class, () ->
                htmlParser.getDataFromResponse(html)
        );
        String expectedMessage = "not logged into FDDB";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    @SneakyThrows
    public void getDataFromResponse_whenNoDataAvailable() {
        // given
        ClassPathResource classPathResource = new ClassPathResource("no-data-available.html");
        String html = Files.readString(classPathResource.getFile().toPath(), StandardCharsets.ISO_8859_1);
        HtmlParser htmlParser = new HtmlParser();
        // when; then
        Exception exception = assertThrows(ParseException.class, () ->
                htmlParser.getDataFromResponse(html)
        );
        String expectedMessage = "no value found";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }

}
