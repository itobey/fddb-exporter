package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Based on https://fddb.info/db/i18n/myday20/?%20\%20lang=de&p=1724105323&q=1724191723
 */
class FddbParserServiceTest {

    private FddbParserService fddbParserService;

    @BeforeEach
    void setUp() {
        fddbParserService = new FddbParserService();
    }

    @Test
    @SneakyThrows
    void parseDiary_whenLoggedInAndDataAvailable_shouldParseAccordingly() {
        // Given
        Resource resource = new ClassPathResource("valid-response.html");
        Path path = resource.getFile().toPath();
        String content = Files.readString(path, StandardCharsets.UTF_8);

        // When
        FddbData fddbData = fddbParserService.parseDiary(content);

        // Then
        assertNotNull(fddbData);
        assertEquals(2565, fddbData.getTotalCalories());
        assertEquals(110.4, fddbData.getTotalFat());
        assertEquals(246.2, fddbData.getTotalCarbs());
        assertEquals(126.4, fddbData.getTotalProtein());
        assertEquals(51, fddbData.getTotalSugar());
        assertEquals(18.3, fddbData.getTotalFibre());

        List<Product> products = fddbData.getProducts();
        assertEquals(19, products.size());

        // Check a few specific products
        Product pizza = products.getFirst();
        assertEquals("Pizza", pizza.getName());
        assertEquals("150 g", pizza.getAmount());
        assertEquals(300, pizza.getCalories());
        assertEquals(10.5, pizza.getFat());
        assertEquals(30, pizza.getCarbs());
        assertEquals(13.5, pizza.getProtein());
        assertEquals("https://fddb.info/db/en/food/selbstgemacht_pizza/index.html", pizza.getLink());

        Product amaranth = products.get(1);
        assertEquals("Bio Amaranth gepufft", amaranth.getName());
        assertEquals("10 g", amaranth.getAmount());
        assertEquals(37, amaranth.getCalories());
        assertEquals(0.5, amaranth.getFat());
        assertEquals(6.5, amaranth.getCarbs());
        assertEquals(1.2, amaranth.getProtein());
        assertEquals("https://fddb.info/db/en/food/antersdorfer_muehle_bio_amaranth_gepufft/index.html", amaranth.getLink());

        Product senf = products.get(18);
        assertEquals("Senf", senf.getName());
        assertEquals("30 g", senf.getAmount());
        assertEquals(26, senf.getCalories());
        assertEquals(1.2, senf.getFat());
        assertEquals(1.8, senf.getCarbs());
        assertEquals(1.8, senf.getProtein());
        assertEquals("https://fddb.info/db/en/food/durchschnittswert_senf/index.html", senf.getLink());
    }

    @Test
    @SneakyThrows
    void parseDiary_whenNotLoggedIn_shouldThrowException() {
        // Given
        Resource resource = new ClassPathResource("__files/unauthenticated.html");
        Path path = resource.getFile().toPath();
        String content = Files.readString(path, StandardCharsets.UTF_8);

        // When; Then
        Assertions.assertThatExceptionOfType(AuthenticationException.class)
                .isThrownBy(() -> fddbParserService.parseDiary(content));
    }

    @Test
    @SneakyThrows
    void parseDiary_whenLoggedInAndNoDataAvailable_shouldThrowException() {
        // Given
        Resource resource = new ClassPathResource("no-data-available.html");
        Path path = resource.getFile().toPath();
        String content = Files.readString(path, StandardCharsets.UTF_8);

        // When; Then
        Assertions.assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> fddbParserService.parseDiary(content));
    }
}