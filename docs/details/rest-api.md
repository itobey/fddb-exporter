# REST API

## API version changes

This documentation now references the v2 API. The main changes are:

- Base path changed from `/api/v1` to `/api/v2`.
- The migration endpoint moved from `/api/v1/fddbdata/migrateToInfluxDb` to `/api/v2/migration/toInfluxDb` (breaking
  change).
- The stats endpoints were moved to top-level under `/api/v2` (`/api/v2/stats` and `/api/v2/stats/averages`).
- The stats endpoint for last 7 and 30 days averages has been removed in `/api/v1`. Use the new rolling averages
  endpoint in `/api/v2` instead.

**Important dates:**

- **v2 Release:** 2025-12-26
- **v1 Deprecation:** 2025-12-26 (deprecated but still functional)
- **v1 Removal:** 2026-06-30 (v1 will be completely removed)

## Overview

This API allows you to retrieve and export data from the database. The endpoints support operations such as retrieving
all data, filtering by date, searching for specific products, and exporting data for a specified date range.

Example responses:

1. [example response for querying data from the database](../resources/example-response.json)
2. [example response when querying a product name](../resources/example-response-products.json)
3. [example response when retrieving stats](../resources/example-response-stats.json)

## Endpoints

### Retrieve All Data

> **GET** `/api/v2/fddbdata`

- **Description:** Retrieves all data from the database as JSON.
- **Response:** A JSON array containing all entries (see full [example response](../resources/example-response.json) of
  an entry in this array).

    ```
    [
        {
          "id": "66d18658bc73187ea859f67c",
          "date": "2024-08-28",
          "products": [
            {
              "name": "Panini Rolls",
              "amount": "75 g",
              "calories": 176.0,
              "fat": 2.2,
              "carbs": 33.8,
              "protein": 2.8,
              "link": "/db/en/food/schaer_panini_rolls/index.html"
            },
            [...]
          ],
          "totalCalories": 2437.0,
          "totalFat": 93.5,
          "totalCarbs": 285.5,
          "totalSugar": 76.3,
          "totalProtein": 103.9,
          "totalFibre": 10.3
        },
      [...]
    ]
    ```

---


### Retrieve Data by Date

> **GET** `/api/v2/fddbdata/{date}`

- **Description:** Retrieves data for a specific day from the database as JSON.
- **Path Parameter:**
    - `date` _(required)_: The specific date in `YYYY-MM-DD` format.
- **Example:** `/api/v2/fddbdata/2024-08-24`
- **Response:** A JSON object containing the data for the specified date (see
  full [example response](../resources/example-response.json)).

    ```
    {
      "id": "66d18658bc73187ea859f67c",
      "date": "2024-08-28",
      "products": [
        {
          "name": "Panini Rolls",
          "amount": "75 g",
          "calories": 176.0,
          "fat": 2.2,
          "carbs": 33.8,
          "protein": 2.8,
          "link": "/db/en/food/schaer_panini_rolls/index.html"
        },
        [...]
      ],
      "totalCalories": 2437.0,
      "totalFat": 93.5,
      "totalCarbs": 285.5,
      "totalSugar": 76.3,
      "totalProtein": 103.9,
      "totalFibre": 10.3
    }
    ```

---

### Search Products by Name

> **GET** `/api/v2/fddbdata/products?name={product}`

- **Description:** Retrieves all entries matching the given product name as JSON. The search is fuzzy, allowing for
  partial matches.
- **Query Parameter:**
    - `name` _(required)_: The name of the product to search for.
- **Example:** `/api/v2/fddbdata/products?name=mountain` _(This will find entries like `Mountain Dew`.)_
- **Response:** A JSON array containing all matching entries (see
  full [example response](../resources/example-response-products.json)).

    ```
    [
      {
        "date": "2023-01-06",
        "product": {
          "name": "Pizza Brot",
          "amount": "150 g",
          "calories": 391.0,
          "fat": 5.1,
          "carbs": 71.1,
          "protein": 15.0,
          "link": "/db/en/food/marziale_pizza_brot/index.html"
        }
      },
      [...]
    ]
    ```

