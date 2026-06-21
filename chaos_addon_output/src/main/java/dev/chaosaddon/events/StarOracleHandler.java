package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Random;

/**
 * Handles Star Oracle passives:
 * - star_foresight: warning particles when a mob targets the player and is close
 * - cosmic_body: no hunger, immunity to poison/wither, +75% melee damage received
 * - star_aura: periodic mini-meteor shower
 */
public class StarOracleHandler {

    private static final Random RNG = new Random();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Cosmic body: suppress hunger
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/cosmic_body")) {
            player.getFoodData().setFoodLevel(20);
        }

        // ── Event Prediction: storm warning + player proximity alert ──
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/star_foresight")
                && player.tickCount % 60 == 0) {
            // Storm prediction: warn 30s before storm / alert during storm
            boolean storming = level.isThundering();
            boolean raining = level.isRaining() && !storming;
            if (storming) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("⚡ ПРЕДСКАЗАНИЕ: Гроза! Молния поражает!"),
                    false);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.5, player.getZ(),
                    12, 0.5, 0.8, 0.5, 0.05);
            } else if (raining) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("🌧 ПРЕДСКАЗАНИЕ: Дождь — растения растут быстрее!"),
                    false);
            }

            // Player proximity alert: detect other players within 50 blocks
            List<net.minecraft.world.entity.player.Player> nearbyPlayers =
                level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                    player.getBoundingBox().inflate(50),
                    e -> e != player && e.isAlive());
            if (!nearbyPlayers.isEmpty()) {
                var closest = nearbyPlayers.stream()
                    .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
                    .orElse(null);
                if (closest != null) {
                    double dist = closest.distanceTo(player);
                    String dir = getDirectionHint(player, closest);
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§e👁 ЗВЁЗДНОЕ ПРЕДВИДЕНИЕ: §f" + closest.getGameProfile().getName()
                            + " §7~" + (int)dist + "м §e" + dir),
                        false);
                    level.sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 2.0, player.getZ(),
                        10, 0.5, 0.3, 0.5, 0.04);
                    level.playSound(null, player.blockPosition(),
                        SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.4f, 1.8f);
                }
            }
        }

        // Star Foresight: detect targeting mobs within 12 blocks, show warning
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/star_foresight")) {
            if (player.tickCount % 10 == 0) {
                List<Mob> targeting = level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(10),
                    e -> e.isAlive() && e.getTarget() == player);
                if (!targeting.isEmpty()) {
                    // Warning ring of particles
                    for (int i = 0; i < 8; i++) {
                        double angle = (Math.PI * 2 / 8) * i;
                        double px = player.getX() + Math.cos(angle) * 1.5;
                        double pz = player.getZ() + Math.sin(angle) * 1.5;
                        level.sendParticles(ParticleTypes.END_ROD,
                            px, player.getY() + 0.5, pz,
                            1, 0, 0.3, 0, 0.0);
                    }
                    level.sendParticles(ParticleTypes.FLASH,
                        player.getX(), player.getY() + 1.5, player.getZ(),
                        1, 0, 0, 0, 0);
                }
            }
        }

        // Star Aura: meteor shower every 20 seconds
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/star_aura")) {
            ChaosAddonConfig cfg = ChaosAddonConfig.get();
            if (player.tickCount % cfg.starAuraMeteorInterval == 0) {
                fireMeteorShower(player, level, cfg.starAuraMeteorCount, cfg.starAuraMeteorRadius);
            }
        }

        // Star Apocalypse: process pending meteor rain
        dev.chaosaddon.commands.StarApocalypseCommand.processMeteors(player, level);

        // ── Celestial Events: triggered by moon phase ──
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/star_foresight")
                && player.tickCount % 400 == 0) {
            int moonPhase = level.getMoonPhase();
            long dayTime = level.getDayTime() % 24000;
            // Full moon at midnight: mega meteor shower
            if (moonPhase == 0 && dayTime > 13000 && dayTime < 14000) {
                fireMeteorShower(player, level, 15, 20);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e🌕 НЕБЕСНОЕ СОБЫТИЕ: Мегеорный Ливень! §7(15 метеоров)"));
                level.playSound(null, player.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.3f);
            }
            // New moon: summon temporary celestial guardian
            else if (moonPhase == 4 && dayTime > 13000 && dayTime < 14000) {
                long lastGuardian = player.getPersistentData().getLong("chaos_celestial_guardian_tick");
                if (level.getGameTime() - lastGuardian > ChaosAddonConfig.get().starGuardianDailyCooldown) {
                    summonCelestialGuardian(player, level);
                    player.getPersistentData().putLong("chaos_celestial_guardian_tick", level.getGameTime());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§9🌑 НЕБЕСНОЕ СОБЫТИЕ: Небесный Страж призван!"));
                }
            }
            // Dawn (time ~23500): foresight range doubled for 1 min  
            else if (dayTime > 23000 && dayTime < 23600) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.LUCK, ChaosAddonConfig.get().starDawnLuckDuration, 1, false, false));
                level.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 2.0, player.getZ(), 30, 0.8, 0.3, 0.8, 0.05);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e🌅 НЕБЕСНОЕ СОБЫТИЕ: Рассветное Предвидение! §7Удача II на 1 мин"));
            }
        }
    }

    /** Cosmic body: amplify melee damage, suppress fire damage halving */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:star_oracle/melee_vulnerability")) return;

        String dmgType = event.getSource().getMsgId();
        boolean isMelee = dmgType.equals("mob") || dmgType.equals("player");
        boolean isFire = dmgType.contains("fire") || dmgType.contains("lava") || dmgType.equals("onFire");
        if (isMelee || isFire) {
            event.setAmount(event.getAmount() * ChaosAddonConfig.get().starMeleeVulnerability);
        }

        // Immunity to poison / wither
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/cosmic_body")) {
            String id = event.getSource().getMsgId();
            if (id.equals("wither") || id.equals("magic")) {
                event.setCanceled(true);
            }
        }
    }

    /** Summon a temporary celestial guardian (Iron Golem with glow) near player */
    private static void summonCelestialGuardian(ServerPlayer player, ServerLevel level) {
        Mob golem = (Mob) EntityType.IRON_GOLEM.create(level);
        if (golem == null) return;
        golem.moveTo(player.getX() + 3, player.getY(), player.getZ(), 0, 0);
        golem.addTag("chaos_managed_entity");
        golem.setCustomName(net.minecraft.network.chat.Component.literal("§9Небесный Страж"));
        golem.setCustomNameVisible(true);
        golem.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.GLOWING, Integer.MAX_VALUE, 0, true, false));
        // Store despawn timer
        golem.getPersistentData().putInt("chaos_despawn_ticks", ChaosAddonConfig.get().starGuardianDespawnTicks);
        level.addFreshEntity(golem);
        level.sendParticles(ParticleTypes.END_ROD,
            golem.getX(), golem.getY() + 1.0, golem.getZ(), 30, 0.6, 1.0, 0.6, 0.05);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ELDER_GUARDIAN_DEATH_LAND, SoundSource.PLAYERS, 0.8f, 1.5f);
    }

    /** Fires N meteors (explosion + particles) at random positions around the player */
    public static void fireMeteorShower(ServerPlayer player, ServerLevel level, int count, int radius) {
        for (int i = 0; i < count; i++) {
            int dx = RNG.nextInt(radius * 2 + 1) - radius;
            int dz = RNG.nextInt(radius * 2 + 1) - radius;
            int tx = (int) player.getX() + dx;
            int tz = (int) player.getZ() + dz;
            int ty = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, tx, tz);

            // Particle trail from sky
            for (int j = 0; j < 5; j++) {
                level.sendParticles(ParticleTypes.FIREWORK,
                    tx + 0.5, ty + 5 - j, tz + 0.5,
                    3, 0.2, 0, 0.2, 0.05);
            }

            // Damage mobs near impact
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(tx - 2, ty - 1, tz - 2, tx + 2, ty + 3, tz + 2),
                e -> e != player && e.isAlive());
            for (LivingEntity v : victims) {
                v.hurt(player.damageSources().magic(), ChaosAddonConfig.get().starMeteorImpactDamage);
            }

            // Impact particles
            level.sendParticles(ParticleTypes.EXPLOSION,
                tx + 0.5, ty + 0.5, tz + 0.5, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME,
                tx + 0.5, ty + 0.5, tz + 0.5, 12, 0.5, 0.3, 0.5, 0.05);
        }

        level.playSound(null, player.blockPosition(),
            SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    /** Returns a compass-direction hint from observer to target. */
    private static String getDirectionHint(net.minecraft.world.entity.Entity from, net.minecraft.world.entity.Entity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "[→В]";
        if (angle < 67.5)  return "[↘ЮВ]";
        if (angle < 112.5) return "[↓Ю]";
        if (angle < 157.5) return "[↙ЮЗ]";
        if (angle < 202.5) return "[←З]";
        if (angle < 247.5) return "[↖СЗ]";
        if (angle < 292.5) return "[↑С]";
        return "[↗СВ]";
    }
}
