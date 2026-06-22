/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Single source of truth for number formatting across the panel, graph, and
 * notifications. The compact form uses uppercase suffixes with trailing zeros
 * dropped: 234K, 23.4K, 1.5M, 2.1B.
 */
public final class GpFormat
{
	private GpFormat()
	{
	}

	private static final NumberFormat GROUPED = NumberFormat.getIntegerInstance(Locale.US);

	/** Compact ≤3-significant-figure form, signed: 234K, 23.4K, 2.34K, 1.5M, 2.1B. */
	public static String shortValue(long value)
	{
		return abbreviate(value, false);
	}

	/**
	 * Compact form capped to a single decimal place: 234K, 23.4K, 2.3K, 1.5M.
	 * Narrower than {@link #shortValue} for sub-10K values, used where column
	 * width is tight (the Price Overview grid).
	 */
	public static String shortValue1dp(long value)
	{
		return abbreviate(value, true);
	}

	/** Compact form with a trailing " gp". */
	public static String shortGp(long value)
	{
		return shortValue(value) + " gp";
	}

	/** Full grouped form with a trailing " gp": "1,234,567 gp". */
	public static String fullGp(long value)
	{
		return GROUPED.format(value) + " gp";
	}

	private static String abbreviate(long value, boolean singleDecimal)
	{
		long abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000_000L)
			return sign + mantissa(abs / 1_000_000_000.0, singleDecimal) + "B";
		else if (abs >= 1_000_000L)
			return sign + mantissa(abs / 1_000_000.0, singleDecimal) + "M";
		else if (abs >= 1_000L)
			return sign + mantissa(abs / 1_000.0, singleDecimal) + "K";

		return sign + GROUPED.format(abs);
	}

	/**
	 * Formats a mantissa in [1, 1000) to 3 significant figures (or a single
	 * decimal place when {@code singleDecimal}), dropping trailing zeros.
	 */
	private static String mantissa(double d, boolean singleDecimal)
	{
		String s;
		if (d >= 100)
			s = String.format(Locale.US, "%.0f", d);
		else if (d >= 10 || singleDecimal)
			s = String.format(Locale.US, "%.1f", d);
		else
			s = String.format(Locale.US, "%.2f", d);

		if (s.contains("."))
		{
			s = s.replaceAll("0+$", "");
			if (s.endsWith("."))
				s = s.substring(0, s.length() - 1);
		}

		return s;
	}
}
