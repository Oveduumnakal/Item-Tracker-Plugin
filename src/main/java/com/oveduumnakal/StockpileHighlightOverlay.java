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

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Widget overlay that draws a colored outline around tracked items in the
 * inventory and bank.
 *
 * <p>For each rendered item widget whose canonical id is tracked, it fetches the
 * item's outline image and blits it at the configured highlight color, modulated
 * by the plugin's pulsing breathing alpha. Skips rendering when inventory/bank
 * highlighting is disabled in config.
 */
public class StockpileHighlightOverlay extends WidgetItemOverlay
{
	private final StockpilePlugin plugin;
	private final StockpileConfig config;
	private final ItemManager itemManager;

	@Inject
	StockpileHighlightOverlay(StockpilePlugin plugin, StockpileConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnInventory();
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightTrackedItems().invBank()
				|| !plugin.isTracked(itemManager.canonicalize(itemId)))
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds != null)
		{
			BufferedImage outline = itemManager.getItemOutline(
					itemId, widgetItem.getQuantity(), config.highlightColor());

			Composite original = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(
					AlphaComposite.SRC_OVER, plugin.breathingAlpha()));
			graphics.drawImage(outline, bounds.x, bounds.y, null);
			graphics.setComposite(original);
		}
	}
}
