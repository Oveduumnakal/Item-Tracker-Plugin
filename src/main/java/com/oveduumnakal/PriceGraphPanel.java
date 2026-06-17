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
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PriceGraphPanel extends JPanel
{
	public enum Mode { PRICE, VOLUME }

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

	private final JPanel tabsBar;
	private final List<JLabel> tabLabels = new ArrayList<>();
	private int hoverX = -1;

	private static final int TAB_BAR_HEIGHT = 28;
	private static final int RIGHT_AXIS_WIDTH = 38;
	private static final int BOTTOM_AXIS_HEIGHT = 34;
	private static final int LEFT_PAD = 8;
	private static final int TOP_PAD = 13;

	public PriceGraphPanel()
	{
		this(Mode.PRICE);
	}

	public PriceGraphPanel(Mode mode)
	{
		this.mode = mode;
		setLayout(new java.awt.BorderLayout());
		setBackground(BG_COLOR);
		setPreferredSize(mode == Mode.PRICE ? new Dimension(240, 250) : new Dimension(240, 182));

		tabsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		tabsBar.setBackground(BG_COLOR);
		// 5px above the timeframe buttons, a little breathing room below.
		tabsBar.setBorder(new EmptyBorder(5, 4, 4, 4));
		for (int i = 0; i < TIMEFRAMES.length; i++)
		{
			final TimeWindow tw = TIMEFRAMES[i];
			final JLabel tab = new JLabel(TIMEFRAME_LABELS[i]);
			tab.setForeground(Color.WHITE);
			tab.setFont(FontManager.getRunescapeSmallFont());
			tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tab.setBorder(new EmptyBorder(2, 4, 2, 4));
			tab.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					activeWindow = tw;
					updateTabHighlight();
					repaint();
				}
			});
			tabLabels.add(tab);
			tabsBar.add(tab);
		}
		add(tabsBar, java.awt.BorderLayout.NORTH);
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
				l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
				l.setBorder(BorderFactory.createCompoundBorder(
						new EmptyBorder(2, 4, 0, 4),
						BorderFactory.createMatteBorder(0, 0, 2, 0, COLOR_AVG)));
			}
			else
			{
				l.setForeground(Color.LIGHT_GRAY);
				l.setFont(FontManager.getRunescapeSmallFont());
				l.setBorder(new EmptyBorder(2, 4, 2, 4));
			}
		}
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
		repaint();
	}

	public TimeWindow getActiveWindow()
	{
		return activeWindow;
	}

	public void setActiveWindow(TimeWindow w)
	{
		this.activeWindow = w == null ? TimeWindow.H24 : w;
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
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setFont(FontManager.getRunescapeSmallFont());
			FontMetrics fm = g2.getFontMetrics();

			int w = getWidth();
			int h = getHeight();

			int plotTop = TAB_BAR_HEIGHT + TOP_PAD;
			int plotBottom = h - BOTTOM_AXIS_HEIGHT;
			int plotLeft = LEFT_PAD;
			int plotRight = w - RIGHT_AXIS_WIDTH;
			int plotW = Math.max(1, plotRight - plotLeft);
			int plotH = Math.max(1, plotBottom - plotTop);

			// Separator between the timeframe buttons and the graph.
			g2.setColor(SEPARATOR_COLOR);
			g2.drawLine(0, TAB_BAR_HEIGHT, w, TAB_BAR_HEIGHT);

			long endSec = System.currentTimeMillis() / 1000L;
			long startSec = endSec - activeWindow.getDuration().getSeconds();
			long span = Math.max(1, endSec - startSec);

			// Collect the points that fall within the selected period.
			List<WikiRealtimePriceClient.PricePoint> visible = new ArrayList<>();
			for (WikiRealtimePriceClient.PricePoint p : seriesForActiveWindow())
			{
				if (p.getTimestamp() >= startSec && p.getTimestamp() <= endSec)
				{
					visible.add(p);
				}
			}

			if (visible.isEmpty())
			{
				g2.setColor(Color.LIGHT_GRAY);
				String msg = "No data";
				g2.drawString(msg, plotLeft + (plotW - fm.stringWidth(msg)) / 2, plotTop + plotH / 2);
				return;
			}

			if (mode == Mode.VOLUME)
			{
				paintVolume(g2, fm, visible, plotLeft, plotTop, plotRight, plotBottom, plotW, plotH, startSec, span);
			}
			else
			{
				paintPrice(g2, fm, visible, plotLeft, plotTop, plotRight, plotBottom, plotW, plotH, startSec, span);
			}

			// X axis (shared layout: vertical labels along the bottom)
			drawXAxis(g2, fm, plotLeft, plotBottom, plotW, startSec, endSec);
		}
		finally
		{
			g2.dispose();
		}
	}

	private void paintPrice(Graphics2D g2, FontMetrics fm,
			List<WikiRealtimePriceClient.PricePoint> visible,
			int plotLeft, int plotTop, int plotRight, int plotBottom, int plotW, int plotH,
			long startSec, long span)
	{
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for (WikiRealtimePriceClient.PricePoint p : visible)
		{
			if (p.getAvgHighPrice() > 0) { max = Math.max(max, p.getAvgHighPrice()); min = Math.min(min, p.getAvgHighPrice()); }
			if (p.getAvgLowPrice() > 0) { max = Math.max(max, p.getAvgLowPrice()); min = Math.min(min, p.getAvgLowPrice()); }
		}
		if (currentPrice > 0) { max = Math.max(max, currentPrice); min = Math.min(min, currentPrice); }
		if (min == Long.MAX_VALUE || max == Long.MIN_VALUE)
		{
			return;
		}

		double[] axis = niceAxis(min, max, 4, 6);
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
			g2.drawString(abbreviate(val), plotRight + 4, y + fm.getAscent() / 2);
		}

		Path2D highPath = new Path2D.Double();
		Path2D lowPath = new Path2D.Double();
		Path2D avgPath = new Path2D.Double();
		boolean fH = true, fL = true, fA = true;
		for (WikiRealtimePriceClient.PricePoint p : visible)
		{
			int x = plotLeft + (int) ((double) (p.getTimestamp() - startSec) / span * plotW);
			if (p.getAvgHighPrice() > 0)
			{
				int y = plotBottom - (int) ((p.getAvgHighPrice() - axisMin) / axisRange * plotH);
				if (fH) { highPath.moveTo(x, y); fH = false; } else highPath.lineTo(x, y);
			}
			if (p.getAvgLowPrice() > 0)
			{
				int y = plotBottom - (int) ((p.getAvgLowPrice() - axisMin) / axisRange * plotH);
				if (fL) { lowPath.moveTo(x, y); fL = false; } else lowPath.lineTo(x, y);
			}
			long avg = midpoint(p);
			if (avg > 0)
			{
				int y = plotBottom - (int) ((avg - axisMin) / axisRange * plotH);
				if (fA) { avgPath.moveTo(x, y); fA = false; } else avgPath.lineTo(x, y);
			}
		}

		// Trend lines drawn at 75% opacity to soften the noisy crisscrossing.
		g2.setStroke(new BasicStroke(0.8f));
		g2.setColor(withAlpha(COLOR_HIGH, 191));
		g2.draw(highPath);
		g2.setColor(withAlpha(COLOR_LOW, 191));
		g2.draw(lowPath);
		g2.setColor(withAlpha(COLOR_AVG, 191));
		g2.draw(avgPath);

		// Current average price: dashed blue-grey line drawn on top.
		if (currentPrice > 0)
		{
			int cy = plotBottom - (int) ((currentPrice - axisMin) / axisRange * plotH);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
					10f, new float[]{4f, 4f}, 0f));
			g2.setColor(CURRENT_LINE_COLOR);
			g2.drawLine(plotLeft, cy, plotRight, cy);
		}

		// Hover crosshair + tooltip (full-form values)
		if (hoverX >= plotLeft && hoverX <= plotRight)
		{
			int idx = closestIndex(visible, plotLeft, plotW, startSec, span);
			if (idx >= 0)
			{
				WikiRealtimePriceClient.PricePoint closest = visible.get(idx);
				g2.setStroke(new BasicStroke(1));
				g2.setColor(new Color(255, 255, 255, 120));
				g2.drawLine(hoverX, plotTop, hoverX, plotBottom);
				String[] lines = {
						"H: " + NUMBER_FORMAT.format(closest.getAvgHighPrice()),
						"L: " + NUMBER_FORMAT.format(closest.getAvgLowPrice()),
						"A: " + NUMBER_FORMAT.format(midpoint(closest)),
				};
				drawTooltip(g2, fm, lines, plotLeft, plotTop, plotRight);
			}
		}
	}

	private void paintVolume(Graphics2D g2, FontMetrics fm,
			List<WikiRealtimePriceClient.PricePoint> visible,
			int plotLeft, int plotTop, int plotRight, int plotBottom, int plotW, int plotH,
			long startSec, long span)
	{
		long maxVol = 0;
		for (WikiRealtimePriceClient.PricePoint p : visible)
		{
			maxVol = Math.max(maxVol, p.getHighPriceVolume() + p.getLowPriceVolume());
		}
		if (maxVol <= 0)
		{
			g2.setColor(Color.LIGHT_GRAY);
			String msg = "No volume data";
			g2.drawString(msg, plotLeft + (plotW - fm.stringWidth(msg)) / 2, plotTop + plotH / 2);
			return;
		}

		// Cap the Y axis at the 95th percentile so a rare spike doesn't flatten
		// every other bar; bars above the cap are clipped and flagged with an arrow.
		List<Long> vols = new ArrayList<>(visible.size());
		for (WikiRealtimePriceClient.PricePoint p : visible)
		{
			vols.add(p.getHighPriceVolume() + p.getLowPriceVolume());
		}
		long cap = percentile(vols, 0.90);
		if (cap <= 0)
		{
			cap = maxVol;
		}

		// Decluttered Y axis: ~5 round increments with faint gridlines.
		final int intervals = 5;
		long step = niceVolumeStep(cap, intervals);
		long axisMax = step * intervals;
		for (int i = 0; i <= intervals; i++)
		{
			int y = plotBottom - (int) ((double) plotH * i / intervals);
			g2.setColor(GRID_COLOR);
			g2.drawLine(plotLeft, y, plotRight, y);
			g2.setColor(Color.GRAY);
			g2.drawString(abbreviate(step * i), plotRight + 4, y + fm.getAscent() / 2);
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
		{
			volArr[i] = vols.get(i);
		}
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

		// Hover crosshair + tooltip with the moving average added.
		if (hoverX >= plotLeft && hoverX <= plotRight)
		{
			int idx = closestIndex(visible, plotLeft, plotW, startSec, span);
			if (idx >= 0)
			{
				WikiRealtimePriceClient.PricePoint closest = visible.get(idx);
				g2.setStroke(new BasicStroke(1));
				g2.setColor(new Color(255, 255, 255, 120));
				g2.drawLine(hoverX, plotTop, hoverX, plotBottom);
				String[] lines = {
						"V: " + NUMBER_FORMAT.format(closest.getHighPriceVolume() + closest.getLowPriceVolume()),
						"MA: " + NUMBER_FORMAT.format(Math.round(ma[idx])),
				};
				drawTooltip(g2, fm, lines, plotLeft, plotTop, plotRight);
			}
		}
	}

	private void drawTooltip(Graphics2D g2, FontMetrics fm, String[] lines, int plotLeft, int plotTop, int plotRight)
	{
		int boxW = 0;
		for (String s : lines) boxW = Math.max(boxW, fm.stringWidth(s));
		boxW += 8;
		int boxH = lines.length * (fm.getHeight() + 1) + 4;
		int bx = hoverX + 8;
		if (bx + boxW > plotRight) bx = hoverX - 8 - boxW;
		if (bx < plotLeft) bx = plotLeft;
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
			{
				continue;
			}
			int x = plotLeft + (int) ((double) (ts - startSec) / span * plotW);
			String label = labelForTick(ts);
			drawVerticalLabel(g2, label, x, plotBottom + 3, fm);
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
				if (cal.getTimeInMillis() / 1000L < startSec) cal.add(Calendar.MONTH, 1);
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
				// Snap to the Sunday on or after the start.
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
				break;
			}
			case WEEK:
			{
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				if (cal.getTimeInMillis() / 1000L < startSec) cal.add(Calendar.DAY_OF_MONTH, 1);
				while (cal.getTimeInMillis() / 1000L <= endSec)
				{
					ticks.add(new long[]{cal.getTimeInMillis() / 1000L});
					cal.add(Calendar.DAY_OF_MONTH, 1);
				}
				break;
			}
			default: // H24
			{
				int incrementHours = 3;
				cal.set(Calendar.MINUTE, 0);
				int hour = cal.get(Calendar.HOUR_OF_DAY);
				int rounded = ((hour + incrementHours - 1) / incrementHours) * incrementHours;
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.add(Calendar.HOUR_OF_DAY, rounded);
				if (cal.getTimeInMillis() / 1000L < startSec) cal.add(Calendar.HOUR_OF_DAY, incrementHours);
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
		{
			return out;
		}
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
	 * Builds a monotone cubic (Fritsch–Carlson) spline through the points, drawn
	 * as cubic Bézier segments. Unlike a plain Catmull-Rom curve it never
	 * overshoots the data, so it can't invent peaks the volume didn't have.
	 */
	private static Path2D monotoneCubic(int[] xsIn, int[] ysIn)
	{
		Path2D path = new Path2D.Double();
		int n0 = xsIn.length;
		if (n0 == 0)
		{
			return path;
		}

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
		{
			return path;
		}

		double[] delta = new double[n - 1];
		for (int i = 0; i < n - 1; i++)
		{
			delta[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
		}

		double[] m = new double[n];
		m[0] = delta[0];
		m[n - 1] = delta[n - 2];
		for (int i = 1; i < n - 1; i++)
		{
			m[i] = (delta[i - 1] + delta[i]) / 2.0;
		}

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
		if (h > 0 && l > 0) return (h + l) / 2;
		return Math.max(h, l);
	}

	/**
	 * Chooses a "nice" enclosing axis with a tick count between {@code minTicks}
	 * and {@code maxTicks}. Returns {@code [axisMin, axisMax, tickCount]}.
	 */
	private static double[] niceAxis(long dataMin, long dataMax, int minTicks, int maxTicks)
	{
		if (dataMax <= dataMin)
		{
			dataMax = dataMin + 1;
		}
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
				if (step <= 0) continue;
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
		{
			chosenStep = bestStep;
		}
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
				{
					return (long) Math.max(1, Math.round(step));
				}
			}
		}
		return Math.max(1, target / intervals);
	}

	private static long percentile(List<Long> values, double p)
	{
		if (values.isEmpty())
		{
			return 0;
		}
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

	private static String abbreviate(long v)
	{
		if (v <= 0) return "0";
		if (v >= 1_000_000_000L) return oneDecimal(v / 1_000_000_000.0) + "B";
		if (v >= 1_000_000L) return oneDecimal(v / 1_000_000.0) + "M";
		if (v >= 1_000L) return oneDecimal(v / 1_000.0) + "K";
		return Long.toString(v);
	}

	/** Formats with at most one decimal place, dropping a trailing ".0". */
	private static String oneDecimal(double d)
	{
		String s = String.format(Locale.US, "%.1f", d);
		if (s.endsWith(".0"))
		{
			s = s.substring(0, s.length() - 2);
		}
		return s;
	}
}
