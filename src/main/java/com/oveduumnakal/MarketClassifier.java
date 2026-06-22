/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure classifiers for the categorical market metrics (Volatility, Liquidity,
 * 30-day range position) shared by the detail-view Market Info panel and the
 * notification engine so both produce identical bucket labels.
 */
final class MarketClassifier
{
	private MarketClassifier()
	{
	}

	/**
	 * Classifies week-series price variability as {@code Low}/{@code Medium}/
	 * {@code High}, or {@code null} when there is too little data.
	 */
	static String volatility(List<WikiRealtimePriceClient.PricePoint> weekSeries)
	{
		long cutoff = System.currentTimeMillis() / 1000L - TimeWindow.WEEK.getDuration().getSeconds();
		List<Long> samples = new ArrayList<>();
		for (WikiRealtimePriceClient.PricePoint p : weekSeries)
		{
			if (p.getTimestamp() < cutoff)
				continue;

			if (p.getAvgHighPrice() > 0)
				samples.add(p.getAvgHighPrice());

			if (p.getAvgLowPrice() > 0)
				samples.add(p.getAvgLowPrice());
		}

		if (samples.size() < 2)
			return null;

		double mean = 0;
		for (long v : samples)
			mean += v;

		mean /= samples.size();
		double variance = 0;
		for (long v : samples)
			variance += (v - mean) * (v - mean);

		variance /= samples.size();
		double pct = mean > 0 ? Math.sqrt(variance) / mean * 100.0 : 0;

		if (pct < 1.5)
			return "Low";

		if (pct <= 5.0)
			return "Medium";

		return "High";
	}

	/**
	 * Classifies 24h volume as {@code Low}/{@code Medium}/{@code High}, or
	 * {@code null} when no volume is known.
	 */
	static String liquidity(long vol24)
	{
		if (vol24 <= 0)
			return null;

		if (vol24 < 500)
			return "Low";

		if (vol24 <= 5000)
			return "Medium";

		return "High";
	}

	/** Min/max average price over the last 30 days, or {@code {0, 0}} when unknown. */
	static long[] thirtyDayRange(List<WikiRealtimePriceClient.PricePoint> monthSeries)
	{
		long cutoff = System.currentTimeMillis() / 1000L - TimeWindow.MONTH.getDuration().getSeconds();
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (WikiRealtimePriceClient.PricePoint p : monthSeries)
		{
			if (p.getTimestamp() < cutoff)
				continue;

			if (p.getAvgLowPrice() > 0)
				min = Math.min(min, p.getAvgLowPrice());

			if (p.getAvgHighPrice() > 0)
				max = Math.max(max, p.getAvgHighPrice());
		}

		if (min == Long.MAX_VALUE || max == Long.MIN_VALUE)
			return new long[]{0, 0};

		return new long[]{min, max};
	}

	/**
	 * Classifies where {@code live} sits within the {@code [min, max]} range as
	 * one of Lowest / Low / Low Avg / Average / High Avg / High / Highest, or
	 * {@code null} when the range is unusable.
	 */
	static String rangePosition(long min, long max, long live)
	{
		if (max <= min || live <= 0)
			return null;

		double frac = Math.max(0, Math.min(1, (double) (live - min) / (max - min)));
		if (frac >= 0.985)
			return "Highest";

		if (frac >= 0.75)
			return "High";

		if (frac >= 0.60)
			return "High Avg";

		if (frac >= 0.40)
			return "Average";

		if (frac >= 0.25)
			return "Low Avg";

		if (frac > 0.015)
			return "Low";

		return "Lowest";
	}
}
