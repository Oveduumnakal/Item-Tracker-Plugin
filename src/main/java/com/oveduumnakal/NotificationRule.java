/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import lombok.Data;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * One user-defined alert condition on a tracked item: when {@code metric} over
 * {@code timeWindow}, compared with {@code operation} against {@code value},
 * holds true.
 *
 * <p>{@code value} is stored as the raw text the user typed (e.g. {@code "5m"},
 * {@code "10%"}, or a category like {@code "High"}); the static helpers here
 * parse that text into comparable numbers for evaluation and format numbers back
 * for display.
 */
@Data
public class NotificationRule
{

	private NotificationMetric metric;
	private TimeWindow timeWindow;
	private NotificationOperation operation;
	private String value = "";

	/**
	 * Parses a numeric threshold, accepting commas and a k/m/b suffix
	 * (e.g. {@code "1,500"}, {@code "5m"} &rarr; 5,000,000).
	 *
	 * @param text the raw user input
	 * @return the parsed value, or empty if blank or unparseable
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
	 * Parses a percent threshold. A trailing {@code %} is optional; when omitted,
	 * a bare fraction below 1 (e.g. {@code 0.05}) is treated as 5%.
	 *
	 * @param text the raw user input
	 * @return the percent value, or empty if blank or unparseable
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

	/** Formats a percent as {@code NN%} when whole, otherwise {@code NN.N%}. */
	public static String formatPercent(double value)
	{
		if (value == Math.rint(value))
			return String.format(Locale.US, "%d%%", (long) value);

		return String.format(Locale.US, "%.1f%%", value);
	}
}
