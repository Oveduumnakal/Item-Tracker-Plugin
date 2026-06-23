/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.time.Duration;

/**
 * A look-back period over which prices and volumes are summarized, from the
 * latest 5-minute snapshot ({@link #LIVE}) up to a {@link #YEAR}.
 *
 * <p>Each constant carries three forms: a compact {@code label} for tight chips
 * (e.g. {@code "1mo"}), a spelled-out {@code longLabel} for headers
 * (e.g. {@code "1 Month"}), and a {@code duration} used to bound queries.
 * {@link #NONE} and {@link #LIVE} have a zero duration; {@link #NONE} is a
 * not-applicable placeholder.
 */
public enum TimeWindow
{
	NONE("None", Duration.ZERO, "None"),
	LIVE("Ltst", Duration.ZERO, "5 Minute"),
	H1("1h", Duration.ofHours(1), "1 Hour"),
	H3("3h", Duration.ofHours(3), "3 Hour"),
	H6("6h", Duration.ofHours(6), "6 Hour"),
	H12("12h", Duration.ofHours(12), "12 Hour"),
	H24("24h", Duration.ofHours(24), "24 Hour"),
	WEEK("1w", Duration.ofDays(7), "1 Week"),
	MONTH("1mo", Duration.ofDays(30), "1 Month"),
	MONTH3("3mo", Duration.ofDays(90), "3 Month"),
	MONTH6("6mo", Duration.ofDays(180), "6 Month"),
	YEAR("1y", Duration.ofDays(365), "1 Year");

	private final String label;
	private final Duration duration;
	private final String longLabel;

	TimeWindow(String label, Duration duration, String longLabel)
	{
		this.label = label;
		this.duration = duration;
		this.longLabel = longLabel;
	}

	public String getLabel()
	{
		return label;
	}

	public String getLongLabel()
	{
		return longLabel;
	}

	public Duration getDuration()
	{
		return duration;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
