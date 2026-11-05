package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductSummaryDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.service.FddbDataClient;
import dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient;
import dev.itobey.adapter.api.fddb.exporter.ui.util.ProductExplorerState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductNavigationTest {
    private UI ui;
    private final FddbDataClient client = mock(FddbDataClient.class);
    private final FddbExporterProperties properties = mock(FddbExporterProperties.class, RETURNS_DEEP_STUBS);
    private final LocalDate monday = LocalDate.of(2026, 10, 12);
    private final LocalDate tuesday = monday.plusDays(1);

    @BeforeEach
    void setUp() {
        ui = new UI();
        UI.setCurrent(ui);
        when(properties.getPersistence().getMongodb().isEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void recreatedProductsViewRestoresQueryWeekdaysSortingAndSelectedOccurrence() throws Exception {
        ProductDTO oats = new ProductDTO();
        oats.setName("Hafer oats");
        ProductWithDateDTO first = new ProductWithDateDTO(monday, oats);
        ProductWithDateDTO second = new ProductWithDateDTO(monday.minusWeeks(1), oats);
        when(client.getProductSummary("hafer", "2026-10-01", "2026-10-31"))
                .thenReturn(ProductSummaryDTO.builder().timesEaten(3).firstDate(second.getDate()).lastDate(tuesday)
                        .weekdayDistribution(Map.of(DayOfWeek.MONDAY, 2L, DayOfWeek.TUESDAY, 1L)).build());
        when(client.searchProducts("hafer", null, "2026-10-01", "2026-10-31", 500))
                .thenReturn(List.of(second, new ProductWithDateDTO(tuesday, oats), first));
        ProductExplorerState state = new ProductExplorerState("hafer", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), Set.of(DayOfWeek.MONDAY), List.of("date:desc"), 1);

        ProductsView restored = new ProductsView(client, properties);
        restored.beforeEnter(enter("products", state.parameters()));

        verify(client).searchProducts("hafer", null, "2026-10-01", "2026-10-31", 500);
        Grid<?> grid = field(restored, "occurrencesGrid", Grid.class);
        assertThat(grid.getListDataView().getItems().toList()).isEqualTo(List.of(first, second));
        assertThat(grid.getSelectedItems()).isEqualTo(Set.of(second));
        assertThat(field(restored, "occurrencesCountLabel", com.vaadin.flow.component.html.Span.class).getText())
                .contains("2 occurrences", "Mon");
    }

    @Test
    void dayReturnLinkKeepsTheSearchAndHighlightsMatchingProducts() throws Exception {
        ProductDTO oats = new ProductDTO();
        oats.setName("Brand Hafer Oats");
        ProductDTO milk = new ProductDTO();
        milk.setName("Milk");
        FddbDataDTO day = new FddbDataDTO();
        day.setProducts(List.of(oats, milk));
        when(client.getByDate(monday.toString())).thenReturn(day);
        EntriesView entries = new EntriesView(client, mock(StatsClient.class), properties);
        QueryParameters query = QueryParameters.simple(Map.of("source", "products", "q", "hafer",
                "weekday", "MONDAY", "sort", "date:desc", "row", "1", "date", monday.toString()));

        entries.beforeEnter(enter("entries", query));

        Anchor back = descendants(entries).filter(Anchor.class::isInstance).map(Anchor.class::cast)
                .filter(anchor -> anchor.getElement().getTextRecursively().contains("Back to product results"))
                .findFirst().orElseThrow();
        assertThat(back.getHref()).startsWith("products?").contains("q=hafer", "weekday=MONDAY", "row=1")
                .doesNotContain("source=", "date=");
        // Chrome can restore the viewport at the page heading. Keep the return action below that
        // anchor so it is still discoverable without scrolling upward, including on a direct reload.
        Component context = back.getParent().orElseThrow();
        List<Component> header = context.getParent().orElseThrow().getChildren().toList();
        assertThat(header.getFirst()).isInstanceOf(com.vaadin.flow.component.html.H2.class);
        assertThat(header.indexOf(context)).isEqualTo(1);
        Grid<ProductDTO> grid = productGrid(entries);
        assertThat(grid.getPartNameGenerator().apply(oats)).isEqualTo("product-search-match");
        assertThat(grid.getPartNameGenerator().apply(milk)).isNull();
    }

    @Test
    void directDayLinkHasNoProductReturnContextAndInvalidSourceStillOpensDay() throws Exception {
        EntriesView entries = new EntriesView(client, mock(StatsClient.class), properties);
        entries.beforeEnter(enter("entries", QueryParameters.simple(Map.of("date", monday.toString(),
                "source", "products", "q", "hafer", "from", "not-a-date"))));
        verify(client).getByDate(monday.toString());
        assertThat(entries.getElement().getTextRecursively()).doesNotContain("Back to product results");
    }

    @Test
    void queryRoundTripEncodesSpecialCharactersAndIgnoresUnknownNavigationTargets() {
        ProductExplorerState state = new ProductExplorerState("Oats & milk + 50% / ä", null, null,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), List.of("date:desc", "amount:asc"), 12);
        assertThat(ProductExplorerState.parse(QueryParameters.fromString(state.parameters().getQueryString())))
                .isEqualTo(state);
        assertThat(state.productsLocation()).startsWith("products?q=").contains("%26", "%2B");
        ProductExplorerState malformed = ProductExplorerState.parse(QueryParameters.simple(Map.of(
                "q", "hafer", "weekday", "UNKNOWN", "row", "oops", "returnTo", "https://example.com")));
        assertThat(malformed.weekdays()).isEmpty();
        assertThat(malformed.row()).isEqualTo(-1);
        assertThat(malformed.productsLocation()).isEqualTo("products?q=hafer");
    }

    private BeforeEnterEvent enter(String path, QueryParameters query) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getLocation()).thenReturn(new Location(path, query));
        return event;
    }

    @SuppressWarnings("unchecked")
    private Grid<ProductDTO> productGrid(EntriesView view) throws Exception {
        return field(view, "dateProductsGrid", Grid.class);
    }

    private <T> T field(Object instance, String name, Class<T> type) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(instance));
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
