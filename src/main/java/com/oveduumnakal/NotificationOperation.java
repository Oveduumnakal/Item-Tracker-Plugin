/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

/**
 * The comparison operator of a {@link NotificationRule}, used to decide whether a
 * metric's current value has met the rule's target. Each constant carries its
 * display {@code symbol} (e.g. {@code ">="}) and evaluates via {@link #test}.
 */
public enum NotificationOperation
{
	GT(">"),
	GTE(">="),
	LT("<"),
	LTE("<="),
	EQ("=");

	private final String symbol;

	NotificationOperation(String symbol)
	{
		this.symbol = symbol;
	}

	public String getSymbol()
	{
		return symbol;
	}

	/**
	 * Applies this operator to a metric reading.
	 *
	 * @param current the metric's current value
	 * @param target  the rule's threshold
	 * @return {@code true} if {@code current} satisfies the operator against {@code target}
	 */
	public boolean test(double current, double target)
	{
		switch (this)
		{
			case GT: return current > target;
			case GTE: return current >= target;
			case LT: return current < target;
			case LTE: return current <= target;
			case EQ: return current == target;
			default: return false;
		}
	}

	@Override
	public String toString()
	{
		return symbol;
	}
}
