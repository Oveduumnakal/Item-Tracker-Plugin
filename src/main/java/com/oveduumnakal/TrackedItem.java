/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import lombok.Data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The full state of one item being tracked: its identity and quantity, the
 * latest wiki prices (high/low/average) and their deltas, per-window summary
 * stats and price history, GE metadata (buy limit, alch values), and the
 * acquisition lots that back its cost-basis profit calculations.
 *
 * <p>Price-history {@code series*} lists are {@code transient}: they are fetched
 * at runtime and not persisted with the rest of the item. The value/profit
 * accessors derive figures from {@code quantity}, the current prices, and the
 * {@link AcquisitionRecord} lots.
 */
@Data
public class TrackedItem
{
	private final int itemId;
	private final String name;
	private int quantity;

	private boolean tradeable = true;
	private boolean priceLoadFailed;

	private boolean favorite;
	private String category;
	private boolean onOverlay;

	private long highPrice;
	private long lowPrice;
	private long avgPrice;

	private long latestHighTime;
	private long latestLowTime;

	private int highDelta;
	private int lowDelta;
	private int avgDelta;
	private long prevHighPrice;
	private long prevLowPrice;
	private long prevAvgPrice;
	private boolean hasDeltas;

	private boolean costBasisInitialized;
	private List<AcquisitionRecord> acquisitions = new ArrayList<>();
	private List<NotificationRule> notifications = new ArrayList<>();

	private boolean notificationsInitialized;

	private TrackItemMode mode = TrackItemMode.TRACK;
	private Map<TimeWindow, PriceStats> windowStats = new EnumMap<>(TimeWindow.class);

	private transient List<WikiRealtimePriceClient.PricePoint> series5m = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series1h = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series6h = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series24h = new ArrayList<>();

	private int buyLimit;
	private long geValue;
	private long highAlch;
	private long lowAlch;
	private boolean metadataLoaded;

	/**
	 * Selects the price-history series whose sampling granularity best fits the
	 * given window: 1h points for a week, 6h for a month, 24h for quarter/half/year,
	 * and 5m points for anything shorter.
	 *
	 * @param window the time window being displayed
	 * @return the backing point list (live, not a copy)
	 */
	public List<WikiRealtimePriceClient.PricePoint> getSeriesFor(TimeWindow window)
	{
		switch (window)
		{
			case WEEK:
				return series1h;
			case MONTH:
				return series6h;
			case MONTH3:
			case MONTH6:
			case YEAR:
				return series24h;
			default:
				return series5m;
		}
	}

	public long getHighValue()
	{
		return (long) quantity * highPrice;
	}

	public long getLowValue()
	{
		return (long) quantity * lowPrice;
	}

	public long getAvgValue()
	{
		return (long) quantity * avgPrice;
	}

	public boolean hasPrices()
	{
		return highPrice > 0 || lowPrice > 0;
	}

	/** @return total gp paid for the lots still held (unsold acquisitions). */
	public long getCostBasis()
	{
		return acquisitions.stream()
				.filter(r -> r.getSoldAt() == null)
				.mapToLong(r -> (long) r.getQuantity() * r.getBoughtAt())
				.sum();
	}

	/** @return profit already locked in from sold lots: sum of qty * (sold - bought). */
	public long getRealizedProfit()
	{
		return acquisitions.stream()
				.filter(r -> r.getSoldAt() != null)
				.mapToLong(r -> (long) r.getQuantity() * (r.getSoldAt() - r.getBoughtAt()))
				.sum();
	}

	/** @return total units across the lots still held (unsold acquisitions). */
	public int getRecordQuantitySum()
	{
		return acquisitions.stream()
				.filter(r -> r.getSoldAt() == null)
				.mapToInt(AcquisitionRecord::getQuantity)
				.sum();
	}

	/**
	 * Total profit if the held lots were valued at {@code markPrice}: realized
	 * profit from sold lots plus the unrealized gain/loss on still-held lots.
	 *
	 * @param markPrice the per-unit price used to mark held lots to market
	 * @return realized plus unrealized profit in gp
	 */
	public long getProfitAt(long markPrice)
	{
		return getRealizedProfit() + acquisitions.stream()
				.filter(r -> r.getSoldAt() == null)
				.mapToLong(r -> (long) r.getQuantity() * (markPrice - r.getBoughtAt()))
				.sum();
	}
}
