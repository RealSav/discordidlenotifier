package com.idlewebhooknotifier;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.io.IOException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
	name = "Idle Webhook Notifier",
	description = "Sends a Discord webhook notification when you stop a skilling or combat action",
	tags = {"idle", "discord", "webhook", "notification", "afk", "notifier", "skilling", "combat"}
)
public class IdleWebhookNotifierPlugin extends Plugin
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private Client client;

	@Inject
	private IdleWebhookNotifierConfig config;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	private int idleTicks = 0;
	private long lastNotifyTimeMillis = 0L;
	private boolean notifiedThisIdlePeriod = false;

	// True once we've observed a genuine skilling/combat action. Only reset
	// when a *new* action starts (or on logout) - this is what lets us tell
	// "never doing anything" apart from "was doing something, now stopped".
	private boolean wasPerformingAction = false;

	private WorldPoint lastLocation = null;

	@Provides
	IdleWebhookNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(IdleWebhookNotifierConfig.class);
	}

	@Override
	protected void startUp()
	{
		resetState();
	}

	@Override
	protected void shutDown()
	{
		resetState();
	}

	private void resetState()
	{
		idleTicks = 0;
		lastNotifyTimeMillis = 0L;
		notifiedThisIdlePeriod = false;
		wasPerformingAction = false;
		lastLocation = null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			resetState();
			return;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		WorldPoint currentLocation = player.getWorldLocation();
		boolean moved = lastLocation != null && !lastLocation.equals(currentLocation);
		lastLocation = currentLocation;

		// getInteracting() is set to your combat opponent, or to an NPC
		// you're skilling from (e.g. a fishing spot). This is a precise,
		// animation-list-free way to catch combat and NPC-based skilling.
		Actor target = player.getInteracting();
		boolean targetingActor = target != null && target != player;

		int animation = player.getAnimation();
		boolean hasActionAnimation = animation != -1;

		// A genuine action: targeting an actor (combat/NPC-skilling), OR
		// playing an action animation while stationary. The "!moved" check
		// is what excludes walking/running - those also set a non-idle
		// animation, but aren't a skilling or combat action.
		boolean isPerformingAction = targetingActor || (hasActionAnimation && !moved);

		if (isPerformingAction)
		{
			if (!wasPerformingAction)
			{
				log.debug("Action started - animation={} targetingActor={}", animation, targetingActor);
			}
			wasPerformingAction = true;
			idleTicks = 0;
			notifiedThisIdlePeriod = false;
			return;
		}

		if (!wasPerformingAction)
		{
			// Standing around or just walking, never actually doing
			// anything to begin with - don't notify.
			return;
		}

		idleTicks++;
		log.debug("Idle tick {}/{} since last action stopped (animation={}, moved={})",
			idleTicks, config.idleThresholdTicks(), animation, moved);

		if (idleTicks < config.idleThresholdTicks())
		{
			return;
		}

		long now = System.currentTimeMillis();
		boolean shouldNotify;

		if (!notifiedThisIdlePeriod)
		{
			shouldNotify = true;
		}
		else if (config.renotifyEnabled())
		{
			int renotifySeconds = Math.max(config.renotifyIntervalSeconds(), 1);
			shouldNotify = (now - lastNotifyTimeMillis) >= renotifySeconds * 1000L;
		}
		else
		{
			shouldNotify = false;
		}

		if (shouldNotify)
		{
			log.debug("Notifying - idle for {} ticks", idleTicks);
			sendWebhookNotification();
			notifiedThisIdlePeriod = true;
			lastNotifyTimeMillis = now;
		}
	}

	private void sendWebhookNotification()
	{
		String webhookUrl = config.webhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank())
		{
			return;
		}

		String playerName = client.getLocalPlayer().getName();
		String template = config.customMessage();
		if (template == null || template.isBlank())
		{
			template = "{player} has gone idle!";
		}
		String content = template.replace("{player}", playerName != null ? playerName : "Your character");

		JsonObject payload = new JsonObject();
		payload.addProperty("content", content);

		RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
		Request request = new Request.Builder()
			.url(webhookUrl)
			.post(body)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Failed to send idle notification webhook", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
			}
		});
	}
}
