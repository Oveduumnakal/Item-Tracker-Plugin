/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Swing component that draws an item's price or volume history as a line/area
 * chart with a timeframe tab bar, optional smoothing, and a hover crosshair.
 *
 * <p>The same class serves both the compact in-panel chart and a larger
 * {@code expanded} pop-out (which uses a bigger font, denser axes, and spelled-out
 * tab labels). It holds four pre-bucketed series (5m/1h/6h/24h) and picks the one
 * matching the active {@link TimeWindow}.
 *
 * <p>Rendering is split in two for performance: the expensive static plot (grid,
 * axes, data paths, smoothing) is rasterized once into {@link #plotCache} and
 * reused, while only the lightweight hover crosshair is redrawn on mouse moves.
 * All drawing happens on the Swing EDT via {@link #paintComponent}.
 */
public class PriceGraphPanel extends JPanel
{
	/** Whether this panel charts prices or trade volume. */
	public enum Mode { PRICE, VOLUME }

	/** Which price lines to draw: all three, just high/low, or just the average. */
	public enum LineSet
	{
		ALL("All"),
		HIGH_LOW("H/L"),
		AVG("Avg");

		final String label;

		LineSet(String label)
		{
			this.label = label;
		}
	}

	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

	private static final Color COLOR_HIGH = new Color(100, 220, 100);
	private static final Color COLOR_LOW = new Color(220, 100, 100);
	private static final Color COLOR_AVG = new Color(255, 200, 0);
	private static final Color GRID_COLOR = new Color(70, 70, 70, 90);
	private static final Color SEPARATOR_COLOR = new Color(80, 80, 80);

	private static final Color VOLUME_COLOR = new Color(120, 140, 180, 110);

	private static final Color VOLUME_OVER_COLOR = new Color(165, 185, 225);

	private static final Color CURRENT_LINE_COLOR = new Color(120, 140, 180);

	private static final Color MA_COLOR = new Color(64, 200, 190);
	private static final Color BG_COLOR = ColorScheme.DARKER_GRAY_COLOR;

	private final Mode mode;
	private List<WikiRealtimePriceClient.PricePoint> series5m = Collections.emptyList();
	private List<WikiRealtimePriceClient.PricePoint> series1h = Collections.emptyList();
	private List<WikiRealtimePriceClient.PricePoint> series6h = Collections.emptyList();
	private List<WikiRealtimePriceClient.PricePoint> series24h = Collections.emptyList();
	private long currentPrice;
	private TimeWindow activeWindow = TimeWindow.H24;

	private static final TimeWindow[] TIMEFRAMES = {
			TimeWindow.H24, TimeWindow.WEEK, TimeWindow.MONTH, TimeWindow.YEAR
	};
	private static final String[] TIMEFRAME_LABELS = {"1d", "1wk", "1mo", "1yr"};
	private static final String[] TIMEFRAME_LABELS_FULL = {"1 Day", "1 Week", "1 Month", "1 Year"};

	private final boolean expanded;

	private final Font baseFont;

	private final JPanel tabsBar;
	private final List<JLabel> tabLabels = new ArrayList<>();
	private int hoverX = -1;

	private boolean smooth = false;
	private JLabel smoothToggle;

	private java.util.function.Consumer<Boolean> smoothListener;

	private LineSet lineSet = LineSet.ALL;
	private JLabel linesToggle;
	private java.util.function.Consumer<LineSet> lineSetListener;

	private transient BufferedImage plotCache;
	private boolean plotCacheDirty = true;

	private static final int TAB_BAR_HEIGHT = 28;

	private final int rightAxisWidth;
	private final int bottomAxisHeight;
	private static final int LEFT_PAD = 8;
	private static final int TOP_PAD = 13;

	private static final int X_AXIS_LABEL_GAP = 12;

	public PriceGraphPanel()
	{
		this(Mode.PRICE, false);
	}

	public PriceGraphPanel(Mode mode)
	{
		this(mode, false);
	}

	/**
	 * Builds the chart and its timeframe tab bar.
	 *
	 * @param mode     whether to chart price or volume
	 * @param expanded {@code true} for the larger pop-out variant (bigger font,
	 *                 denser axes, full tab labels); {@code false} for the sidebar
	 */
	public PriceGraphPanel(Mode mode, boolean expanded)
	{
		this.mode = mode;
		this.expanded = expanded;
		this.baseFont = expanded
				? new Font(Font.MONOSPACED, Font.PLAIN, 13)
				: FontManager.getRunescapeSmallFont();

		FontMetrics axisFm = getFontMetrics(baseFont);
		this.rightAxisWidth = axisFm.stringWidth("999.9K") + 8;
		this.bottomAxisHeight = X_AXIS_LABEL_GAP + axisFm.stringWidth("99/99") + 4;

		setLayout(new java.awt.BorderLayout());
		setBackground(BG_COLOR);
		setPreferredSize(mode == Mode.PRICE ? new Dimension(240, 250) : new Dimension(240, 182));

		tabsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, expanded ? 8 : 2, 0));
		tabsBar.setBackground(BG_COLOR);

		tabsBar.setBorder(new EmptyBorder(5, 4, 4, expanded ? 4 : 0));
		String[] tabTexts = expanded ? TIMEFRAME_LABELS_FULL : TIMEFRAME_LABELS;
		for (int i = 0; i < TIMEFRAMES.length; i++)
		{
			final TimeWindow tw = TIMEFRAMES[i];
			final JLabel tab = new JLabel(tabTexts[i]);
			tab.setForeground(Color.WHITE);
			tab.setFont(baseFont);
			tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tab.setBorder(new EmptyBorder(2, 4, 2, 4));
			tab.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					activeWindow = tw;
					plotCacheDirty = true;
					updateTabHighlight();
					repaint();
				}
			});
			tabLabels.add(tab);
			tabsBar.add(tab);
		}

		JPanel topRow = new JPanel(new java.awt.BorderLayout());
		topRow.setBackground(BG_COLOR);
		topRow.add(tabsBar, java.awt.BorderLayout.WEST);
		if (mode == Mode.PRICE)
		{
			int togglePad = expanded ? 4 : 2;
			linesToggle = new JLabel(lineSet.label);
			linesToggle.setForeground(Color.WHITE);
			linesToggle.setFont(baseFont);
			linesToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			linesToggle.setBorder(new EmptyBorder(2, togglePad, 2, togglePad));
			linesToggle.setToolTipText("Cycle visible lines: All / High & Low / Avg");
			linesToggle.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					LineSet[] all = LineSet.values();
					setLineSet(all[(lineSet.ordinal() + 1) % all.length]);
					if (lineSetListener != null)
						lineSetListener.accept(lineSet);
				}
			});

			smoothToggle = new JLabel("Smooth");
			smoothToggle.setFont(baseFont);
			smoothToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			smoothToggle.setBorder(new EmptyBorder(2, togglePad, 2, togglePad));
			smoothToggle.setToolTipText("Toggle line smoothing");
			smoothToggle.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					setSmooth(!smooth);
					if (smoothListener != null)
						smoothListener.accept(smooth);
				}
			});

			JPanel toggles = new JPanel(new FlowLayout(FlowLayout.RIGHT, expanded ? 8 : 4, 0));
			toggles.setBackground(BG_COLOR);
			toggles.setBorder(new EmptyBorder(5, 0, 4, expanded ? 4 : 0));
			toggles.add(linesToggle);
			toggles.add(smoothToggle);
			topRow.add(toggles, java.awt.BorderLayout.EAST);
			updateSmoothToggle();
		}

		add(topRow, java.awt.BorderLayout.NORTH);
		updateTabHighlight();

		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				hoverX = e.getX();
				repaint();
			}
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				hoverX = -1;
				repaint();
			}
		});
	}

	private void updateTabHighlight()
	{
		for (int i = 0; i < tabLabels.size(); i++)
		{
			JLabel l = tabLabels.get(i);
			if (TIMEFRAMES[i] == activeWindow)
			{
				l.setForeground(COLOR_AVG);
				l.setFont(baseFont.deriveFont(Font.BOLD));
				l.setBorder(BorderFactory.createCompoundBorder(
						new EmptyBorder(2, 4, 0, 4),
						BorderFactory.createMatteBorder(0, 0, 2, 0, COLOR_AVG)));
			}
			else
			{
				l.setForeground(Color.LIGHT_GRAY);
				l.setFont(baseFont);
				l.setBorder(new EmptyBorder(2, 4, 2, 4));
			}
		}
	}

	private void updateSmoothToggle()
	{
		if (smoothToggle == null)
			return;

		smoothToggle.setForeground(smooth ? COLOR_AVG : Color.LIGHT_GRAY);
		smoothToggle.setFont(smooth ? baseFont.deriveFont(Font.BOLD) : baseFont);
	}

	/** Toggles spline smoothing of the data lines and invalidates the plot cache. */
	public void setSmooth(boolean s)
	{
		if (smooth == s)
			return;

		smooth = s;
		plotCacheDirty = true;
		updateSmoothToggle();
		repaint();
	}

	/** Registers a callback fired when the user toggles smoothing, so a sibling chart can stay in sync. */
	public void setSmoothListener(java.util.function.Consumer<Boolean> listener)
	{
		this.smoothListener = listener;
	}

	/** Sets which price lines are drawn (all / high-low / average) and invalidates the plot cache. */
	public void setLineSet(LineSet set)
	{
		if (set == null || lineSet == set)
			return;

		lineSet = set;
		if (linesToggle != null)
			linesToggle.setText(lineSet.label);

		plotCacheDirty = true;
		repaint();
	}

	/** Registers a callback fired when the user changes the line set, so a sibling chart can stay in sync. */
	public void setLineSetListener(java.util.function.Consumer<LineSet> listener)
	{
		this.lineSetListener = listener;
	}

	/**
	 * Replaces the chart's data with fresh per-bucket series and the latest price,
	 * then invalidates the plot cache and repaints. Null series are treated as empty.
	 *
	 * @param currentPrice the live price, drawn as the reference line
	 */
	public void setData(
			List<WikiRealtimePriceClient.PricePoint> series5m,
			List<WikiRealtimePriceClient.PricePoint> series1h,
			List<WikiRealtimePriceClient.PricePoint> series6h,
			List<WikiRealtimePriceClient.PricePoint> series24h,
			long currentPrice)
	{
		this.series5m = series5m == null ? Collections.emptyList() : series5m;
		this.series1h = series1h == null ? Collections.emptyList() : series1h;
		this.series6h = series6h == null ? Collections.emptyList() : series6h;
		this.series24h = series24h == null ? Collections.emptyList() : series24h;
		this.currentPrice = currentPrice;
		plotCacheDirty = true;
		repaint();
	}

	public TimeWindow getActiveWindow()
	{
		return activeWindow;
	}

	/** Selects the displayed timeframe (defaulting to 24h), refreshes the tab highlight, and repaints. */
	public void setActiveWindow(TimeWindow w)
	{
		this.activeWindow = w == null ? TimeWindow.H24 : w;
		plotCacheDirty = true;
		updateTabHighlight();
		repaint();
	}

	/** @return the pre-bucketed series whose granularity matches the active window. */
	private List<WikiRealtimePriceClient.PricePoint> seriesForActiveWindow()
	{
		switch (activeWindow)
		{
			case WEEK:
				return series1h;
			case MONTH:
				return series6h;
			case YEAR:
				return series24h;
			default:
				return series5m;
		}
	}

	/**
	 * Paints the chart: computes the plot rectangle and visible window, lazily
	 * (re)rasterizes the static plot into {@link #plotCache} when size or data
	 * changed, blits the cache, then draws the hover crosshair overlay on top.
	 */
	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		int w = getWidth();
		int h = getHeight();
		if (w <= 0 || h <= 0)
			return;

		int plotTop = TAB_BAR_HEIGHT + TOP_PAD;
		int plotBottom = h - bottomAxisHeight;
		int plotLeft = LEFT_PAD;
		int plotRight = w - rightAxisWidth;
		int plotW = Math.max(1, plotRight - plotLeft);
		int plotH = Math.max(1, plotBottom - plotTop);

		long endSec = System.currentTimeMillis() / 1000L;
		long startSec = endSec - activeWindow.getDuration().getSeconds();
		long span = Math.max(1, endSec - startSec);
		List<WikiRealtimePriceClient.PricePoint> visible = collectVisible(startSec, endSec);

		if (plotCache == null || plotCache.getWidth() != w || plotCache.getHeight() != h || plotCacheDirty)
		{
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D cg = img.createGraphics();
			try
			{
				cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				cg.setFont(baseFont);
				renderStatic(cg, cg.getFontMetrics(), w, visible,
						plotLeft, plotTop, plotRight, plotBottom, plotW, plotH, startSec, endSec, span);
			}
			finally
			{
				cg.dispose();
			}

			plotCache = img;
			plotCacheDirty = false;
		}

		g.drawImage(plotCache, 0, 0, null);

		if (!visible.isEmpty() && hoverX >= plotLeft && hoverX <= plotRight)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setFont(baseFont);
				drawHover(g2, g2.getFontMetrics(), visible, plotLeft, plotTop, plotRight, plotBottom, plotW, startSec, span);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/** @return the active-window points falling within {@code [startSec, endSec]} (recomputed each paint; cheap). */
	private List<WikiRealtimePriceClient.PricePoint> collectVisible(long startSec, long endSec)
	{
		List<WikiRealtimePriceClient.PricePoint> visible = new ArrayList<>();
		for (WikiRealtimePriceClient.PricePoint p : seriesForActiveWindow())
		{
			if (p.getTimestamp() >= startSec && p.getTimestamp() <= endSec)
				visible.add(p);
		}

		return visible;
	}

	/**
	 * Renders the full static plot into the cache image: the tab separator, then
	 * either a "No data" message or the price/volume series with its axes.
	 * Dispatches to {@link #paintPrice} or {@link #paintVolume} by {@link #mode}.
	 */
	private void renderStatic(Graphics2D g2, FontMetrics fm, int w,
			List<WikiRealtimePriceClient.PricePoint> visible,
			int plotLeft, int plotTop, int plotRight, int plotBottom, int plotW, int plotH,
			long startSec, long endSec, long span)
	{

		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(0, TAB_BAR_HEIGHT, w, TAB_BAR_HEIGHT);

		if (visible.isEmpty())
		{
			g2.setColor(Color.LIGHT_GRAY);
			String msg = "No data";
			g2.drawString(msg, plotLeft + (plotW - fm.stringWidth(msg)) / 2, plotTop + plotH / 2);
			return;
		}

		if (mode == Mode.VOLUME)
			paintVolume(g2, fm, visible, plotLeft, plotTop, plotRight, plotBottom, plotW, plotH, startSec, span);
		else
			paintPrice(g2, fm, visible, plotLeft, plotTop, plotRight, plotBottom, plotW, plotH, startSec, span);

		drawXAxis(g2, fm, plotLeft, plotBottom, plotW, startSec, endSec);
	}

	/**
	 * Draws the hover overlay: a vertical crosshair at the cursor and a tooltip
	 * describing the data point nearest the cursor's x position. Drawn fresh on
	 * every paint (not cached) so it stays cheap during mouse movement.
	 */
	private void drawHover(Graphics2D g2, FontMetrics fm,
			List<WikiRealtimePriceClient.PricePoint> visible,
			int plotLeft, int plotTop, int plotRight, int plotBottom, int plotW, long startSec, long span)
	{
		int idx = closestIndex(visible, plotLeft, plotW, startSec, span);
		if (idx < 0)
			return;

		WikiRealtimePriceClient.PricePoint closest = visible.get(idx);
		g2.setStroke(new BasicStroke(1));
		g2.setColor(new Color(255, 255, 255, 120));
		g2.drawLine(hoverX, plotTop, hoverX, plotBottom);

		String[] lines;
		if (mode == Mode.VOLUME)
		{
			double[] vols = new double[visible.size()];
			for (int i = 0; i < visible.size(); i++)
			{
				WikiRealtimePriceClient.PricePoint p = visible.get(i);
				vols[i] = p.getHighPriceVolume() + p.getLowPriceVolume();
			}

			double[] ma = ema(vols, Math.max(2, visible.size() / 10));
			lines = new String[]{
					"V: " + NUMBER_FORMAT.format(closest.getHighPriceVolume() + closest.getLowPriceVolume()),
					"MA: " + NUMBER_FORMAT.format(Math.round(ma[idx])),
			};
		}
		else
		{
			lines = new String[]{
					"H: " + NUMBER_FORMAT.format(closest.getAvgHighPrice()),
					"L: " + NUMBER_FORMAT.format(closest.getAvgLowPrice()),
					"A: " + NUMBER_FORMAT.format(midpoint(closest)),
			};
		}

		drawTooltip(g2, fm, lines, plotLeft, plotTop, plotRight);
	}

	/**
	 * Draws the price chart: computes a "nice" value axis from the visible range,
	 * paints the horizontal grid and y-axis labels, the current-price reference
	 * line, and the selected high/low/average series (raw, smoothed, or with a
	 * moving average per {@link #smooth} and {@link #lineSet}).
	 */
	private void paintPrice(Graphics2D g2, FontMetrics fm,
			List<WikiRealtimePriceClient.PricePoint> visible,
			int plotLeft, int plotTop, int plotRight, int plotBottom, int plotW, int plotH,
			long startSec, long span)
	{

		List<Long> values = new ArrayList<>(visible.size() * 2);
		for (WikiRealtimePriceClient.PricePoint p : visible)
		{
			if (p.getAvgHighPrice() > 0)
				values.add(p.getAvgHighPrice());

			if (p.getAvgLowPrice() > 0)
				values.add(p.getAvgLowPrice());
		}

		if (values.isEmpty())
			return;

		long min, max;
		if (expanded)
		{
			min = Collections.min(values);
			max = Collections.max(values);
			if (currentPrice > 0)
			{
				min = Math.min(min, currentPrice);
				max = Math.max(max, currentPrice);
			}
		}
		else
		{

			min = percentile(values, 0.025);
			max = percentile(values, 0.975);
			if (max <= min)
			{
				min = Collections.min(values);
				max = Collections.max(values);
			}
		}

		double[] axis = niceAxis(min, max, expanded ? 7 : 4, expanded ? 12 : 6);
		double axisMin = axis[0], axisMax = axis[1];
		int ticks = (int) axis[2];
		double axisRange = Math.max(1, axisMax - axisMin);

		for (int i = 0; i <= ticks; i++)
		{
			int y = plotBottom - (int) ((double) plotH * i / ticks);
			g2.setColor(GRID_COLOR);
			g2.drawLine(plotLeft, y, plotRight, y);
			long val = (long) (axisMin + axisRange * i / ticks);
			g2.setColor(Color.GRAY);
			g2.drawString(GpFormat.shortValue(val), plotRight + 4, y + fm.getAscent() / 2);
		}

		int n = visible.size();
		int[] hx = new int[n], hy = new int[n];
		int[] lx = new int[n], ly = new int[n];
		int[] ax = new int[n], ay = new int[n];
		int hc = 0, lc = 0, ac = 0;
		for (WikiRealtimePriceClient.PricePoint p : visible)
		{
			int x = plotLeft + (int) ((double) (p.getTimestamp() - startSec) / span * plotW);
			if (p.getAvgHighPrice() > 0)
			{
				hx[hc] = x; hy[hc] = priceY(p.getAvgHighPrice(), axisMin, axisRange, plotTop, plotBottom, plotH); hc++;
			}

			if (p.getAvgLowPrice() > 0)
			{
				lx[lc] = x; ly[lc] = priceY(p.getAvgLowPrice(), axisMin, axisRange, plotTop, plotBottom, plotH); lc++;
			}

			long avg = midpoint(p);
			if (avg > 0)
			{
				ax[ac] = x; ay[ac] = priceY(avg, axisMin, axisRange, plotTop, plotBottom, plotH); ac++;
			}
		}

		boolean showHighLow = lineSet != LineSet.AVG;
		boolean showAvg = lineSet != LineSet.HIGH_LOW;
		g2.setStroke(new BasicStroke(0.8f));
		if (showHighLow)
		{
			g2.setColor(withAlpha(COLOR_HIGH, 191));
			g2.draw(buildSeriesPath(hx, hy, hc));
			g2.setColor(withAlpha(COLOR_LOW, 191));
			g2.draw(buildSeriesPath(lx, ly, lc));
		}

		if (showAvg)
		{
			g2.setColor(withAlpha(COLOR_AVG, 191));
			g2.draw(buildSeriesPath(ax, ay, ac));
		}

		for (WikiRealtimePriceClient.PricePoint p : visible)
		{
			int x = plotLeft + (int) ((double) (p.getTimestamp() - startSec) / span * plotW);
			if (showHighLow)
			{
				clipMarker(g2, x, p.getAvgHighPrice(), axisMin, axisMax, plotTop, plotBottom, COLOR_HIGH);
				clipMarker(g2, x, p.getAvgLowPrice(), axisMin, axisMax, plotTop, plotBottom, COLOR_LOW);
			}

			if (showAvg)
				clipMarker(g2, x, midpoint(p), axisMin, axisMax, plotTop, plotBottom, COLOR_AVG);
		}

		if (currentPrice > 0)
		{
			int cy = priceY(currentPrice, axisMin, axisRange, plotTop, plotBottom, plotH);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
					10f, new float[]{4f, 4f}, 0f));
			g2.setColor(CURRENT_LINE_COLOR);
			g2.drawLine(plotLeft, cy, plotRight, cy);
		}
	}

	/** Maps a price to its y pixel within the plot, clamped to the plot bounds. */
	private static int priceY(long value, double axisMin, double axisRange,
			int plotTop, int plotBottom, int plotH)
	{
		int y = plotBottom - (int) ((value - axisMin) / axisRange * plotH);
		if (y < plotTop)
			return plotTop;

		if (y > plotBottom)
			return plotBottom;

		return y;
	}

	/**
	 * Draws a small triangle at the top or bottom plot edge marking a data point
	 * that falls outside the visible value axis (so off-scale spikes are still
	 * visible). No-op for in-range or non-positive values.
	 */
	private static void clipMarker(Graphics2D g2, int cx, long value, double axisMin, double axisMax,
			int plotTop, int plotBottom, Color color)
	{
		if (value <= 0 || (value <= axisMax && value >= axisMin))
			return;

		final int gap = 4;
		final int halfW = 3;
		final int height = 4;
		g2.setColor(color);
		if (value > axisMax)
		{
			int base = plotTop - gap;
			g2.fillPolygon(new int[]{cx - halfW, cx + halfW, cx}, new int[]{base, base, base - height}, 3);
		}
		else
		{
			int base = plotBottom + gap;
			g2.fillPolygon(new int[]{cx - halfW, cx + halfW, cx}, new int[]{base, base, base + height}, 3);
		}
	}

	/**
	 * Draws the volume chart: combined high+low traded volume as filled bars/area
	 * with a "nice" volume axis and labels. The axis is capped at the 90th
	 * percentile in the sidebar (so a single spike doesn't flatten everything) but
	 * uses the full range in the expanded pop-out; over-cap bars are tinted.
	 */
	private void paintVolume(Graphics2D g2, FontMetrics fm,
			List<WikiRealtimePriceClient.PricePoint> visible,
			int plotLeft, int plotTop, int plotRight, int plotBottom, int plotW, int plotH,
			long startSec, long span)
	{
		long maxVol = visible.stream()
				.mapToLong(p -> p.getHighPriceVolume() + p.getLowPriceVolume())
				.max()
				.orElse(0);
		if (maxVol <= 0)
		{
			g2.setColor(Color.LIGHT_GRAY);
			String msg = "No volume data";
			g2.drawString(msg, plotLeft + (plotW - fm.stringWidth(msg)) / 2, plotTop + plotH / 2);
			return;
		}

		List<Long> vols = visible.stream()
				.map(p -> p.getHighPriceVolume() + p.getLowPriceVolume())
				.collect(Collectors.toList());
		long cap = expanded ? maxVol : percentile(vols, 0.90);
		if (cap <= 0)
			cap = maxVol;

		long axisMax = Math.max(1, (long) Math.ceil(cap * 1.05));
		int targetLines = expanded ? 10 : 5;
		long step = niceVolumeStep(axisMax, targetLines);
		for (long v = 0; v <= axisMax; v += step)
		{
			int y = plotBottom - (int) ((double) v / axisMax * plotH);
			g2.setColor(GRID_COLOR);
			g2.drawLine(plotLeft, y, plotRight, y);
			g2.setColor(Color.GRAY);
			g2.drawString(GpFormat.shortValue(v), plotRight + 4, y + fm.getAscent() / 2);
		}

		int barW = Math.max(1, plotW / Math.max(1, visible.size()));
		for (WikiRealtimePriceClient.PricePoint p : visible)
		{
			int x = plotLeft + (int) ((double) (p.getTimestamp() - startSec) / span * plotW);
			long v = p.getHighPriceVolume() + p.getLowPriceVolume();
			long shown = Math.min(v, axisMax);
			int barH = (int) ((double) shown / axisMax * plotH);
			g2.setColor(VOLUME_COLOR);
			g2.fillRect(x, plotBottom - barH, barW, barH);
			if (v > axisMax)
			{

				int base = plotTop - 4;
				int cx = x + barW / 2;
				int[] xs = {cx - 3, cx + 3, cx};
				int[] ys = {base, base, base - 4};
				g2.setColor(VOLUME_OVER_COLOR);
				g2.fillPolygon(xs, ys, 3);
			}
		}

		int maWindow = Math.max(2, visible.size() / 10);
		double[] volArr = new double[vols.size()];
		for (int i = 0; i < vols.size(); i++)
			volArr[i] = vols.get(i);

		double[] ma = ema(volArr, maWindow);
		int[] mxs = new int[visible.size()];
		int[] mys = new int[visible.size()];
		for (int i = 0; i < visible.size(); i++)
		{
			WikiRealtimePriceClient.PricePoint p = visible.get(i);
			mxs[i] = plotLeft + (int) ((double) (p.getTimestamp() - startSec) / span * plotW);
			mys[i] = plotBottom - (int) (Math.min(ma[i], axisMax) / axisMax * plotH);
		}

		g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(MA_COLOR);
		g2.draw(monotoneCubic(mxs, mys));
	}

	/** Draws the hover tooltip box of text lines, flipping to the cursor's left near the right edge. */
	private void drawTooltip(Graphics2D g2, FontMetrics fm, String[] lines, int plotLeft, int plotTop, int plotRight)
	{
		int boxW = 0;
		for (String s : lines)
			boxW = Math.max(boxW, fm.stringWidth(s));

		boxW += 8;
		int boxH = lines.length * (fm.getHeight() + 1) + 4;
		int bx = hoverX + 8;
		if (bx + boxW > plotRight)
			bx = hoverX - 8 - boxW;

		if (bx < plotLeft)
			bx = plotLeft;

		int by = plotTop + 4;
		g2.setColor(new Color(20, 20, 20, 220));
		g2.fillRoundRect(bx, by, boxW, boxH, 6, 6);
		g2.setColor(Color.WHITE);
		int ty = by + fm.getAscent() + 2;
		for (String s : lines)
		{
			g2.drawString(s, bx + 4, ty);
			ty += fm.getHeight() + 1;
		}
	}

	/** Draws the x-axis date/time labels at the tick positions for the active window. */
	private void drawXAxis(Graphics2D g2, FontMetrics fm, int plotLeft, int plotBottom, int plotW,
			long startSec, long endSec)
	{
		long span = Math.max(1, endSec - startSec);
		g2.setColor(Color.GRAY);
		for (long[] tick : buildXTicks(startSec, endSec))
		{
			long ts = tick[0];
			if (ts < startSec || ts > endSec)
				continue;

			int x = plotLeft + (int) ((double) (ts - startSec) / span * plotW);
			String label = labelForTick(ts);
			drawVerticalLabel(g2, label, x, plotBottom + X_AXIS_LABEL_GAP, fm);
		}
	}

	/** Draws a string rotated 90° (reading bottom-to-top) hanging below the axis at {@code cx}. */
	private void drawVerticalLabel(Graphics2D g2, String s, int cx, int topY, FontMetrics fm)
	{
		Graphics2D gg = (Graphics2D) g2.create();
		try
		{
			gg.translate(cx, topY);
			gg.rotate(-Math.PI / 2);

			gg.drawString(s, -fm.stringWidth(s), fm.getAscent() / 2);
		}
		finally
		{
			gg.dispose();
		}
	}

	/**
	 * Builds the x-axis tick timestamps for the active window, snapped to natural
	 * boundaries (months for a year, days/weeks for a month, days for a week,
	 * rounded hours for a day) and denser in the expanded pop-out.
	 *
	 * @return single-element {@code long[]} tick timestamps in epoch seconds
	 */
	private List<long[]> buildXTicks(long startSec, long endSec)
	{
		List<long[]> ticks = new ArrayList<>();
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(startSec * 1000L);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);

		switch (activeWindow)
		{
			case YEAR:
			{
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.DAY_OF_MONTH, 1);
				if (cal.getTimeInMillis() / 1000L < startSec)
					cal.add(Calendar.MONTH, 1);

				while (cal.getTimeInMillis() / 1000L <= endSec)
				{
					ticks.add(new long[]{cal.getTimeInMillis() / 1000L});
					cal.add(Calendar.MONTH, 1);
				}

				break;
			}
			case MONTH:
			{
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				if (expanded)
				{

					if (cal.getTimeInMillis() / 1000L < startSec)
						cal.add(Calendar.DAY_OF_MONTH, 1);

					while (cal.getTimeInMillis() / 1000L <= endSec)
					{
						ticks.add(new long[]{cal.getTimeInMillis() / 1000L});
						cal.add(Calendar.DAY_OF_MONTH, 3);
					}
				}
				else
				{

					while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
							|| cal.getTimeInMillis() / 1000L < startSec)
					{
						cal.add(Calendar.DAY_OF_MONTH, 1);
					}

					while (cal.getTimeInMillis() / 1000L <= endSec)
					{
						ticks.add(new long[]{cal.getTimeInMillis() / 1000L});
						cal.add(Calendar.DAY_OF_MONTH, 7);
					}
				}

				break;
			}
			case WEEK:
			{
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				if (cal.getTimeInMillis() / 1000L < startSec)
					cal.add(Calendar.DAY_OF_MONTH, 1);

				while (cal.getTimeInMillis() / 1000L <= endSec)
				{
					ticks.add(new long[]{cal.getTimeInMillis() / 1000L});
					cal.add(Calendar.DAY_OF_MONTH, 1);
				}

				break;
			}
			default:
			{
				int incrementHours = expanded ? 2 : 3;
				cal.set(Calendar.MINUTE, 0);
				int hour = cal.get(Calendar.HOUR_OF_DAY);
				int rounded = ((hour + incrementHours - 1) / incrementHours) * incrementHours;
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.add(Calendar.HOUR_OF_DAY, rounded);
				if (cal.getTimeInMillis() / 1000L < startSec)
					cal.add(Calendar.HOUR_OF_DAY, incrementHours);

				while (cal.getTimeInMillis() / 1000L <= endSec)
				{
					ticks.add(new long[]{cal.getTimeInMillis() / 1000L});
					cal.add(Calendar.HOUR_OF_DAY, incrementHours);
				}

				break;
			}
		}

		return ticks;
	}

	/** Formats a tick timestamp as an axis label appropriate to the active window (time of day vs. date). */
	private String labelForTick(long tsSec)
	{
		Date d = new Date(tsSec * 1000L);
		SimpleDateFormat sdf;
		switch (activeWindow)
		{
			case YEAR:
				sdf = new SimpleDateFormat("MMM", Locale.US);
				break;
			case MONTH:
			case WEEK:
				sdf = new SimpleDateFormat("M/d", Locale.US);
				break;
			default:
				sdf = new SimpleDateFormat("ha", Locale.US);
				break;
		}

		return sdf.format(d);
	}

	/**
	 * Computes an exponential moving average over {@code values}.
	 *
	 * @param period the EMA period; the smoothing factor is {@code 2/(period+1)}
	 * @return a same-length array of smoothed values
	 */
	private static double[] ema(double[] values, int period)
	{
		double[] out = new double[values.length];
		if (values.length == 0)
			return out;

		double alpha = 2.0 / (period + 1);
		double e = values[0];
		out[0] = e;
		for (int i = 1; i < values.length; i++)
		{
			e = alpha * values[i] + (1 - alpha) * e;
			out[i] = e;
		}

		return out;
	}

	/**
	 * Builds the connecting path for a series of {@code n} screen points: a smooth
	 * monotone cubic when {@link #smooth} is set, otherwise straight segments.
	 */
	private Path2D buildSeriesPath(int[] xs, int[] ys, int n)
	{
		if (n <= 0)
			return new Path2D.Double();

		if (smooth && n >= 2)
		{

			double[] sy = movingAverage(ys, n, 3);
			int[] syi = new int[n];
			for (int i = 0; i < n; i++)
				syi[i] = (int) Math.round(sy[i]);

			return monotoneCubic(Arrays.copyOf(xs, n), syi);
		}

		Path2D path = new Path2D.Double();
		path.moveTo(xs[0], ys[0]);
		for (int i = 1; i < n; i++)
			path.lineTo(xs[i], ys[i]);

		return path;
	}

	/**
	 * Computes a centered simple moving average of the first {@code n} y-values.
	 *
	 * @param window the averaging width in samples
	 * @return a length-{@code n} array of averaged values
	 */
	private static double[] movingAverage(int[] ys, int n, int window)
	{
		double[] out = new double[n];
		int half = window / 2;
		for (int i = 0; i < n; i++)
		{
			int lo = Math.max(0, i - half);
			int hi = Math.min(n - 1, i + half);
			double sum = 0;
			for (int j = lo; j <= hi; j++)
				sum += ys[j];

			out[i] = sum / (hi - lo + 1);
		}

		return out;
	}

	/**
	 * Builds a Fritsch–Carlson monotone cubic Hermite spline through the points.
	 * Monotonicity prevents the overshoot a naive cubic would introduce, so the
	 * smoothed line never invents peaks or troughs the data doesn't have.
	 */
	private static Path2D monotoneCubic(int[] xsIn, int[] ysIn)
	{
		Path2D path = new Path2D.Double();
		int n0 = xsIn.length;
		if (n0 == 0)
			return path;

		double[] xs = new double[n0];
		double[] ys = new double[n0];
		int n = 0;
		for (int i = 0; i < n0; i++)
		{
			if (n > 0 && xsIn[i] <= xs[n - 1])
			{
				ys[n - 1] = ysIn[i];
				continue;
			}

			xs[n] = xsIn[i];
			ys[n] = ysIn[i];
			n++;
		}

		path.moveTo(xs[0], ys[0]);
		if (n == 1)
			return path;

		double[] delta = new double[n - 1];
		for (int i = 0; i < n - 1; i++)
			delta[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);

		double[] m = new double[n];
		m[0] = delta[0];
		m[n - 1] = delta[n - 2];
		for (int i = 1; i < n - 1; i++)
			m[i] = (delta[i - 1] + delta[i]) / 2.0;

		for (int i = 0; i < n - 1; i++)
		{
			if (delta[i] == 0)
			{
				m[i] = 0;
				m[i + 1] = 0;
			}
			else
			{
				double a = m[i] / delta[i];
				double b = m[i + 1] / delta[i];
				double s = a * a + b * b;
				if (s > 9)
				{
					double tau = 3.0 / Math.sqrt(s);
					m[i] = tau * a * delta[i];
					m[i + 1] = tau * b * delta[i];
				}
			}
		}

		for (int i = 0; i < n - 1; i++)
		{
			double h = xs[i + 1] - xs[i];
			double c1x = xs[i] + h / 3.0;
			double c1y = ys[i] + m[i] * h / 3.0;
			double c2x = xs[i + 1] - h / 3.0;
			double c2y = ys[i + 1] - m[i + 1] * h / 3.0;
			path.curveTo(c1x, c1y, c2x, c2y, xs[i + 1], ys[i + 1]);
		}

		return path;
	}

	/** @return the index of the point whose x pixel is nearest {@link #hoverX}, or -1 if none. */
	private int closestIndex(List<WikiRealtimePriceClient.PricePoint> points, int plotLeft, int plotW, long startSec, long span)
	{
		int best = -1;
		int bestDx = Integer.MAX_VALUE;
		for (int i = 0; i < points.size(); i++)
		{
			int x = plotLeft + (int) ((double) (points.get(i).getTimestamp() - startSec) / span * plotW);
			int dx = Math.abs(x - hoverX);
			if (dx < bestDx)
			{
				bestDx = dx;
				best = i;
			}
		}

		return best;
	}

	/** @return the high/low midpoint of a point, or whichever side is present if only one is. */
	private static long midpoint(WikiRealtimePriceClient.PricePoint p)
	{
		long h = p.getAvgHighPrice();
		long l = p.getAvgLowPrice();
		if (h > 0 && l > 0)
			return (h + l) / 2;

		return Math.max(h, l);
	}

	/**
	 * Picks a human-friendly value axis covering {@code [dataMin, dataMax]} using
	 * a 1/2/2.5/5/10 step progression so labels land on round numbers.
	 *
	 * @param minTicks minimum number of gridlines to aim for
	 * @param maxTicks maximum number of gridlines to allow
	 * @return {@code [axisMin, axisMax, step]}
	 */
	private static double[] niceAxis(long dataMin, long dataMax, int minTicks, int maxTicks)
	{
		if (dataMax <= dataMin)
			dataMax = dataMin + 1;

		double range = dataMax - dataMin;
		double[] niceMults = {1, 2, 2.5, 5};
		double bestStep = range / minTicks;
		double chosenStep = -1;
		for (int k = -2; k <= 12 && chosenStep < 0; k++)
		{
			double pow = Math.pow(10, k);
			for (double m : niceMults)
			{
				double step = m * pow;
				if (step <= 0)
					continue;

				double aMin = Math.floor(dataMin / step) * step;
				double aMax = Math.ceil(dataMax / step) * step;
				int count = (int) Math.round((aMax - aMin) / step);
				if (count >= minTicks && count <= maxTicks)
				{
					chosenStep = step;
					break;
				}
			}
		}

		if (chosenStep < 0)
			chosenStep = bestStep;

		double axisMin = Math.floor(dataMin / chosenStep) * chosenStep;
		double axisMax = Math.ceil(dataMax / chosenStep) * chosenStep;
		int ticks = Math.max(1, (int) Math.round((axisMax - axisMin) / chosenStep));
		return new double[]{axisMin, axisMax, ticks};
	}

	/** @return a rounded gridline step near {@code target/intervals} (1/2/5 × power of ten) for the volume axis. */
	private static long niceVolumeStep(long target, int intervals)
	{

		double per = target / (double) intervals;
		double[] niceMults = {1, 2, 2.5, 5};
		for (int k = 0; k <= 12; k++)
		{
			double pow = Math.pow(10, k);
			for (double m : niceMults)
			{
				double step = m * pow;
				if (step >= per)
					return (long) Math.max(1, Math.round(step));
			}
		}

		return Math.max(1, target / intervals);
	}

	/**
	 * @param p the percentile in {@code [0, 1]}
	 * @return the {@code p}-th percentile of {@code values} (the list is sorted in place), or 0 if empty
	 */
	private static long percentile(List<Long> values, double p)
	{
		if (values.isEmpty())
			return 0;

		List<Long> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		int idx = (int) Math.ceil(p * sorted.size()) - 1;
		idx = Math.max(0, Math.min(sorted.size() - 1, idx));
		return sorted.get(idx);
	}

	/** @return a copy of {@code c} with the given alpha (0–255). */
	private static Color withAlpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}

}
