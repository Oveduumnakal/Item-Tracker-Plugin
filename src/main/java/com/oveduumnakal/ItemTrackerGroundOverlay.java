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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Scene overlay that outlines tracked items lying on the ground.
 *
 * <p>On each frame it walks the plugin's known ground items, keeps those whose
 * canonical id is tracked, and draws their tile polygon in the configured
 * highlight color &ndash; pulsing via the plugin's breathing alpha. Does nothing
 * when ground highlighting is disabled in config.
 */
public class ItemTrackerGroundOverlay extends Overlay
{
	private final Client client;
	private final ItemTrackerPlugin plugin;
	private final ItemTrackerConfig config;
	private final ItemManager itemManager;

	@Inject
	ItemTrackerGroundOverlay(Client client, ItemTrackerPlugin plugin, ItemTrackerConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightTrackedItems().ground())
			return null;

		Color base = config.highlightColor();
		Color breathing = new Color(base.getRed(), base.getGreen(), base.getBlue(),
				Math.round(plugin.breathingAlpha() * 255));

		for (Map.Entry<TileItem, Tile> entry : plugin.getGroundItems().entrySet())
		{
			if (!plugin.isTracked(itemManager.canonicalize(entry.getKey().getId())))
				continue;

			Tile tile = entry.getValue();
			Shape poly = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());
			if (poly != null)
			{
				graphics.setColor(breathing);
				graphics.draw(poly);
			}
		}

		return null;
	}
}