---

### Export Data by Date Range

> **POST** `/api/v2/fddbdata`

- **Description:** Exports all entries within a specified date range.
- **Request Body:**
    - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
    - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
- **Example Payload:**

    ```
    {
    "fromDate": "2021-05-13",
    "toDate": "2021-08-18"
    }

- **Response:** A JSON object containing the data:

    ```
    {
      "successfulDays": [
        "2024-08-30",
        "2024-08-31"
      ],
      "unsuccessfulDays": [
        "2024-08-29"
      ]
    }
    ```

---

### Export Data for Last N Days

> **GET** `/api/v2/fddbdata/export?days={amount}&includeToday={bool}`

- **Description:** Exports entries for the last specified number of days.
- **Query Parameters:**
    - `days` _(required)_: The number of days to export.
    - `includeToday` _(optional)_: Whether to include the current day in the export. (`true` or `false`)
- **Example:** `/api/v2/fddbdata/export?days=5&includeToday=true`
- **Response:** A JSON object containing the data:

    ```
    {
      "successfulDays": [
        "2024-08-30",
        "2024-08-31"
      ],
      "unsuccessfulDays": [
        "2024-08-29"
      ]
    }
    ```

---

### Retrieve Stats to Data

> **GET** `/api/v2/stats`

- **Description:** Retrieve the stats to the saved data.
- **Note:** The `last7DaysAverage` and `last30DaysAverage` fields have been removed from this endpoint. Use the new
  rolling averages endpoint for flexible period-based averages.
- **Response:** A JSON object containing the data (see [example response](../resources/example-response-stats.json)).

    ```
    {
      "amountEntries": 606,
      "firstEntryDate": "2023-01-01",
      "mostRecentMissingDay": "2024-12-20",
      "entryPercentage": 95.1,
      "uniqueProducts": 150,
      "averageTotals": {
        "avgTotalCalories": 2505.7,
        "avgTotalFat": 125.7,
        "avgTotalCarbs": 204.4,
        "avgTotalSugar": 63.4,
        "avgTotalProtein": 118.0,
        "avgTotalFibre": 18.4
      },
      "highestCaloriesDay": {
        "date": "2024-07-31",
        "total": 5317.0
      },
      "highestFatDay": {
        "date": "2023-09-23",
        "total": 260.3
      },
      "highestCarbsDay": {
        "date": "2024-07-31",
        "total": 501.7
      },
      "highestProteinDay": {
        "date": "2024-08-03",
        "total": 234.1
      },
      "highestFibreDay": {
        "date": "2023-05-09",
        "total": 53.8
      },
      "highestSugarDay": {
        "date": "2023-10-21",
        "total": 220.4
      }
    }
    ```

---

### Retrieve Rolling Averages

> **GET** `/api/v2/stats/averages?fromDate={startDate}&toDate={endDate}`

- **Description:** Retrieve rolling averages for a specified date range. This endpoint calculates averages for all
  entries between the from and to dates (inclusive).
- **Query Parameters:**
  - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
  - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
- **Example:** `/api/v2/stats/averages?fromDate=2024-01-01&toDate=2024-01-31`
- **Response:** A JSON object containing the rolling averages (
  see [example response](../resources/example-response-rolling-averages.json)).

    ```
    {
      "fromDate": "2024-01-01",
      "toDate": "2024-01-31",
      "averages": {
        "avgTotalCalories": 3054.4,
        "avgTotalFat": 123.9,
        "avgTotalCarbs": 303.7,
        "avgTotalSugar": 85.6,
        "avgTotalProtein": 136.8,
        "avgTotalFibre": 25.8
      }
    }
    ```
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.
  - Returns HTTP 400 Bad Request if dates are not in the format `YYYY-MM-DD`.

---

### Migrate MongoDB data to InfluxDb

> **POST** `/api/v2/migration/toInfluxDb`

- **Description:** Migrates existing data from MongoDB to InfluxDb.
- **Response:** HTTP 200 if successful.
