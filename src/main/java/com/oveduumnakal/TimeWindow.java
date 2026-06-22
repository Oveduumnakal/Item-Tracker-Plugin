/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.time.Duration;

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

	/** Spelled-out label, e.g. "1 Hour" / "24 Hour". */
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
