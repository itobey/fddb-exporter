package dev.itobey.adapter.api.fddb.exporter.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FddbSchemaToolsTest {

    private final FddbSchemaTools fddbSchemaTools = new FddbSchemaTools();

    /**
     * The whole point of the tool is that the agent does not have to guess units or field names, so
     * the fields of the day entry and the units they carry have to be in there.
     */
    @ParameterizedTest
    @ValueSource(strings = {"totalCalories", "totalFat", "totalCarbs", "totalSugar", "totalProtein", "totalFibre",
            "products", "amount", "kcal", "grams", "YYYY-MM-DD"})
    void getDataSchema_shouldDocumentTheFieldsAndTheirUnits(String expected) {
        assertThat(fddbSchemaTools.getDataSchema()).contains(expected);
    }

    @Test
    void getDataSchema_shouldWarnThatTheAmountIsNotANumber() {
        assertThat(fddbSchemaTools.getDataSchema()).contains("NOT a number");
    }
}
