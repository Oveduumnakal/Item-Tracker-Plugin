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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

public class PriceGraphPanel extends JPanel
{
	public enum Mode { PRICE, VOLUME }

	private static final Color COLOR_HIGH = new Color(100, 220, 100);
	private static final Color COLOR_LOW = new Color(220, 100, 100);
	private static final Color COLOR_AVG = new Color(255, 200, 0);
	private static final Color GRID_COLOR = new Color(60, 60, 60, 160);
	private static final Color VOLUME_COLOR = new Color(120, 140, 180, 180);
	private static final Color BG_COLOR = ColorScheme.DARKER_GRAY_COLOR;

	private final Mode mode;
	private List<WikiRealtimePriceClient.PricePoint> points = Collections.emptyList();
	private long currentPrice;
	private TimeWindow activeWindow = TimeWindow.H24;
	private Consumer<TimeWindow> onTimeframeChange;

	private static final TimeWindow[] TIMEFRAMES = {
			TimeWindow.H24, TimeWindow.WEEK, TimeWindow.MONTH, TimeWindow.YEAR
	};
	private static final String[] TIMEFRAME_LABELS = {"1D", "1W", "1M", "1Y"};

	private final JPanel tabsBar;
	private final List<JLabel> tabLabels = new ArrayList<>();
	private int hoverX = -1;

	private static final int TAB_BAR_HEIGHT = 24;
	private static final int RIGHT_AXIS_WIDTH = 56;
	private static final int BOTTOM_AXIS_HEIGHT = 18;
	private static final int LEFT_PAD = 8;
	private static final int TOP_PAD = 8;

	public PriceGraphPanel()
	{
		this(Mode.PRICE);
	}

