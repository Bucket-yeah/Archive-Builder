package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * Handles Mirror Phantom passives:
 * - mirror_nature: reflect first instance of each debuff back to attacker (doubled)
 * - disguise: periodic name change + particles to simulate disguise
 * - death_echo: copy effect based on entity type when nearby mob dies
 * - mirror_world: when "chaos_mirror_active" tag is on player, reflect damage to attacker
 */
public class MirrorPhantomHandler {

    /** Tracks which effect types have already been reflected per player */
    private static final Map<UUID, Set<String>> REFLECTED_EFFECTS = new HashMap<>();
    private static final Map<UUID, Long> REFLECTED_EXPIRY = new HashMap<>();
    private static final Map<UUID, Long> DISGUISE_TICK = new HashMap<>();

    private static final Set<String> DEBUFF_IDS = new HashSet<>(Arrays.asList(
        "slowness", "mining_fatigue", "nausea", "blindness", "hunger",
        "weakness", "poison", "wither", "levitation", "unluck",
        "bad_omen", "darkness"
    ));

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Mirror Nature: expire reflected debuff memory after 60 seconds
        long now = level.getGameTime();
        UUID pid = player.getUUID();
        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        if (REFLECTED_EXPIRY.getOrDefault(pid, 0L) < now) {
            REFLECTED_EFFECTS.remove(pid);
            REFLECTED_EXPIRY.remove(pid);
        }

