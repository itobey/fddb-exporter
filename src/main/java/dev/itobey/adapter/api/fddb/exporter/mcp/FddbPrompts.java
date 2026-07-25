package dev.itobey.adapter.api.fddb.exporter.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * The pre-baked workflows an MCP client offers the user by name, e.g. as a slash command.
 * <p>
 * A prompt is a message the <em>user</em> sends, not an answer - it is worth writing one when the
 * useful version of a question is several tool calls in a particular order plus the caveats that
 * keep the answer honest. Nobody types that out, so a "weekly review" typed by hand becomes one
 * tool call and a guess.
 * <p>
 * They resolve their dates server-side and paste concrete ISO dates into the text. That is the
 * quiet reason they are dynamic rather than static strings: a client works from whatever it
 * believes today is, and a review of "last week" anchored on a stale date silently reviews the
 * wrong week.
 */
@Component
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled"},
        havingValue = "true")
public class FddbPrompts {

    /**
     * The default range for the hygiene check: long enough for a gap pattern to be visible, short
     * enough to still be worth fixing.
     */
    private static final int DEFAULT_HYGIENE_DAYS = 90;

    private static final int DEFAULT_PROTEIN_DAYS = 30;

    private static final double DEFAULT_PROTEIN_TARGET = 120;

    private static final int MAX_ANALYSIS_DAYS = 366;

    /**
     * Appended wherever the answer could be read as health advice. The diary is personal health
     * data and the questions these prompts drive are the ones a user is most likely to act on.
     */
    private static final String NOT_ADVICE = "Do not give medical or clinical advice, and do not "
            + "infer anything about weight, body composition or health from the numbers alone - the "
            + "diary records what was logged, not what was eaten and not how it went.";

    @McpPrompt(
            name = "weekly_nutrition_review",
            title = "Weekly nutrition review",
            description = "Reviews the last seven days against the week before and against the "
                    + "all-time average, and names what drove the difference.")
    public McpSchema.GetPromptResult weeklyNutritionReview(
            @McpArg(name = "endDate",
                    description = "Last day of the week to review: an ISO date (YYYY-MM-DD), "
                            + "'today', 'yesterday' or 'N_days_ago'. Defaults to yesterday, since "
                            + "today is usually only half logged")
            String endDate) {
        LocalDate weekTo = endDate == null || endDate.isBlank()
                ? LocalDate.now().minusDays(1)
                : McpDateParser.parse(endDate);
        LocalDate weekFrom = weekTo.minusDays(6);
        LocalDate priorTo = weekFrom.minusDays(1);
        LocalDate priorFrom = priorTo.minusDays(6);
        log.debug("MCP: weekly review prompt for {} to {}", weekFrom, weekTo);

        return promptFor("Weekly nutrition review for " + weekFrom + " to " + weekTo, """
                Review my nutrition for the week of %1$s to %2$s.

                Gather the data first, in this order:
                1. get_averages for %1$s to %2$s.
                2. compare_periods with period A = %1$s to %2$s and period B = %3$s to %4$s, the week before.
                3. list_missing_days for %1$s to %2$s. If days are missing, say how many, and treat every \
                average of this week as resting on that many fewer days.
                4. get_stats, so you can put the week next to my all-time averages and see whether my \
                logging streak held.
                5. list_top_products ranked by CALORIES for %1$s to %2$s, to name what actually drove the week.

                Then write a short report:
                - one paragraph on how the week went,
                - the two or three nutrients that moved most against the previous week, with the numbers, and \
                whether the move is large enough to mean anything given how many days were logged,
                - how the week sits against my all-time average,
                - one or two concrete things to try next week, tied to products that are already in my diary.

                Keep it under 300 words. %5$s
                """.formatted(weekFrom, weekTo, priorFrom, priorTo, NOT_ADVICE));
    }

