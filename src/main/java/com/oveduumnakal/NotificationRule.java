/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import lombok.Data;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * A single per-item notification rule: when the chosen {@link NotificationMetric}
 * over the chosen {@link TimeWindow} satisfies {@link NotificationOperation}
 * against {@link #value}, a notification fires.
 *
 * <p>{@code value} is stored as the normalized display string the user sees
 * (e.g. {@code "5m"}, {@code "10%"}, {@code "Highest"}). The static
 * parse/format helpers convert between that text and the numeric value used for
 * comparison, keeping the panel (on edit) and the plugin (on evaluation) in
 * agreement.
 *
 * <p>Rules are one-and-done: a rule fires exactly once and is then removed from
 * its item's list.
 */
@Data
public class NotificationRule
{
	// Null/blank until the user fills the row in; an incomplete rule never fires.
	private NotificationMetric metric;
	private TimeWindow timeWindow;
	private NotificationOperation operation;
	private String value = "";

	/**
	 * Parses a numeric value entered in short form (commas, k/m/b suffixes,
	 * decimals). Returns empty when the text cannot be parsed.
	 */
	public static OptionalDouble parseNumeric(String text)
	{
		if (text == null)
			return OptionalDouble.empty();

		String s = text.trim().toLowerCase(Locale.US).replace(",", "");
		if (s.isEmpty())
			return OptionalDouble.empty();

		double mult = 1;
		char last = s.charAt(s.length() - 1);
		if (last == 'k')
			mult = 1_000d;
		else if (last == 'm')
			mult = 1_000_000d;
		else if (last == 'b')
			mult = 1_000_000_000d;

		if (mult != 1)
			s = s.substring(0, s.length() - 1);

		try
		{
			return OptionalDouble.of(Double.parseDouble(s) * mult);
		}
		catch (NumberFormatException e)
		{
			return OptionalDouble.empty();
		}
	}

	/**
	 * Parses a percentage value. A trailing {@code %} is taken literally
	 * ({@code "5.5%"} → 5.5); a bare fraction below 1 is read as a ratio
	 * ({@code "0.1"} → 10); anything else is read as a literal percent
	 * ({@code "10"} → 10).
	 */
	public static OptionalDouble parsePercent(String text)
	{
		if (text == null)
			return OptionalDouble.empty();

		String s = text.trim().replace(",", "");
		if (s.isEmpty())
			return OptionalDouble.empty();

		boolean explicit = s.endsWith("%");
		if (explicit)
			s = s.substring(0, s.length() - 1).trim();

		try
		{
			double v = Double.parseDouble(s);
			if (!explicit && Math.abs(v) < 1)
				v *= 100;

			return OptionalDouble.of(v);
		}
		catch (NumberFormatException e)
		{
			return OptionalDouble.empty();
		}
	}

	/** Formats a percentage as {@code NN.N%} or {@code NN%} when whole. */
	public static String formatPercent(double value)
	{
		if (value == Math.rint(value))
			return String.format(Locale.US, "%d%%", (long) value);

		return String.format(Locale.US, "%.1f%%", value);
	}
}
