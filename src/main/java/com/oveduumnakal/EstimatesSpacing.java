/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

/**
 * Vertical density of the GE value estimate rows &ndash; normal
 * ({@link #DEFAULT}) or tightened ({@link #COMPACT}) padding. The {@code label}
 * is the name shown in the config dropdown.
 */
public enum EstimatesSpacing
{
	DEFAULT("Default"),
	COMPACT("Compact");

	private final String label;

	EstimatesSpacing(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
