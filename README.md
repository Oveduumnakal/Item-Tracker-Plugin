<p align="center">
  <img src="banner.png" alt="Stockpile banner">
</p>
<h1 align="center">Stockpile</h1>

A RuneLite plugin for tracking the quantity, value, and profitability of the
items you gather, produce, or trade. Stockpile pairs live Grand Exchange /
wiki pricing with a cost-basis profit model and an in-panel charting suite, so
you can see at a glance what your tracked items are worth and how much you have
actually made.

## Features

### Item tracking

- Track how many of a chosen item you have gathered, produced, or acquired.
- Add items manually, or let auto-add modes pick them up as you obtain them.
- Consolidates like entries and reconciles collection-log quantities.
- Highlight tracked items on the ground and in your inventory.

### Live pricing

- Current Grand Exchange / live wiki value for every tracked item.
- Time-window price and volume breakdowns (5 minute, 1h, 6h, 24h, 1 week,
  1 month, 1 year) sourced from the wiki realtime and timeseries APIs.
- Market-state classification and summary statistics per item.

### Profit tracking

- Cost-basis model: record acquisitions with their quantity and price, and the
  plugin tracks your average cost.
- Per-item estimated profit, plus a portfolio-wide profit total.
- Editable acquisitions table so you can correct or backfill your history.

### Detail view & charts

- A rich per-item detail view with market data and a clean, configurable layout.
- In-panel price and volume graphs with selectable timeframes, a hover
  crosshair, and optional smoothing.
- Pop-out chart windows for a larger, standalone view.

### Notifications

- Per-item rules on price, percent change, volume, and more.
- Flexible operators (above / below / crosses, etc.) with one-shot or repeating
  triggers.

### Configurable UI

- Settings organized into focused sections.
- Detailed View options: section ordering and visibility, overview presets,
  estimate position and spacing, and auto-add behavior.
- Compact, consistent number formatting throughout (e.g. `234K`, `1.5M`, `2.1B`).

## Links

- [Report a bug](https://github.com/Oveduumnakal/Item-Tracker-Plugin/issues/new?labels=bug)
- [Request a feature](https://github.com/Oveduumnakal/Item-Tracker-Plugin/issues/new?labels=enhancement)
- [Buy me a coffee](https://buymeacoffee.com/oveduumnakal)

## Building / running

Run the `run` Gradle task to launch a development client with the plugin loaded:

```
./gradlew run
```

To build the plugin jar:

```
./gradlew build
```
