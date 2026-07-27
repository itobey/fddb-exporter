package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationDetail;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.CorrelationResultDTO;
import dev.itobey.adapter.api.fddb.exporter.service.CorrelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * The MCP tool that lines up what was eaten with dates the user reports something happened on.
 * <p>
 * This one goes through {@link CorrelationService} rather than {@code FddbDataService}, which every
 * other tool uses: the correlation logic never had a delegate there, and both this class and the
 * service carry the same {@code mongodb.enabled} condition, so injecting it directly is safe and
 * keeps the service layer out of it - exactly what {@code CorrelationResourceV2} does.
 * <p>
 * The interesting work here is not the call but the reshaping of its result. The stored output
 * reports a bare {@code percentage} whose denominator is "days on which the product was eaten",
 * which reads like a probability of the event and is not one. Renaming it, putting the denominator
 * in the payload and adding the second, more intuitive ratio is the whole point of the wrapper.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled"},
        havingValue = "true")
public class FddbCorrelationTools {

    /**
     * Upper bound on the event dates accepted in one call. Well past any plausible symptom diary,
     * and it keeps a pasted-in year of dates from turning into a response nobody can read.
     */
    private static final int MAX_EVENT_DATES = 366;

    /**
     * How many matched product names are listed. Enough to see what a keyword actually caught,
     * short enough that a broad keyword does not fill the response with names.
     */
    private static final int MAX_MATCHED_PRODUCTS = 50;

    private static final String NOTE = "This counts co-occurrence, not causation. "
            + "percentageOfProductDays is the share of the days a matching product was eaten that line up "
            + "with an event; percentageOfEvents is the share of events that had it beforehand. Neither is "
            + "a statistical correlation, and over a handful of events either can be high by chance.";

    private final CorrelationService correlationService;

