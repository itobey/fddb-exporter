# MCP Server

The application can expose your nutrition data as an [MCP](https://modelcontextprotocol.io/) server, so an AI assistant
(Claude Desktop, Claude Code, or any other MCP client) can answer questions about your diary in natural language:

> - "How much protein did I average last month?"
> - "Which days did I eat over 3000 kcal this year?"
> - "How often do I eat oats, and on which weekdays?"
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

| Tool              | Parameters                                                | Returns                                                                                        |
|-------------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `get_day`         | `date`                                                    | Daily totals and the full product list for one day                                             |
| `get_days`        | `fromDate`, `toDate`, `includeProducts` (default `false`) | Daily totals for a date range, oldest first. Limited to 366 days                               |
| `search_products` | `name`, `daysOfWeek?`, `fromDate?`, `toDate?`, `limit?`   | Every occurrence of a product with its date, amount and macros                                 |
| `get_stats`       | –                                                         | Entry count, first/last entry, coverage, unique products, all-time averages, extremes, streaks |
| `get_averages`    | `fromDate`, `toDate`                                      | Average daily calories, fat, carbs, sugar, protein and fibre over a range                      |
| `get_data_schema` | –                                                         | The data dictionary: every field, its unit, and the pitfalls of interpreting it                |

### Dates

Every date parameter accepts an ISO date (`2024-12-22`) as well as the relative aliases `today`, `yesterday` and
`N_days_ago` (e.g. `13_days_ago`). "The last 14 days" is therefore `fromDate=13_days_ago, toDate=today`, and the
assistant never has to guess what today's date is.

### Response size

MCP results are read by a language model, so the tools are built to keep responses small:

- `get_days` omits the product lists unless `includeProducts` is set — a long range with products is a very large
  response.
- `search_products` caps its results (100 by default, 500 at most) and reports a `truncated` flag, so a count derived
  from a capped result is never mistaken for the full picture.
- The database id is stripped and empty fields are dropped.

### Things the tools do not do

- They never write. Exporting new data from FDDB stays with the REST API, the Web UI and the scheduler.
- Averages and trends only cover days that actually have an entry; unlogged days are skipped rather than counted as
  zero.
