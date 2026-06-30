/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oveduumnakal;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

/**
 * RuneLite configuration for the Stockpile plugin.
 *
 * <p>Defines every user-facing setting as a defaulted {@code @ConfigItem}
 * accessor, grouped into five {@code @ConfigSection}s: main view, tracked-item
 * row display, GE estimates, tracking/highlighting, and the detail view. The
 * {@code KEY_*} constants are the persisted setting keys (also used directly by
 * the plugin when reading/writing config), and {@link #GROUP} names the config
 * group. Each accessor's behavior is described by its annotation; the per-item
 * {@code name}/{@code description} are the source of truth shown in the UI.
 */
@ConfigGroup(StockpileConfig.GROUP)
public interface StockpileConfig extends Config
{
	String GROUP = "stockpile";

	String KEY_TRACKED_ITEMS = "trackedItemIds";
	String KEY_CATEGORIES = "trackedCategories";

	String KEY_PRICE_REFRESH_SECONDS = "priceRefreshSeconds";
	String KEY_PRICE_CHANGE_INDICATOR = "priceChangeIndicator";

	String KEY_SHOW_COL_HIGH = "showColHigh";
	String KEY_SHOW_COL_LOW = "showColLow";
	String KEY_SHOW_COL_AVG = "showColAvg";
	String KEY_SHOW_COL_VOLUME = "showColVolume";
	String KEY_SHOW_QUANTITY_VALUE = "showQuantityValue";
	String KEY_ROW_1_DATA = "row1Data";
	String KEY_ROW_2_DATA = "row2Data";
	String KEY_ROW_3_DATA = "row3Data";
	String KEY_SHOW_ITEM_PROFIT_ROW = "showItemProfitRow";
	String KEY_STALE_PRICE_THRESHOLD = "stalePriceThresholdMinutes";
	String KEY_COMPACT_VIEW = "compactView";

	String KEY_SHOW_ITEM_VALUES = "showItemValues";
	String KEY_SHOW_COLLECTION_VALUES = "showCollectionValues";
	String KEY_SHOW_MARKET_INFO = "showMarketInfo";
	String KEY_SHOW_PRICE_OVERVIEW = "showPriceOverview";
	String KEY_SHOW_PRICE_GRAPH = "showPriceGraph";
	String KEY_SHOW_VOLUME_GRAPH = "showVolumeGraph";
	String KEY_SHOW_ALCH_INFO = "showAlchInfo";
	String KEY_SHOW_NOTIFICATIONS = "showNotifications";
	String KEY_SHOW_ITEM_LOG = "showItemLog";
	String KEY_PRICE_OVERVIEW_ROWS = "priceOverviewPreset";
	String KEY_AUTO_ADD_ITEMS = "autoAddItems";
	String KEY_NOTIFICATION_STYLE = "notificationStyle";

	String KEY_SHOW_GE_ESTIMATES = "showGeEstimates";
	String KEY_GE_ESTIMATES_POSITION = "geEstimatesPosition";
	String KEY_GE_ESTIMATES_FORMAT = "geEstimatesFormat";
	String KEY_GE_ESTIMATES_SPACING = "geEstimatesSpacing";
	String KEY_SHOW_EST_HIGH = "showEstHigh";
	String KEY_SHOW_EST_LOW = "showEstLow";
	String KEY_SHOW_EST_AVG = "showEstAvg";
	String KEY_SHOW_EST_PROFIT = "showEstProfit";

	String KEY_ADD_CONTEXT_MENU_OPTION = "addContextMenuOption";
	String KEY_TRACK_ITEM_COLOR = "trackItemColor";
	String KEY_STOP_TRACKING_COLOR = "stopTrackingColor";
	String KEY_HIGHLIGHT_TRACKED_ITEMS = "highlightTrackedItems";
	String KEY_HIGHLIGHT_COLOR = "highlightColor";
	String KEY_GLOW_EFFECT = "glowEffect";

	/** Top-level panel behavior: price refresh, change indicator, and global toggles. */
	@ConfigSection(
			name = "Main View Settings",
			description = "Top-level main view settings",
			position = 0
	)
	String mainViewSection = "mainView";

	/** Which columns and rows each tracked-item entry shows in the list. */
	@ConfigSection(
			name = "Tracked Item Display",
			description = "Controls what each tracked item row shows",
			position = 1
	)
	String trackedItemSection = "trackedItem";

