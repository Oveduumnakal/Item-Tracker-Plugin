/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single buy (and optional matching sell) of a tracked item, forming one lot
 * of its cost basis.
 *
 * <p>{@code quantity} units were bought at {@code boughtAt} gp each. While
 * {@code soldAt} is {@code null} the lot is still held and contributes to the
 * item's cost basis and unrealized profit; once set, the lot is realized at that
 * sale price.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcquisitionRecord
{
	private int quantity;
	private long boughtAt;
	private Long soldAt;
}
