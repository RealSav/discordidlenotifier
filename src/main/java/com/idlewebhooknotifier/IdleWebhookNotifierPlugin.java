package com.idlewebhooknotifier;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.AnimationID;
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

    // Verified against RuneLite's own official Idle Notifier plugin's animation matching
    // (net.runelite.api.gameval.AnimationID constants). Matching directly against these means
    // detection is instant - no debounce needed, so single-tick actions like pickpocketing or
    // agility obstacles aren't delayed - and OSRS's own idle fidget/spawn animations are
    // automatically excluded since they simply aren't in this set.
    //
    // NOTE: Agility, Runecrafting, and general Thieving (pickpocketing/stalls) aren't animation-
    // matched even by RuneLite's own official plugin - those actions don't have distinctive
    // persistent animations to key off. This isn't a gap specific to this plugin.
    private static final Set<Integer> SKILLING_ANIMATIONS = Set.of(
        // Woodcutting
        AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE, AnimationID.HUMAN_WOODCUTTING_IRON_AXE,
        AnimationID.HUMAN_WOODCUTTING_STEEL_AXE, AnimationID.HUMAN_WOODCUTTING_BLACK_AXE,
        AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE, AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE,
        AnimationID.HUMAN_WOODCUTTING_RUNE_AXE, AnimationID.HUMAN_WOODCUTTING_GILDED_AXE,
        AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE, AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL,
        AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE, AnimationID.HUMAN_WOODCUTTING_3A_AXE,
        AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE, AnimationID.HUMAN_OPENHEAVYCHEST,
        AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL, AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE,
        AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE, AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL, AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL, AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE, AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL, AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE,
        AnimationID.FORESTRY_2H_AXE_CHOPPING_3A,
        // Woodcutting: Ents & Canoes
        AnimationID.HUMAN_CANOEING_CARVE_BRONZE_AXE, AnimationID.HUMAN_CANOEING_CARVE_IRON_AXE,
        AnimationID.HUMAN_CANOEING_CARVE_STEEL_AXE, AnimationID.HUMAN_CANOEING_CARVE_BLACK_AXE,
        AnimationID.HUMAN_CANOEING_CARVE_MITHRIL_AXE, AnimationID.HUMAN_CANOEING_CARVE_ADAMANT_AXE,
        AnimationID.HUMAN_CANOEING_CARVE_RUNE_AXE, AnimationID.BRUT_HUMAN_CANOEING_CARVE_GILDED_AXE,
        AnimationID.HUMAN_CANOEING_CARVE_DRAGON_AXE, AnimationID.HUMAN_CANOEING_CARVE_TRAILBLAZER_AXE_NO_INFERNAL,
        AnimationID.HUMAN_CANOEING_CARVE_INFERNAL_AXE, AnimationID.HUMAN_CANOEING_CARVE_LEAGUE_TRAILBLAZER_AXE,
        AnimationID.BRUT_HUMAN_CANOEING_CARVE_3A_AXE, AnimationID.HUMAN_CANOEING_CARVE_CRYSTAL_AXE,
        AnimationID.BRUT_HUMAN_CANOEING_CARVE_CRYSTAL_AXE, AnimationID.BRUT_HUMAN_CANOEING_CARVE_LEAGUE_TRAILBLAZER_AXE,
        AnimationID.HUMAN_CANOEING_CARVE_BRONZE_2H_AXE, AnimationID.HUMAN_CANOEING_CARVE_IRON_2H_AXE,
        AnimationID.HUMAN_CANOEING_CARVE_STEEL_2H_AXE, AnimationID.HUMAN_CANOEING_CARVE_BLACK_2H_AXE,
        AnimationID.HUMAN_CANOEING_CARVE_MITHRIL_2H_AXE, AnimationID.HUMAN_CANOEING_CARVE_ADAMANT_2H_AXE,
        AnimationID.HUMAN_CANOEING_CARVE_RUNE_2H_AXE, AnimationID.HUMAN_CANOEING_CARVE_DRAGON_2H_AXE,
        AnimationID.HUMAN_CANOEING_CARVE_CRYSTAL_2H_AXE, AnimationID.HUMAN_CANOEING_CARVE_CRYSTAL_2H_AXE_INACTIVE,
        AnimationID.HUMAN_CANOEING_CARVE_3A_2H_AXE, AnimationID.TBW_CLEANUP_PLAYER_SURPRISE_STEPBACK,
        // Sap collection
        AnimationID.HUMAN_PICKUPTABLE_WALKMERGE_NOHELD,
        // Firemaking
        AnimationID.FORESTRY_CAMPFIRE_BURNING_ARCTIC_PINE_LOG, AnimationID.FORESTRY_CAMPFIRE_BURNING_BLISTERWOOD_LOGS,
        AnimationID.FORESTRY_CAMPFIRE_BURNING_LOGS, AnimationID.FORESTRY_CAMPFIRE_BURNING_MAGIC_LOGS,
        AnimationID.FORESTRY_CAMPFIRE_BURNING_MAHOGANY_LOGS, AnimationID.FORESTRY_CAMPFIRE_BURNING_MAPLE_LOGS,
        AnimationID.FORESTRY_CAMPFIRE_BURNING_OAK_LOGS, AnimationID.FORESTRY_CAMPFIRE_BURNING_REDWOOD_LOGS,
        AnimationID.FORESTRY_CAMPFIRE_BURNING_TEAK_LOGS, AnimationID.FORESTRY_CAMPFIRE_BURNING_WILLOW_LOGS,
        AnimationID.FORESTRY_CAMPFIRE_BURNING_YEW_LOGS,
        // Cooking
        AnimationID.HUMAN_FIRECOOKING, AnimationID.HUMAN_COOKING, AnimationID.HUMAN_MAKE_WINE, AnimationID.HUMAN_CUT_FOOD,
        // Crafting (gems, glass, spinning, weaving, battlestaves, pottery)
        AnimationID.HUMAN_OPALCUTTING, AnimationID.HUMAN_JADECUTTING, AnimationID.HUMAN_REDTOPAZCUTTING,
        AnimationID.HUMAN_SAPPHIRECUTTING, AnimationID.HUMAN_EMERALDCUTTING, AnimationID.HUMAN_RUBYCUTTING,
        AnimationID.HUMAN_DIAMONDCUTTING, AnimationID.HUMAN_DRAGONSTONECUTTING, AnimationID.HUMAN_ONYXCUTTING,
        AnimationID.HUMAN_AMETHYSTCUTTING, AnimationID.HUMAN_GLASSBLOWING, AnimationID.HUMAN_SPINNINGWHEEL_60,
        AnimationID.HUMAN_SPINNINGWHEEL_90, AnimationID.FARMING_USELOOM, AnimationID.HUMAN_BATTLESTAFF_CRAFTING,
        AnimationID.HUMAN_LEATHER_CRAFTING, AnimationID.HUMAN_POTTERYWHEEL, AnimationID.HUMAN_CUTTING_RESTART,
        AnimationID.HUMAN_GOLEM_CHISEL_END,
        // Fletching
        AnimationID.HUMAN_FLETCHING, AnimationID.XBOWS_FLETCHING_WOOD_BRONZE, AnimationID.XBOWS_FLETCHING_OAK_BLURITE,
        AnimationID.XBOWS_FLETCHING_WILLOW_IRON, AnimationID.XBOWS_FLETCHING_TEAK_STEEL, AnimationID.XBOWS_FLETCHING_MAPLE_MITHRIL,
        AnimationID.XBOWS_FLETCHING_MAHOGANY_ADAMANTITE, AnimationID.XBOWS_FLETCHING_YEW_RUNITE, AnimationID.XBOWS_FLETCHING_YEW_DRAGON,
        AnimationID.STRINGING_SHORTBOW, AnimationID.STRINGING_OAK_SHORTBOW, AnimationID.STRINGING_WILLOW_SHORTBOW,
        AnimationID.STRINGING_MAPLE_SHORTBOW, AnimationID.STRINGING_YEW_SHORTBOW, AnimationID.STRINGING_MAGIC_SHORTBOW,
        AnimationID.STRINGING_LONGBOW, AnimationID.STRINGING_OAK_LONGBOW, AnimationID.STRINGING_WILLOW_LONGBOW,
        AnimationID.STRINGING_MAPLE_LONGBOW, AnimationID.STRINGING_YEW_LONGBOW, AnimationID.STRINGING_MAGIC_LONGBOW,
        AnimationID.HUMAN_FLETCHING_ADD_FEATHER, AnimationID.HUMAN_FLETCHING_ADD_ARROW_TIPS,
        AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_BRONZE, AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_IRON,
        AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_BLURITE, AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_STEEL,
        AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_MITHRIL, AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_ADAMANT,
        AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_RUNE, AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_DRAGON,
        AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS,
        // Smithing
        AnimationID.HUMAN_SMITHING, AnimationID.HUMAN_SMITHING_IMCANDO_HAMMER, AnimationID.HUMAN_FURNACE,
        AnimationID.HUMAN_PICKUPFLOOR,
        // Fishing
        AnimationID.INFERNALEEL_BREAK, AnimationID.INFERNALEEL_BREAK_IMCANDO, AnimationID.SNAKEBOSS_SLICEEEL,
        AnimationID.HUMAN_LARGENET, AnimationID.HUMAN_SMALLNET, AnimationID.HUMAN_FISH_ONSPOT, AnimationID.HUMAN_LOBSTER,
        AnimationID.HUMAN_HARPOON, AnimationID.HUMAN_HARPOON_BARBED, AnimationID.HUMAN_HARPOON_DRAGON,
        AnimationID.HUMAN_HARPOON_TRAILBLAZER_NO_INFERNAL, AnimationID.HUMAN_HARPOON_INFERNAL, AnimationID.HUMAN_HARPOON_CRYSTAL,
        AnimationID.HUMAN_HARPOON_LEAGUE_TRAILBLAZER, AnimationID.HUMAN_HARPOON_TRAILBLAZER_RELOADED_NO_INFERNAL,
        AnimationID.HUMAN_HARPOON_TRAILBLAZER, AnimationID.HUMAN_HARPOON_TRAILBLAZER_RELOADED,
        AnimationID.HUMAN_FISHING_CASTING, AnimationID.HUMAN_OCTOPUS_POT, AnimationID.BRUT_PLAYER_HAND_FISHING_END_BLANK,
        AnimationID.HUMAN_FISHING_CASTING_PEARL, AnimationID.HUMAN_FISHING_CASTING_PEARL_FLY, AnimationID.HUMAN_FISHING_CASTING_PEARL_BRUT,
        AnimationID.HUMAN_FISH_ONSPOT_PEARL, AnimationID.HUMAN_FISH_ONSPOT_PEARL_FLY, AnimationID.HUMAN_FISH_ONSPOT_PEARL_BRUT,
        AnimationID.HUMAN_FISHING_CASTING_PEARL_OILY, AnimationID.HUMAN_FISHING_ONSPOT_BRUT, AnimationID.BRUT_HUMAN_KNIFEUSE,
        // Mining (main tiers - see RuneLite's IdleNotifierPlugin source for the full set of
        // Motherlode-wall/Crashed-star variants if you want exhaustive coverage there too)
        AnimationID.HUMAN_MINING_BRONZE_PICKAXE, AnimationID.HUMAN_MINING_IRON_PICKAXE, AnimationID.HUMAN_MINING_STEEL_PICKAXE,
        AnimationID.HUMAN_MINING_BLACK_PICKAXE, AnimationID.HUMAN_MINING_MITHRIL_PICKAXE, AnimationID.HUMAN_MINING_ADAMANT_PICKAXE,
        AnimationID.HUMAN_MINING_RUNE_PICKAXE, AnimationID.HUMAN_MINING_GILDED_PICKAXE, AnimationID.HUMAN_MINING_DRAGON_PICKAXE,
        AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY, AnimationID.HUMAN_MINING_ZALCANO_PICKAXE,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL, AnimationID.HUMAN_MINING_INFERNAL_PICKAXE,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE, AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE,
        AnimationID.HUMAN_MINING_3A_PICKAXE, AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE,
        AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE, AnimationID.ARCEUUS_CHISEL_ESSENCE, AnimationID.ARCEUUS_CHISEL_ESSENCEBLOCK,
        AnimationID.PICKAXE_POWER_SWING, AnimationID.PICKAXE_POWER_SWING_BRONZE, AnimationID.PICKAXE_POWER_SWING_IRON,
        AnimationID.PICKAXE_POWER_SWING_STEEL, AnimationID.PICKAXE_POWER_SWING_BLACK, AnimationID.PICKAXE_POWER_SWING_MITHRIL,
        AnimationID.PICKAXE_POWER_SWING_ADAMANT, AnimationID.PICKAXE_POWER_SWING_RUNE, AnimationID.PICKAXE_POWER_SWING_GILDED,
        AnimationID.PICKAXE_POWER_SWING_DRAGON, AnimationID.PICKAXE_POWER_SWING_PRETTY, AnimationID.PICKAXE_POWER_SWING_ZALCANO,
        AnimationID.PICKAXE_POWER_SWING_INFERNAL, AnimationID.PICKAXE_POWER_SWING_3A, AnimationID.PICKAXE_POWER_SWING_CRYSTAL,
        AnimationID.PICKAXE_POWER_SWING_TRAILBLAZER, AnimationID.PICKAXE_POWER_SWING_LEAGUE_TRAILBLAZER,
        AnimationID.PICKAXE_POWER_SWING_TRAILBLAZER_NO_INFERNAL,
        // Herblore
        AnimationID.HUMAN_HERBING_GRIND, AnimationID.HUMAN_HERBING_VIAL, AnimationID.HUMAN_SALAMANDER_TAR_GRIND,
        AnimationID.HUMAN_MACHINERY_ALCHEMY01_RETORT01_INTERACT01, AnimationID.HUMAN_MACHINERY_ALCHEMY01_ALEMBIC01_INTERACT01,
        AnimationID.HUMAN_MACHINERY_ALCHEMY01_AGITATOR01_INTERACT01, AnimationID.HUMAN_ALCHEMY01_MILL01_INTERACT01,
        AnimationID.HUMAN_HERBING_VIAL_RESTART, AnimationID.HUMAN_HERBING_GRIND_RESTART,
        // Magic (skilling-adjacent)
        AnimationID.HUMAN_CASTCHARGEORB, AnimationID.DREAM_PLAYER_MAKE_PLANK_SPELL, AnimationID.LUNAR_HUMAN_MAGIC_SUMMON1,
        AnimationID.POH_CREATE_MAGIC_TABLET_WITHSTAFF, AnimationID.HUMAN_CAST_ENCHANTRING, AnimationID.HUMAN_ENCHANTAMULETLVL1,
        AnimationID.HUMAN_ENCHANTAMULETLVL2, AnimationID.HUMAN_ENCHANTAMULETLVL3, AnimationID.HUMAN_XBOW_ENCHANT_ARROWTIP,
        // Prayer
        AnimationID.HUMAN_BONE_SACRIFICE, AnimationID.QUEST_AHOY_HUMAN_FILLING_BUCKET, AnimationID.AHOY_BONE_DUMP,
        AnimationID.AHOY_BONE_GRIND, AnimationID.AHOY_FILLBUCKET_BONEDUST, AnimationID.HUMAN_PRAY_BLESSED_BONE_SHARDS_01,
        // Farming
        AnimationID.ULTRACOMPOST_MAKE, AnimationID.PICKING_MID, AnimationID.PICKING_LOW, AnimationID.PICKING_HIGH,
        AnimationID.FARMING_PICK_MUSHROOM, AnimationID.HUMAN_DIG,
        // Misc
        AnimationID.PISC_REPAIR_HAMMER, AnimationID.POH_CREATE_MAGIC_TABLET, AnimationID.HUMAN_FILLBUCKET_SANDPIT,
        AnimationID.MILKIT, AnimationID.PLAYER_CHURNS_MILK_SHORT, AnimationID.PLAYER_CHURNS_MILK_MEDIUM,
        AnimationID.PLAYER_CHURNS_MILK_LONG, AnimationID.VM_PLAYER_USE_SPECIMEN_BRUSH, AnimationID.VM_PLAYER_USE_ROCKPICK,
        AnimationID.HUMAN_PICKUPTABLE, AnimationID.VARLAMORE_THIEVING_SEARCH
    );

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

        // getInteracting() is set to your combat opponent, or to an NPC you're
        // skilling from (e.g. a fishing spot) - catches combat and NPC-based
        // skilling precisely, no animation list needed.
        Actor target = player.getInteracting();
        boolean targetingActor = target != null && target != player;

        int animation = player.getAnimation();
        boolean isKnownSkillingAnimation = SKILLING_ANIMATIONS.contains(animation);

        boolean isPerformingAction = targetingActor || isKnownSkillingAnimation;

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

        if (animation != -1)
        {
            // Agility, Runecrafting, and general Thieving aren't in SKILLING_ANIMATIONS by
            // design (see the comment on that set) - expect to see unmatched IDs from those.
            // Anything else showing up here repeatedly might be worth adding to the set.
            log.debug("Unmatched non-idle animation {} - not in SKILLING_ANIMATIONS, ignoring", animation);
        }

        if (!wasPerformingAction)
        {
            // Standing around, walking, or an animation we don't recognize as
            // a skilling/combat action - don't notify.
            return;
        }

        idleTicks++;
        log.debug("Idle tick {}/{} since last action stopped", idleTicks, config.idleThresholdTicks());

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
