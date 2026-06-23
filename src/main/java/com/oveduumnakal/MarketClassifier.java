/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives the human-readable market ratings shown for an item &ndash;
 * volatility, liquidity, and where today's price sits within its 30-day range
 * &ndash; from raw wiki price history.
 *
 * <p>Each method maps continuous figures onto coarse {@code "Low"}/{@code
 * "Medium"}/{@code "High"}-style buckets, returning {@code null} when there is
 * not enough data to classify. This is a stateless package-private utility.
 */
final class MarketClassifier
{
	private MarketClassifier()
	{
	}

	/**
	 * Rates price volatility over the past week as the coefficient of variation
	 * (standard deviation / mean) of the high/low samples.
	 *
	 * @param weekSeries 1h price points covering (at least) the last week
	 * @return {@code "Low"} (&lt;1.5%), {@code "Medium"} (&le;5%), {@code "High"},
	 *         or {@code null} if fewer than two samples are available
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
	 * Rates trading liquidity from the last 24h of volume.
	 *
	 * @param vol24 units traded in the past day
	 * @return {@code "Low"} (&lt;500), {@code "Medium"} (&le;5000), {@code "High"},
	 *         or {@code null} when volume is unknown
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

	/**
	 * Finds the lowest low and highest high over the past 30 days.
	 *
	 * @param monthSeries price points covering (at least) the last month
	 * @return a two-element {@code [min, max]} array, or {@code [0, 0]} if no
	 *         in-window samples were found
	 */
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
	 * Labels where the live price falls within a {@code [min, max]} range as a
	 * fraction of the way up, from {@code "Lowest"} to {@code "Highest"}.
	 *
	 * @param min  range low (e.g. from {@link #thirtyDayRange})
	 * @param max  range high
	 * @param live the current price
	 * @return a position label, or {@code null} if the range or live price is invalid
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
