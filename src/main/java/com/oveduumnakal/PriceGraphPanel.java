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

public class PriceGraphPanel extends JPanel
{
	public enum Mode { PRICE, VOLUME }

	/** Which of the High/Low/Avg price lines are drawn. */
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
	// Faint blue-grey bars act as background context behind the moving-average line.
	private static final Color VOLUME_COLOR = new Color(120, 140, 180, 110);
	// Brighter opaque blue for the "exceeds cap" arrow on clipped bars.
	private static final Color VOLUME_OVER_COLOR = new Color(165, 185, 225);
	// The current-average dashed line on the price graph uses the same blue-grey as the volume bars.
	private static final Color CURRENT_LINE_COLOR = new Color(120, 140, 180);
	// Teal moving-average line — the primary readable element of the volume graph.
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

	// The larger pop-out copy spells the timeframe tabs out and shows denser axes.
	private final boolean expanded;
	// Font for tabs, axes and tooltips: a smooth monospaced font in the pop-out,
	// the RuneScape Small font in the sidebar.
	private final Font baseFont;

	private final JPanel tabsBar;
	private final List<JLabel> tabLabels = new ArrayList<>();
	private int hoverX = -1;

	// Price mode: when on, the high/low/avg lines are drawn as smooth curves
	// instead of straight segments. Toggled by the "Smooth" label in the tab bar.
	private boolean smooth = false;
	private JLabel smoothToggle;
	// Notified when the user clicks the toggle, so the shared preference and the
	// sibling (sidebar/pop-out) graph can stay in sync.
	private java.util.function.Consumer<Boolean> smoothListener;

	// Which High/Low/Avg lines are drawn, cycled by the "lines" toggle (price mode).
	private LineSet lineSet = LineSet.ALL;
	private JLabel linesToggle;
	private java.util.function.Consumer<LineSet> lineSetListener;

	// The data plot is expensive to render (paths, EMA, spline), so it is cached
	// to an image and only rebuilt when the data, timeframe, or size changes.
	// Mouse-move hovering then only blits the cache and redraws the crosshair.
	private transient BufferedImage plotCache;
	private boolean plotCacheDirty = true;

	private static final int TAB_BAR_HEIGHT = 28;
	// Reserved space for the axis labels, sized to the actual label font so the
	// larger pop-out font does not clip the y-axis suffix or the date labels.
	private final int rightAxisWidth;
	private final int bottomAxisHeight;
	private static final int LEFT_PAD = 8;
	private static final int TOP_PAD = 13;
	// Gap below the plot before the x-axis labels start, leaving room for the
	// downward clip-indicator triangles (which sit just under the plot bottom).
	private static final int X_AXIS_LABEL_GAP = 12;

	public PriceGraphPanel()
	{
		this(Mode.PRICE, false);
	}

	public PriceGraphPanel(Mode mode)
	{
		this(mode, false);
	}

