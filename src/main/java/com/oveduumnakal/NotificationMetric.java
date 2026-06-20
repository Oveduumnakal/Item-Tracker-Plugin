/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A metric a per-item notification rule can watch. Each value carries the short
 * label shown in the Notifications table dropdown, the longer display name used
 * in the fired notification text, and behaviour flags describing how the rest of
 * the row (time frame, operation, value) is constrained.
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

	/** Broad category of the metric's value, driving input type and comparisons. */
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

	/** Allowed values for categorical metrics; empty for numeric metrics. */
	public List<String> getOptions()
	{
		return options;
	}

	public boolean isCategorical()
	{
		return kind == Kind.CATEGORY;
	}

	/** Categorical metrics only make sense with an equality test. */
	public boolean locksOperationToEquals()
	{
		return kind == Kind.CATEGORY;
	}

	/** The 30-day range is, by definition, a one-month window. */
	public boolean locksTimeframeToMonth()
	{
		return this == RANGE_30D;
	}

	/** Quantity reflects current holdings and has no per-window value. */
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