	public PriceGraphPanel(Mode mode)
	{
		this.mode = mode;
		setLayout(new java.awt.BorderLayout());
		setBackground(BG_COLOR);
		setPreferredSize(mode == Mode.PRICE ? new Dimension(220, 200) : new Dimension(220, 90));

		if (mode == Mode.PRICE)
		{
			tabsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
			tabsBar.setBackground(BG_COLOR);
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
						if (onTimeframeChange != null)
						{
							onTimeframeChange.accept(tw);
						}
						repaint();
					}
				});
				tabLabels.add(tab);
				tabsBar.add(tab);
			}
			add(tabsBar, java.awt.BorderLayout.NORTH);
			updateTabHighlight();
		}
		else
		{
			tabsBar = null;
		}

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
		if (tabsBar == null)
		{
			return;
		}
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

	public void setData(List<WikiRealtimePriceClient.PricePoint> points, long currentPrice)
	{
		this.points = points == null ? Collections.emptyList() : points;
		this.currentPrice = currentPrice;
		repaint();
	}

	public void setOnTimeframeChange(Consumer<TimeWindow> cb)
	{
		this.onTimeframeChange = cb;
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

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int w = getWidth();
			int h = getHeight();

			int plotTop = (mode == Mode.PRICE ? TAB_BAR_HEIGHT : 0) + TOP_PAD;
			int plotBottom = h - BOTTOM_AXIS_HEIGHT;
			int plotLeft = LEFT_PAD;
			int plotRight = w - RIGHT_AXIS_WIDTH;
			int plotW = Math.max(1, plotRight - plotLeft);
			int plotH = Math.max(1, plotBottom - plotTop);

			if (points.isEmpty())
			{
				g2.setColor(Color.LIGHT_GRAY);
				g2.setFont(FontManager.getRunescapeSmallFont());
				String msg = "No data";
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(msg, plotLeft + (plotW - fm.stringWidth(msg)) / 2,
						plotTop + plotH / 2);
				return;
			}

			long maxVal = Long.MIN_VALUE, minVal = Long.MAX_VALUE;
			long maxTs = Long.MIN_VALUE, minTs = Long.MAX_VALUE;
			long maxVol = 0;
			for (WikiRealtimePriceClient.PricePoint p : points)
			{
				long hp = p.getAvgHighPrice();
				long lp = p.getAvgLowPrice();
				if (hp > 0) maxVal = Math.max(maxVal, hp);
				if (lp > 0) minVal = Math.min(minVal, lp);
				if (hp > 0) minVal = Math.min(minVal, hp);
				if (lp > 0) maxVal = Math.max(maxVal, lp);
				maxTs = Math.max(maxTs, p.getTimestamp());
				minTs = Math.min(minTs, p.getTimestamp());
				maxVol = Math.max(maxVol, p.getHighPriceVolume() + p.getLowPriceVolume());
			}
			if (currentPrice > 0)
			{
				maxVal = Math.max(maxVal, currentPrice);
				minVal = Math.min(minVal, currentPrice);
			}
			if (maxVal == Long.MIN_VALUE || minVal == Long.MAX_VALUE || maxTs == minTs)
			{
				return;
			}
			long range = Math.max(1, maxVal - minVal);

			g2.setStroke(new BasicStroke(1));
			g2.setFont(FontManager.getRunescapeSmallFont());
			FontMetrics fm = g2.getFontMetrics();

			// Gridlines + right axis labels (price = price ticks; volume = volume ticks)
			long axisMax = mode == Mode.PRICE ? maxVal : maxVol;
			long axisRange = mode == Mode.PRICE ? range : Math.max(1, maxVol);
			int gridLines = 4;
			for (int i = 0; i <= gridLines; i++)
			{
				int y = plotTop + (int) ((double) plotH * i / gridLines);
				g2.setColor(GRID_COLOR);
				g2.drawLine(plotLeft, y, plotRight, y);
				long val = axisMax - (axisRange * i / gridLines);
				g2.setColor(Color.GRAY);
				g2.drawString(abbreviate(val), plotRight + 4, y + fm.getAscent() / 2);
			}

			if (mode == Mode.VOLUME)
			{
				if (maxVol > 0)
				{
					g2.setColor(VOLUME_COLOR);
					int barW = Math.max(1, plotW / Math.max(1, points.size()));
					for (WikiRealtimePriceClient.PricePoint p : points)
					{
						double tFrac = (double) (p.getTimestamp() - minTs) / (maxTs - minTs);
						int x = plotLeft + (int) (tFrac * plotW);
						long v = p.getHighPriceVolume() + p.getLowPriceVolume();
						int barH = (int) ((double) v / maxVol * plotH);
						g2.fillRect(x, plotBottom - barH, barW, barH);
					}
				}

				// Bottom time-axis labels
				g2.setColor(Color.GRAY);
				SimpleDateFormat tf = new SimpleDateFormat("MMM d");
				int labels = 4;
				for (int i = 0; i <= labels; i++)
				{
					long ts = minTs + (maxTs - minTs) * i / labels;
					String s = tf.format(new Date(ts * 1000L));
					int x = plotLeft + plotW * i / labels;
					g2.drawString(s, x - fm.stringWidth(s) / 2, plotBottom + 12);
				}

				// Crosshair + readout
				if (hoverX >= plotLeft && hoverX <= plotRight)
				{
					g2.setColor(new Color(255, 255, 255, 120));
					g2.setStroke(new BasicStroke(1));
					g2.drawLine(hoverX, plotTop, hoverX, plotBottom);

					WikiRealtimePriceClient.PricePoint closest = points.get(0);
					int bestDx = Integer.MAX_VALUE;
					for (WikiRealtimePriceClient.PricePoint p : points)
					{
						double tFrac = (double) (p.getTimestamp() - minTs) / (maxTs - minTs);
						int x = plotLeft + (int) (tFrac * plotW);
						int dx = Math.abs(x - hoverX);
						if (dx < bestDx)
						{
							bestDx = dx;
							closest = p;
						}
					}
					String line = "V: " + abbreviate(closest.getHighPriceVolume() + closest.getLowPriceVolume());
					int boxW = fm.stringWidth(line) + 8;
					int boxH = fm.getHeight() + 4;
					int bx = hoverX + 8;
					if (bx + boxW > plotRight) bx = hoverX - 8 - boxW;
					int by = plotTop + 4;
					g2.setColor(new Color(20, 20, 20, 220));
					g2.fillRoundRect(bx, by, boxW, boxH, 6, 6);
					g2.setColor(Color.WHITE);
					g2.drawString(line, bx + 4, by + fm.getAscent() + 2);
				}
				return;
			}

			Path2D highPath = new Path2D.Double();
			Path2D lowPath = new Path2D.Double();
			Path2D avgPath = new Path2D.Double();
			boolean firstH = true, firstL = true, firstA = true;
			long maxAvgVal = Long.MIN_VALUE;
			long minLowVal = Long.MAX_VALUE;
			int maxAvgX = 0, maxAvgY = 0;
			int minLowX = 0, minLowY = 0;
			int lastAvgX = 0, lastAvgY = 0;
			for (WikiRealtimePriceClient.PricePoint p : points)
			{
				double tFrac = (double) (p.getTimestamp() - minTs) / (maxTs - minTs);
				int x = plotLeft + (int) (tFrac * plotW);
				if (p.getAvgHighPrice() > 0)
				{
					int y = plotTop + (int) ((double) (maxVal - p.getAvgHighPrice()) / range * plotH);
					if (firstH) { highPath.moveTo(x, y); firstH = false; }
					else highPath.lineTo(x, y);
					if (p.getAvgHighPrice() > maxAvgVal)
					{
						maxAvgVal = p.getAvgHighPrice();
						maxAvgX = x; maxAvgY = y;
					}
				}
				if (p.getAvgLowPrice() > 0)
				{
					int y = plotTop + (int) ((double) (maxVal - p.getAvgLowPrice()) / range * plotH);
					if (firstL) { lowPath.moveTo(x, y); firstL = false; }
					else lowPath.lineTo(x, y);
					if (p.getAvgLowPrice() < minLowVal)
					{
						minLowVal = p.getAvgLowPrice();
						minLowX = x; minLowY = y;
					}
				}
				long avg = midpoint(p);
				if (avg > 0)
				{
					int y = plotTop + (int) ((double) (maxVal - avg) / range * plotH);
					if (firstA) { avgPath.moveTo(x, y); firstA = false; }
					else avgPath.lineTo(x, y);
					lastAvgX = x; lastAvgY = y;
				}
			}

			g2.setStroke(new BasicStroke(1.5f));
			g2.setColor(COLOR_HIGH);
			g2.draw(highPath);
			g2.setColor(COLOR_LOW);
			g2.draw(lowPath);
			g2.setColor(COLOR_AVG);
			g2.draw(avgPath);

			// Pills for high/low
			if (maxAvgVal != Long.MIN_VALUE)
			{
				drawPill(g2, "↑ H " + abbreviate(maxAvgVal), maxAvgX, maxAvgY - 8, COLOR_HIGH);
			}
			if (minLowVal != Long.MAX_VALUE)
			{
				drawPill(g2, "↓ L " + abbreviate(minLowVal), minLowX, minLowY + 14, COLOR_LOW);
			}

			// Current price dashed line + pill
			if (currentPrice > 0)
			{
				int cy = plotTop + (int) ((double) (maxVal - currentPrice) / range * plotH);
				g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
						10f, new float[]{4f, 4f}, 0f));
				g2.setColor(COLOR_AVG);
				g2.drawLine(plotLeft, cy, plotRight, cy);
				g2.setStroke(new BasicStroke(1));
				g2.fillOval(lastAvgX - 3, lastAvgY - 3, 6, 6);
				drawPill(g2, abbreviate(currentPrice), plotRight - 30, cy, COLOR_AVG);
			}

			// Bottom time-axis labels
			g2.setColor(Color.GRAY);
			SimpleDateFormat tf = new SimpleDateFormat("MMM d");
			int labels = 4;
			for (int i = 0; i <= labels; i++)
			{
				long ts = minTs + (maxTs - minTs) * i / labels;
				String s = tf.format(new Date(ts * 1000L));
				int x = plotLeft + plotW * i / labels;
				g2.drawString(s, x - fm.stringWidth(s) / 2, plotBottom + 12);
			}

			// Crosshair + readout
			if (hoverX >= plotLeft && hoverX <= plotRight && !points.isEmpty())
			{
				g2.setColor(new Color(255, 255, 255, 120));
				g2.setStroke(new BasicStroke(1));
				g2.drawLine(hoverX, plotTop, hoverX, plotBottom);

				WikiRealtimePriceClient.PricePoint closest = points.get(0);
				int bestDx = Integer.MAX_VALUE;
				for (WikiRealtimePriceClient.PricePoint p : points)
				{
					double tFrac = (double) (p.getTimestamp() - minTs) / (maxTs - minTs);
					int x = plotLeft + (int) (tFrac * plotW);
					int dx = Math.abs(x - hoverX);
					if (dx < bestDx)
					{
						bestDx = dx;
						closest = p;
					}
				}
				String[] lines = {
						"H: " + abbreviate(closest.getAvgHighPrice()),
						"L: " + abbreviate(closest.getAvgLowPrice()),
						"A: " + abbreviate(midpoint(closest)),
				};
				int boxW = 0;
				for (String s : lines) boxW = Math.max(boxW, fm.stringWidth(s));
				boxW += 8;
				int boxH = lines.length * (fm.getHeight() + 1) + 4;
				int bx = hoverX + 8;
				if (bx + boxW > plotRight) bx = hoverX - 8 - boxW;
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
		}
		finally
		{
			g2.dispose();
		}
	}

	private static long midpoint(WikiRealtimePriceClient.PricePoint p)
	{
		long h = p.getAvgHighPrice();
		long l = p.getAvgLowPrice();
		if (h > 0 && l > 0) return (h + l) / 2;
		return Math.max(h, l);
	}

	private void drawPill(Graphics2D g2, String text, int x, int y, Color base)
	{
		FontMetrics fm = g2.getFontMetrics();
		int padH = 4, padV = 2;
		int w = fm.stringWidth(text) + padH * 2;
		int h = fm.getHeight();
		int rx = Math.max(0, Math.min(getWidth() - w - 2, x - w / 2));
		int ry = Math.max(0, y - h);
		g2.setColor(base);
		g2.fillRoundRect(rx, ry, w, h, h, h);
		g2.setColor(Color.BLACK);
		g2.drawString(text, rx + padH, ry + fm.getAscent() - padV);
	}

	private static String abbreviate(long v)
	{
		if (v <= 0) return "—";
		if (v >= 1_000_000_000L) return String.format("%.2fB", v / 1_000_000_000.0);
		if (v >= 1_000_000L) return String.format("%.2fM", v / 1_000_000.0);
		if (v >= 1_000L) return String.format("%.1fK", v / 1_000.0);
		return Long.toString(v);
	}
}
