# REST API

## Overview

This API allows you to retrieve and export data from the database. The endpoints support operations such as retrieving
all data, filtering by date, searching for specific products, and exporting data for a specified date range.

Example responses:

1. [example response for querying data from the database](../resources/example-response.json)
2. [example response when querying a product name](../resources/example-response-products.json)
3. [example response when retrieving stats](../resources/example-response-stats.json)

## Endpoints

### Retrieve All Data

> **GET** `/api/v1/fddbdata`

- **Description:** Retrieves all data from the database as JSON.
- **Response:** A JSON array containing all entries (see full [example response](../resources/example-response.json) of
  an entry in this array).

    ```json
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
            ...
          ],
          "totalCalories": 2437.0,
          "totalFat": 93.5,
          "totalCarbs": 285.5,
          "totalSugar": 76.3,
          "totalProtein": 103.9,
          "totalFibre": 10.3
        },
      ...
    ]
    ```

---

### Retrieve Data by Date

> **GET** `/api/v1/fddbdata/{date}`

- **Description:** Retrieves data for a specific day from the database as JSON.
- **Path Parameter:**
    - `date` _(required)_: The specific date in `YYYY-MM-DD` format.
- **Example:** `/api/v1/fddbdata/2024-08-24`
- **Response:** A JSON object containing the data for the specified date (see
  full [example response](../resources/example-response.json)).

    ```json
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
        ...
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

> **GET** `/api/v1/fddbdata/products?name={product}`

- **Description:** Retrieves all entries matching the given product name as JSON. The search is fuzzy, allowing for
  partial matches.
- **Query Parameter:**
    - `name` _(required)_: The name of the product to search for.
- **Example:** `/api/v1/fddbdata/products?name=mountain` _(This will find entries like `Mountain Dew`.)_
- **Response:** A JSON array containing all matching entries (see
  full [example response](../resources/example-response-products.json)).

    ```json
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
      ...
    ]
    ```

---

### Export Data by Date Range

> **POST** `/api/v1/fddbdata`

- **Description:** Exports all entries within a specified date range.
- **Request Body:**
    - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
    - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
- **Example Payload:**

    ```json
    {
    "fromDate": "2021-05-13",
    "toDate": "2021-08-18"
    }

- **Response:** A JSON object containing the data:

    ```json
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

> **GET** `/api/v1/fddbdata/export?days={amount}&includeToday={bool}`

- **Description:** Exports entries for the last specified number of days.
- **Query Parameters:**
    - `days` _(required)_: The number of days to export.
    - `includeToday` _(optional)_: Whether to include the current day in the export. (`true` or `false`)
- **Example:** `/api/v1/fddbdata/export?days=5&includeToday=true`
- **Response:** A JSON object containing the data:

    ```json
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

> **GET** `/api/v1/fddbdata/stats`

- **Description:** Retrieve the stats to the saved data.
- **Response:** A JSON object containing the data (see [example response](../resources/example-response-stats.json)).

    ```json
    {
      "amountEntries": 606,
      "firstEntryDate": "2023-01-01",
      "entryPercentage": 95.13343799058084,
      "averageTotals": {
        "avgTotalCalories": 2505.651815181518,
        "avgTotalFat": 125.65561056105611,
        "avgTotalCarbs": 204.4245874587459,
        "avgTotalSugar": 63.448019801980195,
        "avgTotalProtein": 117.9844884488449,
        "avgTotalFibre": 18.43102310231023
      },
      "last7DaysAverage": {
        "avgTotalCalories": 3054.4285714285716,
        "avgTotalFat": 123.92857142857143,
        "avgTotalCarbs": 303.75714285714287,
        "avgTotalSugar": 85.65714285714286,
        "avgTotalProtein": 136.18571428571428,
        "avgTotalFibre": 25.82857142857143
      },
      "last30DaysAverage": {
        "avgTotalCalories": 2833.3333333333335,
        "avgTotalFat": 120.11333333333333,
        "avgTotalCarbs": 286.4,
        "avgTotalSugar": 81.34333333333333,
        "avgTotalProtein": 127.08,
        "avgTotalFibre": 19.363333333333333
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

### Migrate MongoDB data to InfluxDb

> **POST** `/api/v1/fddbdata/migrateToInfluxDb`

- **Description:** Migrates existing data from MongoDB to InfluxDb.
- **Response:** HTTP 200 if successful.