        // Disguise: every 30 seconds change custom name to "???" for 5 seconds
        if (OriginHelper.hasPower(player, "chaos_addon:mirror_phantom/disguise")) {
            Long lastDisguise = DISGUISE_TICK.getOrDefault(pid, 0L);
            if (now - lastDisguise >= cfg.mirrorDisguiseCycle) {
                player.setCustomName(net.minecraft.network.chat.Component.literal("???")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
                player.setCustomNameVisible(true);
                DISGUISE_TICK.put(pid, now);

                level.sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.5, 0.8, 0.5, 0.1);
                level.sendParticles(ParticleTypes.WITCH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    8, 0.3, 0.5, 0.3, 0.05);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            // Remove custom name after disguise duration
            if (now - lastDisguise >= cfg.mirrorDisguiseDuration && now - lastDisguise < cfg.mirrorDisguiseDuration + 1) {
                player.setCustomName(null);
                player.setCustomNameVisible(false);
            }
        }

        // Sun vulnerability: Slowness III + Weakness II in sunlight
        if (OriginHelper.hasPower(player, "chaos_addon:mirror_phantom/sun_vulnerability")
                && player.tickCount % cfg.mirrorSunCheckInterval == 0) {
            boolean inSun = level.canSeeSky(player.blockPosition())
                && level.isDay()
                && !player.isInWaterOrRain()
                && !player.isUnderWater();
            if (inSun) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, cfg.mirrorSunDebuffDuration, 2, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, cfg.mirrorSunDebuffDuration, 1, false, false));
            }
        }

        // Mirror world: check tag active, show FX
        if (player.getTags().contains("chaos_mirror_world")) {
            if (player.tickCount % 10 == 0) {
                for (int i = 0; i < 6; i++) {
                    double angle = (Math.PI * 2 / 6) * i + (player.tickCount * 0.05);
                    double px = player.getX() + Math.cos(angle) * 3.0;
                    double pz = player.getZ() + Math.sin(angle) * 3.0;
                    level.sendParticles(ParticleTypes.ENCHANT,
                        px, player.getY() + 1.0, pz,
                        1, 0, 0.3, 0, 0.0);
                }
                level.sendParticles(ParticleTypes.GLOW,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    4, 0.4, 0.8, 0.4, 0.02);
            }
        }
    }

    /** Mirror Nature: reflect first instance of each debuff */
    @SubscribeEvent
    public static void onDebuffReceived(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:mirror_phantom/mirror_nature")) return;

        // Check for magic/poison/wither damage source as proxy for debuff
        String dmgId = event.getSource().getMsgId();
        if (!dmgId.equals("magic") && !dmgId.equals("wither") && !dmgId.equals("poison")) return;

        UUID pid = player.getUUID();
        Set<String> reflected = REFLECTED_EFFECTS.computeIfAbsent(pid, k -> new HashSet<>());

        if (reflected.contains(dmgId)) return; // already reflected this type
        reflected.add(dmgId);

        if (!(player.level() instanceof ServerLevel level)) return;
        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        REFLECTED_EXPIRY.put(pid, level.getGameTime() + cfg.mirrorReflectExpiry);

        // Reflect back to attacker
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            attacker.hurt(player.damageSources().magic(), event.getAmount() * cfg.mirrorReflectMult);
            event.setCanceled(true);
            level.sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0, player.getZ(),
                20, 0.5, 0.8, 0.5, 0.1);
            level.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.8f, 1.5f);
        }
    }

    /** Mirror World: reflect melee damage back to attacker */
    @SubscribeEvent
    public static void onMirrorWorldDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getTags().contains("chaos_mirror_world")) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:mirror_phantom/mirror_world")) return;

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            attacker.hurt(player.damageSources().magic(), event.getAmount());
            event.setCanceled(true);

            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.FLASH,
                    attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
                    1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.ENCHANT,
                    attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
                    15, 0.4, 0.6, 0.4, 0.1);
            }
        }
    }

    /**
     * Mirror Death: when the Mirror Phantom player dies, spawn a Phantom copy at their location.
     * The copy has 50% of max HP and lasts 30 seconds, distracting enemies.
     */
    @SubscribeEvent
    public static void onMirrorPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:mirror_phantom/death_echo")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        var phantom = EntityType.PHANTOM.create(level);
        if (phantom == null) return;
        phantom.moveTo(player.getX(), player.getY() + 0.5, player.getZ(), player.getYRot(), 0);
        phantom.addTag("chaos_managed_entity");
        phantom.addTag("chaos_mirror_copy");
        phantom.setHealth(Math.max(1.0f, player.getMaxHealth() * 0.5f));
        phantom.setCustomName(net.minecraft.network.chat.Component.literal(
            "§d" + player.getGameProfile().getName() + " [Отражение]"));
        phantom.setCustomNameVisible(true);
        phantom.getPersistentData().putInt("chaos_despawn_ticks", 600); // 30s
        phantom.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, true, false));
        level.addFreshEntity(phantom);

        level.sendParticles(ParticleTypes.ENCHANT,
            player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.8, 1.2, 0.8, 0.1);
        level.sendParticles(ParticleTypes.FLASH,
            player.getX(), player.getY() + 1.0, player.getZ(), 3, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0f, 0.6f);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "§d🪞 Зеркальная смерть — ваше отражение живёт 30с..."));
    }

    /** Death Echo: copy effect based on entity type */
    @SubscribeEvent
    public static void onNearbyDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof ServerPlayer) return;

        LivingEntity dead = event.getEntity();

        for (ServerPlayer player : level.players()) {
            if (!OriginHelper.hasPower(player, "chaos_addon:mirror_phantom/death_echo")) continue;
            if (player.distanceTo(dead) > 8) continue;

            MobEffectInstance echo = getEchoEffect(dead);
            if (echo == null) continue;

            player.addEffect(echo);

            level.sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.2, player.getZ(),
                15, 0.4, 0.6, 0.4, 0.1);
            level.sendParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(),
                6, 0.3, 0.5, 0.3, 0.0);
            level.playSound(null, player.blockPosition(),
                SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 0.7f, 1.0f);
            break;
        }
    }

    private static MobEffectInstance getEchoEffect(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        if (type == EntityType.BLAZE) {
            return new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0);
        } else if (type == EntityType.WITCH) {
            return new MobEffectInstance(MobEffects.LUCK, 400, 0);
        } else if (type == EntityType.WITHER_SKELETON || type == EntityType.SKELETON) {
            return new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1);
        } else if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) {
            return new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1);
        } else if (type == EntityType.ENDERMAN) {
            return new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0);
        } else if (type == EntityType.ZOMBIE || type == EntityType.DROWNED) {
            return new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0);
        } else if (type == EntityType.CREEPER) {
            return new MobEffectInstance(MobEffects.ABSORPTION, 400, 1);
        } else if (type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN) {
            return new MobEffectInstance(MobEffects.WATER_BREATHING, 400, 0);
        }
        return new MobEffectInstance(MobEffects.REGENERATION, 200, 0);
    }
}
