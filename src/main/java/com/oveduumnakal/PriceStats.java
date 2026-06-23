/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated price and volume figures for one item over a single
 * {@link TimeWindow}: the window's high, low, and average prices (gp) and the
 * total traded {@code volume}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PriceStats
{
	private long high;
	private long low;
	private long avg;
	private long volume;
}
