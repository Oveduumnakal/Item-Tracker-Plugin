/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

/**
 * Comparison operator for a notification rule. The symbol is shown in the table
 * dropdown and in the fired notification text.
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

	/** Tests {@code current <op> target} for numeric metrics. */
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
