/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The item attribute a {@link NotificationRule} watches &ndash; a price, profit,
 * volume, percent change, quantity, or a categorical rating.
 *
 * <p>Each constant pairs a short {@code label} (used in the rule chip) with a
 * longer {@code displayName} and a {@link Kind} that drives how the rule's value
 * is entered and compared. Categorical metrics additionally carry their allowed
 * {@code options}. The {@code locks*}/{@code is*} predicates capture per-metric
 * UI constraints (e.g. {@link #RANGE_30D} only makes sense over a month).
 */
public enum NotificationMetric
{
	HIGH("High", "High", Kind.NUMERIC),
	LOW("Low", "Low", Kind.NUMERIC),
	AVERAGE("Average", "Average", Kind.NUMERIC),
	ITM_PROFIT("Itm Profit", "Item Profit", Kind.NUMERIC),
	HA_PROFIT("HA Profit", "HA Profit", Kind.NUMERIC),
	VOLUME("Volume", "Volume", Kind.NUMERIC),
	DELTA_PCT("Δ%", "Price Change", Kind.PERCENT),
	QUANTITY("Quantity", "Quantity", Kind.QUANTITY),
	VOLATILITY("Volatility", "Volatility", Kind.CATEGORY, "Low", "Medium", "High"),
	LIQUIDITY("Liquidity", "Liquidity", Kind.CATEGORY, "Low", "Medium", "High"),
	RANGE_30D("30d Range", "30 Day Range", Kind.CATEGORY,
			"Lowest", "Low", "Low Avg", "Average", "High Avg", "High", "Highest");

	/** The value domain of a metric, controlling input and comparison semantics. */
	public enum Kind
	{
		NUMERIC, PERCENT, QUANTITY, CATEGORY
	}

	private final String label;
	private final String displayName;
	private final Kind kind;
	private final List<String> options;

	NotificationMetric(String label, String displayName, Kind kind, String... options)
	{
		this.label = label;
		this.displayName = displayName;
		this.kind = kind;
		this.options = options.length == 0
				? Collections.emptyList()
				: Collections.unmodifiableList(Arrays.asList(options));
	}

	public String getLabel()
	{
		return label;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public Kind getKind()
	{
		return kind;
	}

	public List<String> getOptions()
	{
		return options;
	}

	public boolean isCategorical()
	{
		return kind == Kind.CATEGORY;
	}

	/** Categorical metrics compare by exact match, so the operator is forced to "=". */
	public boolean locksOperationToEquals()
	{
		return kind == Kind.CATEGORY;
	}

	/** {@link #RANGE_30D} is inherently a 30-day metric, so its timeframe is pinned to a month. */
	public boolean locksTimeframeToMonth()
	{
		return this == RANGE_30D;
	}

	/** Quantity is a live inventory count with no timeframe to choose. */
	public boolean isTimeframeDisabled()
	{
		return this == QUANTITY;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