    @McpPrompt(
            name = "find_trigger_foods",
            title = "Find trigger foods",
            description = "Lines up candidate foods with the dates a symptom occurred, with a "
                    + "control food for comparison and the limits of the method spelled out.")
    public McpSchema.GetPromptResult findTriggerFoods(
            @McpArg(name = "occurrenceDates",
                    description = "The dates the symptom occurred, comma-separated, e.g. "
                            + "'2024-03-04, 2024-03-19, 2024-04-02'. Required",
                    required = true)
            String occurrenceDates,

            @McpArg(name = "symptom",
                    description = "What happened on those dates, e.g. 'migraine' or 'bad sleep'. "
                            + "Used only to word the report")
            String symptom,

            @McpArg(name = "suspectedFoods",
                    description = "Optional comma-separated foods to check first, e.g. "
                            + "'cheese, red wine'. Without them the candidates are taken from what "
                            + "I eat most")
            String suspectedFoods) {
        List<LocalDate> events = parseDateList(occurrenceDates);
        String what = blankTo(symptom, "the symptom");
        List<String> suspects = splitList(suspectedFoods);
        log.debug("MCP: trigger food prompt for {} event date(s)", events.size());

        String candidates = suspects.isEmpty()
                ? "No suspect named. Call list_top_products ranked by FREQUENCY to see what I eat often "
                + "enough for this to be measurable at all, and pick three or four candidates from it - "
                + "or ask me which foods to check before you start."
                : "Start with these suspects: " + String.join(", ", suspects) + ".";

        return promptFor("Trigger food analysis for " + what, """
                I want to see whether anything I eat lines up with %1$s. It occurred on these %2$d dates: %3$s.

                %4$s

                For each candidate:
                1. Call search_products with a SHORT fragment of the name first - FDDB names are German and \
                brand-prefixed, so 'hafer' finds what 'oatmeal' never will. Confirm the fragment catches the \
                right thing and nothing else before using it.
                2. Call correlate_products_with_dates with that fragment as inclusionKeywords and the dates \
                above as occurrenceDates. Add exclusionKeywords if the fragment also catches something \
                unrelated.
                3. Also run one food I eat often but that nobody suspects, as a control. Without it there is \
                nothing to judge the numbers against.

                Then report, per candidate: the same-day, one-day-before and two-day-before figures, how many \
                of the %2$d events each rests on, and how it compares to the control.

                Be explicit about what this cannot do. It counts co-occurrence, not causation. %2$d events is \
                a small sample and a food I eat most days will line up with almost anything. Do not tell me to \
                eliminate a food, and do not name a trigger. If something looks worth following up, say that it \
                is worth raising with a doctor or dietitian, and that a deliberate elimination or a food diary \
                kept for the purpose would answer it properly. %5$s
                """.formatted(what, events.size(), joinDates(events), candidates, NOT_ADVICE));
    }

    @McpPrompt(
            name = "protein_gap_analysis",
            title = "Protein gap analysis",
            description = "Finds the days below a protein target, when they cluster, and which "
                    + "foods already in the diary could close the gap.")
    public McpSchema.GetPromptResult proteinGapAnalysis(
            @McpArg(name = "target",
                    description = "The daily protein target in grams. Defaults to 120")
            String target,

            @McpArg(name = "days",
                    description = "How many days back to analyse, at most 366. Defaults to 30")
            String days) {
        double proteinTarget = parsePositiveNumber(target, DEFAULT_PROTEIN_TARGET, "target");
        int rangeDays = (int) parsePositiveNumber(days, DEFAULT_PROTEIN_DAYS, "days");
        if (rangeDays > MAX_ANALYSIS_DAYS) {
            throw new IllegalArgumentException("At most " + MAX_ANALYSIS_DAYS + " days can be analysed "
                    + "at once, but " + rangeDays + " were requested");
        }
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(rangeDays - 1L);
        log.debug("MCP: protein gap prompt for {} g over {} to {}", proteinTarget, from, to);

        return promptFor("Protein gap analysis against %s g/day".formatted(trim(proteinTarget)), """
                Work out how close I get to %1$s g of protein a day, over %2$s to %3$s.

                Gather:
                1. check_goals for %2$s to %3$s with targets \
                [{"metric":"PROTEIN","comparator":"AT_LEAST","value":%1$s}] and includeDays true, so you have \
                the hit rate, the streaks and the actual value of every day that missed.
                2. get_extreme_days for PROTEIN, direction LOWEST, over the same range, then get_day for the \
                two or three worst - I want to see what those days actually looked like.
                3. get_weekday_breakdown for the same range, to see whether the misses cluster on particular \
                days of the week.
                4. list_top_products ranked by PROTEIN over the same range, for what is already working.

                Then tell me:
                - the hit rate and the average shortfall in grams on the days I missed, not just the average \
                protein - the gap is the number I can act on,
                - whether the misses cluster on a weekday or come from days I logged only partially (check \
                whether a low-protein day is also a low-calorie day; that usually means an incomplete log, not \
                a bad day),
                - three specific changes using products already in my diary, each with the grams it would add \
                and the portion it assumes.

                Note that amounts are free text like "250 g" or "1 Portion", so any portion arithmetic is an \
                estimate - say so where you do it. %4$s
                """.formatted(trim(proteinTarget), from, to, NOT_ADVICE));
    }

