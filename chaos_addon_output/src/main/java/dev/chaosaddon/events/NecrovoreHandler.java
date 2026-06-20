package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * Handles Necrovore passives (spec-accurate):
 * - soul_drop: kill counter NBT, soul HP bonus (tiered), halve on death
 * - undead_diplomacy: nearby undead follow player as pets, don't attack
 * - death_feast: poison heals instead of hurts, fire does double damage, kills restore food
 */
public class NecrovoreHandler {

    private static final ResourceLocation SOUL_HP_MOD =
        ResourceLocation.fromNamespaceAndPath("chaos_addon", "necrovore_soul_hp");

    public static final String SOUL_TAG = "chaos_soul_count";
    private static final String UNDEAD_PET_TAG = "chaos_undead_pet";
    private static final int MAX_SOULS = 30;

    // ── Soul drop on mob death ──────────────────────────────────────────────────
    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof ServerPlayer) return;

        LivingEntity dead = event.getEntity();
        boolean wasPet = dead.getTags().contains(UNDEAD_PET_TAG);

        for (ServerPlayer player : level.players()) {
            if (!OriginHelper.hasPower(player, "chaos_addon:necrovore/soul_drop")) continue;
            if (player.distanceTo(dead) > 15) continue;

            addSouls(player, 1);

            level.sendParticles(ParticleTypes.SOUL,
                dead.getX(), dead.getY() + 0.8, dead.getZ(),
                10, 0.3, 0.4, 0.3, 0.05);
            level.playSound(null, dead.blockPosition(),
                SoundEvents.SCULK_BLOCK_BREAK, SoundSource.PLAYERS, 0.5f, 1.4f);
            break;
        }
    }

    // ── Player death: halve soul count ─────────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:necrovore/soul_drop")) return;
        int current = player.getPersistentData().getInt(SOUL_TAG);
        player.getPersistentData().putInt(SOUL_TAG, current / 2);
    }

    // ── Мёртвая Плоть: poison heals, fire doubles ───────────────────────────────
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:necrovore/death_feast")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        String dmgId = event.getSource().getMsgId();

        // Poison damage (type "magic") while player has Poison effect → heal instead
        if (dmgId.equals("magic") && player.hasEffect(MobEffects.POISON)) {
            event.setCanceled(true);
            if (player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0f); // +0.5❤ per tick
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(),
                2, 0.2, 0.3, 0.2, 0.0);
            return;
        }

        // Fire damage → double
        if (dmgId.equals("inFire") || dmgId.equals("onFire") || dmgId.equals("lava")
                || dmgId.equals("hotFloor")) {
            event.setAmount(event.getAmount() * 2.0f);
        }
    }

    // ── Per-tick passives ──────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:necrovore/soul_drop")) return;

        // ── Soul HP bonus update every second ──
        if (player.tickCount % 20 == 0) {
            int souls = getSouls(player);
            applySoulHpBonus(player, souls);
        }

        // ── Undead diplomacy: tag & steer nearby undead ──
        if (OriginHelper.hasPower(player, "chaos_addon:necrovore/undead_diplomacy")
                && player.tickCount % 40 == 0) {

            List<Mob> nearbyUndead = level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(20),
                e -> e.isAlive() && isUndead(e));

            int petCount = 0;
            for (Mob mob : nearbyUndead) {
                // Don't attack player
                if (mob.getTarget() == player) mob.setTarget(null);

                // Tag up to 10 as following pets
                if (petCount < 10) {
                    mob.addTag(UNDEAD_PET_TAG);
                    mob.getNavigation().moveTo(player, 1.0);
                    petCount++;

                    // Grey smoke particles above each pet (every 2 ticks of 40)
                    if (player.tickCount % 80 == 0) {
                        level.sendParticles(ParticleTypes.SMOKE,
                            mob.getX(), mob.getY() + 1.8, mob.getZ(),
                            3, 0.2, 0.2, 0.2, 0.01);
                    }
                }
            }
        }

        // ── Despawn raised dead ──
        level.getEntitiesOfClass(Mob.class,
            player.getBoundingBox().inflate(80),
            e -> e.getTags().contains("chaos_necro_raised"))
            .forEach(mob -> {
                int ticks = mob.getPersistentData().getInt("chaos_despawn_ticks");
                if (ticks <= 0) {
                    mob.kill();
                    level.sendParticles(ParticleTypes.SOUL,
                        mob.getX(), mob.getY() + 1.0, mob.getZ(),
                        8, 0.3, 0.5, 0.3, 0.05);
                } else {
                    mob.getPersistentData().putInt("chaos_despawn_ticks", ticks - 1);
                    if (player.tickCount % 20 == 0) {
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            mob.getX(), mob.getY() + 0.5, mob.getZ(),
                            2, 0.2, 0.3, 0.2, 0.02);
                    }
                }
            });
    }

    // ── Kill restores 1 food (death feast) ──────────────────────────────────────
    @SubscribeEvent
    public static void onKillRestoreHunger(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:necrovore/death_feast")) return;
        player.getFoodData().eat(1, 0.5f);
    }

    // ── Public API ─────────────────────────────────────────────────────────────
    public static int getSouls(ServerPlayer player) {
        return Math.min(player.getPersistentData().getInt(SOUL_TAG), MAX_SOULS);
    }

    public static boolean spendSouls(ServerPlayer player, int amount) {
        int current = getSouls(player);
        if (current < amount) return false;
        player.getPersistentData().putInt(SOUL_TAG, current - amount);
        return true;
    }

    public static void addSouls(ServerPlayer player, int amount) {
        int current = player.getPersistentData().getInt(SOUL_TAG);
        player.getPersistentData().putInt(SOUL_TAG, Math.min(MAX_SOULS, current + amount));
    }

    // ── Private helpers ────────────────────────────────────────────────────────
    private static boolean isUndead(Mob mob) {
        return mob instanceof Zombie
            || mob instanceof ZombieVillager
            || mob instanceof Skeleton
            || mob instanceof WitherSkeleton
            || mob instanceof Drowned;
    }

    /**
     * Spec: <5 souls → 0 bonus
     *        5–15 souls → each gives +0.5❤ (= +1 attr)
     *       15–30 souls → each gives +1❤  (= +2 attr)
     */
    private static void applySoulHpBonus(ServerPlayer player, int souls) {
        var instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) return;
        instance.removeModifier(SOUL_HP_MOD);

        double bonus;
        if (souls < 5) {
            bonus = 0;
        } else if (souls < 15) {
            bonus = (souls - 5) * 1.0; // 0–10 attr (0–5❤)
        } else {
            bonus = 10.0 + (souls - 15) * 2.0; // 10–40 attr (5–20❤)
        }

        if (bonus > 0) {
            instance.addTransientModifier(new AttributeModifier(
                SOUL_HP_MOD, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
