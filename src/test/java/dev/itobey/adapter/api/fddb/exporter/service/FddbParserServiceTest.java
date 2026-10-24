package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.EmptyDiaryException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FddbParserServiceTest {

    private FddbParserService parser;
    private Document diary;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        parser = new FddbParserService();
        diary = Jsoup.parse(new ClassPathResource("valid-response.html")
                .getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void parseDiary_whenNewDesignHasEnglishValues_shouldParseProductsAndTotals() {
        FddbData data = parser.parseDiary(diary.outerHtml());

        assertThat(data.getTotalCalories()).isEqualTo(1391);
        assertThat(data.getTotalFat()).isEqualTo(97.5);
        assertThat(data.getTotalCarbs()).isEqualTo(185.25);
        assertThat(data.getTotalProtein()).isEqualTo(282.75);
        assertThat(data.getTotalSugar()).isEqualTo(12.5);
        assertThat(data.getTotalFibre()).isEqualTo(23.75);
        // The four meal subtotal rows must not become products.
        assertThat(data.getProducts()).hasSize(13);
        for (int i = 1; i <= 13; i++) {
            Product product = data.getProducts().get(i - 1);
            assertThat(product.getName()).isEqualTo("Test product " + i);
            assertThat(product.getAmount()).isEqualTo(i * 10 + " g");
            assertThat(product.getLink()).isEqualTo(
                    "https://fddb.info/db/en/food/test_product_" + i + "/index.html");
            assertThat(product.getCalories()).isEqualTo(100 + i);
            assertThat(product.getFat()).isEqualTo(i + 0.5);
            assertThat(product.getCarbs()).isEqualTo(2 * i + 0.25);
            assertThat(product.getProtein()).isEqualTo(3 * i + 0.75);
        }
        assertThat(parser.isLoggedOut(diary.outerHtml())).isFalse();
    }

    @Test
    void parseDiary_whenRecipesAndAiFoodsHaveNoLinks_shouldParseNamesAmountsAndNutrients() {
        var rows = diary.select(".myday-table-std tr:has(a)");
        String[] labels = {"1 Portion Test burger 🍔 (mit Käse)", "230 g Test vegetable soup", "1 Portion Test roll"};
        for (int i = 0; i < labels.length; i++) {
            rows.get(i).child(0).html("<span class='mydayshowtime' style='display:none;'>14:05&nbsp;</span>"
                    + labels[i]);
        }

        FddbData data = parser.parseDiary(diary.outerHtml());

        assertThat(data.getProducts()).hasSize(13);
        assertThat(data.getProducts().subList(0, 3)).extracting(Product::getName)
                .containsExactly("Test burger 🍔 (mit Käse)", "Test vegetable soup", "Test roll");
        assertThat(data.getProducts().subList(0, 3)).extracting(Product::getAmount)
                .containsExactly("1 Portion", "230 g", "1 Portion");
        for (int i = 0; i < 3; i++) {
            Product product = data.getProducts().get(i);
            assertThat(product.getLink()).isNull();
            assertThat(product.getCalories()).isEqualTo(101 + i);
            assertThat(product.getFat()).isEqualTo(1.5 + i);
            assertThat(product.getCarbs()).isEqualTo(2.25 + 2 * i);
            assertThat(product.getProtein()).isEqualTo(3.75 + 3 * i);
        }
        assertThat(data.getTotalCalories()).isEqualTo(1391);
    }

    @Test
    void parseDiary_whenCarbsIncludeBreadUnits_shouldReadGramsOnly() {
        diary.select(".myday-table-std tr:has(a)").first().child(3)
                .append("<span>(1.5 BE)</span>");
        assertThat(parser.parseDiary(diary.outerHtml()).getProducts().getFirst().getCarbs()).isEqualTo(2.25);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "true | Teelöffel Hummus, Dip aus Kichererbsen | Teelöffel | Hummus, Dip aus Kichererbsen",
            "false | Teelöffel Hummus, Dip aus Kichererbsen | Teelöffel | Hummus, Dip aus Kichererbsen",
            "true | Esslöffel Test spread | Esslöffel | Test spread",
            "false | 0.5 Stück Test roll | 0.5 Stück | Test roll",
            "true | 0,5 Portion Test soup | 0,5 Portion | Test soup",
            "true | 1/2 Becher Test yoghurt | 1/2 Becher | Test yoghurt",
            "false | Test food without amount | | Test food without amount",
            "true | Test food without amount | | Test food without amount",
            "false | Testfood | | Testfood"
    })
    void parseDiary_whenAmountHasDifferentFormats_shouldPreserveNameAndAmount(
            boolean linked, String label, String expectedAmount, String expectedName) {
        Element productCell = diary.select(".myday-table-std tr:has(a)").first().child(0);
        String link = productCell.selectFirst("a").attr("href");
        productCell.empty().appendElement("span").addClass("mydayshowtime").text("14:05\u00a0");
        if (linked) {
            productCell.appendElement("a").attr("href", link).text(label);
        } else {
            productCell.appendText(label);
        }

        Product product = parser.parseDiary(diary.outerHtml()).getProducts().getFirst();

        assertThat(product.getName()).isEqualTo(expectedName);
        assertThat(product.getAmount()).isEqualTo(expectedAmount);
        assertThat(product.getLink()).isEqualTo(linked ? link : null);
    }

    @Test
    void parseDiary_whenTableHasAdditionalClasses_shouldStillFindIt() {
        diary.select(".myday-table-std").addClass("responsive");
        assertThat(parser.parseDiary(diary.outerHtml()).getProducts()).hasSize(13);
    }

    @Test
    void parseDiary_whenEnergyHasSeparateLegacyColumns_shouldStillParse() {
        diary.select(".myday-table-std th").get(1).attr("colspan", "2");
        diary.select(".myday-table-std tr:has(td)").forEach(row -> {
            var energy = row.child(1);
            String calories = energy.selectFirst(".md-kcal").text();
            energy.before("<td>0 kJ</td>");
            energy.text(calories);
        });
        FddbData data = parser.parseDiary(diary.outerHtml());
        assertThat(data.getTotalCalories()).isEqualTo(1391);
        assertThat(data.getTotalProtein()).isEqualTo(282.75);
        assertThat(data.getProducts()).hasSize(13);
        assertThat(data.getProducts().getFirst().getCalories()).isEqualTo(101);

        // A missing legacy cell must fail rather than be mistaken for the new five-column layout.
        diary.select(".myday-table-std tr:has(a)").first().child(5).remove();
        assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> parser.parseDiary(diary.outerHtml()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"thereof Sugar", "Dietary fiber"})
    void parseDiary_whenSummaryNutrientIsMissing_shouldThrowParseException(String label) {
        diary.select(".fddb-myday-nutrients tr").stream()
                .filter(row -> row.selectFirst("td").text().equals(label)).forEach(row -> row.remove());
        assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> parser.parseDiary(diary.outerHtml()))
                .withMessageContaining("nutrient summary");
    }

    @Test
    void parseDiary_whenProductIsNamedLikeSummary_shouldNotReadItAsSummary() {
        diary.select(".myday-table-std a").first().text("10 g thereof Sugar");
        assertThat(parser.parseDiary(diary.outerHtml()).getTotalSugar()).isEqualTo(12.5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Anmelden", "Login"})
    void parseDiary_whenNewHeaderShowsLogin_shouldRejectAuthentication(String label) {
        diary.body().prepend("<div class='topbar-links'><a href='/login/'>" + label + "</a></div>");
        assertThat(parser.isLoggedOut(diary.outerHtml())).isTrue();
        assertThatExceptionOfType(AuthenticationException.class)
                .isThrownBy(() -> parser.parseDiary(diary.outerHtml()));
    }

    @Test
    void parseDiary_whenNoDiaryTableIsAvailable_shouldThrowParseException() {
        diary.select(".myday-table-std").remove();
        assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> parser.parseDiary(diary.outerHtml()))
                .isNotInstanceOf(EmptyDiaryException.class);
    }

    @Test
    @SneakyThrows
    void parseDiary_whenCapturedEmptyDiary_shouldReportEmptyDiary() {
        String html = new ClassPathResource("no-data-available.html").getContentAsString(StandardCharsets.UTF_8);
        assertThatExceptionOfType(EmptyDiaryException.class).isThrownBy(() -> parser.parseDiary(html));
    }

    @Test
    @SneakyThrows
    void parseDiary_whenSummaryShowsConsumptionButTableIsMissing_shouldReportFormatError() {
        Document empty = Jsoup.parse(new ClassPathResource("no-data-available.html")
                .getContentAsString(StandardCharsets.UTF_8));
        empty.select(".fddb-myday-subline").html("<b>418 kJ</b> (100 kcal) from <b>8400 kJ</b>");
        assertThatExceptionOfType(ParseException.class).isThrownBy(() -> parser.parseDiary(empty.outerHtml()))
                .isNotInstanceOf(EmptyDiaryException.class);
    }

    @Test
    @SneakyThrows
    void parseDiary_whenEmptySummaryIsMissing_shouldReportFormatError() {
        Document empty = Jsoup.parse(new ClassPathResource("no-data-available.html")
                .getContentAsString(StandardCharsets.UTF_8));
        empty.select(".md-summary").remove();
        assertThatExceptionOfType(ParseException.class).isThrownBy(() -> parser.parseDiary(empty.outerHtml()))
                .isNotInstanceOf(EmptyDiaryException.class);
    }

    @Test
    void parseDiary_whenZeroSummaryStillHasProducts_shouldParseTheTable() {
        diary.select(".fddb-myday").prepend("<div class='md-summary'>"
                + "<div class='fddb-myday-subline'><b>0 kJ</b> (0 kcal) from <b>8400 kJ</b></div></div>");
        assertThat(parser.parseDiary(diary.outerHtml()).getProducts()).hasSize(13);
    }

    @Test
    void parseDiary_whenOnlyHeadersAndMealTotalsRemain_shouldThrowParseException() {
        diary.select(".myday-table-std tr:has(a), .myday-table-std tr:has(.myday-table-std-td-footer)").remove();
        assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> parser.parseDiary(diary.outerHtml()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-"})
    void parseDiary_whenNutrientCellIsNotNumeric_shouldThrowParseException(String value) {
        diary.select(".myday-table-std tr:has(a)").first().child(2).text(value);
        assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> parser.parseDiary(diary.outerHtml()))
                .withMessageContaining("fat");
    }

    @Test
    void parseDiary_whenProductRowIsTruncated_shouldThrowParseException() {
        diary.select(".myday-table-std tr:has(a)").first().child(4).remove();
        assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> parser.parseDiary(diary.outerHtml()));
    }

    @Test
    void parseDiary_whenTotalsRowIsTruncated_shouldThrowParseException() {
        diary.select(".myday-table-std tr").last().child(4).remove();
        assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> parser.parseDiary(diary.outerHtml()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid-response.html", "no-data-available.html"})
    @SneakyThrows
    void fixture_shouldContainOnlyPassiveDiaryMarkupAndSyntheticLinks(String fixtureName) {
        Document fixture = Jsoup.parse(new ClassPathResource(fixtureName).getContentAsString(StandardCharsets.UTF_8));
        assertThat(fixture.select("script, style, link, iframe, img, svg, form, input, object, embed")).isEmpty();
        fixture.getAllElements().forEach(element -> element.attributes().forEach(attribute ->
                assertThat(attribute.getKey()).isIn("lang", "charset", "class", "href")));
        assertThat(fixture.select("a")).hasSize(fixtureName.equals("valid-response.html") ? 13 : 0).allSatisfy(link ->
                assertThat(link.attr("href")).matches(
                        "https://fddb\\.info/db/en/food/test_product_[0-9]+/index\\.html"));
    }
}
