/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

/**
 * Controls automatic acquisition creation when tracked-item quantities grow.
 * Every mode except {@link #OFF} behaves like the old "Auto-Update Quantity"
 * flag being enabled; they differ only in the "bought at" price recorded.
 */
public enum AutoAddMode
{
	HIGH("High"),
	LOW("Low"),
	AVG("Avg"),
	ZERO("Zero"),
	OFF("Off");

	private final String label;

	AutoAddMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
