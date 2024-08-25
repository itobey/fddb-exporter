package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.math.BigDecimal;
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
        Resource resource = new ClassPathResource("fddb-new.html");
        Path path = resource.getFile().toPath();
        String content = Files.readString(path, StandardCharsets.UTF_8);

        // When
        FddbData fddbData = fddbParserService.parseDiary(content);

        // Then
        assertNotNull(fddbData);
        assertEquals(2565, fddbData.getTotalCalories());
        assertEquals(new BigDecimal("110.4"), fddbData.getTotalFat());
        assertEquals(new BigDecimal("246.2"), fddbData.getTotalCarbs());
        assertEquals(new BigDecimal("126.4"), fddbData.getTotalProtein());
        assertEquals(new BigDecimal("51"), fddbData.getTotalSugar());
        assertEquals(new BigDecimal("18.3"), fddbData.getTotalFibre());

        List<Product> products = fddbData.getProducts();
        assertEquals(19, products.size());

        // Check a few specific products
        Product pizza = products.getFirst();
        assertEquals("Pizza", pizza.getName());
        assertEquals("150 g", pizza.getAmount());
        assertEquals(300, pizza.getCalories());
        assertEquals(new BigDecimal("10.5"), pizza.getFat());
        assertEquals(new BigDecimal("30"), pizza.getCarbs());
        assertEquals(new BigDecimal("13.5"), pizza.getProtein());
        assertEquals("https://fddb.info/db/en/food/selbstgemacht_pizza/index.html", pizza.getLink());

        Product amaranth = products.get(1);
        assertEquals("Bio Amaranth gepufft", amaranth.getName());
        assertEquals("10 g", amaranth.getAmount());
        assertEquals(37, amaranth.getCalories());
        assertEquals(new BigDecimal("0.5"), amaranth.getFat());
        assertEquals(new BigDecimal("6.5"), amaranth.getCarbs());
        assertEquals(new BigDecimal("1.2"), amaranth.getProtein());
        assertEquals("https://fddb.info/db/en/food/antersdorfer_muehle_bio_amaranth_gepufft/index.html", amaranth.getLink());

        Product senf = products.get(18);
        assertEquals("Senf", senf.getName());
        assertEquals("30 g", senf.getAmount());
        assertEquals(26, senf.getCalories());
        assertEquals(new BigDecimal("1.2"), senf.getFat());
        assertEquals(new BigDecimal("1.8"), senf.getCarbs());
        assertEquals(new BigDecimal("1.8"), senf.getProtein());
        assertEquals("https://fddb.info/db/en/food/durchschnittswert_senf/index.html", senf.getLink());
    }

    @Test
    void parseDiary_whenNotLoggedIn_shouldThrowException() {
        //TODO
    }

    @Test
    void parseDiary_whenLoggedInAndNoDataAvailable_shouldThrowException() {
        //TODO
    }
}