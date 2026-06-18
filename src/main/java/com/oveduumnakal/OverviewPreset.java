/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Preset selecting which time-window rows the detail-view Price Overview shows.
 * Replaces the per-row multi-select with a single, easy-to-read dropdown.
 */
public enum OverviewPreset
{
	RECENT("Recent", EnumSet.of(
			TimeWindow.LIVE, TimeWindow.H1, TimeWindow.H12, TimeWindow.H24)),
	STANDARD("Standard", EnumSet.of(
			TimeWindow.LIVE, TimeWindow.H1, TimeWindow.H24, TimeWindow.WEEK, TimeWindow.MONTH)),
	DETAILED("Detailed", EnumSet.of(
			TimeWindow.LIVE, TimeWindow.H1, TimeWindow.H3, TimeWindow.H6, TimeWindow.H12,
			TimeWindow.H24, TimeWindow.WEEK, TimeWindow.MONTH, TimeWindow.MONTH3,
			TimeWindow.MONTH6, TimeWindow.YEAR));

	private final String label;
	private final Set<TimeWindow> windows;

	OverviewPreset(String label, Set<TimeWindow> windows)
	{
		this.label = label;
		this.windows = Collections.unmodifiableSet(windows);
	}

	/** Time-window rows shown by this preset. */
	public Set<TimeWindow> getWindows()
	{
		return windows;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
