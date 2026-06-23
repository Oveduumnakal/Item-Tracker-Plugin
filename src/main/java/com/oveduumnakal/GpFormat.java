/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Single source of truth for formatting gp values across the panel, graph, and
 * notifications.
 *
 * <p>The compact form abbreviates with uppercase suffixes and drops trailing
 * zeros: {@code 234K}, {@code 23.4K}, {@code 1.5M}, {@code 2.1B}. Negatives keep
 * a leading {@code -}; values under 1,000 are shown as grouped digits. This is a
 * stateless utility and cannot be instantiated.
 */
public final class GpFormat
{
	private GpFormat()
	{
	}

	private static final NumberFormat GROUPED = NumberFormat.getIntegerInstance(Locale.US);

	/** Compact form to at most 3 significant figures: {@code 234K}, {@code 2.34K}, {@code 1.5M}. */
	public static String shortValue(long value)
	{
		return abbreviate(value, false);
	}

	/**
	 * Compact form capped to a single decimal place ({@code 2.3K} rather than
	 * {@code 2.34K}). Narrower than {@link #shortValue} for sub-10K values, for
	 * use where column width is tight (e.g. the price overview grid).
	 */
	public static String shortValue1dp(long value)
	{
		return abbreviate(value, true);
	}

	/** {@link #shortValue} with a trailing {@code " gp"}. */
	public static String shortGp(long value)
	{
		return shortValue(value) + " gp";
	}

	/** Full comma-grouped digits with a trailing {@code " gp"}: {@code "1,234,567 gp"}. */
	public static String fullGp(long value)
	{
		return GROUPED.format(value) + " gp";
	}

	/**
	 * Core abbreviation: scales by the largest fitting magnitude (K/M/B) and
	 * formats the mantissa, or returns grouped digits below 1,000.
	 *
	 * @param singleDecimal cap the mantissa to one decimal place rather than 3 sig-figs
	 */
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
	 * Formats a scaled mantissa in {@code [1, 1000)} to 3 significant figures (or
	 * one decimal place when {@code singleDecimal}), dropping any trailing zeros
	 * and a dangling decimal point.
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