	/** Placement, format, spacing, and rows of the estimated GE sell-value block. */
	@ConfigSection(
			name = "GE Estimates Display",
			description = "Controls the Estimated GE Sell Value section",
			position = 2
	)
	String geEstimatesSection = "geEstimates";

	/** Context-menu integration, highlight colors/mode, and the glow effect. */
	@ConfigSection(
			name = "Tracking",
			description = "Context menu, highlighting, and tracking behavior",
			position = 3
	)
	String trackingSection = "tracking";

	/** Order, visibility, and contents of the per-item detail view sections. */
	@ConfigSection(
			name = "Detailed View",
			description = "Order, visibility, and contents of the item detail view sections",
			position = 4
	)
	String detailViewSection = "detailView";

	@Range(min = 30)
	@ConfigItem(
			keyName = KEY_PRICE_REFRESH_SECONDS,
			name = "Price Refresh (s)",
			description = "How often to refresh GE prices from the API. Minimum 30 seconds.",
			section = mainViewSection,
			position = 0
	)
	default int priceRefreshSeconds()
	{
		return 60;
	}

	@ConfigItem(
			keyName = KEY_PRICE_CHANGE_INDICATOR,
			name = "Price Change Indicator",
			description = "How to display the pulse indicator for price changes",
			section = mainViewSection,
			position = 1
	)
	default PriceIndicatorMode priceChangeIndicator()
	{
		return PriceIndicatorMode.CHANGE;
	}

