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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import com.google.common.collect.ImmutableSet;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
		name = "Item Tracker",
		description = "Track item quantities across your inventory and bank with live GE prices",
		tags = {"items", "bank", "inventory", "price", "ge", "tracker"}
)
public class ItemTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private WikiRealtimePriceClient wikiPriceClient;

	@Inject
	private Notifier notifier;

	@Inject
	private Gson gson;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemTrackerHighlightOverlay highlightOverlay;

	@Inject
	private ItemTrackerGroundOverlay groundOverlay;

	private static final int[] RUNE_POUCH_TYPE_VARBITS = {
			VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
			VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
	};
	private static final int[] RUNE_POUCH_QUANTITY_VARBITS = {
			VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
			VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	};
	private static final ImmutableSet<Integer> RUNE_POUCH_VARBITS;
	static
	{
		ImmutableSet.Builder<Integer> b = ImmutableSet.builder();
		for (int v : RUNE_POUCH_TYPE_VARBITS)
			b.add(v);

		for (int v : RUNE_POUCH_QUANTITY_VARBITS)
			b.add(v);

		RUNE_POUCH_VARBITS = b.build();
	}

	private static final ImmutableSet<Integer> TRACKED_CONTAINERS = ImmutableSet.of(
			InventoryID.INV,
			InventoryID.WORN,
			InventoryID.BANK,
			InventoryID.LOOTING_BAG,
			InventoryID.SEED_BOX,
			InventoryID.SEED_VAULT,
			InventoryID.TACKLE_BOX,
			InventoryID.FORESTRY_KIT,
			InventoryID.HUNTSMANS_KIT,
			InventoryID.BARBARIAN_KNAPSACK
	);

	private final Map<Integer, TrackedItem> trackedItems = new LinkedHashMap<>();

	private final Map<Integer, Map<Integer, Integer>> containerCounts = new HashMap<>();

	private final Map<Integer, Integer> runePouchCounts = new HashMap<>();

	private final Set<Integer> seenContainersSinceLogin = new HashSet<>();

	private boolean runePouchSeenSinceLogin = false;
	private boolean pendingQuantitySync = false;
	private final Map<Integer, Integer> pendingItemDeltas = new HashMap<>();

	private final Map<TileItem, Tile> groundItems = new HashMap<>();

	private ItemTrackerPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> priceRefreshTask;
	private Instant lastPriceRefresh = null;

	// Item IDs for alchemy rune cost calculations.
	private static final int NATURE_RUNE_ID = 561;
	private static final int FIRE_RUNE_ID = 554;

	// Static item metadata (buy limit, alch values) from the Wiki /mapping endpoint.
	private volatile Map<Integer, WikiRealtimePriceClient.ItemMapping> itemMappings = Collections.emptyMap();

	@Override
	protected void startUp() throws Exception
	{
		panel = new ItemTrackerPanel(
				itemManager,
				config,
				this::addTrackedItem,
				this::removeTrackedItem,
				this::onAcquisitionsEdited,
				this::requestDetailData,
				this::clearAcquisitions,
				this::onNotificationsEdited,
				this::clearAllTrackedItems
		);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Item Tracker")
				.icon(icon)
				.priority(6)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(highlightOverlay);
		overlayManager.add(groundOverlay);
		clientThread.invokeLater(() ->
		{
			loadPersistedItems();
			// Show the correct view immediately, including the logged-out placeholder
			// or an empty (but logged-in) list where loadPersistedItems adds nothing.
			refreshPanel();
		});
		executor.execute(this::fetchItemMappings);
		scheduleRefresh();
	}

	private void fetchItemMappings()
	{
		Map<Integer, WikiRealtimePriceClient.ItemMapping> mappings = wikiPriceClient.fetchMapping();
		if (!mappings.isEmpty())
			itemMappings = mappings;
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(highlightOverlay);
		overlayManager.remove(groundOverlay);
		panel.shutdown();
		groundItems.clear();
		if (priceRefreshTask != null)
		{
			priceRefreshTask.cancel(false);
			priceRefreshTask = null;
		}

		trackedItems.clear();
		containerCounts.clear();
		runePouchCounts.clear();
		lastPriceRefresh = null;
	}

	@Provides
	ItemTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ItemTrackerConfig.class);
	}

	private void scheduleRefresh()
	{
		if (priceRefreshTask != null)
			priceRefreshTask.cancel(false);

		int rate = Math.max(30, config.priceRefreshSeconds());
		priceRefreshTask = executor.scheduleAtFixedRate(
				this::refreshGePrices, 0, rate, TimeUnit.SECONDS
		);
	}

	private static final Type PERSIST_TYPE = new TypeToken<List<PersistedItem>>(){}.getType();

	private static class PersistedItem
	{
		int itemId;
		int quantity;
		boolean costBasisInitialized;
		List<AcquisitionRecord> acquisitions;
		List<NotificationRule> notifications;
		boolean notificationsInitialized;
	}

	private void loadPersistedItems()
	{
		String saved = configManager.getRSProfileConfiguration(
				ItemTrackerConfig.GROUP, ItemTrackerConfig.KEY_TRACKED_ITEMS);
		if (saved == null || saved.trim().isEmpty())
			return;

		String trimmed = saved.trim();
		if (trimmed.startsWith("["))
		{
			try
			{
				List<PersistedItem> list = gson.fromJson(trimmed, PERSIST_TYPE);
				if (list != null)
				{
					for (PersistedItem p : list)
						addTrackedItem(p.itemId, p.quantity, p.acquisitions, p.notifications, p.notificationsInitialized, p.costBasisInitialized, false, TrackItemMode.TRACK);
				}

				return;
			}
			catch (JsonSyntaxException e)
			{
				log.warn("Failed to parse persisted item JSON; ignoring", e);
				return;
			}
		}

	}

	void persistTrackedItems()
	{
		List<PersistedItem> list = new ArrayList<>();
		for (TrackedItem item : trackedItems.values())
		{
			PersistedItem p = new PersistedItem();
			p.itemId = item.getItemId();
			p.quantity = item.getQuantity();
			p.costBasisInitialized = item.isCostBasisInitialized();
			p.acquisitions = item.getAcquisitions();
			p.notifications = item.getNotifications();
			p.notificationsInitialized = item.isNotificationsInitialized();
			list.add(p);
		}

		configManager.setRSProfileConfiguration(
				ItemTrackerConfig.GROUP, ItemTrackerConfig.KEY_TRACKED_ITEMS, gson.toJson(list, PERSIST_TYPE));
	}

	private void addTrackedItem(int itemId)
	{
		addTrackedItem(itemId, TrackItemMode.TRACK);
	}

	private void addTrackedItem(int itemId, TrackItemMode mode)
	{
		addTrackedItem(itemId, 0, null, null, false, false, true, mode);
	}

	private void addTrackedItem(int itemId, int initialQuantity, List<AcquisitionRecord> records, boolean costBasisInitialized)
	{
		addTrackedItem(itemId, initialQuantity, records, null, false, costBasisInitialized, true, TrackItemMode.TRACK);
	}

	private void addTrackedItem(int itemId, int initialQuantity, List<AcquisitionRecord> records,
			List<NotificationRule> notifications, boolean notificationsInitialized,
			boolean costBasisInitialized, boolean syncOnAdd, TrackItemMode mode)
	{
		clientThread.invokeLater(() ->
		{
			if (trackedItems.containsKey(itemId))
				return;

			var composition = itemManager.getItemComposition(itemId);
			TrackedItem tracked = new TrackedItem(itemId, composition.getName());
			tracked.setTradeable(composition.isTradeable());
			tracked.setQuantity(initialQuantity);
			tracked.setMode(mode == null ? TrackItemMode.TRACK : mode);
			if (records != null)
				tracked.setAcquisitions(new ArrayList<>(records));

			if (notifications != null)
				tracked.setNotifications(new ArrayList<>(notifications));

			tracked.setNotificationsInitialized(notificationsInitialized);
			tracked.setCostBasisInitialized(costBasisInitialized);
			trackedItems.put(itemId, tracked);

			if (syncOnAdd && tracked.getMode() == TrackItemMode.TRACK)
				syncQuantitiesForItem(tracked);

			persistTrackedItems();
			refreshPanel();
			refreshGePrices();
		});
	}

	private void removeTrackedItem(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			trackedItems.remove(itemId);
			persistTrackedItems();
			refreshPanel();
		});
	}

	/**
	 * Removes every entry from the tracked list (both tracked and view-only),
	 * which also discards their notifications and collection-log rows since those
	 * live on the {@link TrackedItem}. Invoked from the panel's Clear button after
	 * the user confirms.
	 */
	private void clearAllTrackedItems()
	{
		clientThread.invokeLater(() ->
		{
			trackedItems.clear();
			persistTrackedItems();
			refreshPanel();
		});
	}

	/**
	 * Lightweight periodic fetch used to keep the main panel's per-window
	 * stats (including 24h volume) current. Only the 5m resolution is pulled.
	 */
	private void requestSeries(int itemId, boolean refreshAfter)
	{
		executor.execute(() ->
		{
			List<WikiRealtimePriceClient.PricePoint> points = wikiPriceClient.fetchTimeseries(itemId, "5m");
			clientThread.invokeLater(() ->
			{
				TrackedItem tracked = trackedItems.get(itemId);
				if (tracked == null)
					return;

				tracked.setSeries5m(points);
				recomputeWindowStats(tracked);
				if (refreshAfter)
					refreshPanel();
			});
		});
	}

	/**
	 * Heavyweight fetch for the detailed view: pulls all three timeseries
	 * resolutions (5m, 6h, 24h) and item metadata so the graphs, price
	 * overview, market info and alch sections can all be populated without
	 * re-fetching when the user switches graph timeframes.
	 */
	private void requestDetailData(int itemId)
	{
		executor.execute(() ->
		{
			List<WikiRealtimePriceClient.PricePoint> s5 = wikiPriceClient.fetchTimeseries(itemId, "5m");
			List<WikiRealtimePriceClient.PricePoint> s1h = wikiPriceClient.fetchTimeseries(itemId, "1h");
			List<WikiRealtimePriceClient.PricePoint> s6 = wikiPriceClient.fetchTimeseries(itemId, "6h");
			List<WikiRealtimePriceClient.PricePoint> s24 = wikiPriceClient.fetchTimeseries(itemId, "24h");
			clientThread.invokeLater(() ->
			{
				TrackedItem tracked = trackedItems.get(itemId);
				if (tracked == null)
					return;

				tracked.setSeries5m(s5);
				tracked.setSeries1h(s1h);
				tracked.setSeries6h(s6);
				tracked.setSeries24h(s24);
				applyItemMetadata(tracked);
				recomputeWindowStats(tracked);

				final long nature = runePrice(NATURE_RUNE_ID);
				final long fire = runePrice(FIRE_RUNE_ID);
				SwingUtilities.invokeLater(() ->
				{
					panel.setAlchRunePrices(nature, fire);
					panel.refreshDetailData(itemId);
				});
				refreshPanel();
			});
		});
	}

	/**
	 * Recomputes the per-window stats from whichever timeseries resolution
	 * best covers each window. Must run on the client thread.
	 */
	private void recomputeWindowStats(TrackedItem tracked)
	{
		Map<TimeWindow, PriceStats> stats = new java.util.EnumMap<>(TimeWindow.class);
		for (TimeWindow w : TimeWindow.values())
		{
			if (w == TimeWindow.NONE)
				continue;

			if (w == TimeWindow.LIVE)
				stats.put(w, new PriceStats(tracked.getHighPrice(), tracked.getLowPrice(), tracked.getAvgPrice(), 0));
			else
			{
				// Prefer the resolution that best covers the window, but fall back
				// to the always-present 5m series until the detail view has fetched
				// the coarser series so the main panel stays populated.
				List<WikiRealtimePriceClient.PricePoint> series = tracked.getSeriesFor(w);
				if (series.isEmpty())
					series = tracked.getSeries5m();

				stats.put(w, WikiRealtimePriceClient.computeStats(series, w));
			}
		}

		tracked.setWindowStats(stats);
	}

	private void applyItemMetadata(TrackedItem tracked)
	{
		WikiRealtimePriceClient.ItemMapping mapping = itemMappings.get(tracked.getItemId());
		if (mapping == null)
			return;

		tracked.setBuyLimit(mapping.getLimit());
		tracked.setGeValue(mapping.getValue());
		tracked.setHighAlch(mapping.getHighAlch());
		tracked.setLowAlch(mapping.getLowAlch());
		tracked.setMetadataLoaded(true);
	}

	private long runePrice(int itemId)
	{
		TrackedItem tracked = trackedItems.get(itemId);
		if (tracked != null && tracked.getAvgPrice() > 0)
			return tracked.getAvgPrice();

		// Fall back to the item manager's cached GE price when the rune isn't tracked.
		return Math.max(0, itemManager.getItemPrice(itemId));
	}

	private void clearAcquisitions(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
				return;

			tracked.getAcquisitions().clear();
			persistTrackedItems();
			refreshPanel();
		});
	}

	private void refreshGePrices()
	{
		executor.execute(() ->
		{
			Map<Integer, WikiRealtimePriceClient.ItemPrices> all = wikiPriceClient.fetchAll();

			clientThread.invokeLater(() -> applyGePrices(all));
		});
	}

	private void applyGePrices(Map<Integer, WikiRealtimePriceClient.ItemPrices> all)
	{
		boolean fetchFailed = all.isEmpty();

		for (TrackedItem item : trackedItems.values())
		{
			WikiRealtimePriceClient.ItemPrices prices = all.get(item.getItemId());
			if (prices != null)
			{
				if (item.hasPrices())
				{
					item.setHighDelta(Long.compare(prices.getHigh(), item.getHighPrice()));
					item.setLowDelta(Long.compare(prices.getLow(), item.getLowPrice()));
					item.setAvgDelta(Long.compare(prices.avg(), item.getAvgPrice()));
					item.setPrevHighPrice(item.getHighPrice());
					item.setPrevLowPrice(item.getLowPrice());
					item.setPrevAvgPrice(item.getAvgPrice());
					item.setHasDeltas(true);
				}

				item.setHighPrice(prices.getHigh());
				item.setLowPrice(prices.getLow());
				item.setAvgPrice(prices.avg());
				item.setPriceLoadFailed(false);
				item.getWindowStats().put(TimeWindow.LIVE,
						new PriceStats(prices.getHigh(), prices.getLow(), prices.avg(), 0));

				if (!item.isCostBasisInitialized())
				{
					if (item.getQuantity() > 0 && item.getAcquisitions().isEmpty())
						addOpenAcquisition(item, item.getQuantity(), autoAddPrice(item));

					item.setCostBasisInitialized(true);
					persistTrackedItems();
				}
			}
			else if (!item.hasPrices() && item.isTradeable())
				item.setPriceLoadFailed(true);
		}

		if (fetchFailed)
		{
			refreshPanel();
			return;
		}

		lastPriceRefresh = Instant.now();
		evaluateNotifications();
		refreshPanel(true);

		final int detailId = panel.getDetailItemId();
		for (TrackedItem item : trackedItems.values())
		{
			if (item.isTradeable() && item.hasPrices())
			{
				// The open detail item gets the full multi-resolution refresh;
				// everything else just refreshes the cheap 5m stats.
				if (item.getItemId() == detailId)
					requestDetailData(item.getItemId());
				else
					requestSeries(item.getItemId(), false);
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!ItemTrackerConfig.GROUP.equals(event.getGroup()))
			return;

		if (SECTION_SLOT_KEYS.contains(event.getKey()))
		{
			boolean swapped = swapConflictingSection(event);
			refreshPanel();
			if (swapped)
			{
				// The swap writes the conflicting section's new slot to config, but the
				// open settings panel doesn't observe config changes, so the swapped
				// dropdown would keep showing its old value. Force the panel to rebuild.
				rebuildOpenConfigPanel();
			}

			return;
		}

		switch (event.getKey())
		{
			case ItemTrackerConfig.KEY_TRACKED_ITEMS:
				return;
			case ItemTrackerConfig.KEY_PRICE_REFRESH_SECONDS:
				scheduleRefresh();
				return;
			default:
				refreshPanel();
		}
	}

	/** Config keys of the nine "Show {Section}" detail-view ordering dropdowns. */
	private static final java.util.Set<String> SECTION_SLOT_KEYS = java.util.Set.of(
			ItemTrackerConfig.KEY_SHOW_ITEM_VALUES,
			ItemTrackerConfig.KEY_SHOW_COLLECTION_VALUES,
			ItemTrackerConfig.KEY_SHOW_MARKET_INFO,
			ItemTrackerConfig.KEY_SHOW_PRICE_OVERVIEW,
			ItemTrackerConfig.KEY_SHOW_PRICE_GRAPH,
			ItemTrackerConfig.KEY_SHOW_VOLUME_GRAPH,
			ItemTrackerConfig.KEY_SHOW_ALCH_INFO,
			ItemTrackerConfig.KEY_SHOW_NOTIFICATIONS,
			ItemTrackerConfig.KEY_SHOW_ITEM_LOG);

	/**
	 * Enforces the "each slot used once" rule: when a section is moved to a slot
	 * already held by another section, the other section takes the moved
	 * section's previous slot. NONE is exempt (any number may be hidden). The
	 * resulting state has no duplicate, so the re-entrant ConfigChanged from the
	 * swap finds nothing further to swap.
	 *
	 * @return true if a conflicting section was moved.
	 */
	private boolean swapConflictingSection(ConfigChanged event)
	{
		SectionSlot newSlot;
		SectionSlot oldSlot;
		try
		{
			newSlot = SectionSlot.valueOf(event.getNewValue());
			oldSlot = SectionSlot.valueOf(event.getOldValue());
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			return false;
		}

		if (newSlot == SectionSlot.NONE || newSlot == oldSlot)
			return false;

		for (String key : SECTION_SLOT_KEYS)
		{
			if (key.equals(event.getKey()))
				continue;

			SectionSlot other = configManager.getConfiguration(
					ItemTrackerConfig.GROUP, key, SectionSlot.class);
			if (other == newSlot)
			{
				configManager.setConfiguration(ItemTrackerConfig.GROUP, key, oldSlot);
				return true;
			}
		}

		return false;
	}

	/**
	 * Forces the currently open RuneLite settings panel to rebuild so a slot
	 * swapped onto another section is reflected in its dropdown. RuneLite's
	 * ConfigPanel doesn't observe config changes, and exposes no public refresh,
	 * so we locate it in the Swing tree and invoke its private rebuild(). Failure
	 * is non-fatal: the config value is already correct, only the display lags.
	 */
	private void rebuildOpenConfigPanel()
	{
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				Class<?> configPanelClass = Class.forName("net.runelite.client.plugins.config.ConfigPanel");
				java.lang.reflect.Method rebuild = configPanelClass.getDeclaredMethod("rebuild");
				rebuild.setAccessible(true);
				for (java.awt.Window window : java.awt.Window.getWindows())
				{
					for (java.awt.Component panel : findComponents(window, configPanelClass))
						rebuild.invoke(panel);
				}
			}
			catch (ReflectiveOperationException e)
			{
				log.debug("Unable to refresh config panel after section swap", e);
			}
		});
	}

	/** Recursively collects components of the given type under {@code root}. */
	private static List<java.awt.Component> findComponents(java.awt.Component root, Class<?> type)
	{
		List<java.awt.Component> found = new ArrayList<>();
		if (type.isInstance(root))
			found.add(root);

		if (root instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) root).getComponents())
				found.addAll(findComponents(child, type));
		}

		return found;
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.addContextMenuOption())
			return;

		final MenuEntry[] entries = event.getMenuEntries();
		for (int idx = entries.length - 1; idx >= 0; --idx)
		{
			final MenuEntry entry = entries[idx];
			int itemId = getItemIdFromMenuEntry(entry);
			if (itemId <= 0)
				continue;

			final int canonicalId = itemManager.canonicalize(itemId);
			final boolean tracked = trackedItems.containsKey(canonicalId);

			client.createMenuEntry(1)
					.setOption(tracked
							? ColorUtil.prependColorTag("Stop Tracking", config.stopTrackingColor())
							: ColorUtil.prependColorTag("Track Item", config.trackItemColor()))
					.setTarget(entry.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e ->
					{
						if (tracked)
							removeTrackedItem(canonicalId);
						else
							addTrackedItem(canonicalId);
					});
			return;
		}
	}

	private int getItemIdFromMenuEntry(MenuEntry entry)
	{
		switch (entry.getType())
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case EXAMINE_ITEM_GROUND:
				return entry.getIdentifier();
			default:
				break;
		}

		Widget w = entry.getWidget();
		if (w == null)
			return -1;

		int interfaceId = WidgetUtil.componentToInterface(w.getId());
		if (interfaceId == InterfaceID.INVENTORY
				|| interfaceId == InterfaceID.BANKMAIN
				|| interfaceId == InterfaceID.BANKSIDE)
		{
			return w.getItemId();
		}

		return -1;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (!TRACKED_CONTAINERS.contains(containerId))
			return;

		boolean firstSync = seenContainersSinceLogin.add(containerId);

		Map<Integer, Integer> oldCounts = containerCounts.getOrDefault(containerId, Collections.emptyMap());
		Map<Integer, Integer> newCounts = new HashMap<>();
		ItemContainer container = event.getItemContainer();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0)
					newCounts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		if (!firstSync)
		{
			Set<Integer> allIds = new HashSet<>(oldCounts.keySet());
			allIds.addAll(newCounts.keySet());
			for (int itemId : allIds)
			{
				int delta = newCounts.getOrDefault(itemId, 0) - oldCounts.getOrDefault(itemId, 0);
				if (delta != 0)
					pendingItemDeltas.merge(itemId, delta, Integer::sum);
			}

			pendingQuantitySync = true;
		}

		containerCounts.put(containerId, newCounts);

		if (firstSync && containerId == InventoryID.BANK && config.autoAddItems() != AutoAddMode.OFF)
			reconcileAllQuantities();

		refreshPanel();
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (pendingQuantitySync)
		{
			pendingQuantitySync = false;
			syncQuantitiesFromContainers();
		}

		// Evaluate every tick (cheap: numeric comparisons) so rules fire promptly on
		// quantity/price changes from any source, not only on the periodic price poll.
		evaluateNotifications();

		if (!config.highlightTrackedItems().ground() || client.isMenuOpen())
			return;

		final MenuEntry[] entries = client.getMenuEntries();
		final List<MenuEntry> normal = new ArrayList<>(entries.length);
		final List<MenuEntry> trackedTakes = new ArrayList<>();

		for (MenuEntry entry : entries)
		{
			if (entry.getType() == MenuAction.GROUND_ITEM_THIRD_OPTION
					&& isTracked(itemManager.canonicalize(entry.getIdentifier())))
			{
				trackedTakes.add(entry);
			}
			else
				normal.add(entry);
		}

		if (trackedTakes.isEmpty())
			return;

		normal.addAll(trackedTakes);
		client.setMenuEntries(normal.toArray(new MenuEntry[0]));
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		groundItems.put(event.getItem(), event.getTile());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundItems.remove(event.getItem());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				groundItems.clear();
				break;
			case LOGGED_IN:
				trackedItems.clear();
				containerCounts.clear();
				runePouchCounts.clear();
				seenContainersSinceLogin.clear();
				runePouchSeenSinceLogin = false;
				pendingQuantitySync = false;
				pendingItemDeltas.clear();
				loadPersistedItems();
				refreshPanel();
				break;
			case LOGIN_SCREEN:
				// Logged out: refresh so the panel shows the logged-out placeholder.
				refreshPanel();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (RUNE_POUCH_VARBITS.contains(event.getVarbitId()))
		{
			Map<Integer, Integer> oldPouch = new HashMap<>(runePouchCounts);
			syncRunePouch();
			boolean firstSync = !runePouchSeenSinceLogin;
			runePouchSeenSinceLogin = true;
			if (!firstSync)
			{
				Set<Integer> allIds = new HashSet<>(oldPouch.keySet());
				allIds.addAll(runePouchCounts.keySet());
				for (int itemId : allIds)
				{
					int delta = runePouchCounts.getOrDefault(itemId, 0) - oldPouch.getOrDefault(itemId, 0);
					if (delta != 0)
						pendingItemDeltas.merge(itemId, delta, Integer::sum);
				}

				pendingQuantitySync = true;
			}

			refreshPanel();
		}
	}

	private void syncRunePouch()
	{
		runePouchCounts.clear();
		EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < RUNE_POUCH_TYPE_VARBITS.length; i++)
		{
			int typeId = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
			int qty    = client.getVarbitValue(RUNE_POUCH_QUANTITY_VARBITS[i]);
			if (typeId == 0 || qty <= 0)
				continue;

			int itemId = runeEnum.getIntValue(typeId);
			runePouchCounts.merge(itemId, qty, Integer::sum);
		}
	}

	/**
	 * "Bought at" price recorded for an auto-added acquisition, per the Auto Add
	 * Items mode. OFF falls back to the average price so the cost-basis seeding
	 * (which is not gated by the mode) keeps its historical behavior.
	 */
	private long autoAddPrice(TrackedItem tracked)
	{
		switch (config.autoAddItems())
		{
			case HIGH:
				return tracked.getHighPrice();
			case LOW:
				return tracked.getLowPrice();
			case ZERO:
				return 0;
			case AVG:
			case OFF:
			default:
				return tracked.getAvgPrice();
		}
	}

	private void addOpenAcquisition(TrackedItem tracked, int qty, long boughtAt)
	{
		if (qty <= 0)
			return;

		List<AcquisitionRecord> records = tracked.getAcquisitions();

		int undoBudget = qty;
		java.util.Iterator<AcquisitionRecord> it = records.iterator();
		while (it.hasNext() && undoBudget > 0)
		{
			AcquisitionRecord r = it.next();
			Long sold = r.getSoldAt();
			if (sold != null && r.getBoughtAt() == boughtAt && sold == boughtAt)
			{
				int undo = Math.min(r.getQuantity(), undoBudget);
				r.setQuantity(r.getQuantity() - undo);
				if (r.getQuantity() == 0)
					it.remove();

				undoBudget -= undo;
			}
		}

		for (AcquisitionRecord r : records)
		{
			if (r.getSoldAt() == null && r.getBoughtAt() == boughtAt)
			{
				r.setQuantity(r.getQuantity() + qty);
				return;
			}
		}

		records.add(new AcquisitionRecord(qty, boughtAt, null));
	}

	private boolean mergeClosed(List<AcquisitionRecord> records, int qty, long boughtAt, long soldAtPrice)
	{
		for (AcquisitionRecord r : records)
		{
			Long sold = r.getSoldAt();
			if (sold != null && r.getBoughtAt() == boughtAt && sold == soldAtPrice)
			{
				r.setQuantity(r.getQuantity() + qty);
				return true;
			}
		}

		return false;
	}

	private void closeFifo(TrackedItem tracked, int amount, long soldAtPrice)
	{
		List<AcquisitionRecord> records = tracked.getAcquisitions();
		int remaining = amount;

		java.util.Iterator<AcquisitionRecord> cancelIt = records.iterator();
		while (cancelIt.hasNext() && remaining > 0)
		{
			AcquisitionRecord r = cancelIt.next();
			if (r.getSoldAt() == null && r.getBoughtAt() == soldAtPrice)
			{
				int cancel = Math.min(r.getQuantity(), remaining);
				r.setQuantity(r.getQuantity() - cancel);
				if (r.getQuantity() == 0)
					cancelIt.remove();

				remaining -= cancel;
			}
		}

		int i = 0;
		while (i < records.size() && remaining > 0)
		{
			AcquisitionRecord r = records.get(i);
			if (r.getSoldAt() != null)
			{
				i++;
				continue;
			}

			if (r.getQuantity() <= remaining)
			{
				int closeQty = r.getQuantity();
				remaining -= closeQty;
				if (mergeClosed(records, closeQty, r.getBoughtAt(), soldAtPrice))
					records.remove(i);
				else
				{
					r.setSoldAt(soldAtPrice);
					i++;
				}
			}
			else
			{
				int closeQty = remaining;
				r.setQuantity(r.getQuantity() - closeQty);
				remaining = 0;
				if (!mergeClosed(records, closeQty, r.getBoughtAt(), soldAtPrice))
					records.add(i, new AcquisitionRecord(closeQty, r.getBoughtAt(), soldAtPrice));
			}
		}
	}

	private void syncQuantitiesFromContainers()
	{
		if (pendingItemDeltas.isEmpty())
			return;

		Map<Integer, Integer> deltas = new HashMap<>(pendingItemDeltas);
		pendingItemDeltas.clear();
		if (config.autoAddItems() == AutoAddMode.OFF || trackedItems.isEmpty())
			return;

		boolean changed = false;
		for (TrackedItem tracked : trackedItems.values())
		{
			if (tracked.getMode() != TrackItemMode.TRACK)
				continue;

			Integer delta = deltas.get(tracked.getItemId());
			if (delta == null || delta == 0)
				continue;

			if (delta > 0)
				addOpenAcquisition(tracked, delta, autoAddPrice(tracked));
			else
				closeFifo(tracked, -delta, tracked.getAvgPrice());

			tracked.setQuantity(tracked.getQuantity() + delta);
			changed = true;
		}

		if (changed)
		{
			persistTrackedItems();
			refreshPanel();
		}
	}

	private void reconcileAllQuantities()
	{
		pendingItemDeltas.clear();
		if (client.getGameState() != GameState.LOGGED_IN || trackedItems.isEmpty())
			return;

		for (int containerId : TRACKED_CONTAINERS)
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
				continue;

			Map<Integer, Integer> counts = containerCounts.computeIfAbsent(containerId, k -> new HashMap<>());
			counts.clear();
			for (Item item : container.getItems())
			{
				if (item.getId() > 0)
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		syncRunePouch();

		boolean changed = false;
		for (TrackedItem tracked : trackedItems.values())
		{
			if (tracked.getMode() != TrackItemMode.TRACK)
				continue;

			int total = runePouchCounts.getOrDefault(tracked.getItemId(), 0)
					+ containerCounts.values().stream()
					.mapToInt(c -> c.getOrDefault(tracked.getItemId(), 0))
					.sum();
			int logDelta = total - tracked.getRecordQuantitySum();
			if (logDelta > 0)
			{
				addOpenAcquisition(tracked, logDelta, autoAddPrice(tracked));
				changed = true;
			}
			else if (logDelta < 0)
			{
				closeFifo(tracked, -logDelta, tracked.getAvgPrice());
				changed = true;
			}

			if (tracked.getQuantity() != total)
			{
				tracked.setQuantity(total);
				changed = true;
			}
		}

		if (changed)
			persistTrackedItems();
	}

	private void syncQuantitiesForItem(TrackedItem tracked)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
			return;

		for (int containerId : TRACKED_CONTAINERS)
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
				continue;

			Map<Integer, Integer> counts = containerCounts.computeIfAbsent(containerId, k -> new HashMap<>());
			counts.clear();
			for (Item item : container.getItems())
			{
				if (item.getId() > 0)
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		syncRunePouch();

		int total = runePouchCounts.getOrDefault(tracked.getItemId(), 0)
				+ containerCounts.values().stream()
				.mapToInt(c -> c.getOrDefault(tracked.getItemId(), 0))
				.sum();
		tracked.setQuantity(total);
	}

	boolean isTracked(int itemId)
	{
		return trackedItems.containsKey(itemId);
	}

	void onAcquisitionsEdited(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
				return;

			tracked.setCostBasisInitialized(true);
			tracked.setQuantity(tracked.getRecordQuantitySum());
			persistTrackedItems();
			refreshPanel();
		});
	}

	private void onNotificationsEdited(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			if (trackedItems.containsKey(itemId))
				persistTrackedItems();
		});
	}

	// Δ% beyond this magnitude indicates a sparse/stale window average rather than
	// a real price move; such values are ignored so a one-and-done rule is not
	// fired on noise from a near-zero denominator.
	private static final double MAX_DELTA_PCT = 1000.0;

	private static final long GLOW_PERIOD_SLOW_MS = 2000;
	private static final long GLOW_PERIOD_MEDIUM_MS = 1500;
	private static final long GLOW_PERIOD_FAST_MS = 1000;
	private static final float GLOW_MIN_ALPHA = 0.2f;
	private static final float GLOW_MAX_ALPHA = 1f;

	float breathingAlpha()
	{
		long period;
		switch (config.glowEffect())
		{
			case SLOW:
				period = GLOW_PERIOD_SLOW_MS;
				break;
			case MEDIUM:
				period = GLOW_PERIOD_MEDIUM_MS;
				break;
			case FAST:
				period = GLOW_PERIOD_FAST_MS;
				break;
			default:
				return GLOW_MAX_ALPHA;
		}

		double phase = (System.currentTimeMillis() % period) / (double) period;
		double wave = (Math.sin(phase * 2 * Math.PI) + 1) / 2;
		return GLOW_MIN_ALPHA + (GLOW_MAX_ALPHA - GLOW_MIN_ALPHA) * (float) wave;
	}

	Map<TileItem, Tile> getGroundItems()
	{
		return groundItems;
	}

	private void refreshPanel()
	{
		refreshPanel(false);
	}

	private void refreshPanel(boolean pricesUpdated)
	{
		final Instant refresh = lastPriceRefresh;
		final PriceIndicatorMode indicatorMode = pricesUpdated
				? config.priceChangeIndicator()
				: PriceIndicatorMode.OFF;
		final List<TrackedItem> items = new ArrayList<>(trackedItems.values());
		// LOADING counts as in-game (region change) so the panel doesn't flash the
		// logged-out placeholder while running around.
		final GameState gs = client.getGameState();
		final boolean loggedIn = gs == GameState.LOGGED_IN || gs == GameState.LOADING;
		SwingUtilities.invokeLater(() -> panel.rebuild(items, refresh, indicatorMode, loggedIn));
	}

	/**
	 * Evaluates every item's notification rules and fires those whose condition
	 * is met. Rules are one-and-done: a rule fires exactly once and is then
	 * removed from its item's list. Removed rules are persisted and the panel is
	 * refreshed so the change is reflected immediately. Must run on the client
	 * thread (reads {@link #trackedItems}).
	 */
	private void evaluateNotifications()
	{
		Notification style = config.notificationStyle();
		if (!style.isEnabled())
			return;

		// Hold off while the user is editing a rule: firing removes the rule and
		// refreshes the panel, which would wedge the open cell editor. Evaluation
		// resumes on the next tick once they finish editing.
		if (panel.isEditingNotifications())
			return;

		boolean changed = false;
		for (TrackedItem item : trackedItems.values())
		{
			Iterator<NotificationRule> it = item.getNotifications().iterator();
			while (it.hasNext())
			{
				NotificationRule rule = it.next();
				Boolean condition = evaluateRule(item, rule);
				if (condition == null)
				{
					// Required data not ready yet, or a blank row; leave it in place.
					continue;
				}

				if (condition)
				{
					// One-and-done: fire once, then drop the rule from the list.
					notifier.notify(style, notificationText(item, rule));
					it.remove();
					changed = true;
				}
			}
		}

		if (changed)
		{
			persistTrackedItems();
			refreshPanel();
		}
	}

	/** @return true/false for the rule's condition, or null when it cannot be evaluated yet. */
	private Boolean evaluateRule(TrackedItem item, NotificationRule rule)
	{
		NotificationMetric metric = rule.getMetric();
		if (metric == null || rule.getOperation() == null)
		{
			// Incomplete (blank) row; nothing to evaluate.
			return null;
		}

		if (metric.isCategorical())
		{
			String current = categoryValue(item, metric);
			if (current == null || rule.getValue() == null)
				return null;

			return current.equalsIgnoreCase(rule.getValue().trim());
		}

		TimeWindow window = metric.locksTimeframeToMonth() ? TimeWindow.MONTH : rule.getTimeWindow();
		OptionalDouble current = numericValue(item, metric, window);
		if (!current.isPresent())
			return null;

		OptionalDouble target = metric.getKind() == NotificationMetric.Kind.PERCENT
				? NotificationRule.parsePercent(rule.getValue())
				: NotificationRule.parseNumeric(rule.getValue());
		if (!target.isPresent())
			return null;

		return rule.getOperation().test(current.getAsDouble(), target.getAsDouble());
	}

	private OptionalDouble numericValue(TrackedItem item, NotificationMetric metric, TimeWindow window)
	{
		if (metric == NotificationMetric.QUANTITY)
			return OptionalDouble.of(item.getQuantity());

		PriceStats s = item.getWindowStats().get(window);
		long avg = s == null ? 0 : s.getAvg();
		switch (metric)
		{
			case HIGH:
				return s == null ? OptionalDouble.empty() : OptionalDouble.of(s.getHigh());
			case LOW:
				return s == null ? OptionalDouble.empty() : OptionalDouble.of(s.getLow());
			case AVERAGE:
				return s == null ? OptionalDouble.empty() : OptionalDouble.of(s.getAvg());
			case VOLUME:
				return s == null ? OptionalDouble.empty() : OptionalDouble.of(s.getVolume());
			case ITM_PROFIT:
				return avg <= 0 ? OptionalDouble.empty() : OptionalDouble.of(item.getProfitAt(avg));
			case HA_PROFIT:
				if (avg <= 0 || item.getHighAlch() <= 0)
					return OptionalDouble.empty();

				return OptionalDouble.of(item.getHighAlch() - avg - runePrice(NATURE_RUNE_ID) - 5 * runePrice(FIRE_RUNE_ID));
			case DELTA_PCT:
			{
				long current = item.getAvgPrice();
				if (current <= 0 || avg <= 0)
					return OptionalDouble.empty();

				double pct = Math.round(((double) (current - avg) / avg) * 1000.0) / 10.0;
				return Math.abs(pct) > MAX_DELTA_PCT ? OptionalDouble.empty() : OptionalDouble.of(pct);
			}
			default:
				return OptionalDouble.empty();
		}
	}

	private String categoryValue(TrackedItem item, NotificationMetric metric)
	{
		switch (metric)
		{
			case VOLATILITY:
				return MarketClassifier.volatility(item.getSeriesFor(TimeWindow.WEEK));
			case LIQUIDITY:
			{
				PriceStats s = item.getWindowStats().get(TimeWindow.H24);
				return MarketClassifier.liquidity(s == null ? 0 : s.getVolume());
			}
			case RANGE_30D:
			{
				long[] range = MarketClassifier.thirtyDayRange(item.getSeriesFor(TimeWindow.MONTH));
				return MarketClassifier.rangePosition(range[0], range[1], item.getAvgPrice());
			}
			default:
				return null;
		}
	}

	private String notificationText(TrackedItem item, NotificationRule rule)
	{
		NotificationMetric metric = rule.getMetric();
		String valueDisplay;
		if (metric.isCategorical())
			valueDisplay = rule.getValue();
		else if (metric.getKind() == NotificationMetric.Kind.PERCENT)
		{
			OptionalDouble v = NotificationRule.parsePercent(rule.getValue());
			valueDisplay = v.isPresent() ? NotificationRule.formatPercent(v.getAsDouble()) : rule.getValue();
		}
		else
		{
			OptionalDouble v = NotificationRule.parseNumeric(rule.getValue());
			valueDisplay = v.isPresent()
					? String.format(Locale.US, "%,d", Math.round(v.getAsDouble()))
					: rule.getValue();
		}

		return "Item Tracker: " + item.getName() + " - " + metric.getDisplayName()
				+ " " + rule.getOperation().getSymbol() + " " + valueDisplay;
	}
}
