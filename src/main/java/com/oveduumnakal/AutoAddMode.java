/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

/**
 * Controls whether newly obtained items are auto-added to the tracker and, if so,
 * which price seeds their initial cost basis.
 *
 * <ul>
 *   <li>{@link #HIGH}/{@link #LOW}/{@link #AVG} &ndash; seed the cost basis at the
 *       item's current high, low, or average price.</li>
 *   <li>{@link #ZERO} &ndash; auto-add with a zero cost basis (pure gain).</li>
 *   <li>{@link #OFF} &ndash; disable auto-adding.</li>
 * </ul>
 *
 * <p>The {@code label} is the human-readable name shown in the config dropdown.
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
