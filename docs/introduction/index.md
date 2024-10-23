# What is FDDB Exporter?

FDDB Exporter is a tool designed to extract nutritional data from [FDDB.info](https://fddb.info/) and store it in a
MongoDB / InfluxDB database.

You may want to do this for the following reasons:

- FDDB only stores entries for up to 2 years for premium members, and even less for free users. Storing your data in a
  database allows you to keep your data for as long as you want.
- You can query your data to see on which days you have entered specific products. This is especially useful if you
  suspect food allergies
  or sensitivities to quickly identify problematic products.
- You can use the data to create summaries or graphs and charts to visualize your nutritional intake over time.
