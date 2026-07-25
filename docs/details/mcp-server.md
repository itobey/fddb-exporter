# MCP Server

The application can expose your nutrition data as an [MCP](https://modelcontextprotocol.io/) server, so an AI assistant
(Claude Desktop, Claude Code, or any other MCP client) can answer questions about your diary in natural language:

> - "How much protein did I average last month vs. the month before?"
> - "Which days did I eat over 3000 kcal this year?"
> - "How often do I eat oats, and on which weekdays?"
> - "Where do most of my calories come from?"
> - "Did I stay under 2200 kcal and over 120 g of protein last month?"
> - "What did I eat yesterday?"

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

### Meta

| Tool              | Parameters | Returns                                                                         |
|-------------------|------------|---------------------------------------------------------------------------------|
| `get_data_schema` | –          | The data dictionary: every field, its unit, and the pitfalls of interpreting it  |

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
