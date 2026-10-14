package dev.itobey.adapter.api.fddb.exporter.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.select.Select;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MobileEntriesListTest {
    @Test
    void pagedHistory_shouldSortWithoutMutatingInputAndResetAfterReload() {
        MobileEntriesList list = new MobileEntriesList(date -> {
        });
        List<FddbDataDTO> history = new ArrayList<>();
        LocalDate first = LocalDate.of(2026, 9, 1);
        for (int i = 0; i < 35; i++) {
            history.add(day(first.plusDays(i)));
        }
        list.setItems(history);

        assertThat(detailButtons(list)).hasSize(30);
        assertThat(detailButtons(list).getFirst().getAriaLabel()).contains("View details for 2026-10-05");
        assertThat(history.getFirst().getDate()).isEqualTo(first);
        button(list, "Next").click();
        assertThat(detailButtons(list)).hasSize(5);
        assertThat(button(list, "Next").isEnabled()).isFalse();

        Select<?> sort = components(list).filter(Select.class::isInstance).map(Select.class::cast).findFirst().orElseThrow();
        setOldestFirst(sort);
        assertThat(detailButtons(list)).hasSize(30);
        assertThat(detailButtons(list).getFirst().getAriaLabel()).contains("View details for 2026-09-01");
        assertThat(button(list, "Previous").isEnabled()).isFalse();

        list.setItems(List.of(day(first)));
        assertThat(detailButtons(list)).hasSize(1);
        list.setItems(List.of());
        assertThat(list.isVisible()).isFalse();
        assertThat(detailButtons(list)).isEmpty();
    }

    @Test
    void viewDetails_shouldOpenTheSelectedDayAndHandleMissingProducts() {
        AtomicReference<LocalDate> selected = new AtomicReference<>();
        MobileEntriesList list = new MobileEntriesList(selected::set);
        LocalDate date = LocalDate.of(2026, 10, 14);
        list.setItems(List.of(day(date)));

        assertThat(list.getElement().getTextRecursively()).contains("0 products", "Protein", "Fibre", "View Details");
        detailButtons(list).getFirst().click();
        assertThat(selected.get()).isEqualTo(date);
    }

    @SuppressWarnings("unchecked")
    private static void setOldestFirst(Select<?> select) {
        ((Select<String>) select).setValue("Oldest first");
    }

    private static FddbDataDTO day(LocalDate date) {
        FddbDataDTO day = new FddbDataDTO();
        day.setDate(date);
        day.setTotalCalories(2144);
        return day;
    }

    private static List<Button> detailButtons(Component root) {
        return components(root).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals("View Details")).toList();
    }

    private static Button button(Component root, String label) {
        return components(root).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals(label)).findFirst().orElseThrow();
    }

    private static Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(MobileEntriesListTest::components));
    }
}
