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

/**
 * The plugin's side panel and its entire Swing UI.
 *
 * <p>Uses a {@link CardLayout} to switch between two views: the main list of
 * tracked items (with search, totals, and per-row prices/value/profit) and a
 * per-item detail card (current values, market info, price/volume charts, a
 * price overview grid, alch info, notification rules, and an editable
 * acquisitions log). It also manages detail-section ordering/visibility, the
 * price-change pulse animations, and the chart pop-out windows.
 *
 * <p>The panel is purely a view: it never touches game state directly. All
 * actions (add/remove item, edit acquisitions/notifications, request detail
 * data, clear) are delegated to the plugin through the callbacks supplied to the
 * constructor, and the plugin pushes data back via {@link #rebuild} and
 * {@link #refreshDetailData}. All methods run on the Swing EDT.
 */
@Slf4j
public class StockpilePanel extends PluginPanel
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
	private final StockpileConfig config;
	private final BiConsumer<Integer, TrackItemMode> onAddItem;
	private final Consumer<Integer> onRemoveItem;
	private final Consumer<Integer> onAcquisitionsEdited;
	private final Consumer<Integer> onRequestDetailData;
	private final Consumer<Integer> onClearAcquisitions;
	private final Consumer<Integer> onNotificationsEdited;
	private final Runnable onClearAll;

	private final CardLayout cardLayout = new CardLayout();

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

	/** A transient, read-only item shown in the detail view via {@link #showPreview} but never added to the tracked list. */
	private TrackedItem previewItem;

	private final JPanel detailCard = new JPanel(new BorderLayout(0, 8));
	private final JLabel detailIconLabel = new JLabel();
	private final JLabel detailNameLabel = new JLabel();
	private final JLabel detailQtyLabel = new JLabel();

	private final JLabel icvHigh = new JLabel();
	private final JLabel icvLow = new JLabel();
	private final JLabel icvAvg = new JLabel();
	private final JLabel icvVolume = new JLabel();

	private JPanel ccvSection;
	private final JLabel ccvHigh = new JLabel();
	private final JLabel ccvLow = new JLabel();
	private final JLabel ccvAvg = new JLabel();
	private final JLabel ccvQuantity = new JLabel();
	private final JLabel ccvProfit = new JLabel();

	private final JLabel miBuyLimit = new JLabel();
	private final JLabel miGeTax = new JLabel();
	private final JLabel miVolatility = new JLabel();
	private final JLabel miLiquidity = new JLabel();
	private PriceRangeBar priceRangeBar;
	private final JLabel rangePositionLabel = new JLabel();

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

	private volatile boolean editingNotifications;
	private JPanel notificationsSection;
	private static final int DEFAULT_NOTIFICATION_ROWS = 5;

	private JButton acqPopoutButton;
	private AcquisitionsTableModel acqPopoutModel;
	private JTable acqPopoutTable;
	private JScrollPane acqPopoutScroll;
	private JPanel overviewGrid;
	private PriceGraphPanel priceGraph;
	private PriceGraphPanel volumeGraph;

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

	private final JPanel footerPanel = new JPanel(new BorderLayout());

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

	/** One in-flight price-change pulse: the label being animated, its base color, and the animation start time. */
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

	/**
	 * Builds the panel and its two cards (main list and detail view).
	 *
	 * @param itemManager           for item names, icons, and prices
	 * @param config                the plugin configuration
	 * @param onAddItem             callback to start tracking an item in a mode
	 * @param onRemoveItem          callback to stop tracking an item
	 * @param onAcquisitionsEdited  callback after the acquisitions log is edited
	 * @param onRequestDetailData   callback to request full history/metadata for an item
	 * @param onClearAcquisitions   callback to clear an item's acquisitions
	 * @param onNotificationsEdited callback after notification rules are edited
	 * @param onClearAll            callback to clear all tracked items
	 */
	public StockpilePanel(
			ItemManager itemManager,
			StockpileConfig config,
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

		JLabel title = new JLabel("Stockpile", SwingConstants.CENTER);
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setBorder(new EmptyBorder(0, 0, 4, 0));

		JPanel titleWrapper = new JPanel(new BorderLayout());
		titleWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titleWrapper.add(title, BorderLayout.CENTER);

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
		topPanel.add(titleWrapper);
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

		cardLayout.show(cardsHost, CARD_LOGGED_OUT);

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

		footerPanel.setVisible(false);
		getWrappedPanel().add(footerPanel, BorderLayout.SOUTH);

		refreshAgeTimer = new Timer(1000, e -> updateRefreshLabel());
		refreshAgeTimer.start();

		loadingGlowTimer = new Timer(50, e -> updateLoadingGlow());
		loadingGlowTimer.start();

		pulseTimer = new Timer(25, e -> updatePulses());
		pulseTimer.start();
	}

	/** Moves the GE estimates block above or below the other sections per the configured position. */
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

	/** Applies normal or compact row padding to the GE estimates block per the configured spacing. */
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

	/** Stops the animation timers so the panel can be disposed cleanly. */
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

	/** Begins a color pulse on a label (green up / red down) reflecting the sign of a price change. */
	private void startPulse(JLabel label, int delta)
	{
		label.setText(delta > 0 ? "▲" : delta < 0 ? "▼" : "–");
		Color base = delta > 0 ? COLOR_HIGH : delta < 0 ? COLOR_LOW : LOADING_COLOR;
		label.setForeground(new Color(base.getRed(), base.getGreen(), base.getBlue(), 0));
		pulseEntries.add(new PulseEntry(label, base, System.currentTimeMillis()));
	}

	/** Timer tick that advances every active pulse's color toward its base, retiring finished ones. */
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

	/** Updates the footer's "updated N ago" text from the last price-refresh timestamp. */
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

	/** Filters the add-item search dropdown to items matching the typed query. */
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

	/** Builds one clickable row in the search-results dropdown that adds the item when clicked. */
	private JPanel buildSearchResultRow(int itemId, String itemName)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel nameLabel = new JLabel();
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		setEllipsisText(nameLabel, itemName);

		JButton viewBtn = new JButton(buildEyeIcon(14));
		viewBtn.setPreferredSize(new Dimension(28, 22));
		viewBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		viewBtn.setForeground(Color.WHITE);
		viewBtn.setFocusPainted(false);
		viewBtn.setBorderPainted(true);
		viewBtn.setBorder(BorderFactory.createLineBorder(ColorScheme.LIGHT_GRAY_COLOR));
		viewBtn.setToolTipText("View prices only");
		viewBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		viewBtn.addActionListener(e ->
		{
			onAddItem.accept(itemId, TrackItemMode.VIEW);
			searchField.setText("");
			searchResultsPanel.setVisible(false);
		});

		Color addGreen = new Color(0, 153, 0);
		JButton addBtn = new JButton("+");
		addBtn.setPreferredSize(new Dimension(28, 22));
		addBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		addBtn.setForeground(addGreen);
		addBtn.setFocusPainted(false);
		addBtn.setBorderPainted(true);
		addBtn.setBorder(BorderFactory.createLineBorder(addGreen));
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

	/**
	 * Rebuilds the main item list from the latest tracked items and totals.
	 *
	 * <p>This is the primary entry point the plugin calls after any data change:
	 * it repopulates the rows, updates the value/profit totals and the refresh
	 * timestamp, and (when {@code indicatorMode} permits) starts pulse animations
	 * for items whose price moved.
	 */
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
			if (detail == null && previewItem != null && previewItem.getItemId() == detailItemId)
				detail = previewItem;

			if (detail != null)
			{
				final TrackedItem shown = detail;
				SwingUtilities.invokeLater(() -> populateDetail(shown));
			}
			else if (!currentItems.isEmpty())
			{

				SwingUtilities.invokeLater(this::showMain);
			}
		}
		else
		{

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

	/**
	 * Builds one row of the main list for a tracked item: icon, name, quantity,
	 * the configured data rows (prices/value/volume/profit), hover affordances,
	 * and a click handler that opens the item's detail view.
	 */
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

		JLabel nameLabel = new JLabel();
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		setEllipsisText(nameLabel, item.getName());

		JLabel qtyLabel = new JLabel("Qty: " + GpFormat.shortValue(item.getQuantity()));
		qtyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		qtyLabel.setFont(FontManager.getRunescapeSmallFont());
		qtyLabel.setToolTipText(NUMBER_FORMAT.format(item.getQuantity()));

		JPanel nameRow = new JPanel(new BorderLayout(6, 0));
		nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameRow.add(iconLabel, BorderLayout.WEST);
		nameRow.add(nameLabel, BorderLayout.CENTER);

		if (showQty)
			nameRow.add(qtyLabel, BorderLayout.EAST);

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

				JLabel pulse = createDeltaLabel();
				if (itemIndicatorMode != PriceIndicatorMode.OFF)
					pulseIfShown(pulse, item.getAvgDelta(), itemIndicatorMode);

				c.gridx = col++;
				c.weightx = 0;
				c.anchor = GridBagConstraints.WEST;
				pricesPanel.add(pulse, c);

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

	/** Attaches a mouse listener to a component and all its descendants, so a whole row reacts as one. */
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

	/** Installs a pre-formatted compact value on a label with a full-number tooltip and a hover tint. */
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

	private static final String ELLIPSIS = "…";

	private static final String FULL_TEXT_KEY = "stockpile.fullText";

	/**
	 * Assigns text to a label, truncating it with a trailing ellipsis so it fits the
	 * label's available width and exposing the untruncated text as a tooltip. The text
	 * is re-truncated automatically whenever the label is resized.
	 */
	private static void setEllipsisText(JLabel label, String fullText)
	{
		label.putClientProperty(FULL_TEXT_KEY, fullText);
		label.setToolTipText(fullText);

		boolean hasListener = Arrays.stream(label.getComponentListeners())
				.anyMatch(l -> l instanceof EllipsisResizeListener);

		if (!hasListener)
			label.addComponentListener(new EllipsisResizeListener());

		applyEllipsis(label);
	}

	/** Re-truncates a label's stored full text with an ellipsis to fit its current width. */
	private static void applyEllipsis(JLabel label)
	{
		Object stored = label.getClientProperty(FULL_TEXT_KEY);

		if (!(stored instanceof String))
			return;

		String fullText = (String) stored;
		Insets insets = label.getInsets();
		int available = label.getWidth() - insets.left - insets.right;

		if (available <= 0)
		{
			label.setText(fullText);
			return;
		}

		FontMetrics fm = label.getFontMetrics(label.getFont());

		if (fm.stringWidth(fullText) <= available)
		{
			label.setText(fullText);
			return;
		}

		int budget = available - fm.stringWidth(ELLIPSIS);

		if (budget <= 0)
		{
			label.setText(ELLIPSIS);
			return;
		}

		int end = 0;
		int used = 0;

		while (end < fullText.length() && used + fm.charWidth(fullText.charAt(end)) <= budget)
		{
			used += fm.charWidth(fullText.charAt(end));
			end++;
		}

		label.setText(fullText.substring(0, end) + ELLIPSIS);
	}

	/** Re-applies ellipsis truncation to its label whenever the label's width changes. */
	private static final class EllipsisResizeListener extends ComponentAdapter
	{
		@Override
		public void componentResized(ComponentEvent e)
		{
			applyEllipsis((JLabel) e.getComponent());
		}
	}

	private static String toHex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	/**
	 * Mouse listener that swaps a value label to a tinted background (and the full
	 * comma-grouped number) while hovered, restoring the compact text on exit.
	 */
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

	/** Formats a totals value as either full or abbreviated gp per the configured {@link ValueFormat}. */
	private static String formatTotalGp(long value, ValueFormat fmt)
	{
		return fmt == ValueFormat.FULL ? GpFormat.fullGp(value) : GpFormat.shortGp(value);
	}

	/**
	 * Constructs the detail-view card once: the header, the scrollable body, and
	 * every detail section (current values, market info, charts, overview grid,
	 * alch, notifications, acquisitions log). Sections are populated later per item.
	 */
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
		detailNameLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
				detailNameLabel.getFontMetrics(detailNameLabel.getFont()).getHeight()));
		detailQtyLabel.setForeground(Color.WHITE);
		detailQtyLabel.setFont(FontManager.getRunescapeSmallFont());

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
					StockpilePanel.this,
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

		rebuildOverviewGrid();
		applyDetailSectionLayout();
	}

	/** Builds the notifications section: the rules table and its add/remove/edit controls. */
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

		notificationsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

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

	/** Notifies the plugin (via callback) that the current item's notification rules changed, so it can persist them. */
	private void notifyNotificationsEdited()
	{
		if (onNotificationsEdited != null && detailItemId > 0)
			onNotificationsEdited.accept(detailItemId);
	}

	private JPanel buildDetailSection(String title, Component... contents)
	{
		JPanel wrapper = newSectionWrapper();
		wrapper.add(buildDetailSectionTitle(title, true));
		for (Component c : contents)
			wrapper.add(c);

		return wrapper;
	}

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

		row.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createCompoundBorder(
						new EmptyBorder(10, 0, 0, 0),
						new MatteBorder(1, 0, 0, 0, new Color(80, 80, 80))),
				new EmptyBorder(4, 0, 2, 0)));

		JLabel label = new JLabel(title, SwingConstants.CENTER);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeBoldFont());

		row.add(Box.createHorizontalStrut(popBtn.getPreferredSize().width), BorderLayout.WEST);
		row.add(label, BorderLayout.CENTER);
		row.add(popBtn, BorderLayout.EAST);
		return row;
	}

	/** Reorders and shows/hides the detail sections to match the configured slot assignments. */
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

	/** Rebuilds the price overview grid to match the configured preset of time-window rows. */
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

	/** @return the item id whose detail view is open, or a non-positive value when on the main list. */
	public int getDetailItemId()
	{
		return detailItemId;
	}

	/** @return whether the user is mid-edit in the notifications table, so the plugin should defer firing rules. */
	public boolean isEditingNotifications()
	{
		return editingNotifications;
	}

	/** Supplies the latest nature/fire rune prices used to compute high-alch profit in the detail view. */
	public void setAlchRunePrices(long naturePrice, long firePrice)
	{
		this.natureRunePrice = naturePrice;
		this.fireRunePrice = firePrice;
	}

	/** Re-populates the open detail view with fresh data for {@code itemId} (no-op if a different item is shown). */
	public void refreshDetailData(int itemId)
	{
		if (detailItemId != itemId)
			return;

		TrackedItem item = currentItems.get(itemId);
		if (item == null && previewItem != null && previewItem.getItemId() == itemId)
			item = previewItem;

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

				int vx = firstRow[0].getX() - 3;
				if (!windowLabels.isEmpty())
				{
					JLabel ref = windowLabels.get(0);
					vx = ref.getX() + ref.getWidth() + sepGap;
				}

				g.setColor(DIVIDER_COLOR);
				g.drawLine(vx, 4, vx, getHeight() - 4);

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

	/** (Re)creates the overview grid panels (sidebar and pop-out) for the given set of time-window rows. */
	private void populateOverviewGrid(Set<TimeWindow> rows)
	{
		fillOverviewGrid(overviewGrid, overviewLabels, overviewWindowLabels, rows,
				FontManager.getRunescapeSmallFont(), false);
	}

	/** Lays out the overview grid's header and one row of price/volume labels per selected time window. */
	private void fillOverviewGrid(JPanel grid, Map<TimeWindow, JLabel[]> labels,
			List<JLabel> windowLabels, Set<TimeWindow> rows, Font font, boolean expanded)
	{
		grid.removeAll();
		labels.clear();
		windowLabels.clear();

		int vPad = expanded ? 7 : 2;
		int hPad = expanded ? 9 : 1;
		int hPadSep = expanded ? 16 : 3;
		grid.setBorder(new EmptyBorder(expanded ? 10 : 6, expanded ? 14 : 3,
				expanded ? 10 : 2, expanded ? 14 : 3));

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

			c.insets = new Insets(vPad, i == 1 ? hPadSep : hPad, vPad, hPad);
			grid.add(h, c);
		}

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

	private static String fullWindowLabel(TimeWindow w)
	{
		return w.getLongLabel();
	}

	/** Fills the overview grid's cells with an item's per-window high/low/avg/volume/Δ% values. */
	private void populateOverviewLabels(Map<TimeWindow, JLabel[]> labels, TrackedItem item, boolean full)
	{
		for (TimeWindow w : OVERVIEW_WINDOWS)
		{
			JLabel[] cells = labels.get(w);
			if (cells == null)
				continue;

			if (w == TimeWindow.LIVE)
			{

				List<WikiRealtimePriceClient.PricePoint> s5 = item.getSeries5m();
				WikiRealtimePriceClient.PricePoint last = s5.isEmpty() ? null : s5.get(s5.size() - 1);
				long high = last == null ? 0 : last.getAvgHighPrice();
				long low = last == null ? 0 : last.getAvgLowPrice();
				long avg = last == null ? 0 : overviewMidpoint(last);
				setPriceCell(cells[0], high, COLOR_HIGH, "High", TINT_HIGH, full);
				setPriceCell(cells[1], low, COLOR_LOW, "Low", TINT_LOW, full);
				setPriceCell(cells[2], avg, COLOR_AVG, "Avg", TINT_AVG, full);
				setOverviewPlaceholder(cells[3]);
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

			if (s == null)
				setOverviewPlaceholder(cells[4]);
			else
				installVolumeValue(cells[4], s.getVolume(), full);
		}
	}

	/** Tracks one open pop-out window and the refresher used to push fresh item data into it. */
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

	private boolean graphSmooth = true;
	private PriceGraphPanel.LineSet graphLineSet = PriceGraphPanel.LineSet.ALL;
	private PriceGraphPanel pricePopoutGraph;

	/** Pushes fresh data for {@code item} into every open pop-out window. */
	private void refreshPopouts(TrackedItem item)
	{
		for (PopoutHandle h : new ArrayList<>(openPopouts))
			h.refresher.accept(item);
	}

	/** Disposes all open pop-out windows (e.g. when leaving the detail view). */
	private void closePopouts()
	{
		for (PopoutHandle h : new ArrayList<>(openPopouts))
			h.frame.dispose();

		openPopouts.clear();
	}

	/**
	 * Opens a non-modal pop-out window hosting {@code content}, registering its
	 * refresher so live updates reach it and running {@code onClose} when dismissed.
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

	/** Opens an expanded chart pop-out mirroring (and kept in sync with) the in-panel graph. */
	private void openGraphPopout(String title, PriceGraphPanel.Mode mode, PriceGraphPanel source)
	{
		PriceGraphPanel graph = new PriceGraphPanel(mode, true);
		graph.setActiveWindow(source.getActiveWindow());
		graph.setPreferredSize(new Dimension(640, mode == PriceGraphPanel.Mode.PRICE ? 460 : 360));

		Runnable onClose = null;
		if (mode == PriceGraphPanel.Mode.PRICE)
		{

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

	/** Opens the price overview grid in a standalone pop-out window. */
	private void openOverviewPopout()
	{
		Map<TimeWindow, JLabel[]> labels = new EnumMap<>(TimeWindow.class);
		List<JLabel> windowLabels = new ArrayList<>();
		JPanel grid = createOverviewGrid(labels, windowLabels, 12);

		Font big = new Font(Font.MONOSPACED, Font.PLAIN, 18);

		fillOverviewGrid(grid, labels, windowLabels, OverviewPreset.DETAILED.getWindows(), big, true);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.add(grid, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(top);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

		Consumer<TrackedItem> refresher = it -> populateOverviewLabels(labels, it, true);
		showPopout("Price Overview", scroll, refresher, null);
	}

	private void updateAcqPopoutButton()
	{
		if (acqPopoutButton == null)
			return;

		acqPopoutButton.setVisible(acqPopoutModel == null);
	}

	/** Opens the editable acquisitions (collection log) table in a standalone pop-out window. */
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

	/** Appends a new empty acquisition row to the table and scrolls it into view. */
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

	/** Removes the selected acquisition rows and commits the change. */
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

	/** Consolidates the acquisitions log, merging like rows and dropping empty ones. */
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

	/** Clears all acquisitions for the current item after confirmation, via the plugin callback. */
	private void acqClear()
	{
		TrackedItem t = currentItems.get(detailItemId);
		if (t == null || t.getAcquisitions().isEmpty())
			return;

		int choice = JOptionPane.showConfirmDialog(
				StockpilePanel.this,
				"Clear the entire collection log for this item?",
				"Clear Collection Log",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION)
			return;

		if (onClearAcquisitions != null)

			onClearAcquisitions.accept(detailItemId);
	}

	private Icon buildPopoutIcon()
	{
		int s = 11;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g.setStroke(new BasicStroke(1f));

		g.drawRect(0, 4, 6, 6);
		g.drawLine(4, 6, s - 1, 0);
		g.drawLine(s - 5, 0, s - 1, 0);
		g.drawLine(s - 1, 0, s - 1, 4);
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

	/** Wires the per-column cell editors/renderers on the notifications table to the current metric's input type. */
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

	/** Builds the market-info section (buy limit, GE value, volatility, liquidity, 30-day range, etc.). */
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

	/** Builds the alch-info section (high/low alch values and the high-alch profit estimate). */
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

	/** Loads the bundled {@code eye.png} scaled to a square icon for the view-only button. */
	private Icon buildEyeIcon(int size)
	{
		try
		{
			BufferedImage img = net.runelite.client.util.ImageUtil.loadImageResource(getClass(), "eye.png");
			return new ImageIcon(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
		}
		catch (Exception ex)
		{
			return new ImageIcon(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
		}
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

	/** Switches to the detail card for an item, requesting its full data and populating the view. */
	private void showDetail(int itemId)
	{
		TrackedItem item = currentItems.get(itemId);
		if (item == null)
			return;

		previewItem = null;
		detailItemId = itemId;
		populateDetail(item);
		footerPanel.setVisible(false);
		cardLayout.show(cardsHost, CARD_DETAIL);
		if (onRequestDetailData != null)
			onRequestDetailData.accept(itemId);
	}

	/**
	 * Opens a read-only preview of an untracked item in the detail view. Unlike
	 * {@link #showDetail}, the item is not in the tracked list; the plugin supplies
	 * its price/history data directly and the tracked-only sections stay hidden.
	 */
	public void showPreview(TrackedItem item)
	{
		previewItem = item;
		detailItemId = item.getItemId();
		populateDetail(item);
		footerPanel.setVisible(false);
		cardLayout.show(cardsHost, CARD_DETAIL);
	}

	/** Returns to the main item list, closing any open pop-outs. */
	private void showMain()
	{
		detailItemId = -1;
		previewItem = null;
		closePopouts();
		footerPanel.setVisible(true);
		cardLayout.show(cardsHost, CARD_MAIN);
	}

	/** Prompts for confirmation, then clears all tracked items via the plugin callback. */
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

	/**
	 * Fills every detail-view section with the given item's data: name/quantity,
	 * current values, market info, the charts, the overview grid, alch figures,
	 * notification rules, and the acquisitions log.
	 */
	private void populateDetail(TrackedItem item)
	{
		final boolean viewOnly = item.getMode() == TrackItemMode.VIEW;

		rebuildOverviewGrid();
		applyDetailSectionLayout();

		AsyncBufferedImage icon = itemManager.getImage(item.getItemId());
		icon.addTo(detailIconLabel);
		setEllipsisText(detailNameLabel, item.getName());

		int detailQty = item.getQuantity();
		detailQtyLabel.setText("Qty: " + GpFormat.shortValue(detailQty));
		detailQtyLabel.setToolTipText(NUMBER_FORMAT.format(detailQty));
		detailQtyLabel.setVisible(!viewOnly);

		final boolean hasPrices = item.hasPrices();
		final ValueFormat full = ValueFormat.FULL;

		icvHigh.setText("High: " + (hasPrices ? formatTotalGp(item.getHighPrice(), full) : "—"));
		icvLow.setText("Low: " + (hasPrices ? formatTotalGp(item.getLowPrice(), full) : "—"));
		icvAvg.setText("Average: " + (hasPrices ? formatTotalGp(item.getAvgPrice(), full) : "—"));
		long vol24 = windowVolume(item, TimeWindow.H24);
		icvVolume.setText("Volume (24h): " + (vol24 > 0 ? NUMBER_FORMAT.format(vol24) : "—"));

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

		populateOverviewLabels(overviewLabels, item, false);

		if (priceGraph != null)
			priceGraph.setData(item.getSeries5m(), item.getSeries1h(), item.getSeries6h(), item.getSeries24h(), item.getAvgPrice());

		if (volumeGraph != null)
			volumeGraph.setData(item.getSeries5m(), item.getSeries1h(), item.getSeries6h(), item.getSeries24h(), item.getAvgPrice());

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

		if (!viewOnly && item.getNotifications().isEmpty())
		{
			for (int i = 0; i < DEFAULT_NOTIFICATION_ROWS; i++)
				item.getNotifications().add(new NotificationRule());

			item.setNotificationsInitialized(true);
			notifyNotificationsEdited();
		}

		if (!viewOnly && !notificationsTable.isEditing())
		{
			notificationsModel.setItem(item);
			applyNotificationRenderers();
			notificationsTable.revalidate();
		}

		if (!viewOnly && !acquisitionsTable.isEditing())
		{
			acquisitionsModel.setItem(item);
			applyTableRenderers();
			acquisitionsTable.revalidate();
		}

		notificationsSection.setVisible(!viewOnly);
		acquisitionsSection.setVisible(!viewOnly);
		updateAcqPopoutButton();

		if (acqPopoutModel != null)
		{
			acqPopoutModel.setItem(item);
			applyAcqRenderers(acqPopoutTable, acqPopoutModel, true);
			acqPopoutTable.revalidate();
		}

		refreshPopouts(item);
	}

	private void setOverviewPlaceholder(JLabel label)
	{
		clearItemValue(label, "-");
	}

	/** Sets a price cell's text (full or abbreviated), color, tooltip, and hover tint, or a placeholder if unset. */
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

	/** @return the total traded volume for an item over the given window, or 0 if unknown. */
	private long windowVolume(TrackedItem item, TimeWindow window)
	{
		PriceStats s = item.getWindowStats().get(window);
		return s == null ? 0 : s.getVolume();
	}

	/** Sets a volume cell's compact/full text with a full-number tooltip and hover tint, or a placeholder. */
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

	/** Sets a label to the signed percent change of the current price vs. the window average, colored up/down. */
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

	/** Sets a profit label to a signed, colored gp figure, or a placeholder when the profit is unknown. */
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

	/** Builds the tooltip breaking down an alch-profit figure: alch value minus item cost and rune cost. */
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

	/** @return the Grand Exchange sell tax on a unit at {@code avgPrice} (per the live GE tax rules). */
	private long geTax(long avgPrice)
	{
		if (avgPrice < 50)
			return 0;

		long tax = (long) Math.floor(avgPrice * 0.02);
		return Math.min(tax, 5_000_000L);
	}

	/** Sets the market-info volatility rating from the item's week series via {@link MarketClassifier}. */
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

	/** Sets the market-info liquidity rating from the last 24h volume via {@link MarketClassifier}. */
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

	/** Sets the market-info "30-day range position" rating for where the live price sits within its month range. */
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

	/** @return the {@code [min, max]} price range over the item's last 30 days via {@link MarketClassifier}. */
	private long[] thirtyDayRange(TrackedItem item)
	{
		return MarketClassifier.thirtyDayRange(item.getSeriesFor(TimeWindow.MONTH));
	}

	private void applyTableRenderers()
	{
		applyAcqRenderers(acquisitionsTable, acquisitionsModel, false);
	}

	private static final String[] ACQ_FULL_HEADERS = {"Quantity", "Bought Price", "Sold Price", "Profit"};

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

	/**
	 * Swing table model backing the editable acquisitions log: one row per
	 * {@link AcquisitionRecord} with quantity, buy price, sell price, and derived
	 * profit columns. Edits are written straight back to the item's records.
	 */
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
				// Ignore unparseable input and keep the prior cell value.
			}
		}
	}

	/**
	 * Swing table model backing the notification rules: one row per
	 * {@link NotificationRule} with metric, timeframe, operator, and value columns.
	 * Editing a cell mutates the rule and notifies the plugin to persist it.
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

	/**
	 * Cell editor for a notification rule's value column that adapts to the row's
	 * metric: a dropdown of allowed options for categorical metrics, or a free-text
	 * field for numeric/percent ones.
	 */
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

	/** Cell renderer for the notifications table, applying the panel's fonts/colors and centered alignment. */
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

	/** Cell renderer for the acquisitions table, coloring the profit column and formatting gp values. */
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

	/** Small custom-painted bar showing where the live price sits within its 30-day low/high range. */
	private static final class PriceRangeBar extends JPanel
	{
		private static final Color RANGE_RED = new Color(220, 100, 100);
		private static final Color RANGE_GOLD = new Color(255, 200, 0);
		private static final Color RANGE_GREEN = new Color(100, 220, 100);
		private static final int TRIANGLE_H = 9;
		private static final int BAR_H = 5;
		private static final int BAR_ARC = 3;

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
