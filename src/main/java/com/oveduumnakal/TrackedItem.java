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

@Data
public class TrackedItem
{
	private final int itemId;
	private final String name;
	private int quantity;

	private boolean tradeable = true;
	private boolean priceLoadFailed;

	private long highPrice;
	private long lowPrice;
	private long avgPrice;

	private int highDelta;
	private int lowDelta;
	private int avgDelta;
	private long prevHighPrice;
	private long prevLowPrice;
	private long prevAvgPrice;
	private boolean hasDeltas;

	private boolean costBasisInitialized;
	private List<AcquisitionRecord> acquisitions = new ArrayList<>();

	private TrackItemMode mode = TrackItemMode.TRACK;
	private Map<TimeWindow, PriceStats> windowStats = new EnumMap<>(TimeWindow.class);

	// Timeseries at the Wiki resolutions, used by the detailed view.
	private transient List<WikiRealtimePriceClient.PricePoint> series5m = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series1h = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series6h = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series24h = new ArrayList<>();

	// Static item metadata sourced from the Wiki /mapping endpoint.
	private int buyLimit;
	private long geValue;
	private long highAlch;
	private long lowAlch;
	private boolean metadataLoaded;

	/**
	 * Returns the timeseries resolution best suited to the given window.
	 * Short windows use 5m data, week/month use 6h, year uses 24h.
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

	public long getCostBasis()
	{
		long sum = 0;
		for (AcquisitionRecord r : acquisitions)
		{
			if (r.getSoldAt() == null)
			{
				sum += (long) r.getQuantity() * r.getBoughtAt();
			}
		}
		return sum;
	}

	public long getRealizedProfit()
	{
		long sum = 0;
		for (AcquisitionRecord r : acquisitions)
		{
			if (r.getSoldAt() != null)
			{
				sum += (long) r.getQuantity() * (r.getSoldAt() - r.getBoughtAt());
			}
		}
		return sum;
	}

	public long getUnrealizedProfit()
	{
		long sum = 0;
		for (AcquisitionRecord r : acquisitions)
		{
			if (r.getSoldAt() == null)
			{
				sum += (long) r.getQuantity() * (lowPrice - r.getBoughtAt());
			}
		}
		return sum;
	}

	public int getRecordQuantitySum()
	{
		int sum = 0;
		for (AcquisitionRecord r : acquisitions)
		{
			if (r.getSoldAt() == null)
			{
				sum += r.getQuantity();
			}
		}
		return sum;
	}

	public long getProfit()
	{
		return getRealizedProfit() + getUnrealizedProfit();
	}
}
