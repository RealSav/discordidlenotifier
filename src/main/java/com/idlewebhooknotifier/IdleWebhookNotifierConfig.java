package com.idlewebhooknotifier;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("idlewebhooknotifier")
public interface IdleWebhookNotifierConfig extends Config
{
	@ConfigItem(
		keyName = "webhookUrl",
		name = "Discord Webhook URL",
		description = "The Discord webhook to POST idle notifications to. Use a server/channel "
			+ "you don't mind getting pinged in - anyone who has this URL can post to it, so "
			+ "treat it like a password.",
		position = 0
	)
	default String webhookUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customMessage",
		name = "Custom message",
		description = "Message sent when you go idle. Use {player} to insert your character's name.",
		position = 1
	)
	default String customMessage()
	{
		return "{player} has gone idle - get back to it!";
	}

	@ConfigItem(
		keyName = "idleThresholdTicks",
		name = "Idle threshold (ticks)",
		description = "How many game ticks (0.6s each) after your last skilling/combat action "
			+ "stops before you're notified. Higher = fewer false positives from brief animation "
			+ "gaps mid-action, but a slower alert.",
		position = 2
	)
	default int idleThresholdTicks()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "renotifyEnabled",
		name = "Repeat notifications while idle",
		description = "If enabled, you'll keep getting reminders at the interval below for as "
			+ "long as you remain idle. If disabled, you'll only be notified once per idle period.",
		position = 3
	)
	default boolean renotifyEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "renotifyIntervalSeconds",
		name = "Re-notify interval (seconds)",
		description = "How often to send a repeat reminder while you're still idle. Only applies "
			+ "if 'Repeat notifications while idle' is enabled above.",
		position = 4
	)
	default int renotifyIntervalSeconds()
	{
		return 60;
	}
}
