/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Draggable in-game overlay that renders the user's selected tracked items (up to
 * {@link StockpilePlugin#OVERLAY_MAX}). Each item is drawn either in the dense compact
 * two-row layout or as a replica of the panel's standard row (icon/name/qty, the configured
 * time-window price rows, and the est. profit line), per the configured {@link OverlayLayout}.
 * Hidden entirely when the overlay is disabled or no items are selected.
 */
public class StockpileScreenOverlay extends Overlay
{
	private static final Color NAME_COLOR = Color.WHITE;
	private static final Color QTY_COLOR = new Color(200, 200, 200);
	private static final Color LABEL_COLOR = new Color(170, 170, 170);
	private static final Color HIGH_COLOR = new Color(100, 220, 100);
	private static final Color LOW_COLOR = new Color(220, 100, 100);
	private static final Color AVG_COLOR = new Color(255, 200, 0);
	private static final Color VOLUME_COLOR = new Color(190, 130, 220);
	private static final Color MUTED_COLOR = new Color(150, 150, 150);
	private static final Color BACKGROUND = ComponentConstants.STANDARD_BACKGROUND_COLOR;

	/** Dark brown border matching RuneLite's tan overlay background (rather than a stark black). */
	private static final Color BORDER = new Color(56, 48, 35);

	private static final int PAD = 6;
	private static final int ICON = 18;
	private static final int GAP = 6;
	private static final int SEG_GAP = 5;

	private final StockpilePlugin plugin;
	private final StockpileConfig config;
	private final ItemManager itemManager;

	/** Cached 18px scaled icons by item id, populated asynchronously on first use. */
	private final Map<Integer, BufferedImage> iconCache = new HashMap<>();

	/** One coloured text segment within a rendered line. */
	private static final class Seg
	{
		final String text;
		final Color color;

		Seg(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}
	}

	/** Which overlay slot (0-based) this box renders — the item at that index in the overlay set. */
	private final int slot;

	StockpileScreenOverlay(StockpilePlugin plugin, StockpileConfig config, ItemManager itemManager, int slot)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.slot = slot;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/** @return the overlay layer: above interfaces when configured on top, otherwise behind windows (bank, GE, ...). */
	@Override
	public OverlayLayer getLayer()
	{
		return config.screenOverlayOnTop() ? OverlayLayer.ABOVE_WIDGETS : OverlayLayer.UNDER_WIDGETS;
	}

	/** @return a per-slot unique name so each box persists (and is dragged) independently. */
	@Override
	public String getName()
	{
		return "stockpileScreenOverlay" + slot;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showScreenOverlay())
			return null;

		List<TrackedItem> items = plugin.getOverlayItems();
		if (slot >= items.size())
			return null;

		TrackedItem item = items.get(slot);

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = graphics.getFontMetrics();
		int lineHeight = fm.getHeight();
		boolean compact = config.screenOverlayLayout() == OverlayLayout.COMPACT;

		List<List<Seg>> lines = blockLines(item, compact);

		int width = PAD * 2 + ICON + GAP + maxLineWidth(fm, lines);
		int height = PAD * 2 + lines.size() * lineHeight;

		graphics.setColor(BACKGROUND);
		graphics.fillRect(0, 0, width, height);
		graphics.setColor(BORDER);
		graphics.drawRect(0, 0, width - 1, height - 1);

		BufferedImage icon = iconFor(item.getItemId());
		if (icon != null)
			graphics.drawImage(icon, PAD, PAD, null);

		int textX = PAD + ICON + GAP;
		int y = PAD;
		for (List<Seg> line : lines)
		{
			drawLine(graphics, fm, textX, y + fm.getAscent(), line);
			y += lineHeight;
		}

