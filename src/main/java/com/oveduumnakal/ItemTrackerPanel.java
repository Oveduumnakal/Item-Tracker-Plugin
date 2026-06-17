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
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
	private static final Color TINT_VOLUME = new Color(55, 55, 55);

	private final ItemManager itemManager;
	private final ItemTrackerConfig config;
	private final BiConsumer<Integer, TrackItemMode> onAddItem;
	private final Consumer<Integer> onRemoveItem;
	private final Consumer<Integer> onAcquisitionsEdited;
	private final Consumer<Integer> onRequestDetailData;
	private final Consumer<Integer> onClearAcquisitions;

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

	// Item current values section
	private final JLabel icvHigh = new JLabel();
	private final JLabel icvLow = new JLabel();
	private final JLabel icvAvg = new JLabel();
	private final JLabel icvVolume = new JLabel();

	// Collection current values section
	private JPanel ccvSection;
	private final JLabel ccvHigh = new JLabel();
	private final JLabel ccvLow = new JLabel();
	private final JLabel ccvAvg = new JLabel();
	private final JLabel ccvQuantity = new JLabel();
	private final JLabel ccvProfit = new JLabel();

	// Market info section
	private final JLabel miBuyLimit = new JLabel();
	private final JLabel miGeTax = new JLabel();
	private final JLabel miVolatility = new JLabel();
	private final JLabel miLiquidity = new JLabel();
	private PriceRangeBar priceRangeBar;
	private final JLabel rangePositionLabel = new JLabel();

	// Alch section
	private final JLabel haValue = new JLabel();
	private final JLabel haProfit = new JLabel();
	private final JLabel laValue = new JLabel();
	private final JLabel laProfit = new JLabel();
	private final JLabel alchEstProfit = new JLabel();
	private JPanel alchEstProfitRow;

	private long natureRunePrice;
	private long fireRunePrice;

	private AcquisitionsTableModel acquisitionsModel;
	private JTable acquisitionsTable;
	private int acqHoverRow = -1;
	private int acqHoverCol = -1;
	private JScrollPane acquisitionsScroll;
	private JPanel acquisitionsSection;
	private JPanel overviewGrid;
	private PriceGraphPanel priceGraph;
	private PriceGraphPanel volumeGraph;
	private final Map<TimeWindow, JLabel[]> overviewLabels = new EnumMap<>(TimeWindow.class);
	private final List<JLabel> overviewWindowLabels = new ArrayList<>();
	private static final TimeWindow[] OVERVIEW_WINDOWS = {
			TimeWindow.LIVE, TimeWindow.H1, TimeWindow.H3, TimeWindow.H6, TimeWindow.H12,
			TimeWindow.H24, TimeWindow.WEEK, TimeWindow.MONTH, TimeWindow.MONTH3,
			TimeWindow.MONTH6, TimeWindow.YEAR
	};
	private final IconTextField searchField;
	private final JPanel searchResultsPanel;

	private final JPanel trackedItemsPanel;
	private final JPanel bottomPanel;
	private final JLabel totalsTitle;
	private final JPanel geEstimatesSlotTop = new JPanel(new BorderLayout());
	private final JPanel geEstimatesSlotBottom = new JPanel(new BorderLayout());
	private EstimatesPosition currentEstimatesPosition;

	private static final Color DIVIDER_COLOR = new Color(80, 80, 80);
	private static final Color OVERVIEW_ROW_DIVIDER = new Color(45, 45, 45);
	private static final javax.swing.border.Border TITLE_BORDER_WITH_TOP_DIVIDER =
			BorderFactory.createCompoundBorder(
					BorderFactory.createCompoundBorder(
							new EmptyBorder(10, 0, 0, 0),
							new MatteBorder(1, 0, 0, 0, DIVIDER_COLOR)),
					new EmptyBorder(10, 0, 12, 0));
	private static final javax.swing.border.Border TITLE_BORDER_NO_DIVIDER =
			new EmptyBorder(10, 0, 12, 0);

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
	private static final int PRICES_LEFT_PAD = 10;
	private static final int PRICES_RIGHT_PAD = 0;
	private static final Color COLOR_VOLUME = new Color(200, 200, 200);
	private final List<PulseEntry> pulseEntries = new ArrayList<>();
	private final Timer pulseTimer;
	private final JLabel totalHighDeltaLabel;
	private final JLabel totalLowDeltaLabel;
	private final JLabel totalAvgDeltaLabel;
	private final JLabel coinsIcon;
	private long lastCoinsIconValue = -1;
	private final Map<Integer, ImageIcon> coinsIconCache = new HashMap<>();

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
			ItemTrackerConfig config,
			BiConsumer<Integer, TrackItemMode> onAddItem,
			Consumer<Integer> onRemoveItem,
			Consumer<Integer> onAcquisitionsEdited,
			Consumer<Integer> onRequestDetailData,
			Consumer<Integer> onClearAcquisitions)
	{
		this.itemManager = itemManager;
		this.config = config;
		this.onAddItem = onAddItem;
		this.onRemoveItem = onRemoveItem;
		this.onAcquisitionsEdited = onAcquisitionsEdited;
		this.onRequestDetailData = onRequestDetailData;
		this.onClearAcquisitions = onClearAcquisitions;

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

		totalsTitle = new JLabel("Estimated GE Sell Value", SwingConstants.CENTER);
		totalsTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalsTitle.setFont(FontManager.getRunescapeBoldFont());
		totalsTitle.setBorder(TITLE_BORDER_WITH_TOP_DIVIDER);

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

		geEstimatesSlotTop.setBackground(ColorScheme.DARK_GRAY_COLOR);
		geEstimatesSlotBottom.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topPanel.add(title);
		topPanel.add(searchField);
		topPanel.add(Box.createVerticalStrut(4));
		topPanel.add(searchResultsPanel);
		topPanel.add(geEstimatesSlotTop);
		topPanel.add(trackedLabelWrapper);

		JPanel mainCard = new JPanel(new BorderLayout(0, 8));
		mainCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
		mainCard.add(topPanel, BorderLayout.NORTH);

		JPanel itemsAndTotals = new JPanel(new BorderLayout(0, 8));
		itemsAndTotals.setBackground(ColorScheme.DARK_GRAY_COLOR);
		itemsAndTotals.add(trackedItemsPanel, BorderLayout.NORTH);
		itemsAndTotals.add(geEstimatesSlotBottom, BorderLayout.CENTER);
		mainCard.add(itemsAndTotals, BorderLayout.CENTER);

		applyEstimatesPosition(EstimatesPosition.BOTTOM);

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

	private void applyEstimatesPosition(EstimatesPosition position)
	{
		if (position == currentEstimatesPosition && bottomPanel.getParent() != null)
		{
			return;
		}
		currentEstimatesPosition = position;
		geEstimatesSlotTop.removeAll();
		geEstimatesSlotBottom.removeAll();
		if (position == EstimatesPosition.TOP)
		{
			totalsTitle.setBorder(TITLE_BORDER_NO_DIVIDER);
			geEstimatesSlotTop.add(bottomPanel, BorderLayout.CENTER);
			geEstimatesSlotTop.add(buildDividerStrip(), BorderLayout.SOUTH);
		}
		else
		{
			totalsTitle.setBorder(TITLE_BORDER_WITH_TOP_DIVIDER);
			geEstimatesSlotBottom.add(bottomPanel, BorderLayout.NORTH);
		}
		geEstimatesSlotTop.revalidate();
		geEstimatesSlotBottom.revalidate();
		geEstimatesSlotTop.repaint();
		geEstimatesSlotBottom.repaint();
	}

	private JPanel buildDividerStrip()
	{
		JPanel strip = new JPanel(new BorderLayout());
		strip.setBackground(ColorScheme.DARK_GRAY_COLOR);
		strip.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createCompoundBorder(
						new EmptyBorder(10, 0, 0, 0),
						new MatteBorder(1, 0, 0, 0, DIVIDER_COLOR)),
				new EmptyBorder(0, 0, 10, 0)));
		return strip;
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

		ImageIcon cached = coinsIconCache.get(quantity);
		if (cached != null)
		{
			coinsIcon.setIcon(cached);
			return;
		}

		AsyncBufferedImage img = itemManager.getImage(net.runelite.api.gameval.ItemID.COINS, quantity, false);
		img.onLoaded(() ->
		{
			ImageIcon icon = new ImageIcon(img);
			coinsIconCache.put(quantity, icon);
			if (quantity == lastCoinsIconValue)
			{
				coinsIcon.setIcon(icon);
			}
		});
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
			long rate = Math.max(30, config.priceRefreshSeconds());
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
			ValueFormat totalFmt = config.geEstimatesFormat();
			boolean showEstHigh = config.showEstHigh();
			boolean showEstLow = config.showEstLow();
			boolean showEstAvg = config.showEstAvg();
			boolean showEstProfit = config.showEstProfit();

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
					trackedItemsPanel.add(buildTrackedItemRow(item, indicatorMode));
					trackedItemsPanel.add(Box.createVerticalStrut(4));
				}
			}

			boolean hasPrices = items.stream().anyMatch(TrackedItem::hasPrices);

			totalHighRow.setVisible(showEstHigh);
			totalLowRow.setVisible(showEstLow);
			totalAvgRow.setVisible(showEstAvg);

			totalHighLabel.setText("High:  " + (hasPrices ? formatTotalGp(totalHigh, totalFmt) : "—"));
			totalLowLabel.setText( "Low:   " + (hasPrices ? formatTotalGp(totalLow,  totalFmt) : "—"));
			totalAvgLabel.setText( "Avg:   " + (hasPrices ? formatTotalGp(totalAvg, totalFmt) : "—"));
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
			bottomPanel.setVisible(config.showGeEstimates());
			applyEstimatesPosition(config.geEstimatesPosition());

			updateCoinsIcon(hasPrices ? totalAvg : 0);

			if (indicatorMode != PriceIndicatorMode.OFF && hasPrices && anyDeltas)
			{
				pulseIfShown(totalHighDeltaLabel, Long.compare(totalHigh, prevPriceTotalHigh), indicatorMode);
				pulseIfShown(totalLowDeltaLabel,  Long.compare(totalLow,  prevPriceTotalLow),  indicatorMode);
				pulseIfShown(totalAvgDeltaLabel,  Long.compare(totalAvg,  prevPriceTotalAvg),  indicatorMode);
			}

			if (anyProfitData && hasPrices && showEstProfit)
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

	private JPanel buildTrackedItemRow(TrackedItem item, PriceIndicatorMode indicatorMode)
	{
		final PriceIndicatorMode itemIndicatorMode = item.isHasDeltas() ? indicatorMode : PriceIndicatorMode.OFF;
		final boolean hovered = item.getItemId() == hoveredItemId;
		final List<TimeWindow> rowWindows = Arrays.asList(config.row1Data(), config.row2Data(), config.row3Data());
		final boolean showColHigh = config.showColHigh();
		final boolean showColLow = config.showColLow();
		final boolean showColAvg = config.showColAvg();
		final boolean showColVolume = config.showColVolume();
		final boolean showQty = config.showQuantityValue();
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
		if (showQty)
		{
			nameRow.add(qtyLabel);
		}
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
				long h, l, a, vol;
				if (window == TimeWindow.LIVE || stats == null)
				{
					h = item.getHighPrice();
					l = item.getLowPrice();
					a = item.getAvgPrice();
					vol = stats != null ? stats.getVolume() : 0;
				}
				else
				{
					h = stats.getHigh();
					l = stats.getLow();
					a = stats.getAvg();
					vol = stats.getVolume();
				}

				JLabel windowLbl = new JLabel(window.toString());
				windowLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				windowLbl.setFont(FontManager.getRunescapeSmallFont());
				c.gridx = 0;
				c.gridy = gridy;
				c.weightx = 0;
				c.anchor = GridBagConstraints.WEST;
				pricesPanel.add(windowLbl, c);

				// Pack visible columns to the left; fill remaining slots with
				// placeholders so each visible column keeps its share of width.
				List<JLabel> visibleCells = new ArrayList<>(4);
				if (showColHigh)
				{
					JLabel cell = new JLabel("", SwingConstants.CENTER);
					cell.setFont(FontManager.getRunescapeSmallFont());
					cell.setForeground(COLOR_HIGH);
					installItemValue(cell, h, "", "High", TINT_HIGH);
					visibleCells.add(cell);
				}
				if (showColLow)
				{
					JLabel cell = new JLabel("", SwingConstants.CENTER);
					cell.setFont(FontManager.getRunescapeSmallFont());
					cell.setForeground(COLOR_LOW);
					installItemValue(cell, l, "", "Low", TINT_LOW);
					visibleCells.add(cell);
				}
				if (showColAvg)
				{
					JLabel cell = new JLabel("", SwingConstants.CENTER);
					cell.setFont(FontManager.getRunescapeSmallFont());
					cell.setForeground(COLOR_AVG);
					installItemValue(cell, a, "", "Avg", TINT_AVG);
					visibleCells.add(cell);
				}
				if (showColVolume)
				{
					JLabel cell = new JLabel("", SwingConstants.CENTER);
					cell.setForeground(COLOR_VOLUME);
					cell.setFont(FontManager.getRunescapeSmallFont());
					String volText = window == TimeWindow.LIVE ? "-" : formatItemShort(vol);
					cell.setText(volText);
					if (window != TimeWindow.LIVE)
					{
						cell.setToolTipText("Volume: " + NUMBER_FORMAT.format(vol));
					}
					HoverTintListener volListener = new HoverTintListener(cell, volText, TINT_VOLUME);
					cell.addMouseListener(volListener);
					SwingUtilities.invokeLater(volListener::applyIfHovered);
					visibleCells.add(cell);
				}

				int col = 1;
				for (JLabel cell : visibleCells)
				{
					c.gridx = col++;
					c.weightx = 1;
					c.anchor = GridBagConstraints.CENTER;
					pricesPanel.add(cell, c);
				}

				// Inline delta pulse, packed left with the visible columns.
				JLabel pulse = createDeltaLabel();
				if (itemIndicatorMode != PriceIndicatorMode.OFF)
				{
					pulseIfShown(pulse, item.getAvgDelta(), itemIndicatorMode);
				}
				c.gridx = col++;
				c.weightx = 0;
				c.anchor = GridBagConstraints.WEST;
				pricesPanel.add(pulse, c);

				// Trailing placeholders so visible columns keep their widths.
				for (int i = visibleCells.size(); i < 4; i++)
				{
					c.gridx = col++;
					c.weightx = 1;
					c.anchor = GridBagConstraints.CENTER;
					pricesPanel.add(new JLabel(), c);
				}

				gridy++;
			}

			highLabel = null;
			lowLabel = null;
			avgLabel = null;

			centerPanel.add(pricesPanel);

			if (config.showItemProfitRow()
					&& item.isCostBasisInitialized() && item.hasPrices())
			{
				long itemProfit = item.getAvgValue() - item.getCostBasis();
				String sign = itemProfit > 0 ? "+" : "";
				ValueFormat fmt = config.geEstimatesFormat();

				JLabel itemProfitPrefix = new JLabel("Est. Profit:");
				itemProfitPrefix.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				itemProfitPrefix.setFont(FontManager.getRunescapeSmallFont());

				JLabel itemProfitValue = new JLabel(sign + formatTotalGp(itemProfit, fmt));
				itemProfitValue.setFont(FontManager.getRunescapeSmallFont());
				itemProfitValue.setForeground(itemProfit == 0 ? ColorScheme.LIGHT_GRAY_COLOR
						: (itemProfit > 0 ? COLOR_HIGH : COLOR_LOW));
				applyTotalTooltip(itemProfitValue, itemProfit, fmt);

				JPanel itemProfitRow = new JPanel(new BorderLayout(6, 0));
				itemProfitRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				itemProfitRow.add(itemProfitPrefix, BorderLayout.WEST);
				itemProfitRow.add(itemProfitValue, BorderLayout.CENTER);

				JPanel itemProfitSection = new JPanel(new BorderLayout());
				itemProfitSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				itemProfitSection.setAlignmentX(Component.LEFT_ALIGNMENT);
				itemProfitSection.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createCompoundBorder(
								new EmptyBorder(4, 0, 0, 0),
								new MatteBorder(1, 0, 0, 0, new Color(80, 80, 80))
						),
						new EmptyBorder(4, 10, 0, 0)
				));
				itemProfitSection.add(itemProfitRow, BorderLayout.CENTER);
				centerPanel.add(itemProfitSection);
			}
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
		installItemValue(label, value, prefix, null, tint);
	}

	private void installItemValue(JLabel label, long value, String prefix, String tooltipLabel, Color tint)
	{
		String shortText = prefix + formatItemShort(value);
		label.setText(shortText);
		String tooltipPrefix = tooltipLabel == null ? "" : tooltipLabel + ": ";
		label.setToolTipText(tooltipPrefix + NUMBER_FORMAT.format(value) + " gp");
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

	/**
	 * Short format capped to 3 significant figures: 234K, 23.4K, 2.34K, 1.2M …
	 * (never a 4-digit mantissa like 234.5K). Trailing zeros are dropped.
	 */
	private static String formatShort3Sig(long value)
	{
		long abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000_000L)
		{
			return sign + sig3(abs / 1_000_000_000.0) + "B";
		}
		else if (abs >= 1_000_000L)
		{
			return sign + sig3(abs / 1_000_000.0) + "M";
		}
		else if (abs >= 1_000L)
		{
			return sign + sig3(abs / 1_000.0) + "K";
		}
		return sign + NUMBER_FORMAT.format(abs);
	}

	/** Formats a value in [1, 1000) to 3 significant figures, dropping trailing zeros. */
	private static String sig3(double d)
	{
		String s;
		if (d >= 100)
		{
			s = String.format(Locale.US, "%.0f", d);
		}
		else if (d >= 10)
		{
			s = String.format(Locale.US, "%.1f", d);
		}
		else
		{
			s = String.format(Locale.US, "%.2f", d);
		}
		if (s.contains("."))
		{
			s = s.replaceAll("0+$", "");
			if (s.endsWith("."))
			{
				s = s.substring(0, s.length() - 1);
			}
		}
		return s;
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
		detailIconLabel.setVerticalAlignment(SwingConstants.CENTER);
		detailNameLabel.setForeground(Color.WHITE);
		detailNameLabel.setFont(FontManager.getRunescapeBoldFont());
		detailQtyLabel.setForeground(Color.WHITE);
		detailQtyLabel.setFont(FontManager.getRunescapeSmallFont());

		// Title: icon on the left, name over Qty stacked to its right.
		JPanel titleTextStack = new JPanel();
		titleTextStack.setLayout(new BoxLayout(titleTextStack, BoxLayout.Y_AXIS));
		titleTextStack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		detailNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		detailQtyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleTextStack.add(detailNameLabel);
		titleTextStack.add(Box.createVerticalStrut(2));
		titleTextStack.add(detailQtyLabel);

		JPanel titleRow = new JPanel(new BorderLayout(8, 0))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titleRow.setBorder(new EmptyBorder(16, 0, 0, 0));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.add(detailIconLabel, BorderLayout.WEST);
		titleRow.add(titleTextStack, BorderLayout.CENTER);

		JPanel topStack = new JPanel();
		topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
		topStack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topStack.add(headerRow);
		topStack.add(titleRow);

		topStack.add(buildDetailSectionTitle("Item Current Values", true));
		topStack.add(buildCurrentValuesBlock(icvHigh, icvLow, icvAvg, icvVolume, null));

		ccvSection = new JPanel();
		ccvSection.setLayout(new BoxLayout(ccvSection, BoxLayout.Y_AXIS));
		ccvSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		ccvSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		ccvSection.add(buildDetailSectionTitle("Collection Current Values", true));
		ccvSection.add(buildCurrentValuesBlock(ccvHigh, ccvLow, ccvAvg, ccvQuantity, ccvProfit));
		topStack.add(ccvSection);

		topStack.add(buildDetailSectionTitle("Price Overview", true));
		topStack.add(buildOverviewGrid());

		topStack.add(buildDetailSectionTitle("Price Graph", true));
		priceGraph = new PriceGraphPanel(PriceGraphPanel.Mode.PRICE);
		priceGraph.setAlignmentX(Component.LEFT_ALIGNMENT);
		topStack.add(priceGraph);

		topStack.add(Box.createVerticalStrut(4));

		topStack.add(buildDetailSectionTitle("Volume Graph", true));
		volumeGraph = new PriceGraphPanel(PriceGraphPanel.Mode.VOLUME);
		volumeGraph.setAlignmentX(Component.LEFT_ALIGNMENT);
		topStack.add(volumeGraph);

		topStack.add(buildDetailSectionTitle("Market Info", true));
		topStack.add(buildMarketInfoBlock());

		topStack.add(buildDetailSectionTitle("Alchemy Info", true));
		topStack.add(buildAlchBlock());

		detailCard.add(topStack, BorderLayout.NORTH);

		acquisitionsModel = new AcquisitionsTableModel();
		acquisitionsTable = new JTable(acquisitionsModel)
		{
			@Override
			public Dimension getPreferredScrollableViewportSize()
			{
				int visibleRows = Math.min(10, Math.max(5, getRowCount() + 1));
				Dimension prefBody = super.getPreferredScrollableViewportSize();
				return new Dimension(prefBody.width, visibleRows * getRowHeight());
			}

			@Override
			public String getToolTipText(MouseEvent e)
			{
				int row = rowAtPoint(e.getPoint());
				int col = columnAtPoint(e.getPoint());
				if (row < 0 || col < 0)
				{
					return null;
				}
				Object val = getValueAt(row, col);
				if (!(val instanceof Number))
				{
					return null;
				}
				long v = ((Number) val).longValue();
				// Only the short-form (overflowing) cells carry a full-value tooltip.
				if (Math.abs(v) < (col == 3 ? 1000 : 10000))
				{
					return null;
				}
				return acqTooltipLabel(col) + ": " + NUMBER_FORMAT.format(v);
			}

			@Override
			protected JTableHeader createDefaultTableHeader()
			{
				return new JTableHeader(columnModel)
				{
					@Override
					public String getToolTipText(MouseEvent e)
					{
						int col = columnAtPoint(e.getPoint());
						return col < 0 ? null : acqTooltipLabel(convertColumnIndexToModel(col));
					}
				};
			}
		};
		acquisitionsTable.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int r = acquisitionsTable.rowAtPoint(e.getPoint());
				int c = acquisitionsTable.columnAtPoint(e.getPoint());
				if (r != acqHoverRow || c != acqHoverCol)
				{
					acqHoverRow = r;
					acqHoverCol = c;
					acquisitionsTable.repaint();
				}
			}
		});
		acquisitionsTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		acquisitionsTable.setForeground(Color.WHITE);
		acquisitionsTable.setGridColor(new Color(60, 60, 60));
		acquisitionsTable.setRowHeight(22);
		acquisitionsTable.setFillsViewportHeight(true);
		acquisitionsTable.getTableHeader().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		acquisitionsTable.getTableHeader().setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		acquisitionsTable.getTableHeader().setFont(FontManager.getRunescapeSmallFont());
		applyTableRenderers();
		TableCellRenderer headerRenderer = acquisitionsTable.getTableHeader().getDefaultRenderer();
		if (headerRenderer instanceof DefaultTableCellRenderer)
		{
			((DefaultTableCellRenderer) headerRenderer).setHorizontalAlignment(SwingConstants.CENTER);
		}

		acquisitionsTable.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				if (acqHoverRow != -1 || acqHoverCol != -1)
				{
					acqHoverRow = -1;
					acqHoverCol = -1;
					acquisitionsTable.repaint();
				}
			}

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
				scrollAcquisitionsToBottom();
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
		acquisitionsScroll = tableScroll;

		JButton addRowBtn = new JButton("+ Add");
		addRowBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		addRowBtn.setForeground(Color.WHITE);
		addRowBtn.setFocusPainted(false);
		addRowBtn.setFont(FontManager.getRunescapeSmallFont());
		addRowBtn.setMargin(new Insets(2, 5, 2, 5));
		addRowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addRowBtn.addActionListener(e -> {
			TrackedItem t = currentItems.get(detailItemId);
			if (t == null) return;
			long price = t.getAvgPrice() > 0 ? t.getAvgPrice() : 0;
			t.getAcquisitions().add(new AcquisitionRecord(0, price, null));
			acquisitionsModel.fireTableDataChanged();
			acquisitionsTable.revalidate();
			onAcquisitionsEdited.accept(detailItemId);
			scrollAcquisitionsToBottom();
		});

		JButton removeRowBtn = new JButton("− Remove");
		removeRowBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		removeRowBtn.setForeground(Color.WHITE);
		removeRowBtn.setFocusPainted(false);
		removeRowBtn.setFont(FontManager.getRunescapeSmallFont());
		removeRowBtn.setMargin(new Insets(2, 5, 2, 5));
		removeRowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		removeRowBtn.setEnabled(false);

		Runnable removeSelectedRows = () -> {
			TrackedItem t = currentItems.get(detailItemId);
			if (t == null) return;
			if (acquisitionsTable.isEditing())
			{
				acquisitionsTable.getCellEditor().stopCellEditing();
			}
			int[] selected = acquisitionsTable.getSelectedRows();
			if (selected.length == 0) return;
			List<AcquisitionRecord> records = t.getAcquisitions();
			java.util.Arrays.sort(selected);
			for (int i = selected.length - 1; i >= 0; i--)
			{
				int idx = selected[i];
				if (idx >= 0 && idx < records.size())
				{
					records.remove(idx);
				}
			}
			acquisitionsModel.fireTableDataChanged();
			acquisitionsTable.revalidate();
			onAcquisitionsEdited.accept(detailItemId);
		};

		removeRowBtn.addActionListener(e -> removeSelectedRows.run());
		acquisitionsTable.getSelectionModel().addListSelectionListener(e ->
				removeRowBtn.setEnabled(acquisitionsTable.getSelectedRowCount() > 0));

		acquisitionsTable.getInputMap(JComponent.WHEN_FOCUSED)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelectedRows");
		acquisitionsTable.getActionMap().put("deleteSelectedRows", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				removeSelectedRows.run();
			}
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

		JButton clearBtn = new JButton("Clear");
		clearBtn.setToolTipText("Remove every row from the collection log");
		clearBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clearBtn.setForeground(new Color(220, 100, 100));
		clearBtn.setFocusPainted(false);
		clearBtn.setFont(FontManager.getRunescapeSmallFont());
		clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		clearBtn.setMargin(new Insets(2, 5, 2, 5));
		clearBtn.addActionListener(e -> {
			TrackedItem t = currentItems.get(detailItemId);
			if (t == null || t.getAcquisitions().isEmpty()) return;
			int choice = JOptionPane.showConfirmDialog(
					ItemTrackerPanel.this,
					"Clear the entire collection log for this item?",
					"Clear Collection Log",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.YES_OPTION) return;
			if (acquisitionsTable.isEditing())
			{
				acquisitionsTable.getCellEditor().cancelCellEditing();
			}
			if (onClearAcquisitions != null)
			{
				onClearAcquisitions.accept(detailItemId);
			}
		});

		// Give all four buttons a uniform height (the broom's icon otherwise makes it taller).
		JButton[] logButtons = {addRowBtn, removeRowBtn, cleanBtn, clearBtn};
		int btnHeight = 0;
		for (JButton b : logButtons)
		{
			btnHeight = Math.max(btnHeight, b.getPreferredSize().height);
		}
		for (JButton b : logButtons)
		{
			b.setPreferredSize(new Dimension(b.getPreferredSize().width, btnHeight));
		}

		JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tableButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tableButtons.setBorder(new EmptyBorder(4, 0, 4, 0));
		tableButtons.add(addRowBtn);
		tableButtons.add(removeRowBtn);
		tableButtons.add(cleanBtn);
		tableButtons.add(clearBtn);

		acquisitionsSection = new JPanel(new BorderLayout(0, 4));
		acquisitionsSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		acquisitionsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		acquisitionsSection.add(buildDetailSectionTitle("Item Collection Log", true), BorderLayout.NORTH);
		acquisitionsSection.add(tableScroll, BorderLayout.CENTER);
		acquisitionsSection.add(tableButtons, BorderLayout.SOUTH);

		topStack.add(acquisitionsSection);
	}

	public int getDetailItemId()
	{
		return detailItemId;
	}

	public void setAlchRunePrices(long naturePrice, long firePrice)
	{
		this.natureRunePrice = naturePrice;
		this.fireRunePrice = firePrice;
	}

	/** Re-runs detail population when the given item is the one on screen. */
	public void refreshDetailData(int itemId)
	{
		if (detailItemId != itemId)
		{
			return;
		}
		TrackedItem item = currentItems.get(itemId);
		if (item != null)
		{
			populateDetail(item);
		}
	}

	private void scrollAcquisitionsToBottom()
	{
		if (acquisitionsScroll == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			JScrollBar bar = acquisitionsScroll.getVerticalScrollBar();
			bar.setValue(bar.getMaximum());
		});
	}

	/**
	 * Builds a GE-Estimates-styled block of stacked "Label: value" rows.
	 * The fourth row is grey (volume / quantity); when {@code profit} is
	 * supplied it is appended below a divider, matching the GE Estimates view.
	 */
	private JPanel buildCurrentValuesBlock(JLabel high, JLabel low, JLabel avg, JLabel fourth, JLabel profit)
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

		high.setForeground(COLOR_HIGH);
		low.setForeground(COLOR_LOW);
		avg.setForeground(COLOR_AVG);
		fourth.setForeground(COLOR_VOLUME);
		for (JLabel l : new JLabel[]{high, low, avg, fourth})
		{
			l.setFont(FontManager.getRunescapeSmallFont());
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.add(l);
			block.add(row);
		}

		if (profit != null)
		{
			profit.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			profit.setFont(FontManager.getRunescapeSmallFont());
			JPanel profitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			profitRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			profitRow.add(profit);
			profitRow.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createCompoundBorder(
							new EmptyBorder(4, 0, 0, 0),
							new MatteBorder(1, 0, 0, 0, new Color(80, 80, 80))),
					new EmptyBorder(4, 0, 0, 0)));
			block.add(profitRow);
		}
		return block;
	}

	private JPanel buildOverviewGrid()
	{
		overviewGrid = new JPanel(new GridBagLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				JLabel[] firstRow = overviewLabels.get(OVERVIEW_WINDOWS[0]);
				if (firstRow == null || firstRow[0] == null)
				{
					return;
				}
				// Vertical separator 2px to the right of the label column.
				int vx = firstRow[0].getX() - 3;
				if (!overviewWindowLabels.isEmpty())
				{
					JLabel ref = overviewWindowLabels.get(0);
					vx = ref.getX() + ref.getWidth() + 2;
				}
				g.setColor(DIVIDER_COLOR);
				g.drawLine(vx, 4, vx, getHeight() - 4);

				// Fainter horizontal separators between each data row.
				g.setColor(OVERVIEW_ROW_DIVIDER);
				JLabel prev = null;
				for (TimeWindow w : OVERVIEW_WINDOWS)
				{
					JLabel[] cells = overviewLabels.get(w);
					if (cells == null || cells[0] == null)
					{
						continue;
					}
					JLabel cur = cells[0];
					if (prev != null)
					{
						int y = (prev.getY() + prev.getHeight() + cur.getY()) / 2;
						g.drawLine(2, y, getWidth() - 2, y);
					}
					prev = cur;
				}
			}
		};
		overviewGrid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		overviewGrid.setBorder(new EmptyBorder(6, 3, 2, 3));
		overviewGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 1, 2, 1);
		c.fill = GridBagConstraints.HORIZONTAL;

		String[] headers = {"", "High", "Low", "Avg", "Δ%", "Vol"};
		Color[] headerColors = {
				ColorScheme.LIGHT_GRAY_COLOR, COLOR_HIGH, COLOR_LOW, COLOR_AVG,
				ColorScheme.LIGHT_GRAY_COLOR, COLOR_VOLUME};
		for (int i = 0; i < headers.length; i++)
		{
			JLabel h = new JLabel(headers[i], SwingConstants.CENTER);
			h.setForeground(headerColors[i]);
			h.setFont(FontManager.getRunescapeSmallFont());
			c.gridx = i;
			c.gridy = 0;
			c.weightx = i == 0 ? 0 : 1;
			// Extra left inset on the High column leaves 1px between it and the separator.
			c.insets = new Insets(2, i == 1 ? 3 : 1, 2, 1);
			overviewGrid.add(h, c);
		}

		// 1px divider spanning the full width beneath the header row.
		JPanel headerDivider = new JPanel();
		headerDivider.setBackground(DIVIDER_COLOR);
		headerDivider.setPreferredSize(new Dimension(0, 1));
		headerDivider.setMinimumSize(new Dimension(0, 1));
		GridBagConstraints dc = new GridBagConstraints();
		dc.gridx = 0;
		dc.gridy = 1;
		dc.gridwidth = headers.length;
		dc.weightx = 1;
		dc.fill = GridBagConstraints.HORIZONTAL;
		dc.insets = new Insets(1, 1, 2, 1);
		overviewGrid.add(headerDivider, dc);

		int y = 2;
		Color[] cellColors = {COLOR_HIGH, COLOR_LOW, COLOR_AVG, COLOR_VOLUME, COLOR_VOLUME};
		for (TimeWindow w : OVERVIEW_WINDOWS)
		{
			JLabel windowLbl = new JLabel(w == TimeWindow.LIVE ? "5m" : w.toString(), SwingConstants.RIGHT);
			windowLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			windowLbl.setFont(FontManager.getRunescapeSmallFont());
			c.gridx = 0;
			c.gridy = y;
			c.weightx = 0;
			c.anchor = GridBagConstraints.EAST;
			overviewGrid.add(windowLbl, c);
			overviewWindowLabels.add(windowLbl);

			JLabel[] cells = new JLabel[5];
			for (int i = 0; i < 5; i++)
			{
				cells[i] = new JLabel("—", SwingConstants.CENTER);
				cells[i].setForeground(cellColors[i]);
				cells[i].setFont(FontManager.getRunescapeSmallFont());
				c.gridx = i + 1;
				c.gridy = y;
				c.weightx = 1;
				c.anchor = GridBagConstraints.CENTER;
				c.insets = new Insets(2, i == 0 ? 3 : 1, 2, 1);
				overviewGrid.add(cells[i], c);
			}
			overviewLabels.put(w, cells);
			y++;
		}
		return overviewGrid;
	}

	private JPanel buildMarketInfoBlock()
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

		miBuyLimit.setHorizontalAlignment(SwingConstants.CENTER);
		miGeTax.setHorizontalAlignment(SwingConstants.CENTER);
		miVolatility.setHorizontalAlignment(SwingConstants.CENTER);
		miLiquidity.setHorizontalAlignment(SwingConstants.CENTER);

		block.add(buildMarketInfoPair("Buy Limit", miBuyLimit, "GE Tax", miGeTax));
		block.add(Box.createVerticalStrut(6));
		block.add(buildMarketInfoPair("Volatility", miVolatility, "Liquidity", miLiquidity));

		// Faint separator above the 30 Day Price Range sub-section.
		block.add(Box.createVerticalStrut(8));
		JPanel rangeSep = new JPanel();
		rangeSep.setBackground(OVERVIEW_ROW_DIVIDER);
		rangeSep.setAlignmentX(Component.LEFT_ALIGNMENT);
		rangeSep.setPreferredSize(new Dimension(0, 1));
		rangeSep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		block.add(rangeSep);
		block.add(Box.createVerticalStrut(8));

		JLabel rangeTitle = new JLabel("30 Day Price Range", SwingConstants.CENTER);
		rangeTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		rangeTitle.setFont(FontManager.getRunescapeSmallFont());
		rangeTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		rangeTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, rangeTitle.getPreferredSize().height));
		block.add(rangeTitle);
		block.add(Box.createVerticalStrut(4));

		rangePositionLabel.setHorizontalAlignment(SwingConstants.CENTER);
		rangePositionLabel.setFont(FontManager.getRunescapeSmallFont());
		rangePositionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		rangePositionLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
				rangePositionLabel.getPreferredSize().height));
		block.add(rangePositionLabel);
		block.add(Box.createVerticalStrut(3));

		priceRangeBar = new PriceRangeBar();
		block.add(priceRangeBar);
		return block;
	}

	private JPanel buildMarketInfoPair(String leftLabel, JLabel leftValue, String rightLabel, JLabel rightValue)
	{
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.insets = new Insets(1, 4, 1, 4);

		JLabel lh = new JLabel(leftLabel, SwingConstants.CENTER);
		lh.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lh.setFont(FontManager.getRunescapeSmallFont());
		JLabel rh = new JLabel(rightLabel, SwingConstants.CENTER);
		rh.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		rh.setFont(FontManager.getRunescapeSmallFont());

		leftValue.setFont(FontManager.getRunescapeSmallFont());
		leftValue.setForeground(Color.WHITE);
		rightValue.setFont(FontManager.getRunescapeSmallFont());
		rightValue.setForeground(Color.WHITE);

		c.gridx = 0; c.gridy = 0; grid.add(lh, c);
		c.gridx = 1; c.gridy = 0; grid.add(rh, c);
		c.gridx = 0; c.gridy = 1; grid.add(leftValue, c);
		c.gridx = 1; c.gridy = 1; grid.add(rightValue, c);
		return grid;
	}

	private JPanel buildAlchBlock()
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

		haValue.setHorizontalAlignment(SwingConstants.CENTER);
		haProfit.setHorizontalAlignment(SwingConstants.CENTER);
		laValue.setHorizontalAlignment(SwingConstants.CENTER);
		laProfit.setHorizontalAlignment(SwingConstants.CENTER);

		block.add(buildMarketInfoPair("High Alchemy Value", haValue, "Profit", haProfit));
		block.add(Box.createVerticalStrut(6));
		block.add(buildMarketInfoPair("Low Alchemy Value", laValue, "Profit", laProfit));

		JLabel estPrefix = new JLabel("Est. Profit:");
		estPrefix.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		estPrefix.setFont(FontManager.getRunescapeSmallFont());
		alchEstProfit.setFont(FontManager.getRunescapeSmallFont());
		alchEstProfit.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		alchEstProfitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		alchEstProfitRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		alchEstProfitRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		alchEstProfitRow.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createCompoundBorder(
						new EmptyBorder(6, 0, 0, 0),
						new MatteBorder(1, 0, 0, 0, new Color(80, 80, 80))),
				new EmptyBorder(4, 0, 0, 0)));
		alchEstProfitRow.add(estPrefix);
		alchEstProfitRow.add(alchEstProfit);
		block.add(alchEstProfitRow);
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
			return new ImageIcon(img.getScaledInstance(12, 12, Image.SCALE_SMOOTH));
		}
		catch (Exception ex)
		{
			return new ImageIcon(new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB));
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
		if (onRequestDetailData != null)
		{
			onRequestDetailData.accept(itemId);
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

		int detailQty = item.getQuantity();
		detailQtyLabel.setText("Qty: " + abbreviateQty(detailQty));
		detailQtyLabel.setToolTipText(NUMBER_FORMAT.format(detailQty));

		final boolean hasPrices = item.hasPrices();
		final ValueFormat full = ValueFormat.FULL;

		// --- Item Current Values ---
		icvHigh.setText("High: " + (hasPrices ? formatTotalGp(item.getHighPrice(), full) : "—"));
		icvLow.setText("Low: " + (hasPrices ? formatTotalGp(item.getLowPrice(), full) : "—"));
		icvAvg.setText("Average: " + (hasPrices ? formatTotalGp(item.getAvgPrice(), full) : "—"));
		long vol24 = windowVolume(item, TimeWindow.H24);
		icvVolume.setText("Volume (24h): " + (vol24 > 0 ? NUMBER_FORMAT.format(vol24) : "—"));

		// --- Collection Current Values ---
		int colQty = item.getRecordQuantitySum();
		ccvSection.setVisible(colQty > 0);
		if (colQty > 0)
		{
			long cHigh = (long) colQty * item.getHighPrice();
			long cLow = (long) colQty * item.getLowPrice();
			long cAvg = (long) colQty * item.getAvgPrice();
			ccvHigh.setText("High: " + (hasPrices ? formatTotalGp(cHigh, full) : "—"));
			ccvLow.setText("Low: " + (hasPrices ? formatTotalGp(cLow, full) : "—"));
			ccvAvg.setText("Average: " + (hasPrices ? formatTotalGp(cAvg, full) : "—"));
			ccvQuantity.setText("Quantity: " + NUMBER_FORMAT.format(colQty));
			long estProfit = cAvg - item.getCostBasis();
			String sign = estProfit > 0 ? "+" : "";
			ccvProfit.setText("Est. Profit: " + (hasPrices ? sign + formatTotalGp(estProfit, full) : "—"));
			ccvProfit.setForeground(!hasPrices || estProfit == 0 ? ColorScheme.LIGHT_GRAY_COLOR
					: (estProfit > 0 ? COLOR_HIGH : COLOR_LOW));
		}

		// --- Price Overview ---
		for (TimeWindow w : OVERVIEW_WINDOWS)
		{
			JLabel[] cells = overviewLabels.get(w);
			if (cells == null) continue;

			if (w == TimeWindow.LIVE)
			{
				// First row shows the latest 5-minute bucket rather than live prices.
				List<WikiRealtimePriceClient.PricePoint> s5 = item.getSeries5m();
				WikiRealtimePriceClient.PricePoint last = s5.isEmpty() ? null : s5.get(s5.size() - 1);
				long high = last == null ? 0 : last.getAvgHighPrice();
				long low = last == null ? 0 : last.getAvgLowPrice();
				long avg = last == null ? 0 : overviewMidpoint(last);
				setPriceCell(cells[0], high, COLOR_HIGH, "High", TINT_HIGH);
				setPriceCell(cells[1], low, COLOR_LOW, "Low", TINT_LOW);
				setPriceCell(cells[2], avg, COLOR_AVG, "Avg", TINT_AVG);
				setOverviewPlaceholder(cells[3]); // Δ% not meaningful for the 5m row
				if (last == null)
				{
					setOverviewPlaceholder(cells[4]);
				}
				else
				{
					installVolumeValue(cells[4], last.getHighPriceVolume() + last.getLowPriceVolume());
				}
				continue;
			}

			PriceStats s = item.getWindowStats().get(w);
			setPriceCell(cells[0], s == null ? 0 : s.getHigh(), COLOR_HIGH, "High", TINT_HIGH);
			setPriceCell(cells[1], s == null ? 0 : s.getLow(), COLOR_LOW, "Low", TINT_LOW);
			setPriceCell(cells[2], s == null ? 0 : s.getAvg(), COLOR_AVG, "Avg", TINT_AVG);
			applyDeltaPct(cells[3], item, w);

			// Vol shows the real count, including 0 when nothing traded in the window.
			if (s == null)
			{
				setOverviewPlaceholder(cells[4]);
			}
			else
			{
				installVolumeValue(cells[4], s.getVolume());
			}
		}

		// --- Graphs ---
		if (priceGraph != null)
		{
			priceGraph.setData(item.getSeries5m(), item.getSeries1h(), item.getSeries6h(), item.getSeries24h(), item.getAvgPrice());
		}
		if (volumeGraph != null)
		{
			volumeGraph.setData(item.getSeries5m(), item.getSeries1h(), item.getSeries6h(), item.getSeries24h(), item.getAvgPrice());
		}

		// --- Market Info ---
		miBuyLimit.setText(item.getBuyLimit() > 0 ? NUMBER_FORMAT.format(item.getBuyLimit()) : "N/A");
		long tax = geTax(item.getAvgPrice());
		miGeTax.setText(hasPrices ? "~" + formatTotalGp(tax, full) : "—");
		applyVolatility(item);
		applyLiquidity(vol24);
		if (priceRangeBar != null)
		{
			long[] range = thirtyDayRange(item);
			priceRangeBar.setRange(range[0], range[1], item.getAvgPrice());
			applyRangePosition(range[0], range[1], item.getAvgPrice());
		}

		// --- Alchemy Info ---
		long ha = item.getHighAlch();
		long la = item.getLowAlch();
		long avg = item.getAvgPrice();
		haValue.setText(ha > 0 ? formatTotalGp(ha, full) : "—");
		laValue.setText(la > 0 ? formatTotalGp(la, full) : "—");
		long haP = ha - avg - natureRunePrice - 5 * fireRunePrice;
		long laP = la - avg - natureRunePrice - 3 * fireRunePrice;
		boolean alchKnown = ha > 0 && hasPrices;
		boolean laKnown = la > 0 && hasPrices;
		applyProfitLabel(haProfit, haP, alchKnown);
		applyProfitLabel(laProfit, laP, laKnown);
		haProfit.setToolTipText(alchKnown ? alchProfitTooltip("High", ha, avg, 5) : null);
		laProfit.setToolTipText(laKnown ? alchProfitTooltip("Low", la, avg, 3) : null);
		boolean showAlchProfit = colQty > 0 && alchKnown;
		alchEstProfitRow.setVisible(showAlchProfit);
		if (showAlchProfit)
		{
			long estProfit = haP * colQty;
			String sign = estProfit > 0 ? "+" : "";
			alchEstProfit.setText(sign + formatTotalGp(estProfit, full));
			alchEstProfit.setForeground(estProfit == 0 ? ColorScheme.LIGHT_GRAY_COLOR
					: (estProfit > 0 ? COLOR_HIGH : COLOR_LOW));
			alchEstProfit.setToolTipText("<html>High alch profit (" + signedGp(haP)
					+ ") × " + NUMBER_FORMAT.format(colQty) + " in collection log"
					+ "<br>= " + signedGp(estProfit) + "</html>");
		}

		// --- Collection Log ---
		acquisitionsModel.setItem(item);
		applyTableRenderers();
		acquisitionsTable.revalidate();
		acquisitionsSection.setVisible(item.getMode() != TrackItemMode.VIEW);
	}

	/**
	 * Uniform "-" placeholder shared by every empty Price Overview cell. The
	 * label keeps its existing column colour (green/red/gold for High/Low/Avg,
	 * grey for Δ%/Vol) so only the dash character is standardised.
	 */
	private void setOverviewPlaceholder(JLabel label)
	{
		clearItemValue(label, "-");
	}

	/**
	 * Sets a High/Low/Avg overview cell: the value in the column colour, or a
	 * column-coloured "-" placeholder when the value is 0 (no trade of that type).
	 */
	private void setPriceCell(JLabel label, long value, Color color, String tooltipLabel, Color tint)
	{
		label.setForeground(color);
		if (value > 0)
		{
			installItemValue(label, value, "", tooltipLabel, tint);
		}
		else
		{
			setOverviewPlaceholder(label);
		}
	}

	private static long overviewMidpoint(WikiRealtimePriceClient.PricePoint p)
	{
		long h = p.getAvgHighPrice();
		long l = p.getAvgLowPrice();
		if (h > 0 && l > 0) return (h + l) / 2;
		return Math.max(h, l);
	}

	private long windowVolume(TrackedItem item, TimeWindow window)
	{
		PriceStats s = item.getWindowStats().get(window);
		return s == null ? 0 : s.getVolume();
	}

	private void installVolumeValue(JLabel label, long vol)
	{
		String text = formatItemShort(vol);
		label.setForeground(COLOR_VOLUME);
		label.setText(text);
		label.setToolTipText("Volume: " + NUMBER_FORMAT.format(vol));
		for (MouseListener ml : label.getMouseListeners())
		{
			if (ml instanceof HoverTintListener)
			{
				label.removeMouseListener(ml);
			}
		}
		HoverTintListener listener = new HoverTintListener(label, text, TINT_VOLUME);
		label.addMouseListener(listener);
		SwingUtilities.invokeLater(listener::applyIfHovered);
	}

	/**
	 * Computes the percentage change of the average price over the window's
	 * timeframe: current average versus the average one full period ago.
	 */
	private void applyDeltaPct(JLabel label, TrackedItem item, TimeWindow window)
	{
		for (MouseListener ml : label.getMouseListeners())
		{
			if (ml instanceof HoverTintListener)
			{
				label.removeMouseListener(ml);
			}
		}
		label.setToolTipText(null);

		long current = item.getAvgPrice();
		PriceStats stats = item.getWindowStats().get(window);
		long baseline = stats == null ? 0 : stats.getAvg();
		if (current <= 0 || baseline <= 0)
		{
			label.setText("-");
			label.setForeground(COLOR_VOLUME);
			label.setToolTipText(null);
			return;
		}

		double pct = Math.round(((double) (current - baseline) / baseline) * 1000.0) / 10.0;
		String pctText;
		Color color;
		Color tint;
		if (pct == 0.0)
		{
			pctText = "0%";
			color = COLOR_VOLUME;
			tint = TINT_VOLUME;
		}
		else
		{
			pctText = String.format(Locale.US, "%+.1f%%", pct);
			color = pct > 0 ? COLOR_HIGH : COLOR_LOW;
			tint = pct > 0 ? TINT_HIGH : TINT_LOW;
		}

		label.setText(pctText);
		label.setForeground(color);
		label.setToolTipText(pctText + " change compared to " + spelledInterval(window) + " avg.");

		HoverTintListener listener = new HoverTintListener(label, pctText, tint);
		label.addMouseListener(listener);
		SwingUtilities.invokeLater(listener::applyIfHovered);
	}

	private static String spelledInterval(TimeWindow window)
	{
		switch (window)
		{
			case H1: return "1 hour";
			case H3: return "3 hour";
			case H6: return "6 hour";
			case H12: return "12 hour";
			case H24: return "24 hour";
			case WEEK: return "1 week";
			case MONTH: return "1 month";
			case MONTH3: return "3 month";
			case MONTH6: return "6 month";
			case YEAR: return "1 year";
			default: return window.toString();
		}
	}

	private void applyProfitLabel(JLabel label, long profit, boolean known)
	{
		if (!known)
		{
			label.setText("—");
			label.setForeground(Color.WHITE);
			return;
		}
		String sign = profit > 0 ? "+" : "";
		label.setText(sign + formatTotalGp(profit, ValueFormat.FULL));
		label.setForeground(profit == 0 ? Color.WHITE : (profit > 0 ? COLOR_HIGH : COLOR_LOW));
	}

	private static String signedGp(long v)
	{
		return (v > 0 ? "+" : "") + NUMBER_FORMAT.format(v) + " gp";
	}

	/**
	 * Builds a breakdown tooltip for an alchemy profit:
	 * alch value − item avg − nature rune − {fireQty} × fire rune.
	 */
	private String alchProfitTooltip(String label, long alchValue, long itemAvg, int fireQty)
	{
		long natureCost = natureRunePrice;
		long fireCost = (long) fireQty * fireRunePrice;
		long profit = alchValue - itemAvg - natureCost - fireCost;
		return "<html>" + label + " alch profit:<br>"
				+ NUMBER_FORMAT.format(alchValue) + " (alch value)<br>"
				+ "− " + NUMBER_FORMAT.format(itemAvg) + " (item avg price)<br>"
				+ "− " + NUMBER_FORMAT.format(natureRunePrice) + " (nature rune)<br>"
				+ "− " + fireQty + " × " + NUMBER_FORMAT.format(fireRunePrice) + " (fire rune)<br>"
				+ "= " + signedGp(profit) + "</html>";
	}

	private long geTax(long avgPrice)
	{
		if (avgPrice < 50)
		{
			return 0;
		}
		long tax = (long) Math.floor(avgPrice * 0.02);
		return Math.min(tax, 5_000_000L);
	}

	private void applyVolatility(TrackedItem item)
	{
		List<WikiRealtimePriceClient.PricePoint> series = item.getSeriesFor(TimeWindow.WEEK);
		long cutoff = System.currentTimeMillis() / 1000L - TimeWindow.WEEK.getDuration().getSeconds();
		List<Long> samples = new ArrayList<>();
		for (WikiRealtimePriceClient.PricePoint p : series)
		{
			if (p.getTimestamp() < cutoff) continue;
			if (p.getAvgHighPrice() > 0) samples.add(p.getAvgHighPrice());
			if (p.getAvgLowPrice() > 0) samples.add(p.getAvgLowPrice());
		}
		if (samples.size() < 2)
		{
			miVolatility.setText("—");
			miVolatility.setForeground(Color.WHITE);
			miVolatility.setToolTipText(null);
			return;
		}
		double mean = 0;
		for (long v : samples) mean += v;
		mean /= samples.size();
		double variance = 0;
		for (long v : samples) variance += (v - mean) * (v - mean);
		variance /= samples.size();
		double pct = mean > 0 ? Math.sqrt(variance) / mean * 100.0 : 0;

		String label;
		Color color;
		String tooltip;
		if (pct < 1.5)
		{
			label = "Low"; color = COLOR_HIGH; tooltip = "Stable Price";
		}
		else if (pct <= 5.0)
		{
			label = "Medium"; color = COLOR_AVG; tooltip = "Occasional/Moderate Price Swings";
		}
		else
		{
			label = "High"; color = COLOR_LOW; tooltip = "Large/Frequent Price Swings";
		}
		miVolatility.setText(label);
		miVolatility.setForeground(color);
		miVolatility.setToolTipText(tooltip);
	}

	private void applyLiquidity(long vol24)
	{
		String label;
		Color color;
		if (vol24 <= 0)
		{
			miLiquidity.setText("—");
			miLiquidity.setForeground(Color.WHITE);
			miLiquidity.setToolTipText(null);
			return;
		}
		if (vol24 < 500)
		{
			label = "Low"; color = COLOR_LOW;
		}
		else if (vol24 <= 5000)
		{
			label = "Medium"; color = COLOR_AVG;
		}
		else
		{
			label = "High"; color = COLOR_HIGH;
		}
		miLiquidity.setText(label);
		miLiquidity.setForeground(color);
		miLiquidity.setToolTipText("24h volume: " + NUMBER_FORMAT.format(vol24));
	}

	/**
	 * Classifies where the current average sits within the 30-day range and
	 * shows it as High / High Avg / Average / Low Avg / Low.
	 */
	private void applyRangePosition(long min, long max, long live)
	{
		if (max <= min || live <= 0)
		{
			rangePositionLabel.setText("-");
			rangePositionLabel.setForeground(COLOR_VOLUME);
			return;
		}
		double frac = Math.max(0, Math.min(1, (double) (live - min) / (max - min)));
		String text;
		Color color;
		if (frac >= 0.75)
		{
			text = "High";
			color = COLOR_HIGH;
		}
		else if (frac >= 0.60)
		{
			text = "High Avg";
			color = COLOR_AVG;
		}
		else if (frac >= 0.40)
		{
			text = "Average";
			color = COLOR_AVG;
		}
		else if (frac >= 0.25)
		{
			text = "Low Avg";
			color = COLOR_AVG;
		}
		else
		{
			text = "Low";
			color = COLOR_LOW;
		}
		rangePositionLabel.setText(text);
		rangePositionLabel.setForeground(color);
	}

	private long[] thirtyDayRange(TrackedItem item)
	{
		List<WikiRealtimePriceClient.PricePoint> series = item.getSeriesFor(TimeWindow.MONTH);
		long cutoff = System.currentTimeMillis() / 1000L - TimeWindow.MONTH.getDuration().getSeconds();
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (WikiRealtimePriceClient.PricePoint p : series)
		{
			if (p.getTimestamp() < cutoff) continue;
			if (p.getAvgLowPrice() > 0) min = Math.min(min, p.getAvgLowPrice());
			if (p.getAvgHighPrice() > 0) max = Math.max(max, p.getAvgHighPrice());
		}
		if (min == Long.MAX_VALUE || max == Long.MIN_VALUE)
		{
			return new long[]{0, 0};
		}
		return new long[]{min, max};
	}

	private void applyTableRenderers()
	{
		JTextField centerEditorField = new JTextField();
		centerEditorField.setHorizontalAlignment(SwingConstants.CENTER);
		DefaultCellEditor centerEditor = new DefaultCellEditor(centerEditorField);
		int cols = acquisitionsTable.getColumnCount();
		for (int i = 0; i < cols; i++)
		{
			acquisitionsTable.getColumnModel().getColumn(i).setCellRenderer(new AcqCellRenderer(i == 3));
			if (i != 3)
			{
				acquisitionsTable.getColumnModel().getColumn(i).setCellEditor(centerEditor);
			}
		}
	}

	private static String acqTooltipLabel(int col)
	{
		switch (col)
		{
			case 0: return "Quantity";
			case 1: return "Bought At";
			case 2: return "Sold At";
			case 3: return "Profit";
			default: return "";
		}
	}

	private class AcquisitionsTableModel extends AbstractTableModel
	{
		private final String[] COLS_FULL = {"Qty", "Bought", "Sold", "Profit"};
		private final String[] COLS_NO_PROFIT = {"Qty", "Bought", "Sold"};
		private TrackedItem item;

		void setItem(TrackedItem item)
		{
			this.item = item;
			fireTableStructureChanged();
		}

		private String[] cols()
		{
			return config.showItemProfitRow() ? COLS_FULL : COLS_NO_PROFIT;
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

	/**
	 * Renders collection-log numbers: full format at ≤4 digits, short format
	 * when they would overflow. Short-form cells highlight (column-tinted) and
	 * carry a full-value tooltip on hover, mirroring the rest of the plugin.
	 */
	private class AcqCellRenderer extends DefaultTableCellRenderer
	{
		private final boolean profit;

		AcqCellRenderer(boolean profit)
		{
			this.profit = profit;
			setHorizontalAlignment(SwingConstants.CENTER);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
				boolean isSelected, boolean hasFocus, int row, int column)
		{
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (!(value instanceof Number))
			{
				setText(value == null ? "" : value.toString());
				if (!isSelected)
				{
					setBackground(table.getBackground());
					setForeground(Color.WHITE);
				}
				return this;
			}

			long v = ((Number) value).longValue();
			// Profit overflows sooner (it carries a sign), so it goes short at 4+ digits
			// and is capped to 3 significant figures (e.g. 234K / 23.4K, never 234.5K).
			boolean shortForm = Math.abs(v) >= (profit ? 1000 : 10000);
			String text = shortForm
					? (profit ? formatShort3Sig(v) : formatItemShort(v))
					: NUMBER_FORMAT.format(v);
			if (profit && v > 0)
			{
				text = "+" + text;
			}
			setText(text);

			Color fg = profit ? (v > 0 ? COLOR_HIGH : v < 0 ? COLOR_LOW : Color.WHITE) : Color.WHITE;
			setForeground(isSelected ? table.getSelectionForeground() : fg);

			boolean hovered = shortForm && row == acqHoverRow && column == acqHoverCol;
			if (isSelected)
			{
				setBackground(table.getSelectionBackground());
			}
			else if (hovered)
			{
				setForeground(fg);
				setBackground(profit ? (v >= 0 ? TINT_HIGH : TINT_LOW) : TINT_VOLUME);
			}
			else
			{
				setBackground(table.getBackground());
			}
			return this;
		}
	}

	/**
	 * 30-day price range gradient (red → gold → green) with an inverted
	 * triangle marking where the live price sits within the range. The
	 * triangle is tinted to match the gradient colour beneath it.
	 */
	private static final class PriceRangeBar extends JPanel
	{
		private static final Color RANGE_RED = new Color(220, 100, 100);
		private static final Color RANGE_GOLD = new Color(255, 200, 0);
		private static final Color RANGE_GREEN = new Color(100, 220, 100);
		private static final int TRIANGLE_H = 9;
		private static final int BAR_H = 5;
		private static final int BAR_ARC = 3; // 1.5px corner radius

		private long min;
		private long max;
		private long live;

		PriceRangeBar()
		{
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setPreferredSize(new Dimension(220, TRIANGLE_H + 2 + BAR_H + 16));
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		void setRange(long min, long max, long live)
		{
			this.min = min;
			this.max = max;
			this.live = live;
			repaint();
		}

		private static Color lerp(Color a, Color b, double t)
		{
			return new Color(
					(int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
					(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
					(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
		}

		private static Color colorAt(double f)
		{
			f = Math.max(0, Math.min(1, f));
			if (f < 0.5)
			{
				return lerp(RANGE_RED, RANGE_GOLD, f / 0.5);
			}
			return lerp(RANGE_GOLD, RANGE_GREEN, (f - 0.5) / 0.5);
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
				int barW = Math.max(1, w);
				int barY = TRIANGLE_H + 2;

				boolean hasData = max > min;
				if (!hasData)
				{
					g2.setColor(new Color(80, 80, 80));
					g2.fillRoundRect(0, barY, barW, BAR_H, BAR_ARC, BAR_ARC);
					g2.setColor(Color.GRAY);
					String msg = "No data";
					g2.drawString(msg, (barW - fm.stringWidth(msg)) / 2, barY + BAR_H + fm.getAscent() + 2);
					return;
				}

				// Draw the gradient clipped to a rounded rectangle for soft corners.
				Shape oldClip = g2.getClip();
				g2.setClip(new java.awt.geom.RoundRectangle2D.Double(0, barY, barW, BAR_H, BAR_ARC, BAR_ARC));
				for (int x = 0; x < barW; x++)
				{
					g2.setColor(colorAt((double) x / Math.max(1, barW - 1)));
					g2.drawLine(x, barY, x, barY + BAR_H);
				}
				g2.setClip(oldClip);

				double frac = Math.max(0, Math.min(1, (double) (live - min) / (max - min)));
				int tx = (int) Math.round(frac * (barW - 1));
				g2.setColor(colorAt(frac));
				int[] xs = {tx - 5, tx + 5, tx};
				int[] ys = {0, 0, TRIANGLE_H};
				g2.fillPolygon(xs, ys, 3);

				int labelY = barY + BAR_H + fm.getAscent() + 2;
				g2.setColor(RANGE_RED);
				g2.drawString(NUMBER_FORMAT.format(min), 0, labelY);
				g2.setColor(RANGE_GREEN);
				String maxS = NUMBER_FORMAT.format(max);
				g2.drawString(maxS, barW - fm.stringWidth(maxS), labelY);
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
