# Web UI

The FDDB Exporter includes a built-in web interface powered
by [Vaadin](https://vaadin.com/) / [Hilla](https://hilla.dev/), providing a modern and intuitive way to interact with
all features of the application directly from your browser.

## Overview

The web UI offers a complete graphical interface for all FDDB Exporter features. All functionality available through the
REST API can also be accessed through this user-friendly interface, making it easier to manage your nutritional data
without needing to make API calls directly.

## Features

The web interface provides access to all core features:

- **Dashboard**: View your nutritional statistics at a glance
- **Export Data**: Manually trigger data exports for specific date ranges or recent days
- **Daily Search**: Look up nutritional data for any specific date
- **Product Search**: Find products by name with optional day-of-week filtering
- **Statistics**: View comprehensive statistics including averages, highest values, and entry coverage
- **Rolling Averages**: Calculate averages for custom date ranges
- **Correlation Analysis**: Analyze correlations between products and specific dates to identify potential food
  sensitivities
- **Download Data**: Export your data in CSV or JSON format with customizable options

## Screenshots

The following screenshots showcase several pages of the FDDB Exporter web interface for demonstration purposes. Both
desktop and mobile versions provide the same features and functionality - the differences shown below are purely visual
adaptations for optimal display on different screen sizes.

### Desktop Views

<table>
  <tr>
    <th align="center">Dashboard</th>
    <th align="center">Product Search</th>
  </tr>
  <tr>
    <td align="center">
      <a href="../images/desktop-dashboard.jpg" target="_blank">
        <img src="../images/desktop-dashboard.jpg" alt="Dashboard" width="400"/>
      </a>
    </td>
    <td align="center">
      <a href="../images/desktop-product-search.jpg" target="_blank">
        <img src="../images/desktop-product-search.jpg" alt="Product Search" width="400"/>
      </a>
    </td>
  </tr>
  <tr>
    <th align="center">Rolling Averages</th>
    <th align="center">Correlation Analysis</th>
  </tr>
  <tr>
    <td align="center">
      <a href="../images/desktop-rolling-averages.jpg" target="_blank">
        <img src="../images/desktop-rolling-averages.jpg" alt="Rolling Averages" width="400"/>
      </a>
    </td>
    <td align="center">
      <a href="../images/desktop-correlation.jpg" target="_blank">
        <img src="../images/desktop-correlation.jpg" alt="Correlation Analysis" width="400"/>
      </a>
    </td>
  </tr>
</table>

### Mobile Views

<table>
  <tr>
    <th align="center">Data Export</th>
    <th align="center">Download</th>
  </tr>
  <tr>
    <td align="center">
      <a href="../images/mobile-export.jpg" target="_blank">
        <img src="../images/mobile-export.jpg" alt="Mobile Export" width="200"/>
      </a>
    </td>
    <td align="center">
      <a href="../images/mobile-download.jpg" target="_blank">
        <img src="../images/mobile-download.jpg" alt="Mobile Download" width="200"/>
      </a>
    </td>
  </tr>
</table>

## Mobile Support

The web UI is fully responsive and works seamlessly on mobile devices. You can access all features from your smartphone
or tablet with the same functionality as on desktop.

### Progressive Web App (PWA)

The FDDB Exporter web UI can be installed as a Progressive Web App (PWA) on your device for easier access. This may only
work if you are accessing the web UI over HTTPS.

**On Mobile (iOS/Android):**

1. Open the FDDB Exporter URL in your mobile browser
2. Tap the browser menu (share icon on iOS, three dots on Android)
3. Select "Add to Home Screen" or "Install App"
4. The app will be added to your home screen for quick access

**On Desktop (Chrome/Edge):**

1. Open the FDDB Exporter URL in your browser
2. Look for the install icon in the address bar (or use the browser menu)
3. Click "Install" to add it as a standalone application

Once installed as a PWA, you can launch the FDDB Exporter like any other app on your device, with faster loading times
and an app-like experience.

## Accessing the UI

The web interface is available at the root URL of your FDDB Exporter instance:

```
http://localhost:8080/
```

Simply navigate to this URL in your browser after starting the application.

## Version Updates

The web UI includes an automatic version check feature. When a new version of FDDB Exporter is available, a notification
will appear in the sidebar menu and in the logs.

This helps you stay up to date with the latest features and improvements.
