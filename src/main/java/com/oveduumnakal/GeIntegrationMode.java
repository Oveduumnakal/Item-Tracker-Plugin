/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

/**
 * How the plugin ties the Stockpile view to the open Grand Exchange offer &ndash;
 * disabled ({@link #OFF}), a {@link #BUTTON} injected on the GE offer screen,
 * {@link #AUTO} opening the item in Stockpile as soon as an offer screen appears,
 * or {@link #BOTH} doing both at once. The {@code label} is the name shown in the
 * config dropdown.
 */
public enum GeIntegrationMode
{
	OFF("Off"),
	BUTTON("Button on GE"),
	AUTO("Auto-open"),
	BOTH("Both");

	private final String label;

	GeIntegrationMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
