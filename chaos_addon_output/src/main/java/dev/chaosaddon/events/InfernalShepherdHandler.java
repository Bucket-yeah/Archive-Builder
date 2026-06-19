package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Infernal Shepherd passives:
 * - fire_diplomacy: nether mobs neutral; gold in hand = follow; zombie piglin death triggers revenge on killer
 * - lava_feeding: hunger restored in lava; Strength II after 30s continuous lava
 * - lava_lord: fire trail when leaving lava; lava walking (convert lava to magma temporarily)
 */
public class InfernalShepherdHandler {

    private static final Map<UUID, Boolean> WAS_IN_LAVA = new HashMap<>();
    private static final Map<UUID, Integer> LAVA_TICKS = new HashMap<>();
    private static final Map<UUID, Long> STRENGTH_GIVEN = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/lava_feeding")) return;

        long now = level.getGameTime();
        boolean inLava = player.isInLava();

        // Lava Feeding: +1 hunger every 2s in lava
        if (inLava && now % 40 == 0) {
            player.getFoodData().eat(1, 0.5f);
        }
        // Water drains hunger
        if (player.isInWater() && now % 40 == 0) {
            player.getFoodData().eat(-2, 0);
        }

        // Track continuous lava time for Strength II buff
        if (inLava) {
            int ticks = LAVA_TICKS.getOrDefault(player.getUUID(), 0) + 1;
            LAVA_TICKS.put(player.getUUID(), ticks);
            if (ticks >= 600) { // 30 seconds
                Long lastStrength = STRENGTH_GIVEN.getOrDefault(player.getUUID(), 0L);
                if (now - lastStrength >= 400) { // don't spam
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 1, false, true));
                    STRENGTH_GIVEN.put(player.getUUID(), now);
                    player.displayClientMessage(Component.literal("§c☄ Перегрев! Сила усилена!"), true);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        20, 0.5, 0.8, 0.5, 0.08);
                }
            }
        } else {
            LAVA_TICKS.put(player.getUUID(), 0);
        }

        // Lava Lord: fire trail when leaving lava
        boolean wasInLava = WAS_IN_LAVA.getOrDefault(player.getUUID(), false);
        if (OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/lava_lord")) {
            if (!inLava && wasInLava) {
                // Just exited lava - create fire trail blocks around player
                BlockPos pos = player.blockPosition();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos firePos = pos.offset(dx, 0, dz);
                        if (level.getBlockState(firePos).isAir()) {
                            level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                }
                // Schedule fire trail damage for 5 seconds (done via repeated check)
                player.getPersistentData().putInt("infernal_trail_ticks", 100);
            }
            // Apply fire trail damage to nearby entities
            int trailTicks = player.getPersistentData().getInt("infernal_trail_ticks");
            if (trailTicks > 0) {
                player.getPersistentData().putInt("infernal_trail_ticks", trailTicks - 1);
                if (trailTicks % 20 == 0) { // every 1s
                    List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(2.0), e -> e != player && e.isAlive());
                    nearby.forEach(e -> {
                        e.hurt(level.damageSources().inFire(), 1.0f);
                        e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 40));
                    });
                }
            }
        }
        WAS_IN_LAVA.put(player.getUUID(), inLava);

        // Lava Lord: walk on lava - convert lava blocks below to magma temporarily
        if (OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/lava_lord") && !inLava) {
            BlockPos below = player.blockPosition().below();
            if (level.getBlockState(below).getFluidState().getType()
                    == net.minecraft.world.level.material.Fluids.LAVA.getSource()) {
                // Place temporary magma block - let's just use a solid block to walk on
                // and remove it later via ticking (simple: just prevent fall damage)
                // Actually, for lava walking we set the block temporarily
                level.setBlock(below, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                // Schedule removal
                level.scheduleTick(below, Blocks.MAGMA_BLOCK, 80);
            }
        }

        // Fire Diplomacy: nether mobs neutral + follow with gold in hand
        if (OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/fire_diplomacy") && now % 20 == 0) {
            AABB range = player.getBoundingBox().inflate(20);
            boolean hasGold = player.getMainHandItem().is(Items.GOLD_INGOT)
                || player.getOffhandItem().is(Items.GOLD_INGOT)
                || player.getMainHandItem().is(Items.GOLDEN_SWORD)
                || player.getMainHandItem().is(Items.GOLDEN_AXE);

            level.getEntitiesOfClass(Mob.class, range, e -> e.isAlive()).forEach(mob -> {
                if (mob instanceof ZombifiedPiglin || mob instanceof Ghast || mob instanceof Blaze) {
                    if (!mob.isPersistenceRequired()) {
                        mob.setTarget(null);
                        if (hasGold && mob.distanceTo(player) > 3) {
                            mob.getNavigation().moveTo(player, 0.8);
                            level.sendParticles(ParticleTypes.GLOW,
                                mob.getX(), mob.getY() + 1.5, mob.getZ(),
                                2, 0.2, 0.2, 0.2, 0.01);
                        }
                    }
                }
            });
        }
    }

    // Zombie piglin death: redirect aggro to killer
    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ZombifiedPiglin dead)) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        List<ServerPlayer> shepherds = level.players().stream()
            .filter(p -> OriginHelper.hasPower(p, "chaos_addon:infernal_shepherd/fire_diplomacy"))
            .filter(p -> p.distanceTo(dead) <= 20)
            .toList();
        if (shepherds.isEmpty()) return;

        // Find who killed the piglin
        LivingEntity killer = dead.getKillCredit() instanceof LivingEntity le ? le : null;
        if (killer == null || shepherds.contains(killer)) return;

        // Redirect nearby piglins to attack the killer
        level.getEntitiesOfClass(ZombifiedPiglin.class,
            dead.getBoundingBox().inflate(30),
            e -> e.isAlive() && e != dead)
            .forEach(piglin -> {
                piglin.setTarget(killer);
                piglin.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0, false, true));
            });

        if (!shepherds.isEmpty()) {
            level.playSound(null, dead.blockPosition(),
                SoundEvents.HOGLIN_ANGRY, SoundSource.HOSTILE, 0.8f, 0.9f);
        }
    }
}
