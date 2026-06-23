/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client for the OSRS Wiki real-time prices API
 * (<a href="https://prices.runescape.wiki">prices.runescape.wiki</a>).
 *
 * <p>Fetches live high/low prices, item metadata (buy limit, GE value, alch
 * values), and historical time series, and aggregates a series into
 * {@link PriceStats}. Every call is defensive: network, HTTP, and malformed-JSON
 * failures are logged and return an empty result rather than throwing, and
 * individual bad entries are skipped.
 */
@Slf4j
public class WikiRealtimePriceClient
{
	private static final HttpUrl LATEST_URL = HttpUrl.parse(
			"https://prices.runescape.wiki/api/v1/osrs/latest");
	private static final String TIMESERIES_URL =
			"https://prices.runescape.wiki/api/v1/osrs/timeseries";
	private static final HttpUrl MAPPING_URL = HttpUrl.parse(
			"https://prices.runescape.wiki/api/v1/osrs/mapping");

	private static final String USER_AGENT = "RuneLite ItemTracker Plugin";

	/** The latest instant-buy ({@code high}) and instant-sell ({@code low}) prices for one item. */
	@Value
	public static class ItemPrices
	{
		long high;
		long low;

		/** @return the midpoint of high and low, or whichever side is present if only one is. */
		public long avg()
		{
			if (high > 0 && low > 0)
				return (high + low) / 2;

			return Math.max(high, low);
		}
	}

	/**
	 * One sample from a time series: the average high/low prices and traded
	 * volumes over the bucket ending at {@code timestamp} (epoch seconds).
	 */
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class PricePoint
	{
		private long timestamp;
		private long avgHighPrice;
		private long avgLowPrice;
		private long highPriceVolume;
		private long lowPriceVolume;
	}

	/** Static GE metadata for an item: buy {@code limit}, store {@code value}, and high/low alch values. */
	@Value
	public static class ItemMapping
	{
		int limit;
		long value;
		long highAlch;
		long lowAlch;
	}

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	public WikiRealtimePriceClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Fetches the latest high/low prices for every item from the {@code /latest}
	 * endpoint.
	 *
	 * @return a map of item id to prices, or an empty map on any failure
	 */
	public Map<Integer, ItemPrices> fetchAll()
	{
		Request request = new Request.Builder()
				.url(LATEST_URL)
				.header("User-Agent", USER_AGENT)
				.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				log.warn("Wiki price fetch failed: {}", response.code());
				return Collections.emptyMap();
			}

			JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
			JsonObject data = root.getAsJsonObject("data");
			if (data == null)
				return Collections.emptyMap();

			Map<Integer, ItemPrices> result = new HashMap<>(data.size());
			for (Map.Entry<String, JsonElement> entry : data.entrySet())
			{
				try
				{
					int id = Integer.parseInt(entry.getKey());
					JsonObject obj = entry.getValue().getAsJsonObject();
					long high = obj.has("high") && !obj.get("high").isJsonNull()
							? obj.get("high").getAsLong() : 0L;
					long low = obj.has("low") && !obj.get("low").isJsonNull()
							? obj.get("low").getAsLong() : 0L;
					result.put(id, new ItemPrices(high, low));
				}
				catch (NumberFormatException | IllegalStateException e)
				{
					// Skip a malformed entry rather than failing the whole fetch.
				}
			}

			return result;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Error fetching wiki prices", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * Fetches per-item GE metadata from the {@code /mapping} endpoint.
	 *
	 * @return a map of item id to {@link ItemMapping}, or an empty map on any failure
	 */
	public Map<Integer, ItemMapping> fetchMapping()
	{
		Request request = new Request.Builder()
				.url(MAPPING_URL)
				.header("User-Agent", USER_AGENT)
				.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				log.warn("Wiki mapping fetch failed: {}", response.code());
				return Collections.emptyMap();
			}

			JsonArray data = gson.fromJson(response.body().charStream(), JsonArray.class);
			if (data == null)
				return Collections.emptyMap();

			Map<Integer, ItemMapping> result = new HashMap<>(data.size());
			for (JsonElement el : data)
			{
				try
				{
					JsonObject obj = el.getAsJsonObject();
					if (!obj.has("id") || obj.get("id").isJsonNull())
						continue;

					int id = obj.get("id").getAsInt();
					int limit = (int) readLong(obj, "limit");
					long value = readLong(obj, "value");
					long highAlch = readLong(obj, "highalch");
					long lowAlch = readLong(obj, "lowalch");
					result.put(id, new ItemMapping(limit, value, highAlch, lowAlch));
				}
				catch (IllegalStateException | NumberFormatException e)
				{
					// Skip a malformed entry rather than failing the whole fetch.
				}
			}

