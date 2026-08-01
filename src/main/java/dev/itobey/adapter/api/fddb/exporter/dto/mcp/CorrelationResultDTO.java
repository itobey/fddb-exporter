package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * How often a product was eaten around a set of event dates.
 * <p>
 * This is the one MCP result whose numbers are easy to over-read, so the shape is deliberately more
 * explicit than the underlying {@code CorrelationOutputDto} the REST API returns: the percentage is
 * named after its denominator, the denominator itself is a field, and the number of events the
 * whole thing rests on is reported next to it. An agent that sees "83%" without knowing it is 5 out
 * of 6 days will tell someone they found their migraine trigger.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationResultDTO {

    /**
     * The keywords a product name had to match, echoed back so a typo is visible in the result.
     */
    private List<String> inclusionKeywords;

    /**
     * The keywords that disqualified a product, if any were given.
     */
    private List<String> exclusionKeywords;

    /**
     * The earliest day considered, or null when the whole diary was searched.
     */
    private LocalDate startDate;

    /**
     * The number of distinct event dates the windows were measured against. Duplicates in the
     * request are counted once.
     */
    private int eventDateCount;

    /**
     * The number of distinct product names that matched the keywords.
     */
    private int matchedProductCount;

    /**
     * The distinct product names that matched, so the caller can tell whether a keyword collapsed
     * two unrelated foods into one number.
     */
    private List<String> matchedProducts;

    /**
     * Whether {@code matchedProducts} was cut short. The counts are unaffected - only the name list
     * is capped.
     */
    private boolean matchedProductsTruncated;

    /**
     * The number of distinct days on which a matching product was eaten. This is the denominator of
     * every {@code percentageOfProductDays} below.
     */
    private int daysWithMatchingProduct;

    /**
     * A matching product was eaten on the event day itself.
     */
    private Window sameDay;

    /**
     * A matching product was eaten on the day before an event.
     */
    private Window oneDayBefore;

    /**
     * A matching product was eaten two days before an event.
     */
    private Window twoDaysBefore;

    /**
     * A matching product was eaten on the event day or the day before it.
     */
    private Window across2Days;

    /**
     * A matching product was eaten on the event day or in the two days before it.
     */
    private Window across3Days;

    /**
     * The standing caveat about what these numbers are and are not. Part of the payload rather than
     * only of the tool description, because the result is what gets read when the answer is written.
     */
    private String note;

    /**
     * Set when nothing could be measured, e.g. because no product matched the keywords.
     */
    private String message;

    /**
     * One timing window around the events.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Window {

        /**
         * The number of hits in this window. For the single-day windows this is the number of event
         * dates the product lines up with. For the {@code across} windows consecutive days are
         * collapsed into one, so it counts episodes rather than days and can be lower than the
         * length of {@code matchedDates}.
         */
        private int matchedDays;

        /**
         * The share of {@code daysWithMatchingProduct} that falls into this window, in percent. Not
         * a correlation coefficient: it answers "how much of my eating of this lines up with an
         * event", not "how likely is an event after eating this".
         */
        private double percentageOfProductDays;

        /**
         * The share of {@code eventDateCount} that this window covers, in percent - the more
         * intuitive reading, "how many of my events had this beforehand". Null for the
         * {@code across} windows, where the collapsing of consecutive days makes the ratio
         * ambiguous.
         */
        private Double percentageOfEvents;

        /**
         * The days the product was actually eaten - not the event days. For
         * {@code oneDayBefore} each of these is the day before an event.
         */
        private List<LocalDate> matchedDates;
    }
}