	@ConfigItem(
			keyName = KEY_SHOW_COL_HIGH,
			name = "Show High Value",
			description = "Show the High column in the tracked items list",
			section = trackedItemSection,
			position = 0
	)
	default boolean showColHigh()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_COL_LOW,
			name = "Show Low Value",
			description = "Show the Low column in the tracked items list",
			section = trackedItemSection,
			position = 1
	)
	default boolean showColLow()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_COL_AVG,
			name = "Show Avg Value",
			description = "Show the Avg column in the tracked items list",
			section = trackedItemSection,
			position = 2
	)
	default boolean showColAvg()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_COL_VOLUME,
			name = "Show Volume",
			description = "Show the Volume column in the tracked items list",
			section = trackedItemSection,
			position = 3
	)
	default boolean showColVolume()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_QUANTITY_VALUE,
			name = "Show Quantity Value",
			description = "Show the quantity value next to the item name",
			section = trackedItemSection,
			position = 4
	)
	default boolean showQuantityValue()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_ROW_1_DATA,
			name = "Row 1 Data",
			description = "Price data shown on the first row. None hides the row.",
			section = trackedItemSection,
			position = 5
	)
	default TimeWindow row1Data()
	{
		return TimeWindow.LIVE;
	}

	@ConfigItem(
			keyName = KEY_ROW_2_DATA,
			name = "Row 2 Data",
			description = "Price data shown on the second row. None hides the row.",
			section = trackedItemSection,
			position = 6
	)
	default TimeWindow row2Data()
	{
		return TimeWindow.H24;
	}

	@ConfigItem(
			keyName = KEY_ROW_3_DATA,
			name = "Row 3 Data",
			description = "Price data shown on the third row. None hides the row.",
			section = trackedItemSection,
			position = 7
	)
	default TimeWindow row3Data()
	{
		return TimeWindow.WEEK;
	}

	@ConfigItem(
			keyName = KEY_SHOW_ITEM_PROFIT_ROW,
			name = "Show Profit",
			description = "Show the Est. Profit row below each tracked item using only that item's cost basis",
			section = trackedItemSection,
			position = 8
	)
	default boolean showItemProfitRow()
	{
		return true;
	}

	@Range(min = 1)
	@ConfigItem(
			keyName = KEY_STALE_PRICE_THRESHOLD,
			name = "Stale Price (min)",
			description = "Dim the Ltst high or low when its last trade is older than this many minutes.",
			section = trackedItemSection,
			position = 9
	)
	default int stalePriceThresholdMinutes()
	{
		return 60;
	}

	@ConfigItem(
			keyName = KEY_COMPACT_VIEW,
			name = "Compact View",
			description = "Show tracked items as compact two-row entries. Toggleable from the tracked list header.",
			section = trackedItemSection,
			position = 10
	)
	default boolean compactView()
	{
		return false;
	}

	@ConfigItem(
			keyName = KEY_SHOW_ITEM_VALUES,
			name = "Show Item Values",
			description = "Position of the Item Current Values section, or None to hide it",
			section = detailViewSection,
			position = 0
	)
	default SectionSlot showItemValues()
	{
		return SectionSlot.FIRST;
	}

	@ConfigItem(
			keyName = KEY_SHOW_COLLECTION_VALUES,
			name = "Show Collection Values",
			description = "Position of the Collection Current Values section, or None to hide it",
			section = detailViewSection,
			position = 1
	)
	default SectionSlot showCollectionValues()
	{
		return SectionSlot.SECOND;
	}

	@ConfigItem(
			keyName = KEY_SHOW_MARKET_INFO,
			name = "Show Market Info",
			description = "Position of the Market Info section, or None to hide it",
			section = detailViewSection,
			position = 2
	)
	default SectionSlot showMarketInfo()
	{
		return SectionSlot.THIRD;
	}

	@ConfigItem(
			keyName = KEY_SHOW_PRICE_OVERVIEW,
			name = "Show Price Overview",
			description = "Position of the Price Overview section, or None to hide it",
			section = detailViewSection,
			position = 3
	)
	default SectionSlot showPriceOverview()
	{
		return SectionSlot.FOURTH;
	}

	@ConfigItem(
			keyName = KEY_SHOW_PRICE_GRAPH,
			name = "Show Price Graph",
			description = "Position of the Price Graph section, or None to hide it",
			section = detailViewSection,
			position = 4
	)
	default SectionSlot showPriceGraph()
	{
		return SectionSlot.FIFTH;
	}

	@ConfigItem(
			keyName = KEY_SHOW_VOLUME_GRAPH,
			name = "Show Volume Graph",
			description = "Position of the Volume Graph section, or None to hide it",
			section = detailViewSection,
			position = 5
	)
	default SectionSlot showVolumeGraph()
	{
		return SectionSlot.SIXTH;
	}

	@ConfigItem(
			keyName = KEY_SHOW_ALCH_INFO,
			name = "Show Alch Info",
			description = "Position of the Alchemy Info section, or None to hide it",
			section = detailViewSection,
			position = 6
	)
	default SectionSlot showAlchInfo()
	{
		return SectionSlot.SEVENTH;
	}

	@ConfigItem(
			keyName = KEY_SHOW_NOTIFICATIONS,
			name = "Show Notifications",
			description = "Position of the per-item notification rule editor, or None to hide it. "
					+ "Does not enable or disable notifications — use the \"Notifications\" setting for that.",
			section = detailViewSection,
			position = 7
	)
	default SectionSlot showNotifications()
	{
		return SectionSlot.EIGHTH;
	}

	@ConfigItem(
			keyName = KEY_SHOW_ITEM_LOG,
			name = "Show Item Log",
			description = "Position of the Item Collection Log section, or None to hide it",
			section = detailViewSection,
			position = 9
	)
	default SectionSlot showItemLog()
	{
		return SectionSlot.NINTH;
	}

	@ConfigItem(
			keyName = KEY_PRICE_OVERVIEW_ROWS,
			name = "Price Overview Rows",
			description = "How many time-window rows the Price Overview shows. "
					+ "Recent: 5m, 1h, 12h, 24hr. Standard: 5m, 1h, 24hr, 1wk, 1mo. Detailed: all windows.",
			section = detailViewSection,
			position = 10
	)
	default OverviewPreset priceOverviewRows()
	{
		return OverviewPreset.DETAILED;
	}

	@ConfigItem(
			keyName = KEY_AUTO_ADD_ITEMS,
			name = "Auto Add Items",
			description = "Automatically add collection-log entries from inventory/bank changes, and the price they buy in at. "
					+ "High/Low/Avg use the latest matching price, Zero buys in at 0, Off disables auto-adds (manual edits still work).",
			section = detailViewSection,
			position = 11
	)
	default AutoAddMode autoAddItems()
	{
		return AutoAddMode.AVG;
	}

	@ConfigItem(
			keyName = KEY_NOTIFICATION_STYLE,
			name = "Notifications",
			description = "Master switch and delivery style for per-item notifications. Set to Off to disable "
					+ "all item notifications; otherwise use the gear to choose how they are delivered. "
					+ "Independent of \"Show Notifications\", which only controls where the rule editor appears.",
			section = detailViewSection,
			position = 12
	)
	default Notification notificationStyle()
	{
		return Notification.ON;
	}

	@ConfigItem(
			keyName = KEY_SHOW_GE_ESTIMATES,
			name = "Show GE Estimates Section",
			description = "Show the Estimated GE Sell Value section",
			section = geEstimatesSection,
			position = 0
	)
	default boolean showGeEstimates()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_GE_ESTIMATES_POSITION,
			name = "Position",
			description = "Top: under the search bar above the tracked items list. Bottom: below the tracked items list.",
			section = geEstimatesSection,
			position = 1
	)
	default EstimatesPosition geEstimatesPosition()
	{
		return EstimatesPosition.BOTTOM;
	}

	@ConfigItem(
			keyName = KEY_GE_ESTIMATES_FORMAT,
			name = "Price Format",
			description = "How GE Estimate prices are formatted. Short abbreviates with k/m/b and shows a full-value tooltip on hover.",
			section = geEstimatesSection,
			position = 2
	)
	default ValueFormat geEstimatesFormat()
	{
		return ValueFormat.FULL;
	}

	@ConfigItem(
			keyName = KEY_GE_ESTIMATES_SPACING,
			name = "Spacing",
			description = "Vertical spacing of the estimate rows. Default keeps the roomier layout; "
					+ "Compact tightens the rows to match the tracked items list.",
			section = geEstimatesSection,
			position = 3
	)
	default EstimatesSpacing geEstimatesSpacing()
	{
		return EstimatesSpacing.DEFAULT;
	}

	@ConfigItem(
			keyName = KEY_SHOW_EST_HIGH,
			name = "Show High Estimate",
			description = "Show the row containing the estimated high value",
			section = geEstimatesSection,
			position = 4
	)
	default boolean showEstHigh()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_EST_LOW,
			name = "Show Low Estimate",
			description = "Show the row containing the estimated low value",
			section = geEstimatesSection,
			position = 5
	)
	default boolean showEstLow()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_EST_AVG,
			name = "Show Avg Estimate",
			description = "Show the row containing the estimated average value",
			section = geEstimatesSection,
			position = 6
	)
	default boolean showEstAvg()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_EST_PROFIT,
			name = "Show Profit",
			description = "Show the row containing the estimated profit",
			section = geEstimatesSection,
			position = 7
	)
	default boolean showEstProfit()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_ADD_CONTEXT_MENU_OPTION,
			name = "Add Context Menu Option",
			description = "Add a \"Track Item\" / \"Stop Tracking\" entry to right-click menus on the ground, in the bank, or in the inventory",
			section = trackingSection,
			position = 0
	)
	default boolean addContextMenuOption()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_TRACK_ITEM_COLOR,
			name = "\"Track Item\" Color",
			description = "Color of the \"Track Item\" context menu entry",
			section = trackingSection,
			position = 1
	)
	default Color trackItemColor()
	{
		return new Color(0xd8, 0xfb, 0xd4);
	}

	@ConfigItem(
			keyName = KEY_STOP_TRACKING_COLOR,
			name = "\"Stop Tracking\" Color",
			description = "Color of the \"Stop Tracking\" context menu entry",
			section = trackingSection,
			position = 2
	)
	default Color stopTrackingColor()
	{
		return new Color(0xfb, 0xd4, 0xd4);
	}

	@ConfigItem(
			keyName = KEY_HIGHLIGHT_TRACKED_ITEMS,
			name = "Highlight Tracked Items",
			description = "Where to outline tracked items",
			section = trackingSection,
			position = 3
	)
	default HighlightMode highlightTrackedItems()
	{
		return HighlightMode.GROUND;
	}

	@ConfigItem(
			keyName = KEY_HIGHLIGHT_COLOR,
			name = "Highlight Color",
			description = "Color used to outline the highlighted tracked item",
			section = trackingSection,
			position = 4
	)
	default Color highlightColor()
	{
		return new Color(0xfb, 0xcd, 0x2b);
	}

	@ConfigItem(
			keyName = KEY_GLOW_EFFECT,
			name = "Glow Effect",
			description = "Speed of the highlight's breathing/glow effect. Off results in a solid color.",
			section = trackingSection,
			position = 5
	)
	default GlowSpeed glowEffect()
	{
		return GlowSpeed.MEDIUM;
	}
}
