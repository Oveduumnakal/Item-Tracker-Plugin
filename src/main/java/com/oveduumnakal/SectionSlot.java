/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

/**
 * An ordinal position ({@link #FIRST}..{@link #NINTH}) assigned to a detail-view
 * section to control its order, or {@link #NONE} to hide it. Used by the config
 * so each section can be placed independently. The {@code label} is the name
 * shown in the config dropdown.
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
	NINTH("9th"),
	TENTH("10th");

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
