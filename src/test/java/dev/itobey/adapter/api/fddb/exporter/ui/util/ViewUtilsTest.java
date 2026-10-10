package dev.itobey.adapter.api.fddb.exporter.ui.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ViewUtilsTest {

    private final Locale originalLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void formatNumber_shouldUseADotAsDecimalSeparator() {
        assertEquals("2143.7", ViewUtils.formatNumber(2143.65));
        assertEquals("0.0", ViewUtils.formatNumber(0));
        assertEquals("-1.5", ViewUtils.formatNumber(-1.5));
    }

    @Test
    void formatNumber_shouldNotDependOnTheDefaultLocale() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals("2143.7", ViewUtils.formatNumber(2143.65));

        Locale.setDefault(Locale.US);
        assertEquals("2143.7", ViewUtils.formatNumber(2143.65));
    }

    @Test
    void capitalize_shouldLowercaseEverythingButTheFirstCharacter() {
        assertEquals("Monday", ViewUtils.capitalize("MONDAY"));
        assertEquals("Calories", ViewUtils.capitalize("CALORIES"));
        assertEquals("A", ViewUtils.capitalize("A"));
    }

    @Test
    void capitalize_shouldNotDependOnTheDefaultLocale() {
        // Turkish lowercases 'I' to a dotless 'ı' — the result must stay locale independent
        Locale.setDefault(Locale.forLanguageTag("tr"));
        assertEquals("Friday", ViewUtils.capitalize("FRIDAY"));

        Locale.setDefault(Locale.GERMANY);
        assertEquals("Friday", ViewUtils.capitalize("FRIDAY"));
    }

    @Test
    void capitalize_shouldPassThroughNullAndEmptyInput() {
        assertNull(ViewUtils.capitalize(null));
        assertEquals("", ViewUtils.capitalize(""));
    }
}