	public PriceGraphPanel(Mode mode, boolean expanded)
	{
		this.mode = mode;
		this.expanded = expanded;
		this.baseFont = expanded
				? new Font(Font.MONOSPACED, Font.PLAIN, 13)
				: FontManager.getRunescapeSmallFont();

		// Size the axis gutters to the label font: the y-axis must fit a value like
		// "999.9K" (drawn at plotRight + 4) and the bottom must fit a vertical date.
		FontMetrics axisFm = getFontMetrics(baseFont);
		this.rightAxisWidth = axisFm.stringWidth("999.9K") + 8;
		this.bottomAxisHeight = X_AXIS_LABEL_GAP + axisFm.stringWidth("99/99") + 4;

		setLayout(new java.awt.BorderLayout());
		setBackground(BG_COLOR);
		setPreferredSize(mode == Mode.PRICE ? new Dimension(240, 250) : new Dimension(240, 182));

		// Tighter gaps in the narrow sidebar so the tabs and both toggles all fit.
		tabsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, expanded ? 8 : 2, 0));
		tabsBar.setBackground(BG_COLOR);
		// 5px above the timeframe buttons, a little breathing room below.
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

		// Tabs on the left; the price-mode "lines" and "Smooth" toggles on the right.
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

	/** Sets the smoothing state programmatically (does not fire the listener). */
	public void setSmooth(boolean s)
	{
		if (smooth == s)
			return;

		smooth = s;
		plotCacheDirty = true;
		updateSmoothToggle();
		repaint();
	}

	public void setSmoothListener(java.util.function.Consumer<Boolean> listener)
	{
		this.smoothListener = listener;
	}

	/** Sets the visible-line set programmatically (does not fire the listener). */
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

	public void setLineSetListener(java.util.function.Consumer<LineSet> listener)
	{
		this.lineSetListener = listener;
	}

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

	public void setActiveWindow(TimeWindow w)
	{
		this.activeWindow = w == null ? TimeWindow.H24 : w;
		plotCacheDirty = true;
		updateTabHighlight();
		repaint();
	}

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

		// (Re)build the heavy data plot only when the data, timeframe, or size
		// changed; otherwise reuse the cached image so hovering stays cheap.
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

		// Lightweight hover overlay drawn fresh each paint.
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

	/** Points within the active timeframe; recomputed each paint (cheap). */
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

	/** Renders the static graph (separator, axes, data) into the plot cache. */
	private void renderStatic(Graphics2D g2, FontMetrics fm, int w,
			List<WikiRealtimePriceClient.PricePoint> visible,
			int plotLeft, int plotTop, int plotRight, int plotBottom, int plotW, int plotH,
			long startSec, long endSec, long span)
	{
		// Separator between the timeframe buttons and the graph.
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

		// X axis (shared layout: vertical labels along the bottom)
		drawXAxis(g2, fm, plotLeft, plotBottom, plotW, startSec, endSec);
	}

	/** Draws the hover crosshair and value tooltip for the point under the cursor. */
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

	private void paintPrice(Graphics2D g2, FontMetrics fm,
			List<WikiRealtimePriceClient.PricePoint> visible,
			int plotLeft, int plotTop, int plotRight, int plotBottom, int plotW, int plotH,
			long startSec, long span)
	{
		// Collect every High/Low value so the axis can be scaled to the bulk of the
		// data rather than to rare spikes, which otherwise leave most of the chart
		// empty (the lines hug one edge). The expanded pop-out keeps the full range.
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
			// Clip the rare spikes (2.5th/97.5th percentile); they get drawn against
			// the chart edge below. Fall back to the true range when the band collapses
			// (e.g. a near-flat series where most points share a value).
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

		// Y gridlines + value labels
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

		// Trend lines drawn at 75% opacity to soften the noisy crisscrossing.
		// Smoothing replaces the straight segments with an overshoot-free spline.
		// Which lines are shown depends on the current LineSet.
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

		// Clip markers: a small triangle (with a gap above/below the clipped line)
		// wherever a visible line leaves the chart, pointing up past the top or down
		// past the bottom. Colour matches the line. Mirrors the volume over-cap arrow.
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

		// Current average price: dashed blue-grey line drawn on top.
		if (currentPrice > 0)
		{
			int cy = priceY(currentPrice, axisMin, axisRange, plotTop, plotBottom, plotH);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
					10f, new float[]{4f, 4f}, 0f));
			g2.setColor(CURRENT_LINE_COLOR);
			g2.drawLine(plotLeft, cy, plotRight, cy);
		}
	}

	/**
	 * Maps a price to its plot Y, clamped to the plot area so values outside the
	 * (clipped) axis ride the top/bottom edge instead of drawing off-canvas.
	 */
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
	 * Draws a small clip-indicator triangle when {@code value} falls outside the
	 * axis: pointing up (just above the top, with a gap) when it exceeds the top,
	 * or down (just below the bottom) when it falls under it. No-op otherwise.
	 */
	private static void clipMarker(Graphics2D g2, int cx, long value, double axisMin, double axisMax,
			int plotTop, int plotBottom, Color color)
	{
		if (value <= 0 || (value <= axisMax && value >= axisMin))
			return;

		final int gap = 4;   // space between the clipped line edge and the triangle
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

		// Cap the Y axis at the 90th percentile so a rare spike doesn't flatten
		// every other bar; bars above the cap are clipped and flagged with an arrow.
		// The expanded pop-out shows the full range instead (no cap).
		List<Long> vols = visible.stream()
				.map(p -> p.getHighPriceVolume() + p.getLowPriceVolume())
				.collect(Collectors.toList());
		long cap = expanded ? maxVol : percentile(vols, 0.90);
		if (cap <= 0)
			cap = maxVol;

		// Y axis hugs the data: the top is the cap plus a little headroom, with
		// round-number gridlines beneath. (In the capped sidebar view, taller
		// spikes are clipped and flagged with an arrow.)
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

		// Bars (faint), with an up-arrow on any bar that exceeds the capped axis.
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
				// Sit the arrow a few px above the (clipped) bar top so it reads separately.
				int base = plotTop - 4;
				int cx = x + barW / 2;
				int[] xs = {cx - 3, cx + 3, cx};
				int[] ys = {base, base, base - 4};
				g2.setColor(VOLUME_OVER_COLOR);
				g2.fillPolygon(xs, ys, 3);
			}
		}

		// Moving-average line: a single-pass EMA drawn as a monotone cubic spline.
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

	private void drawVerticalLabel(Graphics2D g2, String s, int cx, int topY, FontMetrics fm)
	{
		Graphics2D gg = (Graphics2D) g2.create();
		try
		{
			gg.translate(cx, topY);
			gg.rotate(-Math.PI / 2);
			// After rotation the text reads bottom-to-top; offset so it hangs below the axis.
			gg.drawString(s, -fm.stringWidth(s), fm.getAscent() / 2);
		}
		finally
		{
			gg.dispose();
		}
	}

	/** Builds the x-axis tick timestamps (seconds) for the active window/mode. */
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
					// Denser: every 3 days from the first midnight on/after the start.
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
					// Snap to the Sunday on or after the start, then weekly.
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
			default: // H24
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
	 * Builds the path for one price line from the first {@code n} points: a
	 * straight polyline normally, or an overshoot-free spline when smoothing is on.
	 */
	private Path2D buildSeriesPath(int[] xs, int[] ys, int n)
	{
		if (n <= 0)
			return new Path2D.Double();

		if (smooth && n >= 2)
		{
			// Light centred moving average to shave off jitter, then the spline.
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

	/** Centred (lag-free) moving average over a window of {@code window} samples. */
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
	 * Builds a monotone cubic (Fritsch–Carlson) spline through the points, drawn
	 * as cubic Bézier segments. Unlike a plain Catmull-Rom curve it never
	 * overshoots the data, so it can't invent peaks the volume didn't have.
	 */
	private static Path2D monotoneCubic(int[] xsIn, int[] ysIn)
	{
		Path2D path = new Path2D.Double();
		int n0 = xsIn.length;
		if (n0 == 0)
			return path;

		// Collapse points that map to the same pixel column (and any out-of-order
		// x) so x is strictly increasing — required for the slope computations.
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

		// Clamp tangents so each segment stays monotonic (no overshoot).
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

	private static long midpoint(WikiRealtimePriceClient.PricePoint p)
	{
		long h = p.getAvgHighPrice();
		long l = p.getAvgLowPrice();
		if (h > 0 && l > 0)
			return (h + l) / 2;

		return Math.max(h, l);
	}

	/**
	 * Chooses a "nice" enclosing axis with a tick count between {@code minTicks}
	 * and {@code maxTicks}. Returns {@code [axisMin, axisMax, tickCount]}.
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

	private static long niceVolumeStep(long target, int intervals)
	{
		// Smallest "nice" step whose {intervals} increments cover the target.
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

	private static Color withAlpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}

}
