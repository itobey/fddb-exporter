package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FddbPromptsTest {

    private final FddbPrompts fddbPrompts = promptsWithWriteTools(false);

    private final FddbPrompts writingPrompts = promptsWithWriteTools(true);

    @Test
    void weeklyNutritionReview_shouldResolveBothWeeksAroundTheGivenEndDate() {
        // when
        McpSchema.GetPromptResult result = fddbPrompts.weeklyNutritionReview("2024-03-17");

        // then
        String text = textOf(result);
        assertTrue(text.contains("2024-03-11 to 2024-03-17"), text);
        // the week before, so compare_periods does not have to be told what "the week before" is
        assertTrue(text.contains("2024-03-04 to 2024-03-10"), text);
        assertTrue(text.contains("compare_periods"));
        assertTrue(text.contains("list_missing_days"));
        assertEquals("Weekly nutrition review for 2024-03-11 to 2024-03-17", result.description());
    }

    @Test
    void weeklyNutritionReview_shouldDefaultToTheWeekEndingYesterday() {
        // when
        McpSchema.GetPromptResult result = fddbPrompts.weeklyNutritionReview(null);

        // then: today is usually half logged, so the review stops at yesterday
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertTrue(textOf(result).contains(yesterday.minusDays(6) + " to " + yesterday));
    }

    @Test
    void findTriggerFoods_shouldNormalizeTheDatesAndStateTheLimitsOfTheMethod() {
        // when
        McpSchema.GetPromptResult result = fddbPrompts.findTriggerFoods(
                "2024-04-02, 2024-03-04 ,2024-03-19", "migraine", "cheese, red wine");

        // then
        String text = textOf(result);
        assertTrue(text.contains("2024-03-04, 2024-03-19, 2024-04-02"), text);
        assertTrue(text.contains("these 3 dates"), text);
        assertTrue(text.contains("cheese, red wine"), text);
        assertTrue(text.contains("migraine"), text);
        // the whole reason this prompt exists rather than being typed by hand
        assertTrue(text.contains("control"), text);
        assertTrue(text.contains("co-occurrence, not causation"), text);
    }

    @Test
    void findTriggerFoods_shouldFallBackToTheMostEatenProductsWhenNoSuspectIsNamed() {
        // when
        McpSchema.GetPromptResult result =
                fddbPrompts.findTriggerFoods("2024-04-02", null, "   ");

        // then
        String text = textOf(result);
        assertTrue(text.contains("list_top_products"), text);
        assertTrue(text.contains("the symptom"), text);
    }

    @Test
    void findTriggerFoods_shouldRejectARequestWithoutDates() {
        assertThrows(IllegalArgumentException.class, () -> fddbPrompts.findTriggerFoods("", "migraine", null));
        assertThrows(DateTimeException.class, () -> fddbPrompts.findTriggerFoods("last tuesday", null, null));
    }

    @Test
    void proteinGapAnalysis_shouldUseTheGivenTargetWithoutATrailingDecimal() {
        // when
        McpSchema.GetPromptResult result = fddbPrompts.proteinGapAnalysis("140", "14");

        // then
        String text = textOf(result);
        assertTrue(text.contains("140 g of protein"), text);
        assertTrue(text.contains("\"value\":140"), text);
        LocalDate to = LocalDate.now().minusDays(1);
        assertTrue(text.contains(to.minusDays(13) + " to " + to), text);
        assertEquals("Protein gap analysis against 140 g/day", result.description());
    }

    @Test
    void proteinGapAnalysis_shouldDefaultTo120GramsOver30Days() {
        // when
        McpSchema.GetPromptResult result = fddbPrompts.proteinGapAnalysis(null, null);

        // then
        String text = textOf(result);
        assertTrue(text.contains("120 g of protein"), text);
        LocalDate to = LocalDate.now().minusDays(1);
        assertTrue(text.contains(to.minusDays(29) + " to " + to), text);
    }

    @Test
    void proteinGapAnalysis_shouldRejectATargetThatIsNotAPositiveNumber() {
        assertThrows(IllegalArgumentException.class, () -> fddbPrompts.proteinGapAnalysis("lots", null));
        assertThrows(IllegalArgumentException.class, () -> fddbPrompts.proteinGapAnalysis("-10", null));
        assertThrows(IllegalArgumentException.class, () -> fddbPrompts.proteinGapAnalysis(null, "400"));
    }

    @Test
    void loggingHygieneCheck_shouldDefaultToTheLast90DaysAndSayItCannotExport() {
        // when
        McpSchema.GetPromptResult result = fddbPrompts.loggingHygieneCheck(null, null);

        // then
        LocalDate to = LocalDate.now().minusDays(1);
        String text = textOf(result);
        assertTrue(text.contains(to.minusDays(89) + " and " + to), text);
        assertTrue(text.contains("read-only"), text);
        assertFalse(text.contains("export_missing_days"), text);
    }

    @Test
    void loggingHygieneCheck_shouldPointAtTheExportToolWhenTheWriteToolsAreOn() {
        // when: the gap list is only half an answer if the tool that repairs it is registered
        McpSchema.GetPromptResult result = writingPrompts.loggingHygieneCheck("2024-03-01", "2024-03-10");

        // then
        String text = textOf(result);
        assertTrue(text.contains("export_missing_days for 2024-03-01 to 2024-03-10"), text);
        // the claim that made the model refuse a tool it was holding
        assertFalse(text.contains("read-only"), text);
        assertFalse(text.contains("You cannot export anything yourself"), text);
    }

    @Test
    void weeklyNutritionReview_shouldOfferToFillTheGapsOnlyWhenTheWriteToolsAreOn() {
        // when
        String withoutWriteTools = textOf(fddbPrompts.weeklyNutritionReview("2024-03-17"));
        String withWriteTools = textOf(writingPrompts.weeklyNutritionReview("2024-03-17"));

        // then
        assertFalse(withoutWriteTools.contains("export_missing_days"), withoutWriteTools);
        assertTrue(withWriteTools.contains("export_missing_days for 2024-03-11 to 2024-03-17"),
                withWriteTools);
    }

    @Test
    void loggingHygieneCheck_shouldAcceptRelativeBoundsAndRejectAnInvertedRange() {
        // when
        McpSchema.GetPromptResult result = fddbPrompts.loggingHygieneCheck("6_days_ago", "yesterday");

        // then
        LocalDate to = LocalDate.now().minusDays(1);
        assertTrue(textOf(result).contains(LocalDate.now().minusDays(6) + " and " + to));
        assertThrows(IllegalArgumentException.class, () ->
                fddbPrompts.loggingHygieneCheck("2024-03-10", "2024-03-01"));
    }

    @Test
    void everyPrompt_shouldBeASingleUserMessage() {
        // a prompt is what the user is about to ask; replayed as an assistant turn it makes the
        // model answer a question nobody asked
        assertUserMessage(fddbPrompts.weeklyNutritionReview(null));
        assertUserMessage(fddbPrompts.findTriggerFoods("2024-04-02", null, null));
        assertUserMessage(fddbPrompts.proteinGapAnalysis(null, null));
        assertUserMessage(fddbPrompts.loggingHygieneCheck(null, null));
    }

    private static FddbPrompts promptsWithWriteTools(boolean enabled) {
        FddbExporterProperties.Mcp mcp = new FddbExporterProperties.Mcp();
        mcp.setEnabled(true);
        mcp.setWriteToolsEnabled(enabled);
        FddbExporterProperties properties = new FddbExporterProperties();
        properties.setMcp(mcp);
        return new FddbPrompts(properties);
    }

    private void assertUserMessage(McpSchema.GetPromptResult result) {
        assertEquals(1, result.messages().size());
        assertEquals(McpSchema.Role.USER, result.messages().getFirst().role());
        assertFalse(result.description().isBlank());
        assertFalse(textOf(result).isBlank());
    }

    private String textOf(McpSchema.GetPromptResult result) {
        return ((McpSchema.TextContent) result.messages().getFirst().content()).text();
    }
}