			return result;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Error fetching wiki mapping", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * Fetches the price/volume history for one item at a given sampling step.
	 *
	 * @param itemId   the item to query
	 * @param timestep the bucket size, e.g. {@code "5m"}, {@code "1h"}, {@code "6h"}, {@code "24h"}
	 * @return the points oldest-first, or an empty list on any failure
	 */
	public List<PricePoint> fetchTimeseries(int itemId, String timestep)
	{
		HttpUrl url = HttpUrl.parse(TIMESERIES_URL).newBuilder()
				.addQueryParameter("timestep", timestep)
				.addQueryParameter("id", Integer.toString(itemId))
				.build();

		Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", USER_AGENT)
				.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				log.warn("Wiki timeseries fetch failed: {}", response.code());
				return Collections.emptyList();
			}

			JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
			JsonArray data = root == null ? null : root.getAsJsonArray("data");
			if (data == null)
				return Collections.emptyList();

			List<PricePoint> points = new ArrayList<>(data.size());
			for (JsonElement el : data)
			{
				try
				{
					JsonObject obj = el.getAsJsonObject();
					long ts = readLong(obj, "timestamp");
					long avgHigh = readLong(obj, "avgHighPrice");
					long avgLow = readLong(obj, "avgLowPrice");
					long highVol = readLong(obj, "highPriceVolume");
					long lowVol = readLong(obj, "lowPriceVolume");
					points.add(new PricePoint(ts, avgHigh, avgLow, highVol, lowVol));
				}
				catch (IllegalStateException e)
				{
					// Skip a malformed point rather than failing the whole fetch.
				}
			}

			return points;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Error fetching wiki timeseries", e);
			return Collections.emptyList();
		}
	}

	/** Reads a numeric field as a long, returning 0 when absent, null, or non-numeric. */
	private static long readLong(JsonObject obj, String key)
	{
		if (!obj.has(key) || obj.get(key).isJsonNull())
			return 0L;

		try
		{
			return obj.get(key).getAsLong();
		}
		catch (NumberFormatException | IllegalStateException e)
		{
			return 0L;
		}
	}

	/**
	 * Aggregates a time series into summary {@link PriceStats} over a window.
	 *
	 * <p>Points older than the window are ignored. High and low are simple
	 * averages of samples that have both a price and volume on that side; the
	 * average is volume-weighted across both sides (falling back to the high/low
	 * midpoint when no volume is present). Volume is the total units traded.
	 *
	 * @param points the item's history (may be {@code null} or empty)
	 * @param window the look-back window; a zero duration means "all points"
	 * @return the computed stats, or all-zero stats when there is no data
	 */
	public static PriceStats computeStats(List<PricePoint> points, TimeWindow window)
	{
		if (points == null || points.isEmpty())
			return new PriceStats(0, 0, 0, 0);

		long nowSec = System.currentTimeMillis() / 1000L;
		long cutoff = window.getDuration().getSeconds() > 0
				? nowSec - window.getDuration().getSeconds()
				: Long.MIN_VALUE;

		long highSum = 0, lowSum = 0;
		int highCount = 0, lowCount = 0;
		long weightedSum = 0;
		long totalVol = 0;
		long volume = 0;

		for (PricePoint p : points)
		{
			if (p.getTimestamp() < cutoff)
				continue;

			long hv = p.getHighPriceVolume();
			long lv = p.getLowPriceVolume();
			if (p.getAvgHighPrice() > 0 && hv > 0)
			{
				highSum += p.getAvgHighPrice();
				highCount++;
			}

			if (p.getAvgLowPrice() > 0 && lv > 0)
			{
				lowSum += p.getAvgLowPrice();
				lowCount++;
			}

			weightedSum += p.getAvgHighPrice() * hv + p.getAvgLowPrice() * lv;
			totalVol += hv + lv;
			volume += hv + lv;
		}

		long high = highCount > 0 ? Math.round((double) highSum / highCount) : 0;
		long low = lowCount > 0 ? Math.round((double) lowSum / lowCount) : 0;
		long avg;
		if (totalVol > 0)
			avg = Math.round((double) weightedSum / totalVol);
		else
			avg = (high + low) / 2;

		return new PriceStats(high, low, avg, volume);
	}
}
