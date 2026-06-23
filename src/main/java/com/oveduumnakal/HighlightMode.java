/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oveduumnakal;

/**
 * Where tracked items are highlighted: on the {@link #GROUND}, in the
 * {@link #INV_BANK} (inventory/bank), {@link #BOTH}, or {@link #OFF}. Query the
 * surfaces with {@link #ground()} and {@link #invBank()} rather than comparing
 * constants. The {@code displayName} is the label shown in the config dropdown.
 */
public enum HighlightMode
{
	GROUND("Ground"),
	INV_BANK("Inv/Bank"),
	BOTH("Both"),
	OFF("Off");

	private final String displayName;

	HighlightMode(String displayName)
	{
		this.displayName = displayName;
	}

	/** @return whether ground items should be highlighted in this mode. */
	public boolean ground()
	{
		return this == GROUND || this == BOTH;
	}

	/** @return whether inventory/bank items should be highlighted in this mode. */
	public boolean invBank()
	{
		return this == INV_BANK || this == BOTH;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
