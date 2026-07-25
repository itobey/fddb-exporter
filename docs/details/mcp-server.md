# MCP Server

The application can expose your nutrition data as an [MCP](https://modelcontextprotocol.io/) server, so an AI assistant
(Claude Desktop, Claude Code, or any other MCP client) can answer questions about your diary in natural language:

> - "How much protein did I average last month vs. the month before?"
> - "Which days did I eat over 3000 kcal this year?"
> - "How often do I eat oats, and on which weekdays?"
> - "Where do most of my calories come from?"
> - "Did I stay under 2200 kcal and over 120 g of protein last month?"
> - "I had a migraine on these five dates — did I eat anything unusual the day before?"
> - "What did I eat yesterday?"

It also ships four ready-made [**prompts**](#prompts) — a weekly review, a trigger-food analysis, a protein gap check
and a logging hygiene check — that your client offers you as slash commands.

The MCP server is just another consumer of the same data the REST API and the Web UI use — it does not export anything
from FDDB and it never writes to the database.

## ⚠️ Security

The MCP endpoint has **no authentication**, exactly like the REST API, and it serves personal health data.

- It is **disabled by default**. Enable it only if you understand the consequence.
- Do **not** expose it to the internet without a reverse proxy that adds authentication in front of it.
- It requires MongoDB persistence. With MongoDB disabled, no tools are registered at all.

## Enabling it

Set the following environment variable (or the equivalent property `fddb-exporter.mcp.enabled`):

```
FDDB-EXPORTER_MCP_ENABLED=true
```

The server is then available at:

```
http://localhost:8080/mcp
```

It speaks the **streamable HTTP** transport of the MCP specification.

## Connecting a client

For Claude Code:

```bash
claude mcp add --transport http fddb-exporter http://localhost:8080/mcp
```

For clients configured through a JSON config file (e.g. Claude Desktop):

```json
{
  "mcpServers": {
    "fddb-exporter": {
      "type": "http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

## Available tools

All tools are read-only.

### Diary data

| Tool                | Parameters                                                | Returns                                                                          |
|---------------------|-----------------------------------------------------------|----------------------------------------------------------------------------------|
| `get_day`           | `date`                                                    | Daily totals and the full product list for one day                               |
| `get_days`          | `fromDate`, `toDate`, `includeProducts` (default `false`)  | Daily totals for a date range, oldest first. Limited to 366 days                  |
| `list_missing_days` | `fromDate`, `toDate`                                      | The days in the range that were never logged — "when did I forget?"              |

### Products

| Tool                 | Parameters                                              | Returns                                                                        |
|----------------------|---------------------------------------------------------|--------------------------------------------------------------------------------|
| `search_products`    | `name`, `daysOfWeek?`, `fromDate?`, `toDate?`, `limit?`  | Every occurrence of a product with its date, amount and macros                  |
| `list_top_products`  | `by?`, `fromDate?`, `toDate?`, `limit?`                 | Products ranked by frequency or by the calories/fat/carbs/protein they added    |

### Statistics and analysis

| Tool                     | Parameters                                                       | Returns                                                                                        |
|--------------------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `get_stats`              | –                                                                | Entry count, first/last entry, coverage, unique products, all-time averages, extremes, streaks |
| `get_averages`           | `fromDate`, `toDate`                                             | Average daily calories, fat, carbs, sugar, protein and fibre over a range                      |
| `get_extreme_days`       | `metric`, `direction?`, `limit?`, `fromDate?`, `toDate?`         | The highest or lowest days for one nutrient                                                    |
| `get_trend`              | `metric`, `fromDate`, `toDate`, `granularity?`                   | One nutrient over time, bucketed by day, ISO week or month                                     |
| `get_weekday_breakdown`  | `fromDate?`, `toDate?`                                           | Averages grouped by day of the week — "do my weekends wreck the average?"                      |
| `compare_periods`        | `periodAFrom`, `periodATo`, `periodBFrom`, `periodBTo`           | Both averages plus the absolute and percentage change per nutrient                              |
| `check_goals`            | `fromDate`, `toDate`, `targets`, `includeDays?`                  | Hit rate, streaks and a per-target breakdown against your own targets                          |

### Correlation

| Tool                             | Parameters                                                                | Returns                                                                     |
|----------------------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `correlate_products_with_dates`  | `inclusionKeywords`, `exclusionKeywords?`, `occurrenceDates`, `startDate?` | How often a product was eaten on, one day before and two days before an event  |

### Meta

| Tool              | Parameters | Returns                                                                         |
|-------------------|------------|---------------------------------------------------------------------------------|
| `get_data_schema` | –          | The data dictionary: every field, its unit, and the pitfalls of interpreting it  |

### Correlating food with events

`correlate_products_with_dates` takes the dates something happened on — a migraine, bad sleep, a flare-up — and counts
how often a matching product was eaten in five windows around them: the event day itself, one and two days before it,
and the 2- and 3-day windows leading up to it.

The result reports two ratios per window, because the obvious one is not the one people assume:

- **`percentageOfProductDays`** — of the days you ate the product, how many line up with an event.
- **`percentageOfEvents`** — of your events, how many had the product beforehand. Usually the more intuitive reading.
  It is omitted for the 2- and 3-day windows, where consecutive days are collapsed into one episode.

`matchedDates` are the days the **product was eaten**, not the event days.

Neither number is a statistical correlation coefficient, and none of this is evidence of causation — a food you eat
most days will line up with almost anything, and a handful of events cannot support a conclusion either way. The tool
description says so to the assistant as well, so it should report the numbers with those limits attached rather than
naming a trigger.

### Goals

The application stores no diet goals of its own, so `check_goals` takes them as a parameter — whatever you state in the
conversation. A target is a nutrient, a direction and a value:

```json
[
  {"metric": "CALORIES", "comparator": "AT_MOST", "value": 2200},
  {"metric": "PROTEIN", "comparator": "AT_LEAST", "value": 120}
]
```

Several targets combine, and a day only counts as met when it passes all of them. Values are kcal for `CALORIES` and
grams for every other nutrient. Days without an entry are not evaluated, but they do break a streak — a goal cannot be
claimed for a day with no data.

### Dates

Every date parameter accepts an ISO date (`2024-12-22`) as well as the relative aliases `today`, `yesterday` and
`N_days_ago` (e.g. `13_days_ago`). "The last 14 days" is therefore `fromDate=13_days_ago, toDate=today`, and the
assistant never has to guess what today's date is.

### Response size

MCP results are read by a language model, so the tools are built to keep responses small:

- `get_days` omits the product lists unless `includeProducts` is set — a long range with products is a very large
  response.
- `search_products` and `list_top_products` cap their results and report a `truncated` flag, so a count derived from a
  capped result is never mistaken for the full picture.
- `check_goals` returns the aggregate verdict by default and the individual days only with `includeDays`.
- The database id is stripped and empty fields are dropped.

### Things the tools do not do

- They never write. Exporting new data from FDDB stays with the REST API, the Web UI and the scheduler.
- Averages and trends only cover days that actually have an entry; unlogged days are skipped rather than counted as
  zero. Every aggregating tool reports how many logged days its numbers rest on, so a month with five entries cannot be
  mistaken for a full one — `list_missing_days` gives the gaps themselves.

## Prompts

Next to the tools the server ships four **prompts** — pre-written workflows your client offers you by name. Each runs
several tools in a deliberate order and carries the caveats that keep the answer honest, which is the part nobody types
out by hand.

| Prompt                    | Arguments                                        | What it does                                                                                       |
|---------------------------|--------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `weekly_nutrition_review` | `endDate?`                                       | The last seven days against the week before and against your all-time average, with what drove it     |
| `find_trigger_foods`      | `occurrenceDates`, `symptom?`, `suspectedFoods?` | Lines up candidate foods with the dates a symptom occurred, including a control food to compare with  |
| `protein_gap_analysis`    | `target?`, `days?`                               | The days below a protein target, when they cluster, and what could close the gap                      |
| `logging_hygiene_check`   | `fromDate?`, `toDate?`                           | The gaps and the half-logged days in a range, plus the dates worth re-exporting                       |

### Using them

Prompts are **not** something the assistant picks up on its own. Unlike the tools — which it calls whenever it judges
them useful — a prompt only runs when *you* invoke it by name. Asking "give me a weekly review" in chat will get you
the assistant's improvised version, not the workflow below.

In **Claude Code** each prompt is a slash command, named after the server you registered:

```bash
/mcp__fddb-exporter__weekly_nutrition_review
```

Arguments follow the command, separated by spaces, in the order listed in the table above. A comma-separated list is
therefore written **without** spaces after the commas, so it stays a single argument:

```bash
/mcp__fddb-exporter__find_trigger_foods 2024-03-04,2024-03-19,2024-04-02 migraine cheese
```

In **Claude Desktop** they live behind the `+` button in the message box, under the server's name, with a small form
for the arguments. Other clients differ, but every one of them surfaces prompts somewhere as an explicit user choice —
that is what the MCP specification designates them for.

Every argument marked `?` in the table is optional; a prompt invoked with none at all falls back to sensible defaults.
The review and the analyses end **yesterday**, since today is usually only half logged. `protein_gap_analysis` defaults
to 120 g over 30 days and `logging_hygiene_check` to the last 90 days. `find_trigger_foods` is the only one that
requires an argument. Spaces around the commas of its date list are tolerated wherever your client lets you type them
(a Desktop-style form field, for instance) — they are just harder to pass on a slash command.

Every prompt resolves its dates on the server and writes them into the text as concrete ISO dates. An assistant works
from whatever it believes today is, which is regularly a day or more stale — a "review of last week" anchored on the
wrong date quietly reviews the wrong week.