    @McpTool(
            name = "correlate_products_with_dates",
            description = """
                    Counts how often a product was eaten around a set of event dates - the days the \
                    user reports a migraine, bad sleep, a flare-up. The product is given as \
                    keywords matched case-insensitively against the FDDB product names; search \
                    first with search_products to check a keyword catches what the user means. The \
                    result reports five windows - the event day itself, one and two days before it, \
                    and the 2- and 3-day windows leading up to it - each with the number of hits, \
                    the days the product was eaten on, and two ratios. Read the ratios carefully: \
                    percentageOfProductDays is the share of the days the product was eaten that \
                    fall into that window, percentageOfEvents is the share of events preceded by \
                    it, and neither is a statistical correlation coefficient. This is not evidence \
                    of causation, a handful of events cannot support a conclusion at all, and \
                    matchedDates are the days the product was eaten, not the event days. Report the \
                    numbers with those limits stated and leave elimination decisions to the user \
                    and their doctor.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public CorrelationResultDTO correlateProductsWithDates(
            @McpToolParam(description = "One or more keywords, each a case-insensitive substring of "
                    + "the product name and OR-combined: ['hafer', 'muesli'] counts a product that "
                    + "matches either. At least one is required", required = true)
            List<String> inclusionKeywords,

            @McpToolParam(description = "Optional keywords that disqualify a product: a name "
                    + "containing one of these is not counted even if it matched an inclusion "
                    + "keyword, e.g. include 'brot' but exclude 'vollkorn'", required = false)
            List<String> exclusionKeywords,

            @McpToolParam(description = "The event dates - the days the symptom or event occurred. "
                    + "Each one is " + McpDateParser.ACCEPTED_FORMATS + ". At least one, at most 366",
                    required = true)
            List<String> occurrenceDates,

            @McpToolParam(description = "Optional earliest day to consider: "
                    + McpDateParser.ACCEPTED_FORMATS + ". Omit to search the whole diary. Narrowing "
                    + "it to the period the user actually logged keeps the denominator honest",
                    required = false)
            String startDate) {
        List<String> inclusions = requireKeywords(inclusionKeywords);
        List<String> exclusions = exclusionKeywords == null ? List.of() : exclusionKeywords;
        List<LocalDate> events = parseEventDates(occurrenceDates);
        LocalDate start = McpDateParser.parseOptional(startDate);
        log.debug("MCP: correlating products matching {} with {} event date(s) from {}",
                inclusions, events.size(), start);

        CorrelationInputDto input = new CorrelationInputDto();
        input.setInclusionKeywords(inclusions);
        input.setExclusionKeywords(exclusions);
        input.setOccurrenceDates(events.stream().map(LocalDate::toString).toList());
        input.setStartDate(start == null ? null : start.toString());

        CorrelationOutputDto output = correlationService.createCorrelation(input);
        return toResult(output, inclusions, exclusions, start, events.size());
    }

    private CorrelationResultDTO toResult(CorrelationOutputDto output, List<String> inclusions,
                                          List<String> exclusions, LocalDate start, int eventDateCount) {
        List<String> matchedProducts = output.getMatchedProducts();
        boolean truncated = matchedProducts.size() > MAX_MATCHED_PRODUCTS;

        CorrelationResultDTO.CorrelationResultDTOBuilder result = CorrelationResultDTO.builder()
                .inclusionKeywords(inclusions)
                .exclusionKeywords(exclusions.isEmpty() ? null : exclusions)
                .startDate(start)
                .eventDateCount(eventDateCount)
                .matchedProductCount(matchedProducts.size())
                .matchedProducts(truncated ? matchedProducts.subList(0, MAX_MATCHED_PRODUCTS) : matchedProducts)
                .matchedProductsTruncated(truncated)
                .daysWithMatchingProduct(output.getAmountMatchedDates());

        if (output.getAmountMatchedDates() == 0) {
            // every window would be a zero measured against a zero, which reads like a finding
            return result
                    .message("No product matching " + inclusions + " was found, so there is nothing to "
                            + "line up with the event dates - try a shorter keyword, or search_products "
                            + "to find the spelling FDDB uses")
                    .build();
        }

        return result
                .sameDay(window(output.getCorrelations().getSameDay(), eventDateCount))
                .oneDayBefore(window(output.getCorrelations().getOneDayBefore(), eventDateCount))
                .twoDaysBefore(window(output.getCorrelations().getTwoDaysBefore(), eventDateCount))
                .across2Days(window(output.getCorrelations().getAcross2Days(), null))
                .across3Days(window(output.getCorrelations().getAcross3Days(), null))
                .note(NOTE)
                .build();
    }

    /**
     * @param detail         the window as the correlation service computed it
     * @param eventDateCount the denominator for the share of events, or null for the {@code across}
     *                       windows, where collapsing consecutive days makes that ratio ambiguous
     * @return the window in the shape the MCP result reports it
     */
    private CorrelationResultDTO.Window window(CorrelationDetail detail, Integer eventDateCount) {
        return CorrelationResultDTO.Window.builder()
                .matchedDays(detail.getMatchedDays())
                .percentageOfProductDays(McpMetrics.roundToOneDecimal(detail.getPercentage()))
                .percentageOfEvents(eventDateCount == null || eventDateCount == 0
                        ? null
                        : McpMetrics.roundToOneDecimal((double) detail.getMatchedDays() / eventDateCount * 100))
                .matchedDates(detail.getMatchedDates().stream().map(LocalDate::parse).toList())
                .build();
    }

    private List<String> requireKeywords(List<String> inclusionKeywords) {
        List<String> keywords = inclusionKeywords == null
                ? List.of()
                : inclusionKeywords.stream().filter(keyword -> keyword != null && !keyword.isBlank()).toList();
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("At least one inclusion keyword is required, e.g. "
                    + "['hafer'] - it is matched as a case-insensitive substring of the product name");
        }
        return keywords;
    }

    /**
     * Resolves the event dates and drops duplicates, so that the same day pasted in twice does not
     * inflate the denominator of the per-event share.
     */
    private List<LocalDate> parseEventDates(List<String> occurrenceDates) {
        if (occurrenceDates == null || occurrenceDates.isEmpty()) {
            throw new IllegalArgumentException("At least one event date is required - these are the days "
                    + "the symptom or event occurred on");
        }
        if (occurrenceDates.size() > MAX_EVENT_DATES) {
            throw new IllegalArgumentException("At most " + MAX_EVENT_DATES + " event dates can be "
                    + "correlated at once, but " + occurrenceDates.size() + " were given");
        }
        return occurrenceDates.stream()
                .map(McpDateParser::parse)
                .distinct()
                .sorted()
                .toList();
    }
}
