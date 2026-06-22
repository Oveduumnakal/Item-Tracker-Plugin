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
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
	private final Consumer<Integer> onNotificationsEdited;
	private final Runnable onClearAll;

	private final CardLayout cardLayout = new CardLayout();
	// CardLayout sizes to the largest card, which would make the (shorter) main
	// view inherit the detail view's height and show a needless scrollbar. Report
	// only the visible card's preferred size so each view scrolls on its own merit.
	private final JPanel cardsHost = new JPanel(cardLayout)
	{
		@Override
		public Dimension getPreferredSize()
		{
			for (Component c : getComponents())
			{
				if (c.isVisible())
					return c.getPreferredSize();
			}

			return super.getPreferredSize();
		}
	};
	private static final String CARD_MAIN = "main";
	private static final String CARD_DETAIL = "detail";
	private static final String CARD_LOGGED_OUT = "loggedOut";

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
	private NotificationsTableModel notificationsModel;
	private JTable notificationsTable;
	/** True while a notifications cell editor is open; read off-EDT by the plugin. */
	private volatile boolean editingNotifications;
	private JPanel notificationsSection;
	private static final int DEFAULT_NOTIFICATION_ROWS = 5;
	// Collection-log pop-out: a second table sharing the same item data, plus the
	// header button that opens it (shown only while the table is scrollable).
	private JButton acqPopoutButton;
	private AcquisitionsTableModel acqPopoutModel;
	private JTable acqPopoutTable;
	private JScrollPane acqPopoutScroll;
	private JPanel overviewGrid;
	private PriceGraphPanel priceGraph;
	private PriceGraphPanel volumeGraph;

	// Detail-view section wrappers, host, and applied-layout/row caches.
	private JPanel topStack;
	private JPanel detailSectionsHost;
	private JPanel itemValuesSection;
	private JPanel marketInfoSection;
	private JPanel priceOverviewSection;
	private JPanel priceGraphSection;
	private JPanel volumeGraphSection;
	private JPanel alchInfoSection;
	private String appliedSectionLayout;
	private Set<TimeWindow> appliedOverviewRows;

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
	// Vertical padding for the GE Estimates value rows: roomier by default,
	// tightened to the tracked-items list spacing when Compact is selected.
	private static final javax.swing.border.Border ESTIMATE_ROW_BORDER_DEFAULT =
			new EmptyBorder(3, 0, 3, 0);
	private static final javax.swing.border.Border ESTIMATE_ROW_BORDER_COMPACT =
			new EmptyBorder(1, 0, 1, 0);
	private static final javax.swing.border.Border PROFIT_SECTION_BORDER_DEFAULT =
			BorderFactory.createCompoundBorder(
					BorderFactory.createCompoundBorder(
							new EmptyBorder(4, 0, 0, 0),
							new MatteBorder(1, 0, 0, 0, DIVIDER_COLOR)),
					new EmptyBorder(4, 0, 0, 0));
	private static final javax.swing.border.Border PROFIT_SECTION_BORDER_COMPACT =
			BorderFactory.createCompoundBorder(
					BorderFactory.createCompoundBorder(
							new EmptyBorder(1, 0, 0, 0),
							new MatteBorder(1, 0, 0, 0, DIVIDER_COLOR)),
					new EmptyBorder(1, 0, 0, 0));

	private final JLabel totalHighLabel;
	private final JLabel totalLowLabel;
	private final JLabel totalAvgLabel;
	private final JPanel totalHighRow;
	private final JPanel totalLowRow;
	private final JPanel totalAvgRow;
	private final JLabel lastRefreshLabel;
	/** Pinned footer (below the scroll area) holding the price-refresh counter; main view only. */
	private final JPanel footerPanel = new JPanel(new BorderLayout());
	/** Footer "Clear" button; disabled when the list is empty. */
	private final JButton clearButton = new JButton("Clear");

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
			Consumer<Integer> onClearAcquisitions,
			Consumer<Integer> onNotificationsEdited,
			Runnable onClearAll)
	{
		this.itemManager = itemManager;
		this.config = config;
		this.onAddItem = onAddItem;
		this.onRemoveItem = onRemoveItem;
		this.onAcquisitionsEdited = onAcquisitionsEdited;
		this.onRequestDetailData = onRequestDetailData;
		this.onClearAcquisitions = onClearAcquisitions;
		this.onNotificationsEdited = onNotificationsEdited;
		this.onClearAll = onClearAll;

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

		lastRefreshLabel = new JLabel("Prices not yet loaded", SwingConstants.CENTER);
		lastRefreshLabel.setForeground(new Color(150, 150, 150));
		lastRefreshLabel.setFont(FontManager.getRunescapeSmallFont());

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
		profitSection.setBorder(PROFIT_SECTION_BORDER_DEFAULT);
		profitSection.add(profitRow, BorderLayout.CENTER);
		profitSection.setVisible(false);
		totalsRows.add(profitSection);

		bottomPanel = new JPanel(new BorderLayout(0, 0));
		bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		bottomPanel.add(totalsTitle, BorderLayout.NORTH);
		bottomPanel.add(totalsPanel, BorderLayout.CENTER);

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

		JPanel loggedOutCard = new JPanel(new GridBagLayout());
		loggedOutCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel loggedOutLabel = new JLabel("Log in to view tracked items");
		loggedOutLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		loggedOutLabel.setFont(FontManager.getRunescapeSmallFont());
		loggedOutLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
		loggedOutCard.add(loggedOutLabel);

		cardsHost.setBackground(ColorScheme.DARK_GRAY_COLOR);
		cardsHost.add(mainCard, CARD_MAIN);
		cardsHost.add(detailCard, CARD_DETAIL);
		cardsHost.add(loggedOutCard, CARD_LOGGED_OUT);
		add(cardsHost, BorderLayout.CENTER);

		// Default to the logged-out placeholder until the first refresh; the plugin
		// flips this based on game state (see rebuild).
		cardLayout.show(cardsHost, CARD_LOGGED_OUT);

		// Pin the refresh counter to a footer below the scroll area (via the
		// PluginPanel wrapper) so it stays visible no matter how far the main
		// view is scrolled. Hidden on the detail card (see showMain/showDetail).
		footerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		footerPanel.setBorder(BorderFactory.createCompoundBorder(
				new MatteBorder(1, 0, 0, 0, DIVIDER_COLOR),
				new EmptyBorder(6, 10, 6, 10)));
		footerPanel.add(lastRefreshLabel, BorderLayout.CENTER);

		clearButton.setFont(FontManager.getRunescapeSmallFont());
		clearButton.setForeground(COLOR_LOW);
		clearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clearButton.setFocusPainted(false);
		clearButton.setToolTipText("Remove all tracked items, including their notifications and collection log");
		clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		clearButton.setEnabled(false);
		clearButton.addActionListener(e -> confirmAndClearAll());
		footerPanel.add(clearButton, BorderLayout.EAST);

		// Hidden initially to match the logged-out placeholder shown on startup.
		footerPanel.setVisible(false);
		getWrappedPanel().add(footerPanel, BorderLayout.SOUTH);

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
			return;

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

	/**
	 * Sets the vertical spacing of the GE Estimates value rows (High/Low/Avg and
	 * Est. Profit). Compact tightens them to the tracked-items list spacing.
	 */
	private void applyEstimatesSpacing(EstimatesSpacing spacing)
	{
		boolean compact = spacing == EstimatesSpacing.COMPACT;
		javax.swing.border.Border rowBorder = compact
				? ESTIMATE_ROW_BORDER_COMPACT : ESTIMATE_ROW_BORDER_DEFAULT;
		totalHighRow.setBorder(rowBorder);
		totalLowRow.setBorder(rowBorder);
		totalAvgRow.setBorder(rowBorder);
		profitSection.setBorder(compact
				? PROFIT_SECTION_BORDER_COMPACT : PROFIT_SECTION_BORDER_DEFAULT);
		bottomPanel.revalidate();
		bottomPanel.repaint();
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

	private void updateCoinsIcon(long value)
	{
		int quantity = (int) Math.max(1, Math.min(value, Integer.MAX_VALUE));
		if (quantity == lastCoinsIconValue)
			return;

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
				coinsIcon.setIcon(icon);
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
		row.setBorder(ESTIMATE_ROW_BORDER_DEFAULT);
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
			return;

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
			return;

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
			return;

		double phase = (System.currentTimeMillis() % LOADING_GLOW_PERIOD_MS) / (double) LOADING_GLOW_PERIOD_MS;
		double wave = (Math.sin(phase * 2 * Math.PI) + 1) / 2;
		float alpha = LOADING_GLOW_MIN_ALPHA + (1f - LOADING_GLOW_MIN_ALPHA) * (float) wave;
		Color glow = new Color(
				LOADING_COLOR.getRed(), LOADING_COLOR.getGreen(), LOADING_COLOR.getBlue(),
				Math.round(alpha * 255));

		for (JLabel label : loadingLabels)
			label.setForeground(glow);
	}

	private void updateRefreshLabel()
	{
		if (lastPriceRefresh == null)
			lastRefreshLabel.setText("Prices not yet loaded");
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
			if (shown >= 5)
				break;

			if (trackedItemIds.contains(item.getId()))
				continue;

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

	public void rebuild(List<TrackedItem> items, Instant newLastPriceRefresh,
			PriceIndicatorMode indicatorMode, boolean loggedIn)
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

		if (!loggedIn)
		{
			// Hide everything behind the placeholder and reset to the main view so
			// re-logging in lands on the list rather than a stale detail view.
			SwingUtilities.invokeLater(() ->
			{
				detailItemId = -1;
				closePopouts();
				footerPanel.setVisible(false);
				cardLayout.show(cardsHost, CARD_LOGGED_OUT);
			});
			return;
		}

		if (detailItemId > 0)
		{
			TrackedItem detail = currentItems.get(detailItemId);
			if (detail != null)
				SwingUtilities.invokeLater(() -> populateDetail(detail));
			else if (!currentItems.isEmpty())
			{
				// The item is genuinely gone (e.g. it was untracked), so leave the
				// detail view. An empty list is a transient loading/refresh state and
				// must not bounce us back to the main view.
				SwingUtilities.invokeLater(this::showMain);
			}
		}
		else
		{
			// On the main view: make sure we're showing it (e.g. returning from the
			// logged-out placeholder) with the footer visible.
			SwingUtilities.invokeLater(() ->
			{
				footerPanel.setVisible(true);
				cardLayout.show(cardsHost, CARD_MAIN);
			});
		}

		SwingUtilities.invokeLater(() ->
		{
			loadingLabels.clear();
			pulseEntries.clear();
			clearButton.setEnabled(!trackedItemIds.isEmpty());
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
			applyEstimatesSpacing(config.geEstimatesSpacing());

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
				profitSection.setVisible(false);

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
			iconLabel.setIcon(cached);
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

		JLabel qtyLabel = new JLabel("Qty: " + GpFormat.shortValue(item.getQuantity()));
		qtyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		qtyLabel.setFont(FontManager.getRunescapeSmallFont());
		qtyLabel.setToolTipText(NUMBER_FORMAT.format(item.getQuantity()));

		JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameRow.add(iconLabel);
		nameRow.add(nameLabel);
		if (showQty)
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
					continue;

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
					String volText = window == TimeWindow.LIVE ? "-" : GpFormat.shortValue(vol);
					cell.setText(volText);
					if (window != TimeWindow.LIVE)
						cell.setToolTipText("Volume: " + NUMBER_FORMAT.format(vol));

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
					pulseIfShown(pulse, item.getAvgDelta(), itemIndicatorMode);

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
				long itemProfit = (long) item.getRecordQuantitySum() * item.getAvgPrice() - item.getCostBasis();
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
					return;

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
						hoveredItemId = -1;

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
				addListenerRecursively(child, listener);
		}
	}

	private void installItemValue(JLabel label, long value, String prefix, Color tint)
	{
		installItemValue(label, value, prefix, null, tint);
	}

	private void installItemValue(JLabel label, long value, String prefix, String tooltipLabel, Color tint)
	{
		installShortValue(label, value, prefix + GpFormat.shortValue(value), tooltipLabel, tint);
	}

	/** Installs a pre-formatted compact value with full-number tooltip and hover tint. */
	private void installShortValue(JLabel label, long value, String shortText, String tooltipLabel, Color tint)
	{
		label.setText(shortText);
		String tooltipPrefix = tooltipLabel == null ? "" : tooltipLabel + ": ";
		label.setToolTipText(tooltipPrefix + NUMBER_FORMAT.format(value) + " gp");
		removeHoverTint(label);
		HoverTintListener listener = new HoverTintListener(label, shortText, tint);
		label.addMouseListener(listener);
		SwingUtilities.invokeLater(listener::applyIfHovered);
	}

	private void clearItemValue(JLabel label, String text)
	{
		for (MouseListener ml : label.getMouseListeners())
		{
			if (ml instanceof HoverTintListener)
				label.removeMouseListener(ml);
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
			if (tint == null)
				return;

			label.setText(highlightedText);
		}

		@Override
		public void mouseExited(MouseEvent e)
		{
			if (tint == null)
				return;

			label.setText(shortText);
		}

		void applyIfHovered()
		{
			if (tint == null || !label.isShowing() || label.getWidth() == 0)
				return;

			PointerInfo info = MouseInfo.getPointerInfo();
			if (info == null)
				return;

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
			label.setToolTipText(NUMBER_FORMAT.format(value) + " gp");
		else
			label.setToolTipText(null);
	}

	private static String formatTotalGp(long value, ValueFormat fmt)
	{
		return fmt == ValueFormat.FULL ? GpFormat.fullGp(value) : GpFormat.shortGp(value);
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

		topStack = new JPanel();
		topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
		topStack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topStack.add(headerRow);
		topStack.add(titleRow);

		// Each detail section is its own self-contained wrapper so the layout can
		// reorder or hide them per config. detailSectionsHost holds them in order.
		detailSectionsHost = new JPanel();
		detailSectionsHost.setLayout(new BoxLayout(detailSectionsHost, BoxLayout.Y_AXIS));
		detailSectionsHost.setBackground(ColorScheme.DARK_GRAY_COLOR);
		detailSectionsHost.setAlignmentX(Component.LEFT_ALIGNMENT);
		topStack.add(detailSectionsHost);

		itemValuesSection = buildDetailSection("Item Current Values",
				buildCurrentValuesBlock(icvHigh, icvLow, icvAvg, icvVolume, null));

		ccvSection = buildDetailSection("Collection Current Values",
				buildCurrentValuesBlock(ccvHigh, ccvLow, ccvAvg, ccvQuantity, ccvProfit));

		priceOverviewSection = buildDetailSectionWithPopout("Price Overview",
				this::openOverviewPopout, buildOverviewGrid());

		priceGraph = new PriceGraphPanel(PriceGraphPanel.Mode.PRICE);
		priceGraph.setAlignmentX(Component.LEFT_ALIGNMENT);
		priceGraph.setSmooth(graphSmooth);
		priceGraph.setSmoothListener(b -> {
			graphSmooth = b;
			if (pricePopoutGraph != null)
				pricePopoutGraph.setSmooth(b);
		});
		priceGraph.setLineSet(graphLineSet);
		priceGraph.setLineSetListener(set -> {
			graphLineSet = set;
			if (pricePopoutGraph != null)
				pricePopoutGraph.setLineSet(set);
		});
		priceGraphSection = buildDetailSectionWithPopout("Price Graph",
				() -> openGraphPopout("Price Graph", PriceGraphPanel.Mode.PRICE, priceGraph),
				priceGraph, Box.createVerticalStrut(4));

		volumeGraph = new PriceGraphPanel(PriceGraphPanel.Mode.VOLUME);
		volumeGraph.setAlignmentX(Component.LEFT_ALIGNMENT);
		volumeGraphSection = buildDetailSectionWithPopout("Volume Graph",
				() -> openGraphPopout("Volume Graph", PriceGraphPanel.Mode.VOLUME, volumeGraph),
				volumeGraph);

		marketInfoSection = buildDetailSection("Market Info", buildMarketInfoBlock());

		alchInfoSection = buildDetailSection("Alchemy Info", buildAlchBlock());

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
					return null;

				Object val = getValueAt(row, col);
				if (!(val instanceof Number))
					return null;

				long v = ((Number) val).longValue();
				// Only the short-form (overflowing) cells carry a full-value tooltip.
				if (Math.abs(v) < (col == 3 ? 1000 : 10000))
					return null;

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
		acquisitionsTable.setFont(FontManager.getRunescapeSmallFont());
		acquisitionsTable.setGridColor(new Color(60, 60, 60));
		acquisitionsTable.setRowHeight(22);
		acquisitionsTable.setFillsViewportHeight(true);
		acquisitionsTable.getTableHeader().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		acquisitionsTable.getTableHeader().setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		acquisitionsTable.getTableHeader().setFont(FontManager.getRunescapeSmallFont());
		applyTableRenderers();
		TableCellRenderer headerRenderer = acquisitionsTable.getTableHeader().getDefaultRenderer();
		if (headerRenderer instanceof DefaultTableCellRenderer)
			((DefaultTableCellRenderer) headerRenderer).setHorizontalAlignment(SwingConstants.CENTER);

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
					return;

				int row = acquisitionsTable.rowAtPoint(e.getPoint());
				if (row >= 0)
					return;

				TrackedItem t = currentItems.get(detailItemId);
				if (t == null)
					return;

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
						editor.requestFocusInWindow();
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
			if (t == null)
				return;

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
			if (t == null)
				return;

			if (acquisitionsTable.isEditing())


				acquisitionsTable.getCellEditor().stopCellEditing();

			int[] selected = acquisitionsTable.getSelectedRows();
			if (selected.length == 0)
				return;

			List<AcquisitionRecord> records = t.getAcquisitions();
			java.util.Arrays.sort(selected);
			for (int i = selected.length - 1; i >= 0; i--)
			{
				int idx = selected[i];
				if (idx >= 0 && idx < records.size())
					records.remove(idx);
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
			if (t == null)
				return;

			boolean removed = t.getAcquisitions().removeIf(r -> r.getQuantity() == 0);
			if (!removed)
				return;

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
			if (t == null || t.getAcquisitions().isEmpty())
				return;

			int choice = JOptionPane.showConfirmDialog(
					ItemTrackerPanel.this,
					"Clear the entire collection log for this item?",
					"Clear Collection Log",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.YES_OPTION)
				return;

			if (acquisitionsTable.isEditing())


				acquisitionsTable.getCellEditor().cancelCellEditing();

			if (onClearAcquisitions != null)
				onClearAcquisitions.accept(detailItemId);
		});

		// Give all four buttons a uniform height (the broom's icon otherwise makes it taller).
		JButton[] logButtons = {addRowBtn, removeRowBtn, cleanBtn, clearBtn};
		int btnHeight = 0;
		for (JButton b : logButtons)
			btnHeight = Math.max(btnHeight, b.getPreferredSize().height);

		for (JButton b : logButtons)
			b.setPreferredSize(new Dimension(b.getPreferredSize().width, btnHeight));

		JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tableButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tableButtons.setBorder(new EmptyBorder(4, 0, 4, 0));
		tableButtons.add(addRowBtn);
		tableButtons.add(removeRowBtn);
		tableButtons.add(cleanBtn);
		tableButtons.add(clearBtn);

		// Title with a pop-out button that only appears while the table is scrollable.
		acqPopoutButton = buildPopoutButton(this::openCollectionLogPopout);
		acqPopoutButton.setVisible(false);
		JComponent logTitle = buildDetailSectionTitleRow("Item Collection Log", acqPopoutButton);
		tableScroll.getVerticalScrollBar().addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentShown(ComponentEvent e)
			{
				updateAcqPopoutButton();
			}

			@Override
			public void componentHidden(ComponentEvent e)
			{
				updateAcqPopoutButton();
			}
		});

		acquisitionsSection = new JPanel(new BorderLayout(0, 4));
		acquisitionsSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		acquisitionsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		acquisitionsSection.add(logTitle, BorderLayout.NORTH);
		acquisitionsSection.add(tableScroll, BorderLayout.CENTER);
		acquisitionsSection.add(tableButtons, BorderLayout.SOUTH);

		buildNotificationsSection();

		// Populate the overview rows and order/visibility from config now that
		// every section wrapper exists.
		rebuildOverviewGrid();
		applyDetailSectionLayout();
	}

	/**
	 * Builds the per-item Notifications section: a collection-log-style table of
	 * rules (Metric / Time Frame / Operation / Value) with combo-box editors,
	 * add/remove/clear buttons, and a gear in the header linking to the shared
	 * notification settings.
	 */
	private void buildNotificationsSection()
	{
		notificationsModel = new NotificationsTableModel();
		notificationsTable = new JTable(notificationsModel)
		{
			@Override
			public Dimension getPreferredScrollableViewportSize()
			{
				return new Dimension(getPreferredSize().width,
						Math.min(getPreferredSize().height, getRowHeight() * DEFAULT_NOTIFICATION_ROWS + 2));
			}
		};
		notificationsTable.setFillsViewportHeight(true);
		notificationsTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		notificationsTable.setForeground(Color.WHITE);
		notificationsTable.setGridColor(new Color(60, 60, 60));
		notificationsTable.setRowHeight(22);
		notificationsTable.setFont(FontManager.getRunescapeSmallFont());
		notificationsTable.getTableHeader().setFont(FontManager.getRunescapeSmallFont());
		notificationsTable.getTableHeader().setReorderingAllowed(false);
		notificationsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		// Commit an open cell editor as soon as the table loses focus (e.g. clicking
		// back into the game). Otherwise the editor lingers open, and the guard below
		// would block all notifications for as long as the detail view is shown.
		notificationsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		// Track when a cell editor is open so the plugin can hold off firing (and
		// removing) notifications mid-edit, which would otherwise wedge the editor.
		notificationsTable.addPropertyChangeListener("tableCellEditor",
				e -> editingNotifications = notificationsTable.isEditing());
		applyNotificationRenderers();

		JScrollPane tableScroll = new JScrollPane(notificationsTable);
		tableScroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tableScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));

		JButton addRowBtn = new JButton("+ Add");
		styleNotifButton(addRowBtn, Color.WHITE);
		addRowBtn.addActionListener(e -> {
			TrackedItem t = currentItems.get(detailItemId);
			if (t == null)
				return;

			t.getNotifications().add(new NotificationRule());
			notificationsModel.fireTableDataChanged();
			notificationsTable.revalidate();
			notifyNotificationsEdited();
		});

		JButton removeRowBtn = new JButton("− Remove");
		styleNotifButton(removeRowBtn, Color.WHITE);
		removeRowBtn.setEnabled(false);
		Runnable removeSelected = () -> {
			TrackedItem t = currentItems.get(detailItemId);
			if (t == null)
				return;

			if (notificationsTable.isEditing())


				notificationsTable.getCellEditor().stopCellEditing();

			int[] selected = notificationsTable.getSelectedRows();
			if (selected.length == 0)
				return;

			List<NotificationRule> rules = t.getNotifications();
			java.util.Arrays.sort(selected);
			for (int i = selected.length - 1; i >= 0; i--)
			{
				int idx = selected[i];
				if (idx >= 0 && idx < rules.size())
					rules.remove(idx);
			}

			// Keep at least the blank default rows so the section never collapses
			// below its ready-to-fill layout.
			while (rules.size() < DEFAULT_NOTIFICATION_ROWS)
				rules.add(new NotificationRule());

			notificationsModel.fireTableDataChanged();
			notificationsTable.revalidate();
			notifyNotificationsEdited();
		};
		removeRowBtn.addActionListener(e -> removeSelected.run());
		notificationsTable.getSelectionModel().addListSelectionListener(e ->
				removeRowBtn.setEnabled(notificationsTable.getSelectedRowCount() > 0));
		notificationsTable.getInputMap(JComponent.WHEN_FOCUSED)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelectedRules");
		notificationsTable.getActionMap().put("deleteSelectedRules", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				removeSelected.run();
			}
		});

		JButton clearBtn = new JButton("Clear");
		styleNotifButton(clearBtn, new Color(220, 100, 100));
		clearBtn.setToolTipText("Remove every notification rule");
		clearBtn.addActionListener(e -> {
			TrackedItem t = currentItems.get(detailItemId);
			if (t == null || t.getNotifications().isEmpty())
				return;

			int choice = JOptionPane.showConfirmDialog(this,
					"Remove all notification rules for this item?",
					"Clear Notifications", JOptionPane.YES_NO_OPTION);
			if (choice != JOptionPane.YES_OPTION)
				return;

			t.getNotifications().clear();
			// Reset straight back to blank default rows instead of leaving the
			// section collapsed and empty until the next refresh re-seeds it.
			for (int i = 0; i < DEFAULT_NOTIFICATION_ROWS; i++)
				t.getNotifications().add(new NotificationRule());

			notificationsModel.fireTableDataChanged();
			notificationsTable.revalidate();
			notifyNotificationsEdited();
		});

		JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tableButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tableButtons.setBorder(new EmptyBorder(4, 0, 4, 0));
		tableButtons.add(addRowBtn);
		tableButtons.add(removeRowBtn);
		tableButtons.add(clearBtn);

		notificationsSection = new JPanel(new BorderLayout(0, 4));
		notificationsSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		notificationsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		notificationsSection.add(buildDetailSectionTitle("Notifications", true), BorderLayout.NORTH);
		notificationsSection.add(tableScroll, BorderLayout.CENTER);
		notificationsSection.add(tableButtons, BorderLayout.SOUTH);
	}

	private void styleNotifButton(JButton btn, Color fg)
	{
		btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		btn.setForeground(fg);
		btn.setFocusPainted(false);
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setMargin(new Insets(2, 5, 2, 5));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	private void notifyNotificationsEdited()
	{
		if (onNotificationsEdited != null && detailItemId > 0)
			onNotificationsEdited.accept(detailItemId);
	}

	/**
	 * Wraps a detail-view section title and its content in a self-contained,
	 * vertically stacked panel so {@link #applyDetailSectionLayout()} can reorder
	 * or hide it as a unit (title divider included).
	 */
	private JPanel buildDetailSection(String title, Component... contents)
	{
		JPanel wrapper = newSectionWrapper();
		wrapper.add(buildDetailSectionTitle(title, true));
		for (Component c : contents)
			wrapper.add(c);

		return wrapper;
	}

	/** Like {@link #buildDetailSection} but with a pop-out button in the header. */
	private JPanel buildDetailSectionWithPopout(String title, Runnable onPopout, Component... contents)
	{
		JPanel wrapper = newSectionWrapper();
		wrapper.add(buildDetailSectionTitleRow(title, onPopout));
		for (Component c : contents)
			wrapper.add(c);

		return wrapper;
	}

	private JPanel newSectionWrapper()
	{
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		return wrapper;
	}

	/** Section header with a centred title and a pop-out button on the right. */
	private JComponent buildDetailSectionTitleRow(String title, Runnable onPopout)
	{
		return buildDetailSectionTitleRow(title, buildPopoutButton(onPopout));
	}

	private JComponent buildDetailSectionTitleRow(String title, JButton popBtn)
	{
		JPanel row = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		// Top divider spanning the full width, matching buildDetailSectionTitle.
		row.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createCompoundBorder(
						new EmptyBorder(10, 0, 0, 0),
						new MatteBorder(1, 0, 0, 0, new Color(80, 80, 80))),
				new EmptyBorder(4, 0, 2, 0)));

		JLabel label = new JLabel(title, SwingConstants.CENTER);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeBoldFont());

		// Equal-width strut on the left keeps the title visually centred.
		row.add(Box.createHorizontalStrut(popBtn.getPreferredSize().width), BorderLayout.WEST);
		row.add(label, BorderLayout.CENTER);
		row.add(popBtn, BorderLayout.EAST);
		return row;
	}

	/**
	 * Re-adds the detail sections to the host in the order configured by the
	 * "Show {Section}" dropdowns, skipping any set to None. Cheap to call on every
	 * refresh: a signature check skips the rebuild when nothing changed.
	 */
	private void applyDetailSectionLayout()
	{
		if (detailSectionsHost == null)
			return;

		JPanel[] sections = {
				itemValuesSection, ccvSection, marketInfoSection, priceOverviewSection,
				priceGraphSection, volumeGraphSection, alchInfoSection, notificationsSection,
				acquisitionsSection
		};
		SectionSlot[] slots = {
				config.showItemValues(), config.showCollectionValues(), config.showMarketInfo(),
				config.showPriceOverview(), config.showPriceGraph(), config.showVolumeGraph(),
				config.showAlchInfo(), config.showNotifications(), config.showItemLog()
		};

		StringBuilder sig = new StringBuilder();
		for (SectionSlot slot : slots)
			sig.append(slot.ordinal()).append(',');

		String signature = sig.toString();
		if (signature.equals(appliedSectionLayout))
			return;

		appliedSectionLayout = signature;

		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < sections.length; i++)
		{
			if (!slots[i].isNone())
				order.add(i);
		}

		order.sort(Comparator.comparingInt(i -> slots[i].ordinal()));

		detailSectionsHost.removeAll();
		for (int i : order)
		{
			sections[i].setVisible(true);
			detailSectionsHost.add(sections[i]);
		}

		detailSectionsHost.revalidate();
		detailSectionsHost.repaint();
	}

	/**
	 * Rebuilds the Price Overview grid to contain only the rows selected in
	 * config. Skipped when the selection is unchanged. The populate pass keys off
	 * {@link #overviewLabels}, so omitted rows are simply never filled.
	 */
	private void rebuildOverviewGrid()
	{
		if (overviewGrid == null)
			return;

		Set<TimeWindow> desired = config.priceOverviewRows().getWindows();
		if (desired.equals(appliedOverviewRows))
			return;

		appliedOverviewRows = desired;
		populateOverviewGrid(desired);
	}

	public int getDetailItemId()
	{
		return detailItemId;
	}

	/** True while the user has a notifications cell editor open. Thread-safe. */
	public boolean isEditingNotifications()
	{
		return editingNotifications;
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
			return;

		TrackedItem item = currentItems.get(itemId);
		if (item != null)
			populateDetail(item);
	}

	private void scrollAcquisitionsToBottom()
	{
		if (acquisitionsScroll == null)
			return;

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
		overviewGrid = createOverviewGrid(overviewLabels, overviewWindowLabels, 2);
		return overviewGrid;
	}

	/**
	 * Creates an empty Price Overview grid whose row/column separators are
	 * painted from the supplied label maps. Shared by the sidebar grid and the
	 * larger pop-out copy so both render identically from their own labels.
	 */
	private JPanel createOverviewGrid(Map<TimeWindow, JLabel[]> labels, List<JLabel> windowLabels, int sepGap)
	{
		JPanel grid = new JPanel(new GridBagLayout())
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
				JLabel[] firstRow = labels.get(OVERVIEW_WINDOWS[0]);
				if (firstRow == null || firstRow[0] == null)
					return;

				// Vertical separator sepGap px to the right of the label column.
				int vx = firstRow[0].getX() - 3;
				if (!windowLabels.isEmpty())
				{
					JLabel ref = windowLabels.get(0);
					vx = ref.getX() + ref.getWidth() + sepGap;
				}

				g.setColor(DIVIDER_COLOR);
				g.drawLine(vx, 4, vx, getHeight() - 4);

				// Fainter horizontal separators between each data row.
				g.setColor(OVERVIEW_ROW_DIVIDER);
				JLabel prev = null;
				for (TimeWindow w : OVERVIEW_WINDOWS)
				{
					JLabel[] cells = labels.get(w);
					if (cells == null || cells[0] == null)
						continue;

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
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setBorder(new EmptyBorder(6, 3, 2, 3));
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		return grid;
	}

	/**
	 * (Re)builds the Price Overview grid contents, including only the time-window
	 * rows in {@code rows}. The header row is always present.
	 */
	private void populateOverviewGrid(Set<TimeWindow> rows)
	{
		fillOverviewGrid(overviewGrid, overviewLabels, overviewWindowLabels, rows,
				FontManager.getRunescapeSmallFont(), false);
	}

	/**
	 * (Re)builds a Price Overview grid's contents into the given label maps,
	 * including only the rows in {@code rows} and using {@code font} for every
	 * cell. The {@code expanded} pop-out gets a larger font, spelled-out
	 * time-window labels ("5 Minute", "1 Hour", …) and roomier padding.
	 */
	private void fillOverviewGrid(JPanel grid, Map<TimeWindow, JLabel[]> labels,
			List<JLabel> windowLabels, Set<TimeWindow> rows, Font font, boolean expanded)
	{
		grid.removeAll();
		labels.clear();
		windowLabels.clear();

		// Roomier spacing for the pop-out; tight for the narrow sidebar.
		int vPad = expanded ? 7 : 2;
		int hPad = expanded ? 9 : 1;
		int hPadSep = expanded ? 16 : 3; // larger gap on the column beside the separator
		grid.setBorder(new EmptyBorder(expanded ? 10 : 6, expanded ? 14 : 3,
				expanded ? 10 : 2, expanded ? 14 : 3));

		// Right-align numeric columns in the pop-out so digits line up by place value.
		int colAlign = expanded ? SwingConstants.RIGHT : SwingConstants.CENTER;

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;

		String[] headers = {"", "High", "Low", "Avg", "Δ%", "Vol"};
		Color[] headerColors = {
				ColorScheme.LIGHT_GRAY_COLOR, COLOR_HIGH, COLOR_LOW, COLOR_AVG,
				ColorScheme.LIGHT_GRAY_COLOR, COLOR_VOLUME};
		for (int i = 0; i < headers.length; i++)
		{
			JLabel h = new JLabel(headers[i], i == 0 ? SwingConstants.CENTER : colAlign);
			h.setForeground(headerColors[i]);
			h.setFont(font);
			c.gridx = i;
			c.gridy = 0;
			c.weightx = i == 0 ? 0 : 1;
			// Extra left inset on the High column leaves a gap from the separator.
			c.insets = new Insets(vPad, i == 1 ? hPadSep : hPad, vPad, hPad);
			grid.add(h, c);
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
		dc.insets = new Insets(1, hPad, 2, hPad);
		grid.add(headerDivider, dc);

		int y = 2;
		Color[] cellColors = {COLOR_HIGH, COLOR_LOW, COLOR_AVG, COLOR_VOLUME, COLOR_VOLUME};
		for (TimeWindow w : OVERVIEW_WINDOWS)
		{
			if (!rows.contains(w))
				continue;

			String wLabel = expanded ? fullWindowLabel(w) : (w == TimeWindow.LIVE ? "5m" : w.toString());
			JLabel windowLbl = new JLabel(wLabel, SwingConstants.RIGHT);
			windowLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			windowLbl.setFont(font);
			c.gridx = 0;
			c.gridy = y;
			c.weightx = 0;
			c.anchor = GridBagConstraints.EAST;
			c.insets = new Insets(vPad, hPad, vPad, hPad);
			grid.add(windowLbl, c);
			windowLabels.add(windowLbl);

			JLabel[] cells = new JLabel[5];
			for (int i = 0; i < 5; i++)
			{
				cells[i] = new JLabel("—", colAlign);
				cells[i].setForeground(cellColors[i]);
				cells[i].setFont(font);
				c.gridx = i + 1;
				c.gridy = y;
				c.weightx = 1;
				c.anchor = GridBagConstraints.CENTER;
				c.insets = new Insets(vPad, i == 0 ? hPadSep : hPad, vPad, hPad);
				grid.add(cells[i], c);
			}

			labels.put(w, cells);
			y++;
		}

		grid.revalidate();
		grid.repaint();
	}

	/** Spelled-out time-window label for the pop-out's leftmost column. */
	private static String fullWindowLabel(TimeWindow w)
	{
		return w.getLongLabel();
	}

	/**
	 * Fills a Price Overview grid's value cells from the given item's stats.
	 * When {@code full} is set (the pop-out view), prices show as full
	 * comma-grouped numbers instead of the abbreviated sidebar form.
	 */
	private void populateOverviewLabels(Map<TimeWindow, JLabel[]> labels, TrackedItem item, boolean full)
	{
		for (TimeWindow w : OVERVIEW_WINDOWS)
		{
			JLabel[] cells = labels.get(w);
			if (cells == null)
				continue;


			if (w == TimeWindow.LIVE)
			{
				// First row shows the latest 5-minute bucket rather than live prices.
				List<WikiRealtimePriceClient.PricePoint> s5 = item.getSeries5m();
				WikiRealtimePriceClient.PricePoint last = s5.isEmpty() ? null : s5.get(s5.size() - 1);
				long high = last == null ? 0 : last.getAvgHighPrice();
				long low = last == null ? 0 : last.getAvgLowPrice();
				long avg = last == null ? 0 : overviewMidpoint(last);
				setPriceCell(cells[0], high, COLOR_HIGH, "High", TINT_HIGH, full);
				setPriceCell(cells[1], low, COLOR_LOW, "Low", TINT_LOW, full);
				setPriceCell(cells[2], avg, COLOR_AVG, "Avg", TINT_AVG, full);
				setOverviewPlaceholder(cells[3]); // Δ% not meaningful for the 5m row
				if (last == null)
					setOverviewPlaceholder(cells[4]);
				else
					installVolumeValue(cells[4], last.getHighPriceVolume() + last.getLowPriceVolume(), full);

				continue;
			}

			PriceStats s = item.getWindowStats().get(w);
			setPriceCell(cells[0], s == null ? 0 : s.getHigh(), COLOR_HIGH, "High", TINT_HIGH, full);
			setPriceCell(cells[1], s == null ? 0 : s.getLow(), COLOR_LOW, "Low", TINT_LOW, full);
			setPriceCell(cells[2], s == null ? 0 : s.getAvg(), COLOR_AVG, "Avg", TINT_AVG, full);
			applyDeltaPct(cells[3], item, w);

			// Vol shows the real count, including 0 when nothing traded in the window.
			if (s == null)
				setOverviewPlaceholder(cells[4]);
			else
				installVolumeValue(cells[4], s.getVolume(), full);
		}
	}

	// ---- Pop-out windows ----

	/** A detached section window plus the callback that refreshes it from an item. */
	private static final class PopoutHandle
	{
		final JFrame frame;
		final Consumer<TrackedItem> refresher;
		final Runnable onClose;

		PopoutHandle(JFrame frame, Consumer<TrackedItem> refresher, Runnable onClose)
		{
			this.frame = frame;
			this.refresher = refresher;
			this.onClose = onClose;
		}
	}

	private final List<PopoutHandle> openPopouts = new ArrayList<>();

	// Shared "smooth lines" preference for the price graph, remembered as the
	// sidebar and pop-out graphs open and close. pricePopoutGraph tracks the open
	// price pop-out (if any) so a sidebar toggle can sync it live.
	private boolean graphSmooth = true;
	private PriceGraphPanel.LineSet graphLineSet = PriceGraphPanel.LineSet.ALL;
	private PriceGraphPanel pricePopoutGraph;

	/** Pushes the on-screen detail item into every open pop-out window. */
	private void refreshPopouts(TrackedItem item)
	{
		for (PopoutHandle h : new ArrayList<>(openPopouts))
			h.refresher.accept(item);
	}

	/** Closes every pop-out, e.g. when leaving the detail view. */
	private void closePopouts()
	{
		for (PopoutHandle h : new ArrayList<>(openPopouts))
			h.frame.dispose();

		openPopouts.clear();
	}

	/**
	 * Opens {@code content} in a resizable window, seeds it from the current
	 * detail item, and keeps it updated until it (or the detail view) closes.
	 * {@code onClose} runs when the window is disposed.
	 */
	private void showPopout(String title, JComponent content, Consumer<TrackedItem> refresher, Runnable onClose)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holder.setBorder(new EmptyBorder(8, 8, 8, 8));
		holder.add(content, BorderLayout.CENTER);

		JFrame frame = new JFrame(title);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setContentPane(holder);

		// Seed the content before packing so the window fits the real cell widths
		// (full numbers) and the content's height exactly — no trailing whitespace.
		TrackedItem current = currentItems.get(detailItemId);
		if (current != null)
			refresher.accept(current);

		frame.pack();
		frame.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));

		PopoutHandle handle = new PopoutHandle(frame, refresher, onClose);
		openPopouts.add(handle);
		frame.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				openPopouts.remove(handle);
				if (onClose != null)
					onClose.run();
			}
		});

		frame.setVisible(true);
	}

	/** Opens a larger, live copy of a price/volume graph on its current timeframe. */
	private void openGraphPopout(String title, PriceGraphPanel.Mode mode, PriceGraphPanel source)
	{
		PriceGraphPanel graph = new PriceGraphPanel(mode, true);
		graph.setActiveWindow(source.getActiveWindow());
		graph.setPreferredSize(new Dimension(640, mode == PriceGraphPanel.Mode.PRICE ? 460 : 360));

		Runnable onClose = null;
		if (mode == PriceGraphPanel.Mode.PRICE)
		{
			// Carry the remembered smoothing and line-set state in, and keep the
			// sidebar graph (and shared preferences) in sync when toggled here.
			graph.setSmooth(graphSmooth);
			graph.setSmoothListener(b -> {
				graphSmooth = b;
				source.setSmooth(b);
			});
			graph.setLineSet(graphLineSet);
			graph.setLineSetListener(set -> {
				graphLineSet = set;
				source.setLineSet(set);
			});
			pricePopoutGraph = graph;
			onClose = () -> {
				if (pricePopoutGraph == graph)
					pricePopoutGraph = null;
			};
		}

		Consumer<TrackedItem> refresher = it -> graph.setData(
				it.getSeries5m(), it.getSeries1h(), it.getSeries6h(), it.getSeries24h(), it.getAvgPrice());
		showPopout(title, graph, refresher, onClose);
	}

	/** Opens a larger, live copy of the Price Overview table. */
	private void openOverviewPopout()
	{
		Map<TimeWindow, JLabel[]> labels = new EnumMap<>(TimeWindow.class);
		List<JLabel> windowLabels = new ArrayList<>();
		JPanel grid = createOverviewGrid(labels, windowLabels, 12);
		// The pixel RuneScape fonts get jagged when enlarged, so the pop-out uses a
		// smooth monospaced font: legible at this size and aligns digits in columns.
		Font big = new Font(Font.MONOSPACED, Font.PLAIN, 18);
		// The pop-out always shows every window, regardless of the sidebar preset,
		// with the time-window column spelled out in full.
		fillOverviewGrid(grid, labels, windowLabels, OverviewPreset.DETAILED.getWindows(), big, true);

		// Pin the grid to the top so it grows by row height, not by stretching cells.
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.add(grid, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(top);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

		Consumer<TrackedItem> refresher = it -> populateOverviewLabels(labels, it, true);
		showPopout("Price Overview", scroll, refresher, null);
	}

	/** Shows the title pop-out button whenever the log is docked (not popped out). */
	private void updateAcqPopoutButton()
	{
		if (acqPopoutButton == null)
			return;

		acqPopoutButton.setVisible(acqPopoutModel == null);
	}

	/**
	 * Opens a larger Collection Log in its own window: a second table sharing the
	 * same item data, with full-format numbers, spelled-out headers, and the same
	 * Add/Remove/Clean/Clear actions. Edits route through onAcquisitionsEdited, so
	 * the docked and popped-out tables stay in sync.
	 */
	private void openCollectionLogPopout()
	{
		if (acqPopoutModel != null)
			return;

		acqPopoutModel = new AcquisitionsTableModel();
		acqPopoutTable = new JTable(acqPopoutModel);
		final JTable table = acqPopoutTable;
		final AcquisitionsTableModel model = acqPopoutModel;
		table.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		table.setForeground(Color.WHITE);
		table.setGridColor(new Color(60, 60, 60));
		table.setFillsViewportHeight(true);
		table.getTableHeader().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		table.getTableHeader().setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		table.getTableHeader().setReorderingAllowed(false);
		model.setItem(currentItems.get(detailItemId));
		applyAcqRenderers(table, model, true);

		// Double-click below the rows adds a new row, matching the docked table.
		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1
						&& table.rowAtPoint(e.getPoint()) < 0)
				{
					acqAddRow(table, model);
				}
			}
		});

		JScrollPane scroll = new JScrollPane(table);
		scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		scroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
		scroll.setPreferredSize(new Dimension(560, 380));
		acqPopoutScroll = scroll;

		JButton addBtn = acqTextButton("+ Add", Color.WHITE);
		addBtn.addActionListener(e -> acqAddRow(table, model));

		JButton removeBtn = acqTextButton("− Remove", Color.WHITE);
		removeBtn.setEnabled(false);
		removeBtn.addActionListener(e -> acqRemoveSelected(table, model));
		table.getSelectionModel().addListSelectionListener(e ->
				removeBtn.setEnabled(table.getSelectedRowCount() > 0));
		table.getInputMap(JComponent.WHEN_FOCUSED)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelectedRows");
		table.getActionMap().put("deleteSelectedRows", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				acqRemoveSelected(table, model);
			}
		});

		JButton cleanBtn = new JButton(buildBrushIcon());
		cleanBtn.setToolTipText("Remove all rows with quantity 0");
		cleanBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cleanBtn.setForeground(Color.WHITE);
		cleanBtn.setFocusPainted(false);
		cleanBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		cleanBtn.setMargin(new Insets(2, 4, 2, 4));
		cleanBtn.addActionListener(e -> acqClean(model));

		JButton clearBtn = acqTextButton("Clear", new Color(220, 100, 100));
		clearBtn.setToolTipText("Remove every row from the collection log");
		clearBtn.addActionListener(e -> acqClear());

		JButton[] btns = {addBtn, removeBtn, cleanBtn, clearBtn};
		int btnHeight = 0;
		for (JButton b : btns)
			btnHeight = Math.max(btnHeight, b.getPreferredSize().height);

		for (JButton b : btns)
			b.setPreferredSize(new Dimension(b.getPreferredSize().width, btnHeight));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttons.setBorder(new EmptyBorder(4, 0, 0, 0));
		for (JButton b : btns)
			buttons.add(b);

		JPanel content = new JPanel(new BorderLayout(0, 6));
		content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		content.add(scroll, BorderLayout.CENTER);
		content.add(buttons, BorderLayout.SOUTH);

		updateAcqPopoutButton();

		showPopout("Item Collection Log", content, it -> { }, () -> {
			acqPopoutModel = null;
			acqPopoutTable = null;
			acqPopoutScroll = null;
			updateAcqPopoutButton();
		});
	}

	private JButton acqTextButton(String text, Color fg)
	{
		JButton b = new JButton(text);
		b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		b.setForeground(fg);
		b.setFocusPainted(false);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setMargin(new Insets(2, 5, 2, 5));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	// ---- Shared collection-log mutations (used by the pop-out table) ----

	private void acqAddRow(JTable table, AcquisitionsTableModel model)
	{
		TrackedItem t = currentItems.get(detailItemId);
		if (t == null)
			return;

		long price = t.getAvgPrice() > 0 ? t.getAvgPrice() : 0;
		t.getAcquisitions().add(new AcquisitionRecord(0, price, null));
		model.fireTableDataChanged();
		table.revalidate();
		onAcquisitionsEdited.accept(detailItemId);
		JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
		if (sp != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				JScrollBar bar = sp.getVerticalScrollBar();
				bar.setValue(bar.getMaximum());
			});
		}

		int newRow = model.getRowCount() - 1;
		if (newRow >= 0 && table.editCellAt(newRow, 0))
		{
			table.changeSelection(newRow, 0, false, false);
			Component editor = table.getEditorComponent();
			if (editor != null)
				editor.requestFocusInWindow();
		}
	}

	private void acqRemoveSelected(JTable table, AcquisitionsTableModel model)
	{
		TrackedItem t = currentItems.get(detailItemId);
		if (t == null)
			return;

		if (table.isEditing())


			table.getCellEditor().stopCellEditing();

		int[] selected = table.getSelectedRows();
		if (selected.length == 0)
			return;

		List<AcquisitionRecord> records = t.getAcquisitions();
		java.util.Arrays.sort(selected);
		for (int i = selected.length - 1; i >= 0; i--)
		{
			if (selected[i] >= 0 && selected[i] < records.size())
				records.remove(selected[i]);
		}

		model.fireTableDataChanged();
		table.revalidate();
		onAcquisitionsEdited.accept(detailItemId);
	}

	private void acqClean(AcquisitionsTableModel model)
	{
		TrackedItem t = currentItems.get(detailItemId);
		if (t == null)
			return;

		if (t.getAcquisitions().removeIf(r -> r.getQuantity() == 0))
		{
			model.fireTableDataChanged();
			onAcquisitionsEdited.accept(detailItemId);
		}
	}

	private void acqClear()
	{
		TrackedItem t = currentItems.get(detailItemId);
		if (t == null || t.getAcquisitions().isEmpty())
			return;

		int choice = JOptionPane.showConfirmDialog(
				ItemTrackerPanel.this,
				"Clear the entire collection log for this item?",
				"Clear Collection Log",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION)
			return;

		if (onClearAcquisitions != null)


			onClearAcquisitions.accept(detailItemId);
	}

	/** Small "open in a larger window" icon for a section header. */
	private Icon buildPopoutIcon()
	{
		int s = 11;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g.setStroke(new BasicStroke(1f));
		// A small window in the lower-left with an arrow pointing out to the upper-right.
		g.drawRect(0, 4, 6, 6);
		g.drawLine(4, 6, s - 1, 0);   // arrow shaft
		g.drawLine(s - 5, 0, s - 1, 0); // arrowhead, top edge
		g.drawLine(s - 1, 0, s - 1, 4); // arrowhead, right edge
		g.dispose();
		return new ImageIcon(img);
	}

	private JButton buildPopoutButton(Runnable onClick)
	{
		JButton btn = new JButton(buildPopoutIcon());
		btn.setToolTipText("Open in a larger window");
		btn.setBackground(ColorScheme.DARK_GRAY_COLOR);
		btn.setFocusPainted(false);
		btn.setBorder(new EmptyBorder(2, 4, 2, 4));
		btn.setContentAreaFilled(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.addActionListener(e -> onClick.run());
		return btn;
	}

	/**
	 * Applies fonts, renderers and combo-box editors to the Notifications table.
	 * Metric / Time Frame / Operation are fixed dropdowns; the Value column uses a
	 * per-row editor that switches between a dropdown (categorical metrics) and a
	 * text field (numeric metrics).
	 */
	private void applyNotificationRenderers()
	{
		Font f = FontManager.getRunescapeSmallFont();
		NotifCellRenderer renderer = new NotifCellRenderer();

		JComboBox<NotificationMetric> metricCombo = new JComboBox<>(NotificationMetric.values());
		metricCombo.setFont(f);
		JComboBox<TimeWindow> timeCombo = new JComboBox<>(OVERVIEW_WINDOWS);
		timeCombo.setFont(f);
		JComboBox<NotificationOperation> opCombo = new JComboBox<>(NotificationOperation.values());
		opCombo.setFont(f);

		notificationsTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(metricCombo));
		notificationsTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(timeCombo));
		notificationsTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(opCombo));
		notificationsTable.getColumnModel().getColumn(3).setCellEditor(new NotificationValueEditor());

		for (int i = 0; i < notificationsTable.getColumnCount(); i++)
			notificationsTable.getColumnModel().getColumn(i).setCellRenderer(renderer);

		TableCellRenderer hr = notificationsTable.getTableHeader().getDefaultRenderer();
		if (hr instanceof DefaultTableCellRenderer)
			((DefaultTableCellRenderer) hr).setHorizontalAlignment(SwingConstants.CENTER);
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
			title.setBorder(new EmptyBorder(10, 0, 6, 0));

		return title;
	}

	private void showDetail(int itemId)
	{
		TrackedItem item = currentItems.get(itemId);
		if (item == null)
			return;

		detailItemId = itemId;
		populateDetail(item);
		footerPanel.setVisible(false);
		cardLayout.show(cardsHost, CARD_DETAIL);
		if (onRequestDetailData != null)
			onRequestDetailData.accept(itemId);
	}

	private void showMain()
	{
		detailItemId = -1;
		closePopouts();
		footerPanel.setVisible(true);
		cardLayout.show(cardsHost, CARD_MAIN);
	}

	/**
	 * Footer Clear action: confirms with the user, then asks the plugin to remove
	 * every tracked item (along with its notifications and collection log).
	 */
	private void confirmAndClearAll()
	{
		int count = currentItems.size();
		if (count == 0)
			return;

		int choice = JOptionPane.showConfirmDialog(
				this,
				"Remove all " + count + " tracked item" + (count == 1 ? "" : "s")
						+ ", including their notifications and collection log?\nThis cannot be undone.",
				"Clear tracked items",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION && onClearAll != null)
			onClearAll.run();
	}

	private void populateDetail(TrackedItem item)
	{
		// Apply any config-driven changes to row selection and section order/visibility
		// before filling in values (both are no-ops when nothing changed).
		rebuildOverviewGrid();
		applyDetailSectionLayout();

		AsyncBufferedImage icon = itemManager.getImage(item.getItemId());
		icon.addTo(detailIconLabel);
		detailNameLabel.setText(item.getName());

		int detailQty = item.getQuantity();
		detailQtyLabel.setText("Qty: " + GpFormat.shortValue(detailQty));
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
		populateOverviewLabels(overviewLabels, item, false);

		// --- Graphs ---
		if (priceGraph != null)
			priceGraph.setData(item.getSeries5m(), item.getSeries1h(), item.getSeries6h(), item.getSeries24h(), item.getAvgPrice());

		if (volumeGraph != null)
			volumeGraph.setData(item.getSeries5m(), item.getSeries1h(), item.getSeries6h(), item.getSeries24h(), item.getAvgPrice());

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

		// --- Notifications ---
		// Whenever the table is empty, seed it with blank default rows, mirroring
		// the collection log's ready-to-fill layout. This covers both a fresh item
		// and one whose rules were all removed, so there are always blank lines to
		// fill in rather than a collapsed, header-only section.
		if (item.getNotifications().isEmpty())
		{
			for (int i = 0; i < DEFAULT_NOTIFICATION_ROWS; i++)
				item.getNotifications().add(new NotificationRule());

			item.setNotificationsInitialized(true);
			notifyNotificationsEdited();
		}

		// setItem fires a structure change that drops the custom editors/renderers,
		// so they are re-applied immediately afterwards. Skip this while the user is
		// actively editing a cell: a periodic price refresh would otherwise yank the
		// open editor out from under them and leave the table's editing state stuck,
		// freezing the grid until the next refresh clears it.
		if (!notificationsTable.isEditing())
		{
			notificationsModel.setItem(item);
			applyNotificationRenderers();
			notificationsTable.revalidate();
		}

		// --- Collection Log ---
		if (!acquisitionsTable.isEditing())
		{
			acquisitionsModel.setItem(item);
			applyTableRenderers();
			acquisitionsTable.revalidate();
		}

		acquisitionsSection.setVisible(item.getMode() != TrackItemMode.VIEW);
		updateAcqPopoutButton();

		// Mirror the data into the popped-out collection log, if open.
		if (acqPopoutModel != null)
		{
			acqPopoutModel.setItem(item);
			applyAcqRenderers(acqPopoutTable, acqPopoutModel, true);
			acqPopoutTable.revalidate();
		}

		// Keep any detached section windows in sync with the item on screen.
		refreshPopouts(item);
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
	 * {@code full} renders the complete number instead of the abbreviated form.
	 */
	private void setPriceCell(JLabel label, long value, Color color, String tooltipLabel, Color tint, boolean full)
	{
		label.setForeground(color);
		if (value <= 0)
			setOverviewPlaceholder(label);
		else if (full)
		{
			removeHoverTint(label);
			label.setText(NUMBER_FORMAT.format(value));
			label.setToolTipText((tooltipLabel == null ? "" : tooltipLabel + ": ") + NUMBER_FORMAT.format(value) + " gp");
		}
		else
			installShortValue(label, value, GpFormat.shortValue1dp(value), tooltipLabel, tint);
	}

	/** Removes any hover-to-tint listener so a label can show plain static text. */
	private static void removeHoverTint(JLabel label)
	{
		for (MouseListener ml : label.getMouseListeners())
		{
			if (ml instanceof HoverTintListener)
				label.removeMouseListener(ml);
		}
	}

	private static long overviewMidpoint(WikiRealtimePriceClient.PricePoint p)
	{
		long h = p.getAvgHighPrice();
		long l = p.getAvgLowPrice();
		if (h > 0 && l > 0)
			return (h + l) / 2;

		return Math.max(h, l);
	}

	private long windowVolume(TrackedItem item, TimeWindow window)
	{
		PriceStats s = item.getWindowStats().get(window);
		return s == null ? 0 : s.getVolume();
	}

	private void installVolumeValue(JLabel label, long vol, boolean full)
	{
		label.setForeground(COLOR_VOLUME);
		label.setToolTipText("Volume: " + NUMBER_FORMAT.format(vol));
		if (full)
		{
			removeHoverTint(label);
			label.setText(NUMBER_FORMAT.format(vol));
			return;
		}

		String text = GpFormat.shortValue1dp(vol);
		label.setText(text);
		removeHoverTint(label);
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
				label.removeMouseListener(ml);
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
		return window.getLongLabel().toLowerCase(java.util.Locale.ROOT);
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
			return 0;

		long tax = (long) Math.floor(avgPrice * 0.02);
		return Math.min(tax, 5_000_000L);
	}

	private void applyVolatility(TrackedItem item)
	{
		String label = MarketClassifier.volatility(item.getSeriesFor(TimeWindow.WEEK));
		if (label == null)
		{
			miVolatility.setText("—");
			miVolatility.setForeground(Color.WHITE);
			miVolatility.setToolTipText(null);
			return;
		}

		Color color;
		String tooltip;
		switch (label)
		{
			case "Low":
				color = COLOR_HIGH; tooltip = "Stable Price";
				break;
			case "Medium":
				color = COLOR_AVG; tooltip = "Occasional/Moderate Price Swings";
				break;
			default:
				color = COLOR_LOW; tooltip = "Large/Frequent Price Swings";
				break;
		}

		miVolatility.setText(label);
		miVolatility.setForeground(color);
		miVolatility.setToolTipText(tooltip);
	}

	private void applyLiquidity(long vol24)
	{
		String label = MarketClassifier.liquidity(vol24);
		if (label == null)
		{
			miLiquidity.setText("—");
			miLiquidity.setForeground(Color.WHITE);
			miLiquidity.setToolTipText(null);
			return;
		}

		Color color = "Low".equals(label) ? COLOR_LOW : "Medium".equals(label) ? COLOR_AVG : COLOR_HIGH;
		miLiquidity.setText(label);
		miLiquidity.setForeground(color);
		miLiquidity.setToolTipText("24h volume: " + NUMBER_FORMAT.format(vol24));
	}

	/**
	 * Classifies where the current average sits within the 30-day range and
	 * shows it as Highest / High / High Avg / Average / Low Avg / Low / Lowest.
	 */
	private void applyRangePosition(long min, long max, long live)
	{
		String text = MarketClassifier.rangePosition(min, max, live);
		if (text == null)
		{
			rangePositionLabel.setText("-");
			rangePositionLabel.setForeground(COLOR_VOLUME);
			return;
		}

		Color color;
		switch (text)
		{
			case "Highest":
			case "High":
				color = COLOR_HIGH;
				break;
			case "Low":
			case "Lowest":
				color = COLOR_LOW;
				break;
			default:
				color = COLOR_AVG;
				break;
		}

		rangePositionLabel.setText(text);
		rangePositionLabel.setForeground(color);
	}

	private long[] thirtyDayRange(TrackedItem item)
	{
		return MarketClassifier.thirtyDayRange(item.getSeriesFor(TimeWindow.MONTH));
	}

	private void applyTableRenderers()
	{
		applyAcqRenderers(acquisitionsTable, acquisitionsModel, false);
	}

	private static final String[] ACQ_FULL_HEADERS = {"Quantity", "Bought Price", "Sold Price", "Profit"};

	/**
	 * Applies fonts, row height, renderers, editors and header titles to a
	 * collection-log table. The {@code expanded} pop-out uses a larger monospaced
	 * font, full-format numbers and spelled-out headers.
	 */
	private void applyAcqRenderers(JTable table, AcquisitionsTableModel model, boolean expanded)
	{
		Font f = expanded ? new Font(Font.MONOSPACED, Font.PLAIN, 18) : FontManager.getRunescapeSmallFont();
		table.setFont(f);
		table.setRowHeight(expanded ? 30 : 22);
		table.getTableHeader().setFont(f);

		JTextField centerEditorField = new JTextField();
		centerEditorField.setHorizontalAlignment(SwingConstants.CENTER);
		centerEditorField.setFont(f);
		DefaultCellEditor centerEditor = new DefaultCellEditor(centerEditorField);

		int cols = table.getColumnCount();
		for (int i = 0; i < cols; i++)
		{
			TableColumn col = table.getColumnModel().getColumn(i);
			col.setCellRenderer(new AcqCellRenderer(i == 3, expanded));
			if (i != 3)
				col.setCellEditor(centerEditor);

			col.setHeaderValue(expanded ? ACQ_FULL_HEADERS[i] : model.getColumnName(i));
		}

		TableCellRenderer hr = table.getTableHeader().getDefaultRenderer();
		if (hr instanceof DefaultTableCellRenderer)
			((DefaultTableCellRenderer) hr).setHorizontalAlignment(SwingConstants.CENTER);

		table.getTableHeader().repaint();
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
				return (long) rec.getQuantity() * (rec.getSoldAt() - rec.getBoughtAt());

			if (item != null && item.getLowPrice() > 0)
				return (long) rec.getQuantity() * (item.getLowPrice() - rec.getBoughtAt());

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
			if (item == null || r < 0 || r >= item.getAcquisitions().size())
				return;

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
							rec.setSoldAt(null);
						else
							rec.setSoldAt(Math.max(0, Long.parseLong(s)));

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
	 * Backs the Notifications table. Each row is a {@link NotificationRule};
	 * editing a cell mutates the rule in place and enforces the per-metric locks
	 * (categorical metrics force the {@code =} operator; the 30-day range forces a
	 * one-month window; quantity has no window).
	 */
	private class NotificationsTableModel extends AbstractTableModel
	{
		private final String[] COLS = {"Metric", "Time", "Op", "Value"};
		private TrackedItem item;

		void setItem(TrackedItem item)
		{
			this.item = item;
			fireTableStructureChanged();
		}

		@Override public int getRowCount() { return item == null ? 0 : item.getNotifications().size(); }
		@Override public int getColumnCount() { return COLS.length; }
		@Override public String getColumnName(int c) { return COLS[c]; }

		@Override
		public boolean isCellEditable(int r, int c)
		{
			if (item == null || r < 0 || r >= item.getNotifications().size())
				return false;

			NotificationMetric m = item.getNotifications().get(r).getMetric();
			switch (c)
			{
				case 1: return m == null || (!m.isTimeframeDisabled() && !m.locksTimeframeToMonth());
				case 2: return m == null || !m.locksOperationToEquals();
				default: return true;
			}
		}

		@Override
		public Object getValueAt(int r, int c)
		{
			NotificationRule rule = item.getNotifications().get(r);
			NotificationMetric m = rule.getMetric();
			switch (c)
			{
				case 0: return m;
				case 1: return m != null && m.isTimeframeDisabled() ? "—" : rule.getTimeWindow();
				case 2: return rule.getOperation();
				case 3: return rule.getValue();
				default: return "";
			}
		}

		@Override
		public void setValueAt(Object value, int r, int c)
		{
			if (item == null || r < 0 || r >= item.getNotifications().size())
				return;

			NotificationRule rule = item.getNotifications().get(r);
			switch (c)
			{
				case 0:
					if (!(value instanceof NotificationMetric) || value == rule.getMetric())
						return;

					NotificationMetric m = (NotificationMetric) value;
					rule.setMetric(m);
					// Populate the rest of the row with sensible, valid defaults so a
					// freshly-chosen metric yields a complete rule.
					if (m.locksTimeframeToMonth())
						rule.setTimeWindow(TimeWindow.MONTH);
					else if (m.isTimeframeDisabled())
						rule.setTimeWindow(null);
					else if (rule.getTimeWindow() == null)
						rule.setTimeWindow(TimeWindow.LIVE);

					if (m.locksOperationToEquals())
						rule.setOperation(NotificationOperation.EQ);
					else if (rule.getOperation() == null)
						rule.setOperation(NotificationOperation.GTE);

					rule.setValue(m.isCategorical() ? m.getOptions().get(0) : "");
					fireTableRowsUpdated(r, r);
					break;
				case 1:
					if (!(value instanceof TimeWindow))
						return;

					rule.setTimeWindow((TimeWindow) value);
					break;
				case 2:
					if (!(value instanceof NotificationOperation))
						return;

					rule.setOperation((NotificationOperation) value);
					break;
				case 3:
					applyValueEdit(rule, value == null ? "" : value.toString());
					fireTableRowsUpdated(r, r);
					break;
				default:
					return;
			}

			notifyNotificationsEdited();
		}

		private void applyValueEdit(NotificationRule rule, String raw)
		{
			NotificationMetric m = rule.getMetric();
			// No metric chosen yet (blank row): store the text as-is — there is no
			// kind to parse against, and an incomplete rule never fires anyway.
			if (m == null || m.isCategorical())
			{
				rule.setValue(raw.trim());
				return;
			}

			if (m.getKind() == NotificationMetric.Kind.PERCENT)
			{
				java.util.OptionalDouble v = NotificationRule.parsePercent(raw);
				if (v.isPresent())
					rule.setValue(NotificationRule.formatPercent(v.getAsDouble()));

				return;
			}

			java.util.OptionalDouble v = NotificationRule.parseNumeric(raw);
			if (v.isPresent())
				rule.setValue(GpFormat.shortValue((long) v.getAsDouble()));
		}

	}

	/** Value-column editor: a dropdown for categorical metrics, a text field otherwise. */
	private class NotificationValueEditor extends AbstractCellEditor implements TableCellEditor
	{
		private final JComboBox<String> combo = new JComboBox<>();
		private final JTextField field = new JTextField();
		private JComponent active;

		NotificationValueEditor()
		{
			combo.setFont(FontManager.getRunescapeSmallFont());
			field.setFont(FontManager.getRunescapeSmallFont());
			field.setHorizontalAlignment(SwingConstants.CENTER);
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
		{
			NotificationMetric metric = null;
			TrackedItem t = currentItems.get(detailItemId);
			if (t != null && row >= 0 && row < t.getNotifications().size())
				metric = t.getNotifications().get(row).getMetric();

			if (metric != null && metric.isCategorical())
			{
				combo.removeAllItems();
				for (String opt : metric.getOptions())
					combo.addItem(opt);

				combo.setSelectedItem(value == null ? null : value.toString());
				active = combo;
			}
			else
			{
				field.setText(value == null ? "" : value.toString());
				active = field;
			}

			return active;
		}

		@Override
		public Object getCellEditorValue()
		{
			if (active == combo)
			{
				Object sel = combo.getSelectedItem();
				return sel == null ? "" : sel.toString();
			}

			return field.getText();
		}
	}

	/** Centered renderer for the Notifications table; shows enum/string labels. */
	private static class NotifCellRenderer extends DefaultTableCellRenderer
	{
		NotifCellRenderer()
		{
			setHorizontalAlignment(SwingConstants.CENTER);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
				boolean isSelected, boolean hasFocus, int row, int column)
		{
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			setText(value == null ? "" : value.toString());
			if (!isSelected)
			{
				setBackground(table.getBackground());
				setForeground(Color.WHITE);
			}

			return this;
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
		private final boolean expanded;

		AcqCellRenderer(boolean profit, boolean expanded)
		{
			this.profit = profit;
			this.expanded = expanded;
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
			// The expanded pop-out always shows full comma-grouped numbers.
			boolean shortForm = !expanded && Math.abs(v) >= (profit ? 1000 : 10000);
			String text = shortForm
					? GpFormat.shortValue(v)
					: NUMBER_FORMAT.format(v);
			if (profit && v > 0)
				text = "+" + text;

			setText(text);

			Color fg = profit ? (v > 0 ? COLOR_HIGH : v < 0 ? COLOR_LOW : Color.WHITE) : Color.WHITE;
			setForeground(isSelected ? table.getSelectionForeground() : fg);

			boolean hovered = shortForm && row == acqHoverRow && column == acqHoverCol;
			if (isSelected)
				setBackground(table.getSelectionBackground());
			else if (hovered)
			{
				setForeground(fg);
				setBackground(profit ? (v >= 0 ? TINT_HIGH : TINT_LOW) : TINT_VOLUME);
			}
			else
				setBackground(table.getBackground());

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
				return lerp(RANGE_RED, RANGE_GOLD, f / 0.5);

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
