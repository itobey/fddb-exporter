# What is FDDB Exporter?

FDDB Exporter is a tool designed to extract nutritional data from [FDDB.info](https://fddb.info/) and store it in a
MongoDB / InfluxDB database. It comes with a built-in [Web UI](/visualization/web-ui.html) for easy access to all
features,
as well as a comprehensive [REST API](/details/rest-api.html) for programmatic access.

You may want to do this for the following reasons:

- FDDB only stores entries for up to 2 years for premium members, and even less for free users. Storing your data in a
  database allows you to keep your data for as long as you want.
- You can query your data to see on which days you have entered specific products. This is especially useful if you
  suspect food allergies or sensitivities to quickly identify problematic products.
- You can use the data to create summaries or graphs and charts to visualize your nutritional intake over time.

## Key Highlights

- **Web UI**: Modern, responsive interface accessible from any browser
- **Mobile Support**: Works great on mobile devices and can be installed as a Progressive Web App (PWA)
- **Product Search**: Find products with optional day-of-week filtering (e.g., only show products eaten on Mondays)
- **Correlation Analysis**: Identify potential food sensitivities by correlating products with specific dates
- **Automatic Updates**: Get notified when a new version is available

