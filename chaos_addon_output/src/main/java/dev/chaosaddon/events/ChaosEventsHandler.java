package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Chaos Events: periodic random bonus events per origin (every 10–20 min).
 * Each origin gets a thematic "gift" that creates memorable moments.
 */
public class ChaosEventsHandler {

    private static final Random RNG = new Random();
    private static final Map<UUID, Long> LAST_EVENT_TICK = new HashMap<>();

    private static final long MIN_INTERVAL = 12000L; // 10 min
    private static final long MAX_INTERVAL = 24000L; // 20 min

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % 20 != 0) return; // check once per second

        long now = level.getGameTime();
        UUID pid = player.getUUID();
        long lastEvent = LAST_EVENT_TICK.getOrDefault(pid, 0L);
        long nextInterval = MIN_INTERVAL + (long)(RNG.nextDouble() * (MAX_INTERVAL - MIN_INTERVAL));

        if (now - lastEvent < nextInterval) return;
        LAST_EVENT_TICK.put(pid, now);

        triggerOriginEvent(player, level);
    }

    private static void triggerOriginEvent(ServerPlayer player, ServerLevel level) {
        // Blood Smith: Blood Surge — 50 free charges
        if (OriginHelper.hasOrigin(player, "chaos_addon:blood_smith")) {
            BloodSmithHandler.addCharges(player, 50);
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                player.getX(), player.getY() + 1.5, player.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
            level.playSound(null, player.blockPosition(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 0.5f);
            player.sendSystemMessage(Component.literal("§c⚡ ХАОС-СОБЫТИЕ: §4Кровяной Прилив! §7+50 зарядов крови!"));
            return;
        }

        // Necrovore: Soul Bloom — 10 free souls
        if (OriginHelper.hasOrigin(player, "chaos_addon:necrovore")) {
            NecrovoreHandler.addSouls(player, 10);
            level.sendParticles(ParticleTypes.SOUL,
                player.getX(), player.getY() + 1.5, player.getZ(), 30, 0.5, 0.8, 0.5, 0.08);
            level.playSound(null, player.blockPosition(),
                SoundEvents.SCULK_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.4f);
            player.sendSystemMessage(Component.literal("§5⚡ ХАОС-СОБЫТИЕ: §dЦветение Душ! §7+10 душ!"));
            return;
        }

        // Biomorph: Rapid Adaptation — instant max biome time for current biome
        if (OriginHelper.hasOrigin(player, "chaos_addon:biomorph")) {
            String biomeName = level.getBiome(player.blockPosition()).unwrapKey()
                .map(k -> k.location().toString()).orElse("unknown");
            var bData = player.getData(dev.chaosaddon.init.ModAttachments.BIOME_DATA);
            for (int i = 0; i < 3600; i++) bData.incrementBiomeTime(biomeName);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.5, player.getZ(), 40, 0.6, 0.8, 0.6, 0.05);
            player.sendSystemMessage(Component.literal("§2⚡ ХАОС-СОБЫТИЕ: §aБыстрая Адаптация! §7Биом освоен мгновенно!"));
            return;
        }

        // Swarm Lord: Surge — spawn 5 extra bugs instantly
        if (OriginHelper.hasOrigin(player, "chaos_addon:swarm_lord")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
            level.sendParticles(ParticleTypes.GLOW,
                player.getX(), player.getY() + 1.5, player.getZ(), 50, 0.8, 1.0, 0.8, 0.05);
            player.sendSystemMessage(Component.literal("§6⚡ ХАОС-СОБЫТИЕ: §eРоевой Всплеск! §7Атака и скорость +20с!"));
            return;
        }

        // Lunar Renegade: Moon Blessing — temporary immunity to sun damage
        if (OriginHelper.hasOrigin(player, "chaos_addon:lunar_renegade")) {
            player.getPersistentData().putLong("chaos_lunar_sun_immunity", level.getGameTime() + 6000);
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 600, 0));
            level.sendParticles(ParticleTypes.GLOW,
                player.getX(), player.getY() + 2.0, player.getZ(), 60, 0.8, 1.2, 0.8, 0.05);
            level.playSound(null, player.blockPosition(),
                SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 0.5f, 0.3f);
            player.sendSystemMessage(Component.literal("§b⚡ ХАОС-СОБЫТИЕ: §9Лунное Благословение! §7Иммунитет к солнцу 5 мин!"));
            return;
        }

        // Phantom Archaeologist: Data Recovery — restore all saved XP
        if (OriginHelper.hasOrigin(player, "chaos_addon:phantom_archaeologist")) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 600, 1));
            player.giveExperiencePoints(100);
            level.sendParticles(ParticleTypes.NAUTILUS,
                player.getX(), player.getY() + 1.5, player.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
            player.sendSystemMessage(Component.literal("§6⚡ ХАОС-СОБЫТИЕ: §eВосстановление Данных! §7+100 XP + Удача II 5мин!"));
            return;
        }

        // Ancient Sentinel: Tectonic Pulse — brief invulnerability
        if (OriginHelper.hasOrigin(player, "chaos_addon:ancient_sentinel")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4)); // near-immunity
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 2));
            level.sendParticles(
                new net.minecraft.core.particles.BlockParticleOption(
                    ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
                player.getX(), player.getY() + 0.5, player.getZ(), 60, 0.8, 0.5, 0.8, 0.1);
            level.playSound(null, player.blockPosition(),
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.5f);
            player.sendSystemMessage(Component.literal("§8⚡ ХАОС-СОБЫТИЕ: §7Тектонический Пульс! §6Броня+Атака 10с!"));
            return;
        }

        // Mirror Phantom: Identity Surge — extra reflect + invisibility
        if (OriginHelper.hasOrigin(player, "chaos_addon:mirror_phantom")) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 2));
            level.sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.5, player.getZ(), 50, 0.6, 0.8, 0.6, 0.1);
            player.sendSystemMessage(Component.literal("§d⚡ ХАОС-СОБЫТИЕ: §5Подъём Личности! §7Невидимость+Скорость 30с!"));
            return;
        }

        // Parasitic Mind: Infection Bloom — all infected get healed + boosted
        if (OriginHelper.hasOrigin(player, "chaos_addon:parasitic_mind")) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                player.getX(), player.getY() + 1.5, player.getZ(), 40, 0.6, 0.8, 0.6, 0.03);
            player.sendSystemMessage(Component.literal("§2⚡ ХАОС-СОБЫТИЕ: §aЦветение Заражения! §7Реген+Атака!"));
            return;
        }

        // Generic fallback: any other origin
        player.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        level.sendParticles(ParticleTypes.FLASH,
            player.getX(), player.getY() + 1.5, player.getZ(), 3, 0, 0, 0, 0);
        player.sendSystemMessage(Component.literal("§e⚡ ХАОС-СОБЫТИЕ: §6Хаотичная Удача! §7Удача II + Реген!"));
    }
}