		return new Dimension(width, height);
	}

	/** First line (name + qty) plus value lines for an item, used for both measuring and drawing. */
	private List<List<Seg>> blockLines(TrackedItem item, boolean compact)
	{
		List<List<Seg>> lines = new ArrayList<>();
		lines.add(Arrays.asList(
				new Seg(item.getName(), NAME_COLOR),
				new Seg(" x" + GpFormat.shortValue(item.getQuantity()), QTY_COLOR)));

		if (!item.hasPrices())
		{
			lines.add(Arrays.asList(new Seg(!item.isTradeable() ? "Item not tradeable" : "Prices loading…", MUTED_COLOR)));
			return lines;
		}

		if (compact)
			lines.add(Arrays.asList(
					new Seg(GpFormat.shortValue(item.getAvgValue()), AVG_COLOR),
					new Seg(" (" + GpFormat.shortValue(item.getAvgPrice()) + ")", QTY_COLOR)));
		else
		{
			for (TimeWindow window : Arrays.asList(config.row1Data(), config.row2Data(), config.row3Data()))
			{
				if (window == TimeWindow.NONE)
					continue;

				lines.add(windowLine(item, window));
			}

			if (config.showItemProfitRow() && item.isCostBasisInitialized())
			{
				long profit = (long) item.getRecordQuantitySum() * item.getAvgPrice() - item.getCostBasis();
				String sign = profit > 0 ? "+" : "";
				Color color = profit == 0 ? MUTED_COLOR : (profit > 0 ? HIGH_COLOR : LOW_COLOR);
				lines.add(Arrays.asList(
						new Seg("Profit ", LABEL_COLOR),
						new Seg(sign + GpFormat.shortValue(profit), color)));
			}
		}

		return lines;
	}

	/** Builds one standard-layout price line for a window, honouring the configured visible columns. */
	private List<Seg> windowLine(TrackedItem item, TimeWindow window)
	{
		PriceStats stats = item.getWindowStats().get(window);
		boolean live = window == TimeWindow.LIVE || stats == null;
		long high = live ? item.getHighPrice() : stats.getHigh();
		long low = live ? item.getLowPrice() : stats.getLow();
		long avg = live ? item.getAvgPrice() : stats.getAvg();
		long vol = !live && stats != null ? stats.getVolume() : 0;

		List<Seg> line = new ArrayList<>();
		line.add(new Seg(window.getLabel(), LABEL_COLOR));
		if (config.showColHigh())
			line.add(new Seg(GpFormat.shortValue(high), HIGH_COLOR));

		if (config.showColLow())
			line.add(new Seg(GpFormat.shortValue(low), LOW_COLOR));

		if (config.showColAvg())
			line.add(new Seg(GpFormat.shortValue(avg), AVG_COLOR));

		if (config.showColVolume() && !live)
			line.add(new Seg(GpFormat.shortValue(vol), VOLUME_COLOR));

		return line;
	}

	/** Draws a line of coloured segments left-to-right, returning the total width drawn. */
	private int drawLine(Graphics2D graphics, FontMetrics fm, int x, int baseline, List<Seg> segments)
	{
		int cx = x;
		for (Seg seg : segments)
		{
			graphics.setColor(seg.color);
			graphics.drawString(seg.text, cx, baseline);
			cx += fm.stringWidth(seg.text) + SEG_GAP;
		}

		return cx - x;
	}

	/** @return the widest of the given lines in pixels. */
	private int maxLineWidth(FontMetrics fm, List<List<Seg>> lines)
	{
		int max = 0;
		for (List<Seg> line : lines)
		{
			int w = 0;
			for (Seg seg : line)
				w += fm.stringWidth(seg.text) + SEG_GAP;

			max = Math.max(max, w);
		}

		return max;
	}

	/** @return an 18px cached icon for the item, requesting an async load on the first miss. */
	private BufferedImage iconFor(int itemId)
	{
		BufferedImage cached = iconCache.get(itemId);
		if (cached != null)
			return cached;

		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(() -> iconCache.put(itemId,
				toBuffered(image.getScaledInstance(ICON, ICON, Image.SCALE_SMOOTH))));

		return null;
	}

	/** Converts a scaled {@link Image} to a drawable {@link BufferedImage}. */
	private static BufferedImage toBuffered(Image image)
	{
		BufferedImage buffered = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = buffered.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return buffered;
	}
}
