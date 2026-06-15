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

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class ItemTrackerPanel extends PluginPanel
{
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

	private static final Color COLOR_HIGH = new Color(100, 220, 100);
	private static final Color COLOR_LOW  = new Color(220, 100, 100);
	private static final Color COLOR_AVG  = new Color(255, 200, 0);

	private static final Color TINT_HIGH = new Color(35, 70, 35);
	private static final Color TINT_LOW  = new Color(70, 35, 35);
	private static final Color TINT_AVG  = new Color(75, 60, 25);

	private final ItemManager itemManager;
	private final BiConsumer<Integer, TrackItemMode> onAddItem;
	private final Consumer<Integer> onRemoveItem;
	private final Supplier<ValueFormat> totalValueFormatSupplier;
	private final Supplier<PriceDisplay> priceDisplaySupplier;
	private final Supplier<Integer> refreshRateSupplier;
	private final Supplier<Boolean> trackProfitSupplier;
	private final Supplier<Boolean> showTotalsSupplier;
	private final Supplier<List<TimeWindow>> rowWindowsSupplier;
	private final Consumer<Integer> onAcquisitionsEdited;
	private final BiConsumer<Integer, TimeWindow> onRequestSeries;

	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cardsHost = new JPanel(cardLayout);
	private static final String CARD_MAIN = "main";
	private static final String CARD_DETAIL = "detail";

	private final Map<Integer, TrackedItem> currentItems = new HashMap<>();
	private final Map<Integer, ImageIcon> rowIconCache = new HashMap<>();
	private int detailItemId = -1;

	private final JPanel detailCard = new JPanel(new BorderLayout(0, 8));
	private final JLabel detailIconLabel = new JLabel();
	private final JLabel detailNameLabel = new JLabel();
	private final JLabel detailQtyLabel = new JLabel();
	private final JLabel detailEaHighLabel = new JLabel();
	private final JLabel detailEaLowLabel = new JLabel();
	private final JLabel detailEaAvgLabel = new JLabel();
	private final JLabel detailTotalHighLabel = new JLabel();
	private final JLabel detailTotalLowLabel = new JLabel();
	private final JLabel detailTotalAvgLabel = new JLabel();
	private AcquisitionsTableModel acquisitionsModel;
	private JTable acquisitionsTable;
	private JPanel acquisitionsSection;
	private JPanel breakdownGrid;
	private PriceGraphPanel priceGraph;
	private final Map<TimeWindow, JLabel[]> breakdownLabels = new EnumMap<>(TimeWindow.class);
	private static final TimeWindow[] BREAKDOWN_WINDOWS = {
			TimeWindow.H1, TimeWindow.H3, TimeWindow.H6, TimeWindow.H12,
			TimeWindow.H24, TimeWindow.WEEK, TimeWindow.MONTH, TimeWindow.YEAR
	};
	private final IconTextField searchField;
	private final JPanel searchResultsPanel;

	private final JPanel trackedItemsPanel;
	private final JPanel bottomPanel;

	private final JLabel totalHighLabel;
	private final JLabel totalLowLabel;
	private final JLabel totalAvgLabel;
	private final JPanel totalHighRow;
	private final JPanel totalLowRow;
	private final JPanel totalAvgRow;
	private final JLabel lastRefreshLabel;

	private volatile Instant lastPriceRefresh = null;
	private final java.util.Set<Integer> trackedItemIds = new java.util.HashSet<>();

	private int hoveredItemId = -1;
	private final Timer refreshAgeTimer;

	private static final Color LOADING_COLOR = new Color(150, 150, 150);
	private static final long LOADING_GLOW_PERIOD_MS = 2000;
	private static final float LOADING_GLOW_MIN_ALPHA = 0.2f;
	private final List<JLabel> loadingLabels = new ArrayList<>();
	private final Timer loadingGlowTimer;

	private static final long PULSE_DURATION_MS = 1000;
	private static final int PRICES_LEFT_PAD = 20;
	private static final int PRICES_RIGHT_PAD = 0;
	private final List<PulseEntry> pulseEntries = new ArrayList<>();
	private final Timer pulseTimer;
	private final JLabel totalHighDeltaLabel;
	private final JLabel totalLowDeltaLabel;
	private final JLabel totalAvgDeltaLabel;
	private final JLabel coinsIcon;
	private long lastCoinsIconValue = -1;

	private final JLabel profitLabel;
	private final JPanel profitSection;

	private static final class PulseEntry
	{
		final JLabel label;
		final Color base;
		final long start;

		PulseEntry(JLabel label, Color base, long start)
		{
			this.label = label;
			this.base = base;
			this.start = start;
		}
	}

	public ItemTrackerPanel(
			ItemManager itemManager,
			BiConsumer<Integer, TrackItemMode> onAddItem,
			Consumer<Integer> onRemoveItem,
			Supplier<ValueFormat> totalValueFormatSupplier,
			Supplier<PriceDisplay> priceDisplaySupplier,
			Supplier<Integer> refreshRateSupplier,
			Supplier<Boolean> trackProfitSupplier,
			Supplier<Boolean> showTotalsSupplier,
			Supplier<List<TimeWindow>> rowWindowsSupplier,
			Consumer<Integer> onAcquisitionsEdited,
			BiConsumer<Integer, TimeWindow> onRequestSeries)
	{
		this.itemManager = itemManager;
		this.onAddItem = onAddItem;
		this.onRemoveItem = onRemoveItem;
		this.totalValueFormatSupplier = totalValueFormatSupplier;
		this.priceDisplaySupplier = priceDisplaySupplier;
		this.refreshRateSupplier = refreshRateSupplier;
		this.trackProfitSupplier = trackProfitSupplier;
		this.showTotalsSupplier = showTotalsSupplier;
		this.rowWindowsSupplier = rowWindowsSupplier;
		this.onAcquisitionsEdited = onAcquisitionsEdited;
		this.onRequestSeries = onRequestSeries;

		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Item Tracker");
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setBorder(new EmptyBorder(0, 0, 4, 0));

		searchResultsPanel = new JPanel();
		searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
		searchResultsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchResultsPanel.setVisible(false);

		searchField = new IconTextField();
		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.setMinimumSize(new Dimension(0, 30));
		searchField.addClearListener(() -> searchResultsPanel.setVisible(false));
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { onSearch(searchField.getText()); }
			public void removeUpdate(DocumentEvent e) { onSearch(searchField.getText()); }
			public void changedUpdate(DocumentEvent e) { onSearch(searchField.getText()); }
		});

		trackedItemsPanel = new JPanel();
		trackedItemsPanel.setLayout(new BoxLayout(trackedItemsPanel, BoxLayout.Y_AXIS));
		trackedItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel trackedLabel = new JLabel("Tracked Items", SwingConstants.CENTER);
		trackedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		trackedLabel.setFont(FontManager.getRunescapeBoldFont());
		trackedLabel.setBorder(new EmptyBorder(6, 0, 4, 0));

		JPanel trackedLabelWrapper = new JPanel(new BorderLayout());
		trackedLabelWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		trackedLabelWrapper.add(trackedLabel, BorderLayout.CENTER);

		JPanel totalsPanel = new JPanel(new BorderLayout(6, 0));
		totalsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		totalsPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

		coinsIcon = new JLabel();
		coinsIcon.setPreferredSize(new Dimension(32, 32));
		coinsIcon.setVerticalAlignment(SwingConstants.CENTER);
		updateCoinsIcon(0);
		totalsPanel.add(coinsIcon, BorderLayout.WEST);

		JLabel totalsTitle = new JLabel("Estimated GE Sell Value", SwingConstants.CENTER);
		totalsTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalsTitle.setFont(FontManager.getRunescapeBoldFont());
		totalsTitle.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				new EmptyBorder(10, 0, 0, 0),
				new MatteBorder(1, 0, 0, 0, new Color(80, 80, 80))
			),
			new EmptyBorder(10, 0, 12, 0)
		));

		JPanel totalsRows = new JPanel();
		totalsRows.setLayout(new BoxLayout(totalsRows, BoxLayout.Y_AXIS));
		totalsRows.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		totalHighLabel = new JLabel("High:  —");
		totalHighLabel.setForeground(COLOR_HIGH);
		totalHighLabel.setFont(FontManager.getRunescapeSmallFont());

		totalLowLabel = new JLabel("Low:   —");
		totalLowLabel.setForeground(COLOR_LOW);
		totalLowLabel.setFont(FontManager.getRunescapeSmallFont());

		totalAvgLabel = new JLabel("Avg:   —");
		totalAvgLabel.setForeground(COLOR_AVG);
		totalAvgLabel.setFont(FontManager.getRunescapeSmallFont());

		totalHighDeltaLabel = createDeltaLabel();
		totalLowDeltaLabel = createDeltaLabel();
		totalAvgDeltaLabel = createDeltaLabel();

		totalHighRow = buildTotalsRow(totalHighLabel, totalHighDeltaLabel);
		totalLowRow = buildTotalsRow(totalLowLabel, totalLowDeltaLabel);
		totalAvgRow = buildTotalsRow(totalAvgLabel, totalAvgDeltaLabel);

		totalsRows.add(totalHighRow);
		totalsRows.add(totalLowRow);
		totalsRows.add(totalAvgRow);

		JPanel totalsRowsWrapper = new JPanel(new GridBagLayout());
		totalsRowsWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		GridBagConstraints wrapC = new GridBagConstraints();
		wrapC.fill = GridBagConstraints.HORIZONTAL;
		wrapC.weightx = 1;
		totalsRowsWrapper.add(totalsRows, wrapC);
		totalsPanel.add(totalsRowsWrapper, BorderLayout.CENTER);

		lastRefreshLabel = new JLabel("Prices not yet loaded");
		lastRefreshLabel.setForeground(new Color(150, 150, 150));
		lastRefreshLabel.setFont(FontManager.getRunescapeSmallFont());
		lastRefreshLabel.setBorder(new EmptyBorder(4, 0, 0, 0));

		JLabel profitPrefixLabel = new JLabel("Est. Profit:");
		profitPrefixLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		profitPrefixLabel.setFont(FontManager.getRunescapeSmallFont());

		profitLabel = new JLabel("—");
		profitLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		profitLabel.setFont(FontManager.getRunescapeSmallFont());

		JPanel profitRow = new JPanel(new BorderLayout(6, 0));
		profitRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		profitRow.add(profitPrefixLabel, BorderLayout.WEST);
		profitRow.add(profitLabel, BorderLayout.CENTER);

		profitSection = new JPanel(new BorderLayout());
		profitSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		profitSection.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				new EmptyBorder(4, 0, 0, 0),
				new MatteBorder(1, 0, 0, 0, new Color(80, 80, 80))
			),
			new EmptyBorder(4, 0, 0, 0)
		));
		profitSection.add(profitRow, BorderLayout.CENTER);
		profitSection.setVisible(false);
		totalsRows.add(profitSection);

		bottomPanel = new JPanel(new BorderLayout(0, 0));
		bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		bottomPanel.add(totalsTitle, BorderLayout.NORTH);
		bottomPanel.add(totalsPanel, BorderLayout.CENTER);

		JPanel belowTotals = new JPanel();
		belowTotals.setLayout(new BoxLayout(belowTotals, BoxLayout.Y_AXIS));
		belowTotals.setBackground(ColorScheme.DARK_GRAY_COLOR);
		belowTotals.add(lastRefreshLabel);
		bottomPanel.add(belowTotals, BorderLayout.SOUTH);

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topPanel.add(title);
		topPanel.add(searchField);
		topPanel.add(Box.createVerticalStrut(4));
		topPanel.add(searchResultsPanel);
		topPanel.add(trackedLabelWrapper);

		JPanel mainCard = new JPanel(new BorderLayout(0, 8));
		mainCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
		mainCard.add(topPanel, BorderLayout.NORTH);

		JPanel bottomPinned = new JPanel(new BorderLayout());
		bottomPinned.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bottomPinned.add(bottomPanel, BorderLayout.NORTH);

		JPanel itemsAndTotals = new JPanel(new BorderLayout(0, 8));
		itemsAndTotals.setBackground(ColorScheme.DARK_GRAY_COLOR);
		itemsAndTotals.add(trackedItemsPanel, BorderLayout.NORTH);
		itemsAndTotals.add(bottomPinned, BorderLayout.CENTER);
		mainCard.add(itemsAndTotals, BorderLayout.CENTER);

		buildDetailCard();

		cardsHost.setBackground(ColorScheme.DARK_GRAY_COLOR);
		cardsHost.add(mainCard, CARD_MAIN);
		cardsHost.add(detailCard, CARD_DETAIL);
		add(cardsHost, BorderLayout.CENTER);

		refreshAgeTimer = new Timer(1000, e -> updateRefreshLabel());
		refreshAgeTimer.start();

		loadingGlowTimer = new Timer(50, e -> updateLoadingGlow());
		loadingGlowTimer.start();

		pulseTimer = new Timer(25, e -> updatePulses());
		pulseTimer.start();
	}

	public void shutdown()
	{
		refreshAgeTimer.stop();
		loadingGlowTimer.stop();
		pulseTimer.stop();
	}

	private static final Dimension DELTA_LABEL_SIZE = new Dimension(12, 12);

	private void addPriceRow(JPanel pricesPanel, int gridy, JLabel valueLabel, PriceIndicatorMode mode, int delta)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.gridy = gridy;
		c.gridx = 0;
		c.insets = new Insets(3, 6, 3, 0);
		pricesPanel.add(valueLabel, c);

		JLabel deltaLabel = createDeltaLabel();
		pulseIfShown(deltaLabel, delta, mode);
		c.gridx = 1;
		pricesPanel.add(deltaLabel, c);

		c.gridx = 2;
		c.weightx = 1;
		c.insets = new Insets(0, 0, 0, 0);
		pricesPanel.add(Box.createHorizontalGlue(), c);
	}

	private void updateCoinsIcon(long value)
	{
		int quantity = (int) Math.max(1, Math.min(value, Integer.MAX_VALUE));
		if (quantity == lastCoinsIconValue)
		{
			return;
		}
		lastCoinsIconValue = quantity;
		itemManager.getImage(net.runelite.api.gameval.ItemID.COINS, quantity, false).addTo(coinsIcon);
	}

	private void equalizeTotalsLabelWidths()
	{
		JLabel[] labels = {totalHighLabel, totalLowLabel, totalAvgLabel};
		int maxW = 0;
		for (JLabel l : labels)
		{
			l.setPreferredSize(null);
			maxW = Math.max(maxW, l.getPreferredSize().width);
		}
		for (JLabel l : labels)
		{
			Dimension d = l.getPreferredSize();
			Dimension fixed = new Dimension(maxW, d.height);
			l.setPreferredSize(fixed);
			l.setMinimumSize(fixed);
			l.setMaximumSize(fixed);
		}
	}

	private JPanel buildTotalsRow(JLabel valueLabel, JLabel pulseLabel)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 0, 3, 0));
		row.add(valueLabel);
		row.add(Box.createHorizontalStrut(6));
		row.add(pulseLabel);
		row.add(Box.createHorizontalGlue());
		return row;
	}

	private JLabel createDeltaLabel()
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setPreferredSize(DELTA_LABEL_SIZE);
		label.setMinimumSize(DELTA_LABEL_SIZE);
		label.setMaximumSize(DELTA_LABEL_SIZE);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		return label;
	}

	private void pulseIfShown(JLabel label, int delta, PriceIndicatorMode mode)
	{
		if (mode == PriceIndicatorMode.OFF || (mode == PriceIndicatorMode.CHANGE && delta == 0))
		{
			return;
		}
		startPulse(label, delta);
	}

	private void startPulse(JLabel label, int delta)
	{
		label.setText(delta > 0 ? "▲" : delta < 0 ? "▼" : "–");
		Color base = delta > 0 ? COLOR_HIGH : delta < 0 ? COLOR_LOW : LOADING_COLOR;
		label.setForeground(new Color(base.getRed(), base.getGreen(), base.getBlue(), 0));
		pulseEntries.add(new PulseEntry(label, base, System.currentTimeMillis()));
	}

	private void updatePulses()
	{
		if (pulseEntries.isEmpty())
		{
			return;
		}

		long now = System.currentTimeMillis();
		java.util.Iterator<PulseEntry> it = pulseEntries.iterator();
		while (it.hasNext())
		{
			PulseEntry p = it.next();
			long elapsed = now - p.start;
			if (elapsed >= PULSE_DURATION_MS)
			{
				p.label.setText("");
				it.remove();
				continue;
			}

			float alpha = (float) Math.sin(Math.PI * elapsed / PULSE_DURATION_MS);
			p.label.setForeground(new Color(
					p.base.getRed(), p.base.getGreen(), p.base.getBlue(),
					Math.round(alpha * 255)));
		}
	}

	private void updateLoadingGlow()
	{
		if (loadingLabels.isEmpty())
		{
			return;
		}

		double phase = (System.currentTimeMillis() % LOADING_GLOW_PERIOD_MS) / (double) LOADING_GLOW_PERIOD_MS;
		double wave = (Math.sin(phase * 2 * Math.PI) + 1) / 2;
		float alpha = LOADING_GLOW_MIN_ALPHA + (1f - LOADING_GLOW_MIN_ALPHA) * (float) wave;
		Color glow = new Color(
				LOADING_COLOR.getRed(), LOADING_COLOR.getGreen(), LOADING_COLOR.getBlue(),
				Math.round(alpha * 255));

		for (JLabel label : loadingLabels)
		{
			label.setForeground(glow);
		}
	}

	private void updateRefreshLabel()
	{
		if (lastPriceRefresh == null)
		{
			lastRefreshLabel.setText("Prices not yet loaded");
		}
		else
		{
			long secondsAgo = ChronoUnit.SECONDS.between(lastPriceRefresh, Instant.now());
			long rate = Math.max(30, refreshRateSupplier.get());
			long secondsUntil = Math.max(0, rate - secondsAgo);
			lastRefreshLabel.setText("Price refresh in " + secondsUntil + " seconds");
		}
	}

	private void onSearch(String query)
	{
		if (query == null || query.trim().length() < 2)
		{
			searchResultsPanel.setVisible(false);
			return;
		}

		List<ItemPrice> results = itemManager.search(query);
		searchResultsPanel.removeAll();

		int shown = 0;
		for (ItemPrice item : results)
		{
			if (shown >= 5) break;
			if (trackedItemIds.contains(item.getId())) continue;
			JPanel row = buildSearchResultRow(item.getId(), item.getName());
			searchResultsPanel.add(row);
			searchResultsPanel.add(Box.createVerticalStrut(2));
			shown++;
		}

		searchResultsPanel.setVisible(shown > 0);
		searchResultsPanel.revalidate();
		searchResultsPanel.repaint();
	}

	private JPanel buildSearchResultRow(int itemId, String itemName)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel nameLabel = new JLabel(itemName);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());

		JButton viewBtn = new JButton("👁");
		viewBtn.setPreferredSize(new Dimension(28, 22));
		viewBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		viewBtn.setForeground(Color.WHITE);
		viewBtn.setFocusPainted(false);
		viewBtn.setBorderPainted(false);
		viewBtn.setToolTipText("View prices only");
		viewBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		viewBtn.addActionListener(e ->
		{
			onAddItem.accept(itemId, TrackItemMode.VIEW);
			searchField.setText("");
			searchResultsPanel.setVisible(false);
		});

		JButton addBtn = new JButton("+");
		addBtn.setPreferredSize(new Dimension(28, 22));
		addBtn.setBackground(new Color(0, 153, 0));
		addBtn.setForeground(Color.WHITE);
		addBtn.setFocusPainted(false);
		addBtn.setBorderPainted(false);
		addBtn.setToolTipText("Track item");
		addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addBtn.addActionListener(e ->
		{
			onAddItem.accept(itemId, TrackItemMode.TRACK);
			searchField.setText("");
			searchResultsPanel.setVisible(false);
		});

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttonRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttonRow.add(viewBtn);
		buttonRow.add(addBtn);

		row.add(nameLabel, BorderLayout.CENTER);
		row.add(buttonRow, BorderLayout.EAST);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e) { row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR); }

			@Override
			public void mouseExited(MouseEvent e) { row.setBackground(ColorScheme.DARKER_GRAY_COLOR); }
		});
		row.setCursor(Cursor.getDefaultCursor());

		return row;
	}

	public void rebuild(List<TrackedItem> items, Instant newLastPriceRefresh, PriceIndicatorMode indicatorMode)
	{
		this.lastPriceRefresh = newLastPriceRefresh;
		trackedItemIds.clear();
		currentItems.clear();
		for (TrackedItem item : items)
		{
			trackedItemIds.add(item.getItemId());
			currentItems.put(item.getItemId(), item);
		}
		rowIconCache.keySet().retainAll(trackedItemIds);
		if (detailItemId > 0)
		{
			TrackedItem detail = currentItems.get(detailItemId);
			if (detail != null)
			{
				SwingUtilities.invokeLater(() -> populateDetail(detail));
			}
			else
			{
				SwingUtilities.invokeLater(this::showMain);
			}
		}
		SwingUtilities.invokeLater(() ->
		{
			loadingLabels.clear();
			pulseEntries.clear();
			totalHighDeltaLabel.setText("");
			totalLowDeltaLabel.setText("");
			totalAvgDeltaLabel.setText("");
			trackedItemsPanel.removeAll();

			long totalHigh = 0, totalLow = 0, totalAvg = 0;
			long totalCostBasis = 0;
			boolean anyProfitData = false;
			long prevPriceTotalHigh = 0, prevPriceTotalLow = 0, prevPriceTotalAvg = 0;
			boolean anyDeltas = false;
			ValueFormat totalFmt = totalValueFormatSupplier.get();
			PriceDisplay display = priceDisplaySupplier.get();

			if (items.isEmpty())
			{
				PluginErrorPanel errorPanel = new PluginErrorPanel();
				errorPanel.setContent("No items tracked", "Search above to add an item to track.");

				JPanel emptyWrapper = new JPanel(new BorderLayout());
				emptyWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
				emptyWrapper.add(errorPanel, BorderLayout.CENTER);
				trackedItemsPanel.add(emptyWrapper);
			}
			else
			{
				for (TrackedItem item : items)
				{
					totalHigh += item.getHighValue();
					totalLow  += item.getLowValue();
					totalAvg  += item.getAvgValue();
					if (item.isCostBasisInitialized())
					{
						totalCostBasis += item.getCostBasis();
						anyProfitData = true;
					}
					if (item.isHasDeltas())
					{
						anyDeltas = true;
						prevPriceTotalHigh += (long) item.getQuantity() * item.getPrevHighPrice();
						prevPriceTotalLow  += (long) item.getQuantity() * item.getPrevLowPrice();
						prevPriceTotalAvg  += (long) item.getQuantity() * item.getPrevAvgPrice();
					}
					else
					{
						prevPriceTotalHigh += item.getHighValue();
						prevPriceTotalLow  += item.getLowValue();
						prevPriceTotalAvg  += item.getAvgValue();
					}
					trackedItemsPanel.add(buildTrackedItemRow(item, display, indicatorMode));
					trackedItemsPanel.add(Box.createVerticalStrut(4));
				}
			}

			boolean hasPrices = items.stream().anyMatch(TrackedItem::hasPrices);
			boolean showHighLow = display == PriceDisplay.HIGH_LOW || display == PriceDisplay.BOTH;
			boolean showAvg     = display == PriceDisplay.AVERAGE  || display == PriceDisplay.BOTH;

			totalHighRow.setVisible(showHighLow);
			totalLowRow.setVisible(showHighLow);
			totalAvgRow.setVisible(showAvg);

			totalHighLabel.setText("High:  " + (hasPrices ? formatTotalGp(totalHigh, totalFmt) : "—"));
			totalLowLabel.setText( "Low:   " + (hasPrices ? formatTotalGp(totalLow,  totalFmt) : "—"));
			String avgTotalLabel = display == PriceDisplay.AVERAGE ? "Value" : "Avg";
			totalAvgLabel.setText(avgTotalLabel + ":   " + (hasPrices ? formatTotalGp(totalAvg, totalFmt) : "—"));
			if (hasPrices)
			{
				applyTotalTooltip(totalHighLabel, totalHigh, totalFmt);
				applyTotalTooltip(totalLowLabel,  totalLow,  totalFmt);
				applyTotalTooltip(totalAvgLabel,  totalAvg,  totalFmt);
			}
			else
			{
				totalHighLabel.setToolTipText(null);
				totalLowLabel.setToolTipText(null);
				totalAvgLabel.setToolTipText(null);
			}
			equalizeTotalsLabelWidths();
			bottomPanel.setVisible(showTotalsSupplier.get());

			updateCoinsIcon(hasPrices ? totalAvg : 0);

			if (indicatorMode != PriceIndicatorMode.OFF && hasPrices && anyDeltas)
			{
				pulseIfShown(totalHighDeltaLabel, Long.compare(totalHigh, prevPriceTotalHigh), indicatorMode);
				pulseIfShown(totalLowDeltaLabel,  Long.compare(totalLow,  prevPriceTotalLow),  indicatorMode);
				pulseIfShown(totalAvgDeltaLabel,  Long.compare(totalAvg,  prevPriceTotalAvg),  indicatorMode);
			}

			if (anyProfitData && hasPrices && trackProfitSupplier.get())
			{
				long profit = totalAvg - totalCostBasis;
				String sign = profit > 0 ? "+" : "";
				profitLabel.setText(sign + formatTotalGp(profit, totalFmt));
				applyTotalTooltip(profitLabel, profit, totalFmt);
				profitLabel.setForeground(profit == 0 ? ColorScheme.LIGHT_GRAY_COLOR : (profit > 0 ? COLOR_HIGH : COLOR_LOW));
				profitSection.setVisible(true);
			}
			else
			{
				profitSection.setVisible(false);
			}

			trackedItemsPanel.revalidate();
			trackedItemsPanel.repaint();
		});
	}

	private JPanel buildTrackedItemRow(TrackedItem item, PriceDisplay display, PriceIndicatorMode indicatorMode)
	{
		final PriceIndicatorMode itemIndicatorMode = item.isHasDeltas() ? indicatorMode : PriceIndicatorMode.OFF;
		final boolean hovered = item.getItemId() == hoveredItemId;
		final List<TimeWindow> rowWindows = rowWindowsSupplier.get();
		JPanel card = new JPanel(new BorderLayout(0, 0))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 8, 6, 8));

		JLabel iconLabel = new JLabel();
		iconLabel.setVerticalAlignment(SwingConstants.CENTER);
		iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		ImageIcon cached = rowIconCache.get(item.getItemId());
		if (cached != null)
		{
			iconLabel.setIcon(cached);
		}
		else
		{
			AsyncBufferedImage icon = itemManager.getImage(item.getItemId());
			icon.onLoaded(() ->
			{
				ImageIcon scaled = new ImageIcon(icon.getScaledInstance(18, 18, Image.SCALE_SMOOTH));
				rowIconCache.put(item.getItemId(), scaled);
				iconLabel.setIcon(scaled);
			});
		}

		final Color REMOVE_COLOR = new Color(200, 60, 60);
		final Color REMOVE_HIDDEN = new Color(0, 0, 0, 0);
		JButton removeBtn = new JButton("✕");
		removeBtn.setPreferredSize(new Dimension(20, 20));
		removeBtn.setMargin(new Insets(0, 0, 0, 0));
		removeBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		removeBtn.setForeground(hovered ? REMOVE_COLOR : REMOVE_HIDDEN);
		removeBtn.setFont(FontManager.getRunescapeSmallFont());
		removeBtn.setFocusPainted(false);
		removeBtn.setBorderPainted(false);
		removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		removeBtn.setToolTipText("Remove from tracking");
		removeBtn.addActionListener(e -> onRemoveItem.accept(item.getItemId()));

		JPanel eastPanel = new JPanel(new BorderLayout());
		eastPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		eastPanel.add(removeBtn, BorderLayout.NORTH);
		card.add(eastPanel, BorderLayout.EAST);

		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel nameLabel = new JLabel(item.getName());
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());

		JLabel qtyLabel = new JLabel("Qty: " + abbreviateQty(item.getQuantity()));
		qtyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		qtyLabel.setFont(FontManager.getRunescapeSmallFont());
		qtyLabel.setToolTipText(NUMBER_FORMAT.format(item.getQuantity()));

		JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameRow.add(iconLabel);
		nameRow.add(nameLabel);
		nameRow.add(qtyLabel);
		centerPanel.add(nameRow);

		final JLabel highLabel;
		final JLabel lowLabel;
		final JLabel avgLabel;

		if (!item.hasPrices())
		{
			final JLabel loading;
			if (!item.isTradeable())
			{
				loading = new JLabel("Item not tradeable");
				loading.setForeground(new Color(150, 150, 150));
			}
			else if (item.isPriceLoadFailed())
			{
				loading = new JLabel("Unable to load price");
				loading.setForeground(COLOR_LOW);
			}
			else
			{
				loading = new JLabel("Prices loading...");
				loading.setForeground(LOADING_COLOR);
				loadingLabels.add(loading);
			}
			loading.setFont(FontManager.getRunescapeSmallFont());

			JPanel loadingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
			loadingRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			loadingRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			loadingRow.add(loading);
			centerPanel.add(loadingRow);
			highLabel = null;
			lowLabel = null;
			avgLabel = null;
		}
		else
		{
			boolean showHighLow = display == PriceDisplay.HIGH_LOW || display == PriceDisplay.BOTH;
			boolean showAvg     = display == PriceDisplay.AVERAGE  || display == PriceDisplay.BOTH;

			JPanel pricesPanel = new JPanel(new GridBagLayout());
			pricesPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			pricesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			pricesPanel.setBorder(new EmptyBorder(0, PRICES_LEFT_PAD, 0, PRICES_RIGHT_PAD));

			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.insets = new Insets(1, 0, 1, 4);

			int gridy = 0;
			for (TimeWindow window : rowWindows)
			{
				if (window == TimeWindow.NONE)
				{
					continue;
				}
				PriceStats stats = item.getWindowStats().get(window);
				long h, l, a;
				if (window == TimeWindow.LIVE || stats == null)
				{
					h = item.getHighPrice();
					l = item.getLowPrice();
					a = item.getAvgPrice();
				}
				else
				{
					h = stats.getHigh();
					l = stats.getLow();
					a = stats.getAvg();
				}

				JLabel windowLbl = new JLabel(window.toString());
				windowLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				windowLbl.setFont(FontManager.getRunescapeSmallFont());
				c.gridx = 0;
				c.gridy = gridy;
				c.weightx = 0;
				c.anchor = GridBagConstraints.WEST;
				pricesPanel.add(windowLbl, c);

				JLabel[] cols = new JLabel[3];
				for (int i = 0; i < cols.length; i++)
				{
					cols[i] = new JLabel("", SwingConstants.CENTER);
					cols[i].setFont(FontManager.getRunescapeSmallFont());
				}
				int slot = 0;
				if (showHighLow)
				{
					cols[slot].setForeground(COLOR_HIGH);
					installItemValue(cols[slot++], h, "", TINT_HIGH);
					cols[slot].setForeground(COLOR_LOW);
					installItemValue(cols[slot++], l, "", TINT_LOW);
				}
				if (showAvg)
				{
					cols[slot].setForeground(COLOR_AVG);
					installItemValue(cols[slot++], a, "", TINT_AVG);
				}
				int col = 1;
				for (int i = 0; i < cols.length; i++)
				{
					c.gridx = col++;
					c.weightx = 1;
					c.anchor = GridBagConstraints.CENTER;
					pricesPanel.add(cols[i], c);
				}

				// Inline delta pulse on each row (driven by live avg delta).
				JLabel pulse = createDeltaLabel();
				if (itemIndicatorMode != PriceIndicatorMode.OFF)
				{
					pulseIfShown(pulse, item.getAvgDelta(), itemIndicatorMode);
				}
				c.gridx = col;
				c.weightx = 0;
				c.anchor = GridBagConstraints.EAST;
				pricesPanel.add(pulse, c);

				gridy++;
			}

			highLabel = null;
			lowLabel = null;
			avgLabel = null;

			centerPanel.add(pricesPanel);
		}

		card.add(centerPanel, BorderLayout.CENTER);

		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		MouseAdapter hoverListener = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getSource() == removeBtn)
				{
					return;
				}
				showDetail(item.getItemId());
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				hoveredItemId = item.getItemId();
				removeBtn.setForeground(REMOVE_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), card);
				if (!card.contains(p))
				{
					if (hoveredItemId == item.getItemId())
					{
						hoveredItemId = -1;
					}
					removeBtn.setForeground(REMOVE_HIDDEN);
				}
			}
		};
		addListenerRecursively(card, hoverListener);

		return card;
	}

	private void addListenerRecursively(Component c, MouseListener listener)
	{
		c.addMouseListener(listener);
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				addListenerRecursively(child, listener);
			}
		}
	}

	private static String abbreviateQty(int qty)
	{
		if (qty < 1000)
		{
			return Integer.toString(qty);
		}
		double scaled;
		String suffix;
		if (qty >= 1_000_000_000)
		{
			scaled = qty / 1_000_000_000.0;
			suffix = "b";
		}
		else if (qty >= 1_000_000)
		{
			scaled = qty / 1_000_000.0;
			suffix = "m";
		}
		else
		{
			scaled = qty / 1_000.0;
			suffix = "k";
		}
		String s = String.format("%.1f", scaled);
		s = s.replaceAll("\\.0$", "");
		return s + suffix;
	}

	private void installItemValue(JLabel label, long value, String prefix, Color tint)
	{
		String shortText = prefix + formatItemShort(value);
		label.setText(shortText);
		label.setToolTipText(NUMBER_FORMAT.format(value) + " gp");
		for (MouseListener ml : label.getMouseListeners())
		{
			if (ml instanceof HoverTintListener)
			{
				label.removeMouseListener(ml);
			}
		}
		HoverTintListener listener = new HoverTintListener(label, shortText, tint);
		label.addMouseListener(listener);
		SwingUtilities.invokeLater(listener::applyIfHovered);
	}

	private void clearItemValue(JLabel label, String text)
	{
		for (MouseListener ml : label.getMouseListeners())
		{
			if (ml instanceof HoverTintListener)
			{
				label.removeMouseListener(ml);
			}
		}
		label.setToolTipText(null);
		label.setText(text);
	}

	private static String toHex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	private static final class HoverTintListener extends MouseAdapter
	{
		private final JLabel label;
		private final String shortText;
		private final String highlightedText;
		private final Color tint;

		HoverTintListener(JLabel label, String shortText, Color tint)
		{
			this.label = label;
			this.shortText = shortText;
			this.tint = tint;
			this.highlightedText = tint == null ? shortText
					: "<html><nobr><span style='background-color:" + toHex(tint) + "'>" + shortText + "</span></nobr></html>";
		}

		@Override
		public void mouseEntered(MouseEvent e)
		{
			if (tint == null) return;
			label.setText(highlightedText);
		}

		@Override
		public void mouseExited(MouseEvent e)
		{
			if (tint == null) return;
			label.setText(shortText);
		}

		void applyIfHovered()
		{
			if (tint == null || !label.isShowing() || label.getWidth() == 0)
			{
				return;
			}
			PointerInfo info = MouseInfo.getPointerInfo();
			if (info == null) return;
			Point screen = info.getLocation();
			Point origin = label.getLocationOnScreen();
			if (screen.x >= origin.x && screen.x < origin.x + label.getWidth()
					&& screen.y >= origin.y && screen.y < origin.y + label.getHeight())
			{
				label.setText(highlightedText);
			}
		}
	}

	private void applyTotalTooltip(JLabel label, long value, ValueFormat fmt)
	{
		if (fmt == ValueFormat.ABBREVIATED)
		{
			label.setToolTipText(NUMBER_FORMAT.format(value) + " gp");
		}
		else
		{
			label.setToolTipText(null);
		}
	}

	private static String formatItemShort(long value)
	{
		long abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000_000)
		{
			return sign + String.format("%.2fB", abs / 1_000_000_000.0);
		}
		else if (abs >= 1_000_000)
		{
			return sign + String.format("%.2fM", abs / 1_000_000.0);
		}
		else if (abs >= 1_000)
		{
			return sign + String.format("%.1fK", abs / 1_000.0);
		}
		return sign + NUMBER_FORMAT.format(abs);
	}

	private static String formatTotalGp(long value, ValueFormat fmt)
	{
		if (fmt == ValueFormat.FULL)
		{
			return NUMBER_FORMAT.format(value) + " gp";
		}
		long abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000_000)
		{
			return sign + String.format("%.2fB gp", abs / 1_000_000_000.0);
		}
		else if (abs >= 1_000_000)
		{
			return sign + String.format("%.2fM gp", abs / 1_000_000.0);
		}
		else if (abs >= 1_000)
		{
			return sign + String.format("%.1fK gp", abs / 1_000.0);
		}
		return sign + NUMBER_FORMAT.format(abs) + " gp";
	}

	private void buildDetailCard()
	{
		detailCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
		detailCard.setBorder(new EmptyBorder(10, 10, 10, 10));

		JButton backBtn = new JButton("Back", buildLeftArrowIcon());
		backBtn.setIconTextGap(6);
		backBtn.setVerticalAlignment(SwingConstants.CENTER);
		backBtn.setHorizontalAlignment(SwingConstants.CENTER);
		backBtn.setFocusPainted(false);
		backBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		backBtn.setForeground(Color.WHITE);
		backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		backBtn.addActionListener(e -> showMain());

		JPanel headerRow = new JPanel(new BorderLayout(6, 0))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		headerRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerRow.add(backBtn, BorderLayout.WEST);

		detailIconLabel.setPreferredSize(new Dimension(32, 32));
		detailNameLabel.setForeground(Color.WHITE);
		detailNameLabel.setFont(FontManager.getRunescapeBoldFont());
		detailQtyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailQtyLabel.setFont(FontManager.getRunescapeSmallFont());

		JPanel titleRow = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
		titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titleRow.setBorder(new EmptyBorder(16, 0, 0, 0));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		detailIconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
		detailNameLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
		detailQtyLabel.setAlignmentY(Component.CENTER_ALIGNMENT);

		titleRow.add(detailIconLabel);
		titleRow.add(Box.createHorizontalStrut(8));
		titleRow.add(detailNameLabel);
		titleRow.add(Box.createHorizontalStrut(8));
		titleRow.add(detailQtyLabel);
		titleRow.add(Box.createHorizontalGlue());

		JPanel topStack = new JPanel();
		topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
		topStack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topStack.add(headerRow);
		topStack.add(titleRow);
		topStack.add(buildDetailSectionTitle("Price per item", true));
		topStack.add(buildPriceBlock(detailEaHighLabel, detailEaLowLabel, detailEaAvgLabel));
		topStack.add(buildDetailSectionTitle("Total value", false));
		topStack.add(buildPriceBlock(detailTotalHighLabel, detailTotalLowLabel, detailTotalAvgLabel));

		topStack.add(buildDetailSectionTitle("Price breakdown", true));
		topStack.add(buildBreakdownGrid());

		topStack.add(buildDetailSectionTitle("Price graph", true));
		priceGraph = new PriceGraphPanel();
		priceGraph.setAlignmentX(Component.LEFT_ALIGNMENT);
		priceGraph.setOnTimeframeChange(window -> {
			if (detailItemId > 0 && onRequestSeries != null)
			{
				onRequestSeries.accept(detailItemId, window);
			}
		});
		topStack.add(priceGraph);

		detailCard.add(topStack, BorderLayout.NORTH);

		acquisitionsModel = new AcquisitionsTableModel();
		acquisitionsTable = new JTable(acquisitionsModel)
		{
			@Override
			public Dimension getPreferredScrollableViewportSize()
			{
				int visibleRows = Math.max(5, getRowCount() + 1);
				Dimension prefBody = super.getPreferredScrollableViewportSize();
				return new Dimension(prefBody.width, visibleRows * getRowHeight());
			}
		};
		acquisitionsTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		acquisitionsTable.setForeground(Color.WHITE);
		acquisitionsTable.setGridColor(new Color(60, 60, 60));
		acquisitionsTable.setRowHeight(22);
		acquisitionsTable.setFillsViewportHeight(true);
		acquisitionsTable.getTableHeader().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		acquisitionsTable.getTableHeader().setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		applyTableRenderers();
		TableCellRenderer headerRenderer = acquisitionsTable.getTableHeader().getDefaultRenderer();
		if (headerRenderer instanceof DefaultTableCellRenderer)
		{
			((DefaultTableCellRenderer) headerRenderer).setHorizontalAlignment(SwingConstants.CENTER);
		}

		acquisitionsTable.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() != 2 || e.getButton() != MouseEvent.BUTTON1)
				{
					return;
				}
				int row = acquisitionsTable.rowAtPoint(e.getPoint());
				if (row >= 0)
				{
					return;
				}
				TrackedItem t = currentItems.get(detailItemId);
				if (t == null) return;
				long price = t.getAvgPrice() > 0 ? t.getAvgPrice() : 0;
				t.getAcquisitions().add(new AcquisitionRecord(0, price, null));
				acquisitionsModel.fireTableDataChanged();
				acquisitionsTable.revalidate();
				onAcquisitionsEdited.accept(detailItemId);
				int newRow = acquisitionsModel.getRowCount() - 1;
				if (newRow >= 0 && acquisitionsTable.editCellAt(newRow, 0))
				{
					acquisitionsTable.changeSelection(newRow, 0, false, false);
					Component editor = acquisitionsTable.getEditorComponent();
					if (editor != null)
					{
						editor.requestFocusInWindow();
					}
				}
			}
		});

		JScrollPane tableScroll = new JScrollPane(acquisitionsTable);
		tableScroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tableScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));

		JButton addRowBtn = new JButton("+ Add");
		addRowBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		addRowBtn.setForeground(Color.WHITE);
		addRowBtn.setFocusPainted(false);
		addRowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addRowBtn.addActionListener(e -> {
			TrackedItem t = currentItems.get(detailItemId);
			if (t == null) return;
			long price = t.getAvgPrice() > 0 ? t.getAvgPrice() : 0;
			t.getAcquisitions().add(new AcquisitionRecord(0, price, null));
			acquisitionsModel.fireTableDataChanged();
			acquisitionsTable.revalidate();
			onAcquisitionsEdited.accept(detailItemId);
		});

		JButton removeRowBtn = new JButton("− Remove");
		removeRowBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		removeRowBtn.setForeground(Color.WHITE);
		removeRowBtn.setFocusPainted(false);
		removeRowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		removeRowBtn.setEnabled(false);
		removeRowBtn.addActionListener(e -> {
			TrackedItem t = currentItems.get(detailItemId);
			if (t == null) return;
			int row = acquisitionsTable.getSelectedRow();
			if (row < 0 || row >= t.getAcquisitions().size()) return;
			t.getAcquisitions().remove(row);
			acquisitionsModel.fireTableDataChanged();
			acquisitionsTable.revalidate();
			onAcquisitionsEdited.accept(detailItemId);
		});
		acquisitionsTable.getSelectionModel().addListSelectionListener(e -> {
			int row = acquisitionsTable.getSelectedRow();
			removeRowBtn.setEnabled(row >= 0 && row < acquisitionsModel.getRowCount());
		});

		JButton cleanBtn = new JButton(buildBrushIcon());
		cleanBtn.setToolTipText("Remove all rows with quantity 0");
		cleanBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cleanBtn.setForeground(Color.WHITE);
		cleanBtn.setFocusPainted(false);
		cleanBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		cleanBtn.setMargin(new Insets(2, 4, 2, 4));
		cleanBtn.addActionListener(e -> {
			TrackedItem t = currentItems.get(detailItemId);
			if (t == null) return;
			boolean removed = t.getAcquisitions().removeIf(r -> r.getQuantity() == 0);
			if (!removed) return;
			acquisitionsModel.fireTableDataChanged();
			acquisitionsTable.revalidate();
			onAcquisitionsEdited.accept(detailItemId);
		});

		JPanel tableButtons = new JPanel();
		tableButtons.setLayout(new BoxLayout(tableButtons, BoxLayout.X_AXIS));
		tableButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tableButtons.setBorder(new EmptyBorder(4, 0, 4, 0));
		addRowBtn.setAlignmentY(Component.CENTER_ALIGNMENT);
		removeRowBtn.setAlignmentY(Component.CENTER_ALIGNMENT);
		cleanBtn.setAlignmentY(Component.CENTER_ALIGNMENT);
		tableButtons.add(addRowBtn);
		tableButtons.add(Box.createHorizontalGlue());
		tableButtons.add(removeRowBtn);
		tableButtons.add(Box.createHorizontalGlue());
		tableButtons.add(cleanBtn);

		acquisitionsSection = new JPanel(new BorderLayout(0, 4));
		acquisitionsSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		acquisitionsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		acquisitionsSection.add(buildDetailSectionTitle("Item Collection Log", true), BorderLayout.NORTH);
		acquisitionsSection.add(tableScroll, BorderLayout.CENTER);
		acquisitionsSection.add(tableButtons, BorderLayout.SOUTH);

		topStack.add(acquisitionsSection);
	}

	private JPanel buildBreakdownGrid()
	{
		breakdownGrid = new JPanel(new GridBagLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		breakdownGrid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		breakdownGrid.setBorder(new EmptyBorder(6, 8, 6, 8));
		breakdownGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(2, 4, 2, 4);
		c.fill = GridBagConstraints.HORIZONTAL;

		String[] headers = {"", "High", "Low", "Avg"};
		Color[] headerColors = {ColorScheme.LIGHT_GRAY_COLOR, COLOR_HIGH, COLOR_LOW, COLOR_AVG};
		for (int i = 0; i < headers.length; i++)
		{
			JLabel h = new JLabel(headers[i], SwingConstants.CENTER);
			h.setForeground(headerColors[i]);
			h.setFont(FontManager.getRunescapeBoldFont());
			c.gridx = i;
			c.gridy = 0;
			breakdownGrid.add(h, c);
		}

		int y = 1;
		for (TimeWindow w : BREAKDOWN_WINDOWS)
		{
			JLabel windowLbl = new JLabel(w.toString());
			windowLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			windowLbl.setFont(FontManager.getRunescapeSmallFont());
			c.gridx = 0;
			c.gridy = y;
			breakdownGrid.add(windowLbl, c);

			JLabel[] cells = new JLabel[3];
			Color[] colors = {COLOR_HIGH, COLOR_LOW, COLOR_AVG};
			for (int i = 0; i < 3; i++)
			{
				cells[i] = new JLabel("—", SwingConstants.CENTER);
				cells[i].setForeground(colors[i]);
				cells[i].setFont(FontManager.getRunescapeSmallFont());
				c.gridx = i + 1;
				c.gridy = y;
				breakdownGrid.add(cells[i], c);
			}
			breakdownLabels.put(w, cells);
			y++;
		}
		return breakdownGrid;
	}

	public void updateDetailGraph(int itemId, List<WikiRealtimePriceClient.PricePoint> points, long currentPrice)
	{
		if (detailItemId != itemId || priceGraph == null)
		{
			return;
		}
		priceGraph.setData(points, currentPrice);
	}

	private JPanel buildPriceBlock(JLabel highLabel, JLabel lowLabel, JLabel avgLabel)
	{
		JPanel block = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		block.setBorder(new EmptyBorder(6, 8, 6, 8));
		block.setAlignmentX(Component.LEFT_ALIGNMENT);

		highLabel.setForeground(COLOR_HIGH);
		highLabel.setFont(FontManager.getRunescapeSmallFont());
		lowLabel.setForeground(COLOR_LOW);
		lowLabel.setFont(FontManager.getRunescapeSmallFont());
		avgLabel.setForeground(COLOR_AVG);
		avgLabel.setFont(FontManager.getRunescapeSmallFont());

		JPanel highRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		highRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		highRow.add(highLabel);

		JPanel lowRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		lowRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		lowRow.add(lowLabel);

		JPanel avgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		avgRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		avgRow.add(avgLabel);

		block.add(highRow);
		block.add(lowRow);
		block.add(avgRow);
		return block;
	}

	private Icon buildLeftArrowIcon()
	{
		int w = 9;
		int h = 10;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		int midY = h / 2;
		int[] xs = {0, w - 1, w - 1};
		int[] ys = {midY, 0, h - 1};
		g.fillPolygon(xs, ys, xs.length);
		g.dispose();
		return new ImageIcon(img);
	}

	private Icon buildBrushIcon()
	{
		try
		{
			BufferedImage img = net.runelite.client.util.ImageUtil.loadImageResource(getClass(), "broom.png");
			return new ImageIcon(img);
		}
		catch (Exception ex)
		{
			return new ImageIcon(new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB));
		}
	}

	private JLabel buildDetailSectionTitle(String text, boolean withDivider)
	{
		JLabel title = new JLabel(text, SwingConstants.CENTER)
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (withDivider)
		{
			title.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createCompoundBorder(
					new EmptyBorder(10, 0, 0, 0),
					new MatteBorder(1, 0, 0, 0, new Color(80, 80, 80))
				),
				new EmptyBorder(10, 0, 6, 0)
			));
		}
		else
		{
			title.setBorder(new EmptyBorder(10, 0, 6, 0));
		}
		return title;
	}

	private void showDetail(int itemId)
	{
		TrackedItem item = currentItems.get(itemId);
		if (item == null) return;
		detailItemId = itemId;
		populateDetail(item);
		cardLayout.show(cardsHost, CARD_DETAIL);
		if (onRequestSeries != null)
		{
			onRequestSeries.accept(itemId, priceGraph != null ? priceGraph.getActiveWindow() : TimeWindow.H24);
		}
	}

	private void showMain()
	{
		detailItemId = -1;
		cardLayout.show(cardsHost, CARD_MAIN);
	}

	private void populateDetail(TrackedItem item)
	{
		AsyncBufferedImage icon = itemManager.getImage(item.getItemId());
		icon.addTo(detailIconLabel);
		detailNameLabel.setText(item.getName());

		int detailQty = item.getRecordQuantitySum();
		detailQtyLabel.setText("Qty: " + abbreviateQty(detailQty));
		detailQtyLabel.setToolTipText(NUMBER_FORMAT.format(detailQty));

		boolean hasPrices = item.hasPrices();
		if (hasPrices)
		{
			installItemValue(detailEaHighLabel, item.getHighPrice(), "High: ", TINT_HIGH);
			installItemValue(detailEaLowLabel,  item.getLowPrice(),  "Low:  ", TINT_LOW);
			installItemValue(detailEaAvgLabel,  item.getAvgPrice(),  "Avg:  ", TINT_AVG);
			installItemValue(detailTotalHighLabel, (long) detailQty * item.getHighPrice(), "High: ", TINT_HIGH);
			installItemValue(detailTotalLowLabel,  (long) detailQty * item.getLowPrice(),  "Low:  ", TINT_LOW);
			installItemValue(detailTotalAvgLabel,  (long) detailQty * item.getAvgPrice(),  "Avg:  ", TINT_AVG);
		}
		else
		{
			clearItemValue(detailEaHighLabel, "High: —");
			clearItemValue(detailEaLowLabel,  "Low:  —");
			clearItemValue(detailEaAvgLabel,  "Avg:  —");
			clearItemValue(detailTotalHighLabel, "High: —");
			clearItemValue(detailTotalLowLabel,  "Low:  —");
			clearItemValue(detailTotalAvgLabel,  "Avg:  —");
		}

		acquisitionsModel.setItem(item);
		applyTableRenderers();
		acquisitionsTable.revalidate();
		acquisitionsSection.setVisible(item.getMode() != TrackItemMode.VIEW);

		for (TimeWindow w : BREAKDOWN_WINDOWS)
		{
			JLabel[] cells = breakdownLabels.get(w);
			if (cells == null) continue;
			PriceStats s = item.getWindowStats().get(w);
			if (s == null || (s.getHigh() == 0 && s.getLow() == 0 && s.getAvg() == 0))
			{
				clearItemValue(cells[0], "—");
				clearItemValue(cells[1], "—");
				clearItemValue(cells[2], "—");
			}
			else
			{
				installItemValue(cells[0], s.getHigh(), "", TINT_HIGH);
				installItemValue(cells[1], s.getLow(),  "", TINT_LOW);
				installItemValue(cells[2], s.getAvg(),  "", TINT_AVG);
			}
		}

		if (priceGraph != null)
		{
			priceGraph.setData(item.getDetailSeries(), item.getAvgPrice());
		}
	}

	private void applyTableRenderers()
	{
		DefaultTableCellRenderer centerAlign = new DefaultTableCellRenderer();
		centerAlign.setHorizontalAlignment(SwingConstants.CENTER);
		JTextField centerEditorField = new JTextField();
		centerEditorField.setHorizontalAlignment(SwingConstants.CENTER);
		DefaultCellEditor centerEditor = new DefaultCellEditor(centerEditorField);
		int cols = acquisitionsTable.getColumnCount();
		for (int i = 0; i < cols; i++)
		{
			if (i == 3)
			{
				acquisitionsTable.getColumnModel().getColumn(i).setCellRenderer(new ProfitCellRenderer());
			}
			else
			{
				acquisitionsTable.getColumnModel().getColumn(i).setCellRenderer(centerAlign);
				acquisitionsTable.getColumnModel().getColumn(i).setCellEditor(centerEditor);
			}
		}
	}

	private class AcquisitionsTableModel extends AbstractTableModel
	{
		private final String[] COLS_FULL = {"Qty", "Bought at", "Sold at", "Profit"};
		private final String[] COLS_NO_PROFIT = {"Qty", "Bought at", "Sold at"};
		private TrackedItem item;

		void setItem(TrackedItem item)
		{
			this.item = item;
			fireTableStructureChanged();
		}

		private String[] cols()
		{
			return trackProfitSupplier.get() ? COLS_FULL : COLS_NO_PROFIT;
		}

		@Override public int getRowCount() { return item == null ? 0 : item.getAcquisitions().size(); }
		@Override public int getColumnCount() { return cols().length; }
		@Override public String getColumnName(int c) { return cols()[c]; }
		@Override public boolean isCellEditable(int r, int c) { return c < 3; }

		long rowProfit(AcquisitionRecord rec)
		{
			if (rec.getSoldAt() != null)
			{
				return (long) rec.getQuantity() * (rec.getSoldAt() - rec.getBoughtAt());
			}
			if (item != null && item.getLowPrice() > 0)
			{
				return (long) rec.getQuantity() * (item.getLowPrice() - rec.getBoughtAt());
			}
			return 0;
		}

		@Override
		public Object getValueAt(int r, int c)
		{
			AcquisitionRecord rec = item.getAcquisitions().get(r);
			switch (c)
			{
				case 0: return rec.getQuantity();
				case 1: return rec.getBoughtAt();
				case 2: return rec.getSoldAt() == null ? "" : rec.getSoldAt();
				case 3: return rowProfit(rec);
				default: return "";
			}
		}

		@Override
		public void setValueAt(Object value, int r, int c)
		{
			if (item == null || r < 0 || r >= item.getAcquisitions().size()) return;
			AcquisitionRecord rec = item.getAcquisitions().get(r);
			String s = value == null ? "" : value.toString().trim();
			try
			{
				switch (c)
				{
					case 0:
						rec.setQuantity(Math.max(0, Integer.parseInt(s)));
						break;
					case 1:
						rec.setBoughtAt(Math.max(0, Long.parseLong(s)));
						break;
					case 2:
						if (s.isEmpty())
						{
							rec.setSoldAt(null);
						}
						else
						{
							rec.setSoldAt(Math.max(0, Long.parseLong(s)));
						}
						break;
					default:
						return;
				}
				fireTableRowsUpdated(r, r);
				onAcquisitionsEdited.accept(detailItemId);
			}
			catch (NumberFormatException ex)
			{
				// ignore invalid input, leave value unchanged
			}
		}
	}

	private class ProfitCellRenderer extends DefaultTableCellRenderer
	{
		ProfitCellRenderer()
		{
			setHorizontalAlignment(SwingConstants.CENTER);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
				boolean isSelected, boolean hasFocus, int row, int column)
		{
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (value instanceof Number)
			{
				long v = ((Number) value).longValue();
				c.setForeground(v > 0 ? COLOR_HIGH : v < 0 ? COLOR_LOW : Color.WHITE);
				String sign = v > 0 ? "+" : "";
				setText(sign + NUMBER_FORMAT.format(v));
			}
			else
			{
				c.setForeground(Color.WHITE);
			}
			return c;
		}
	}
}
