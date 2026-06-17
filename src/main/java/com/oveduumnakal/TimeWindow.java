/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.time.Duration;

public enum TimeWindow
{
	NONE("None", Duration.ZERO, "5m"),
	LIVE("Ltst", Duration.ZERO, "5m"),
	H1("1h", Duration.ofHours(1), "5m"),
	H3("3h", Duration.ofHours(3), "5m"),
	H6("6h", Duration.ofHours(6), "5m"),
	H12("12h", Duration.ofHours(12), "5m"),
	H24("24h", Duration.ofHours(24), "5m"),
	WEEK("1w", Duration.ofDays(7), "6h"),
	MONTH("1mo", Duration.ofDays(30), "6h"),
	MONTH3("3mo", Duration.ofDays(90), "24h"),
	MONTH6("6mo", Duration.ofDays(180), "24h"),
	YEAR("1y", Duration.ofDays(365), "24h");

	private final String label;
	private final Duration duration;
	private final String timestep;

	TimeWindow(String label, Duration duration, String timestep)
	{
		this.label = label;
		this.duration = duration;
		this.timestep = timestep;
	}

	public String getLabel()
	{
		return label;
	}

	public Duration getDuration()
	{
		return duration;
	}

	public String getTimestep()
	{
		return timestep;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
