/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

/**
 * Position a detail-view section occupies, or {@link #NONE} to hide it.
 * Used by the "Show {Section}" config dropdowns. The ordinal (minus one for
 * the non-NONE values) gives the sort order within the detail view.
 */
public enum SectionSlot
{
	NONE("None"),
	FIRST("1st"),
	SECOND("2nd"),
	THIRD("3rd"),
	FOURTH("4th"),
	FIFTH("5th"),
	SIXTH("6th"),
	SEVENTH("7th"),
	EIGHTH("8th"),
	NINTH("9th");

	private final String label;

	SectionSlot(String label)
	{
		this.label = label;
	}

	public boolean isNone()
	{
		return this == NONE;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
