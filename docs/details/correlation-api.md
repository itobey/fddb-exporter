# Correlation API

## Overview
The Correlation API enables users to identify patterns between specific dates and products based on keyword matching. 
This may help you correlate events such as allergies or health issues with the consumption of 
specific products. If you suspect a correlation between a specific product and a health issue and have enough data
from FDDB, this tool can help you identify potential correlations.

## Key Features:
- Customizable Input: Include or exclude specific keywords and define a list of occurrence dates.
- Flexible Correlation Periods: Analyze correlations on the same day, up to two days prior, or across two or three days.
- Data Filtering: Optionally specify a start date to narrow down the analysis.
- Comprehensive Output: Results include matched products, correlated dates, and detailed percentages across time periods.

## What the API Does
1. Input a Query: Provide a list of inclusion and exclusion keywords, dates of interest, and (optionally) a start date.
2. Search for Matches: The API matches products in a local MongoDB database against the keywords and dates provided.
3. Generate Correlations: It calculates the frequency and percentage of matches across different time periods:
- Same Day
- One Day Before
- Two Days Before
- Across 2 Days (Same Day + One Day Before)
- Across 3 Days (Same Day + Two Days Before)

This approach accounts for delayed reactions, ensuring comprehensive correlation analysis.

### Input Parameters
| Field Name           | Type     | Description                                                                | Required |
|----------------------|----------|----------------------------------------------------------------------------|----------|
| `inclusionKeywords`  | `Array`  | List of keywords to include in the search (e.g., `["cherry"]`).            | Yes      |
| `occurrenceDates`    | `Array`  | List of dates to correlate against (e.g., `["2024-09-22", "2024-10-19"]`). | Yes      |
| `exclusionKeywords`  | `Array`  | List of keywords to exclude from the search (e.g., `["flavour"]`).         | No       |
| `startDate`          | `String` | Date after which the correlation should be done (e.g., `"2024-09-15"`).    | No       |

It is advisable to use the `startDate` parameter to narrow down the analysis to a specific date range, 
if you do not want to analyze the entire dataset or if you have more data from fddb but started tracking occurrences 
after a certain date.

Example Input:
```
{
  "inclusionKeywords": ["cherry", "cherries"],
  "occurrenceDates": ["2024-08-22", "2024-09-22", "2024-09-27","2024-10-14", "2024-10-19","2024-10-21"],
  "exclusionKeywords": ["flavour", "artificial"],
  "startDate": "2024-09-15"
}
```

### Output Parameters

The output of the API is a JSON object containing the following fields:

| Field Name              | Type      | Description                                                        |
|-------------------------|-----------|--------------------------------------------------------------------|
| `correlations`          | `Object`  | Correlation results grouped by time period.                        |
| `matchedProducts`       | `Array`   | List of products matching the inclusion and exclusion criteria.    |
| `matchedDates`          | `Array`   | List of occurrence dates that correlate with the matched products. |
| `amountMatchedProducts` | `Integer` | Total number of matched products.                                  |
| `amountMatchedDates`    | `Integer` | Total number of matched dates.                                     |

#### correlations Object
| Subfield      | Description                                                     |
|---------------|-----------------------------------------------------------------|
| `across3Days`   | Matches across the same day, one day prior, and two days prior. |
| `across2Days`   | Matches across the same day and one day prior.                  |
| `sameDay`       | Matches on the same day.                                        |
| `oneDayBefore`  | Matches one day prior to the occurrence date.                   |
| `twoDaysBefore` | Matches two days prior to the occurrence date.                  |

Example Output:
```
{
	"correlations": {
		"across3Days": {
			"percentage": 80.0,
			"matchedDates": [
				"2024-09-21",
				"2024-09-26",
				"2024-10-12",
				"2024-10-19"
			],
			"matchedDays": 4
		},
		"across2Days": {
			"percentage": 60.0,
			"matchedDates": [
				"2024-09-21",
				"2024-09-26",
				"2024-10-19"
			],
			"matchedDays": 3
		},
		"sameDay": {
			"percentage": 20.0,
			"matchedDates": [
				"2024-10-19"
			],
			"matchedDays": 1
		},
		"oneDayBefore": {
			"percentage": 40.0,
			"matchedDates": [
				"2024-09-21",
				"2024-09-26"
			],
			"matchedDays": 2
		},
		"twoDaysBefore": {
			"percentage": 40.0,
			"matchedDates": [
				"2024-10-12",
				"2024-10-19"
			],
			"matchedDays": 2
		}
	},
	"matchedProducts": [
		"Cherry jam",
		"cherries",
		"cherry cake"
	],
	"matchedDates": [
		"2024-09-21",
		"2024-09-26",
		"2024-09-29",
		"2024-10-12",
		"2024-10-19"
	],
	"amountMatchedProducts": 3,
	"amountMatchedDates": 5
}
```
