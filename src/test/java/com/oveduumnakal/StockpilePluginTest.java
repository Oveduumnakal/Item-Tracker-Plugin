package com.oveduumnakal;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class StockpilePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(StockpilePlugin.class);
		RuneLite.main(args);
	}
}
