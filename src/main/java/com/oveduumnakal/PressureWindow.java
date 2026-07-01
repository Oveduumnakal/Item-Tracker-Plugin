/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.time.Duration;

/**
 * The look-back period for the Buy/Sell Pressure block, backed by a
 * {@link TimeWindow} that supplies both the aggregation {@code duration} and the
 * price-history granularity used to sum instant-buy vs instant-sell volume. The
 * {@code label} is the name shown in the config dropdown.
 */
public enum PressureWindow
{
	DAY("24 Hours", TimeWindow.H24),
	WEEK("1 Week", TimeWindow.WEEK),
	MONTH("1 Month", TimeWindow.MONTH);

	private final String label;
	private final TimeWindow window;

	PressureWindow(String label, TimeWindow window)
	{
		this.label = label;
		this.window = window;
	}

	public TimeWindow window()
	{
		return window;
	}

	public Duration duration()
	{
		return window.getDuration();
	}

	@Override
	public String toString()
	{
		return label;
	}
}
