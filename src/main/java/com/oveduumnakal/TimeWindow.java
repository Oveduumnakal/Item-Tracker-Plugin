/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.time.Duration;

public enum TimeWindow
{
	NONE("None", Duration.ZERO),
	LIVE("Ltst", Duration.ZERO),
	H1("1h", Duration.ofHours(1)),
	H3("3h", Duration.ofHours(3)),
	H6("6h", Duration.ofHours(6)),
	H12("12h", Duration.ofHours(12)),
	H24("24h", Duration.ofHours(24)),
	WEEK("1w", Duration.ofDays(7)),
	MONTH("1mo", Duration.ofDays(30)),
	MONTH3("3mo", Duration.ofDays(90)),
	MONTH6("6mo", Duration.ofDays(180)),
	YEAR("1y", Duration.ofDays(365));

	private final String label;
	private final Duration duration;

	TimeWindow(String label, Duration duration)
	{
		this.label = label;
		this.duration = duration;
	}

	public String getLabel()
	{
		return label;
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