    @McpPrompt(
            name = "logging_hygiene_check",
            title = "Logging hygiene check",
            description = "Finds the gaps and the half-logged days in a range, so the numbers "
                    + "everything else rests on can be trusted.")
    public McpSchema.GetPromptResult loggingHygieneCheck(
            @McpArg(name = "fromDate",
                    description = "First day to check: an ISO date (YYYY-MM-DD), 'today', "
                            + "'yesterday' or 'N_days_ago'. Defaults to 90 days ago")
            String fromDate,

            @McpArg(name = "toDate",
                    description = "Last day to check: an ISO date (YYYY-MM-DD), 'today', "
                            + "'yesterday' or 'N_days_ago'. Defaults to yesterday")
            String toDate) {
        LocalDate resolvedTo = toDate == null || toDate.isBlank()
                ? LocalDate.now().minusDays(1)
                : McpDateParser.parse(toDate);
        LocalDate resolvedFrom = fromDate == null || fromDate.isBlank()
                ? resolvedTo.minusDays(DEFAULT_HYGIENE_DAYS - 1L)
                : McpDateParser.parse(fromDate);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("The 'from' date cannot be after the 'to' date");
        }
        log.debug("MCP: logging hygiene prompt for {} to {}", resolvedFrom, resolvedTo);

        return promptFor("Logging hygiene check for " + resolvedFrom + " to " + resolvedTo, """
                Check how complete my logging is between %1$s and %2$s.

                Gather:
                1. list_missing_days for %1$s to %2$s - the days with no entry at all.
                2. get_stats, for the overall coverage and my current and longest logging streak.
                3. get_days for %1$s to %2$s without products. A day well below my usual calories is not \
                necessarily a light day; it is often a day I logged breakfast and then stopped. Flag those \
                separately from the missing ones.

                Then tell me:
                - how many days are missing, what share of the range that is, and where the longest gap is,
                - whether the gaps fall on particular weekdays - derive that from the dates themselves,
                - which logged days look partial rather than light, and why you think so,
                - the exact list of dates worth re-exporting, as ISO dates I can paste.

                You cannot export anything yourself - the MCP tools are read-only. Say that plainly and hand \
                me the list; re-exporting is done from the app's web UI or its REST API. Finish with one \
                sentence on how much the gaps affect an average over this range.
                """.formatted(resolvedFrom, resolvedTo));
    }

    /**
     * Wraps the text as the single user message of a prompt. USER rather than ASSISTANT on purpose:
     * a prompt is what the user is about to ask, and a client that replays it as an assistant turn
     * gets a model answering a question nobody asked.
     */
    private McpSchema.GetPromptResult promptFor(String description, String text) {
        McpSchema.PromptMessage message = McpSchema.PromptMessage
                .builder(McpSchema.Role.USER, McpSchema.TextContent.builder(text).build())
                .build();
        return McpSchema.GetPromptResult.builder(List.of(message))
                .description(description)
                .build();
    }

    private List<LocalDate> parseDateList(String value) {
        List<LocalDate> dates = splitList(value).stream()
                .map(McpDateParser::parse)
                .distinct()
                .sorted()
                .toList();
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("At least one date is required, comma-separated, e.g. "
                    + "'2024-03-04, 2024-03-19'");
        }
        return dates;
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private String joinDates(List<LocalDate> dates) {
        return dates.stream().map(LocalDate::toString).reduce((a, b) -> a + ", " + b).orElseThrow();
    }

    private double parsePositiveNumber(String value, double fallback, String name) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException("'" + value + "' is not a number - " + name
                    + " has to be a positive number");
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " has to be a positive number, but was " + value);
        }
        return parsed;
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * Drops the trailing {@code .0} of a whole number, so a target of 120 does not reach the user
     * as "120.0 g".
     */
    private String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
