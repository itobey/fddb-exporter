package dev.itobey.adapter.api.fddb.exporter.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The data dictionary tool.
 * <p>
 * Static text, and worth every byte of it: without it an agent has to guess whether {@code totalFat}
 * is grams or kcal and whether {@code amount} is a number it can do arithmetic on. Guessing wrong
 * produces confident nonsense about someone's diet.
 */
@Component
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled"},
        havingValue = "true")
public class FddbSchemaTools {

    private static final String DATA_SCHEMA = """
            # FDDB-Exporter data model
            
            The data is a personal nutrition diary scraped from fddb.info. There is at most one
            entry per calendar day; a day that was never logged simply has no entry.
            
            ## Day entry
            
            | Field | Type | Meaning |
            |---|---|---|
            | date | ISO date (YYYY-MM-DD) | The day the food was logged for |
            | totalCalories | number | Energy for the whole day, in kcal |
            | totalFat | number | Fat for the whole day, in grams |
            | totalCarbs | number | Carbohydrates for the whole day, in grams |
            | totalSugar | number | Sugar for the whole day, in grams. Part of totalCarbs, not additional to it |
            | totalProtein | number | Protein for the whole day, in grams |
            | totalFibre | number | Fibre for the whole day, in grams |
            | products | list | The products logged that day. Omitted unless explicitly requested |
            
            ## Product
            
            | Field | Type | Meaning |
            |---|---|---|
            | name | text | The FDDB product name, usually German and brand-prefixed, e.g. "Alnatura Haferflocken kernig" |
            | amount | text | A free-text portion such as "250 g", "1 Portion" or "0.5 Stück". NOT a number - do not do arithmetic on it |
            | calories | number | kcal contributed by this portion |
            | fat | number | Grams of fat contributed by this portion |
            | carbs | number | Grams of carbohydrates contributed by this portion |
            | protein | number | Grams of protein contributed by this portion |
            | link | text | URL of the product page on fddb.info |
            
            A product has no fibre or sugar field - those exist only as daily totals.
            
            ## Things that are easy to get wrong
            
            - The daily totals come from FDDB itself. They are close to, but not exactly, the sum of
              the products of that day, and re-deriving them from the products gives slightly
              different numbers.
            - Averages and trends only ever cover days that have an entry. Unlogged days are left
              out rather than counted as zero, so an average is never dragged down by a forgotten day.
            - The same food can appear under several product names (different brands, different
              spellings). A search matches a case-insensitive substring of the name, so a short
              fragment finds more variants than a full name does.
            - The diary reflects what the user logged, not what they ate. Gaps mean "not logged".
            """;

    @McpTool(
            name = "get_data_schema",
            description = """
                    Returns the data dictionary for the diary: every field of a day entry and of a \
                    product, its unit, and the pitfalls of interpreting them. Cheap, and worth \
                    calling once before reasoning about the numbers.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public String getDataSchema() {
        log.debug("MCP: returning the data schema");
        return DATA_SCHEMA;
    }
}
