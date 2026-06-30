/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One user-defined tracked-item category: its display {@code name} and whether
 * its accordion group is currently {@code collapsed} in the panel. The ordered
 * list of these is the source of truth for category order, naming, and
 * collapsed state, persisted separately from the tracked items themselves.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryState
{
	/** Group key for the special "Favorites" pseudo-group (pinned above all categories). */
	public static final String FAVORITES_KEY = "__favorites__";

	/** Group key for the catch-all "Uncategorized" group (items with no category). */
	public static final String UNCATEGORIZED_KEY = "__uncategorized__";

	private String name;
	private boolean collapsed;
}
