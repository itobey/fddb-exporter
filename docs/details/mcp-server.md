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
and a logging hygiene check — that your client offers you as slash commands, and three
[**resources**](#resources) your client can pull in as context.

The MCP server is just another consumer of the same data the REST API and the Web UI use. Out of the box it is
**read-only**: it does not export anything from FDDB and it never writes to the database. The
[export tools](#export-tools-optional) that change that are behind a second flag of their own.

## ⚠️ Security

The MCP endpoint has **no authentication**, exactly like the REST API, and it serves personal health data.

- It is **disabled by default**. Enable it only if you understand the consequence.
- Do **not** expose it to the internet without a reverse proxy that adds authentication in front of it.
- It requires MongoDB persistence. With MongoDB disabled, no tools are registered at all.
- The tools that scrape FDDB and write to your database need a **second** flag,
  `FDDB-EXPORTER_MCP_WRITE-TOOLS-ENABLED`. Without it they are not registered, so an assistant cannot see them, let
  alone call them.

## Enabling it

Set the following environment variable (or the equivalent property `fddb-exporter.mcp.enabled`):

```bash
docker run -e 'FDDB-EXPORTER_MCP_ENABLED=true' ghcr.io/itobey/fddb-exporter
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

Every tool listed here is read-only. The [export tools](#export-tools-optional) are the exception and are off by
default.

### Diary data

| Tool                | Parameters                                                | Returns                                                                          |
|---------------------|-----------------------------------------------------------|----------------------------------------------------------------------------------|
| `get_day`           | `date`                                                    | Daily totals and the full product list for one day                               |
| `get_days`          | `fromDate`, `toDate`, `includeProducts` (default `false`)  | Daily totals for a date range, oldest first. Limited to 366 days                  |
| `list_missing_days` | `fromDate`, `toDate`                                      | The days in the range that were never logged — "when did I forget?"              |

### Products

| Tool                       | Parameters                                                  | Returns                                                                                    |
|----------------------------|-------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `search_products`          | `name`, `daysOfWeek?`, `fromDate?`, `toDate?`, `limit?`      | Every occurrence of a product with its date, amount and macros                              |
| `list_top_products`        | `by?`, `fromDate?`, `toDate?`, `limit?`                      | Products ranked by frequency or by the calories/fat/carbs/protein they added                |
| `get_product_summary`      | `name`, `fromDate?`, `toDate?`                               | One product rolled up: times eaten, first/last date, totals, average, weekday distribution  |
| `list_distinct_products`   | `search?`, `limit?`                                          | The product names your diary actually contains — the vocabulary lookup                      |
| `find_days_with_products`  | `includeKeywords`, `excludeKeywords?`, `startDate?`, `limit?` | The days a matching product was logged on, grouped by day, plus how many days match in total |

### Statistics and analysis

| Tool                     | Parameters                                                       | Returns                                                                                        |
|--------------------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `get_stats`              | –                                                                | Entry count, first/last entry, coverage, unique products, all-time averages, extremes, streaks |
| `get_averages`           | `fromDate`, `toDate`                                             | Average daily calories, fat, carbs, sugar, protein and fibre over a range, and the days it rests on |
| `get_extreme_days`       | `metric`, `direction?`, `limit?`, `fromDate?`, `toDate?`         | The highest or lowest days for one nutrient                                                    |
| `get_trend`              | `metric`, `fromDate`, `toDate`, `granularity?`                   | One nutrient over time, bucketed by day, ISO week or month                                     |
| `get_weekday_breakdown`  | `fromDate?`, `toDate?`                                           | Averages grouped by day of the week — "do my weekends wreck the average?"                      |
| `get_macro_split`        | `fromDate`, `toDate`                                             | Share of energy from fat, carbs and protein — kcal-weighted, not gram-weighted                 |
| `compare_periods`        | `periodAFrom`, `periodATo`, `periodBFrom`, `periodBTo`           | Both averages plus the absolute and percentage change per nutrient                              |
| `check_goals`            | `fromDate`, `toDate`, `targets`, `includeDays?`                  | Hit rate, streaks and a per-target breakdown against your own targets                          |

### Correlation

| Tool                             | Parameters                                                                | Returns                                                                     |
|----------------------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `correlate_products_with_dates`  | `inclusionKeywords`, `exclusionKeywords?`, `occurrenceDates`, `startDate?` | How often a product was eaten on, one day before and two days before an event  |

### Meta

| Tool               | Parameters | Returns                                                                                                  |
|--------------------|------------|-----------------------------------------------------------------------------------------------------------|
| `get_data_schema`  | –          | The data dictionary: every field, its unit, and the pitfalls of interpreting it                            |
| `get_server_info`  | –          | Version, **today's date as the server sees it**, enabled stores, scheduler cron, the window your data covers |

### Export tools (optional)

These are the only tools that write. They log into fddb.info with your configured account, scrape the diary for a set
of days and store the result — the same thing the scheduler and the Web UI do, triggered from a conversation instead.

They are **not registered** unless you enable them explicitly, on top of the MCP server itself:

```bash
docker run -e 'FDDB-EXPORTER_MCP_WRITE-TOOLS-ENABLED=true' ghcr.io/itobey/fddb-exporter
```

A POSIX shell will not `export` a name with hyphens in it — in a shell script write
`FDDB_EXPORTER_MCP_WRITE_TOOLS_ENABLED=true` instead, which binds to the same property. Either way,
`get_server_info` reports `writeToolsEnabled`, so you can check the flag took effect without
guessing from the tool list.

The flag is read at startup, so changing it needs a restart. While it is off, the tools do not exist as far as any
client is concerned.

| Tool                   | Parameters              | Does                                                                                  |
|------------------------|-------------------------|-----------------------------------------------------------------------------------------|
| `export_range`         | `fromDate`, `toDate`    | Scrapes and stores a date range, at most 14 days per call                               |
| `export_days_back`     | `days`, `includeToday?` | Scrapes and stores the last N days (1 to 14), ending yesterday unless you ask for today |
| `export_missing_days`  | `fromDate`, `toDate`    | Scrapes only the days in the range that have no entry yet — the repair for logging gaps  |

Worth knowing before turning this on:

- **Nothing is deleted.** An existing day is updated in place, so re-exporting is safe and never duplicates.
- **A "failed" day is usually an empty one.** If FDDB has nothing logged for a day, that day comes back as
  unsuccessful and its stored data is left alone. The response says so in words, so an assistant does not report a
  malfunction that is really just an unlogged day.
- **Exports are capped at 14 days per call.** Every day is one sequential request to fddb.info and takes roughly a
  second, so the cap is as much about the length of the call as about the load: a longer run outlives the timeout of
  most MCP clients, and a client that gives up while the server keeps scraping leaves the assistant reporting a failure
  for data you now have. Ask for a year and the tool refuses, naming the uncapped paths so the assistant tells you to
  use them instead of issuing the same call twenty-six times.
- **For a bigger backfill, use the Web UI or the REST API.** The Web UI's export page and the
  [REST API](/details/rest-api.md) — `POST /api/v2/fddbdata` or `GET /api/v2/fddbdata/export?days=N` — have no such
  cap, and are what the tool descriptions point the assistant at. Nothing is waiting on the
  response there, and a person can watch a long run finish. The MCP cap is not a limit on what the app can export, only
  on what is sensible to do inside a single tool call.
- `export_missing_days` fetches one day per gap, so days you already logged are never re-fetched. Its cap counts the
  gaps, not the range: a year-long range with nine missing days is fine.
- **A range reaching into the future is refused, not scraped.** The read tools accept any date and answer
  `found: false` for a day that cannot have data, which costs nothing; here the same slip in an assistant's date
  arithmetic would spend the per-call budget on requests to fddb.info for days that have not happened. The refusal
  names the server's own today, so the assistant can correct itself in one step.
- **Only one export runs at a time.** The guard is application-wide, not per client: the MCP tools, the REST API, the
  Web UI and the nightly scheduler all pass through it. A second export is refused immediately with *"An export is
  already running"* rather than queued — an assistant handles "try again later" well, and two runs scraping fddb.info
  under one account at once only double the load. The scheduled export skips that night's run if it collides with a
  manual one; the REST API answers `409 Conflict`.
- **The assistant is told to leave them alone by default.** The server's always-in-context instructions say that
  exporting fetches fresh data from fddb.info under your own account and costs about a second a day, and that it is for
  when you explicitly ask for fresh data — never for answering a question about data that is already stored. The
  sentence is there whether or not the tools are registered, so the posture is set before the tool list is even read.
- Wrong FDDB credentials abort the whole call rather than being reported per day.

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

That last rule is worth knowing when you read `currentStreak`, which counts back from the end of the range. The
scheduler exports **yesterday**, so today is normally unlogged, and a check ending today reports a current streak of
`0` however well the previous fortnight went. End the range on yesterday when the streak is the question. The tool
description tells the assistant the same thing, so it should not report a `0` from a range ending today as a fact
about your habits.

### Dates

Every date parameter accepts an ISO date (`2024-12-22`) as well as the relative aliases `today`, `yesterday` and
`N_days_ago` (e.g. `13_days_ago`). "The last 14 days" is therefore `fromDate=13_days_ago, toDate=today`, and the
assistant never has to guess what today's date is.

### Response size

MCP results are read by a language model, so the tools are built to keep responses small:

- `get_days` omits the product lists unless `includeProducts` is set — a long range with products is a very large
  response.
- `search_products`, `list_top_products`, `list_distinct_products` and `get_extreme_days` cap their results and report
  a `truncated` flag, so a count derived from a capped result is never mistaken for the full picture — and a top-10
  list is not read as "there were only ten".
- `find_days_with_products` groups and caps in the database rather than in memory, and reports both numbers:
  `dayCount` is how many days came back, `matchedDayCount` how many exist. "On how many days did I eat X?" is answered
  by the second one, which stays correct when `truncated` is set.
- `list_missing_days` accepts any range but lists at most 366 dates, with `truncated` set when it cut the list. Its
  `missingCount` and `loggedCount` always describe the whole range, so a five-year audit still answers "how many days
  did I miss?" exactly — only the dates themselves are cut, and a narrower range gets them back. The repair path
  (`export_missing_days`, the REST API, the Web UI) works from the full list either way.
- `check_goals` returns the aggregate verdict by default and the individual days only with `includeDays`.
- The database id is stripped and empty fields are dropped.

### Empty ranges

A range you logged nothing in is an answer, not a failure. `get_day`, `get_averages`, `get_macro_split` and
`get_product_summary` report it as `found: false` with a sentence saying so, instead of failing the tool call — an
assistant that sees a failed call tends to retry it or blame the server, where "you logged nothing that week" is the
thing you asked about.

### When something does go wrong

A parameter the tools can reject themselves — an unparseable date, an inverted range, a range past a cap, a missing
goal target — comes back with a message written for the assistant, naming what was wrong and what the accepted forms
are. Those are the errors you want it to see: it can fix the call and carry on without bothering you.

Anything else — the database being unreachable, an unexpected failure inside the application — is replaced with a
single sentence saying the request failed for a server-side reason and is not worth retrying with different
parameters, while the real exception and its stack trace go to the application log. Internal error text in a chat
window helps nobody: the assistant cannot act on it, and without the "do not retry" it will burn several turns
re-wording a call that failed for reasons no argument can influence.

One exception on purpose: wrong FDDB credentials are reported as such, since that names the one thing that would
actually fix it. The message never contains the credentials themselves.

### Things the tools do not do

- They do not write, unless you turned the [export tools](#export-tools-optional) on. Nothing is ever deleted, in
  either case.
- They do not migrate your data to InfluxDB and they do not hand out CSV exports. Both stay with the REST API and the
  Web UI, where a person decides to run them.
- Averages and trends only cover days that actually have an entry; unlogged days are skipped rather than counted as
  zero. Every aggregating tool reports how many logged days its numbers rest on, so a month with five entries cannot be
  mistaken for a full one — `list_missing_days` gives the gaps themselves.

## Resources

Next to tools the server exposes three **resources**. A resource is context your client pulls in — you attach it, the
assistant reads it, no tool call involved. Claude Desktop surfaces them behind the `+` button; Claude Code lists them
under `@fddb-exporter`.

| Resource            | Contains                                                                     |
|---------------------|--------------------------------------------------------------------------------|
| `fddb://stats`      | The same overview as `get_stats`: coverage, averages, extremes, streaks         |
| `fddb://day/{date}` | One day with its totals and products — `fddb://day/2024-12-22`, `fddb://day/yesterday` |
| `fddb://schema`     | The data dictionary, the same text as `get_data_schema`                        |

They are a convenience, not an extra capability: everything here is available as a tool too. There is deliberately no
"whole diary" or CSV resource — a resource lands in the context window whole, so an unbounded one would fill it.

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

Two of them also adapt to the [export tools](#export-tools-optional). With write tools off, `logging_hygiene_check`
hands you the list of gaps and says plainly that the server cannot fill them. With write tools on, it — and
`weekly_nutrition_review` — instead instructs the assistant to call `export_missing_days` for the range and report
what came back, which turns "here is what is missing" into "here is what was missing". The hygiene check's default
range is 90 days while an export call repairs at most 14, so the prompt also tells the assistant not to loop: past
that it hands you the list and points at the Web UI or the REST API.
