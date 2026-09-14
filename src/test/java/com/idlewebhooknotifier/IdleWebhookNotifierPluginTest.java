package com.idlewebhooknotifier;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class IdleWebhookNotifierPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(IdleWebhookNotifierPlugin.class);
		RuneLite.main(args);
	}
}
