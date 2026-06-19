package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.SwarmData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SwarmHandler {

    private static final Random RNG = new Random();
    private static final float BUG_SCALE = 0.25f;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:swarm_lord/swarm_production")) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        SwarmData data = player.getData(ModAttachments.SWARM_DATA);

        data.bugUUIDs().removeIf(uuid -> {
            var entity = level.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });

        if (player.isInWater()) {
            int count = data.bugUUIDs().size();
            data.bugUUIDs().forEach(uuid -> {
                var entity = level.getEntity(uuid);
                if (entity != null) entity.kill();
            });
            data.bugUUIDs().clear();
            if (count > 0) {
                float dmg = count * cfg.swarmWaterBugDamage;
                player.hurt(player.damageSources().generic(), dmg);
                level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.8f);
            }
            return;
        }

        if (player.tickCount % cfg.swarmSpawnInterval == 0
                && data.bugUUIDs().size() < cfg.swarmMaxBugs) {
            spawnBug(player, level, data);
        }

        tickBugAI(player, level, data);
    }

    private static void spawnBug(ServerPlayer player, ServerLevel level, SwarmData data) {
        BlockPos pos = player.blockPosition().offset(
            RNG.nextInt(3) - 1, 0, RNG.nextInt(3) - 1);

        LivingEntity bug;
        if (RNG.nextFloat() < 0.75f) {
            Silverfish sf = EntityType.SILVERFISH.create(level);
            if (sf == null) return;
            sf.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            sf.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                MobSpawnType.MOB_SUMMONED, null);
            level.addFreshEntity(sf);
            bug = sf;
        } else {
            CaveSpider cs = EntityType.CAVE_SPIDER.create(level);
            if (cs == null) return;
            cs.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            cs.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                MobSpawnType.MOB_SUMMONED, null);
            level.addFreshEntity(cs);
            bug = cs;
        }

        try {
            ScaleTypes.BASE.getScaleData(bug).setScale(BUG_SCALE);
        } catch (Exception ignored) {}

        bug.setCustomName(net.minecraft.network.chat.Component.literal("§aSwarm Bug"));
        bug.setCustomNameVisible(false);
        data.bugUUIDs().add(bug.getUUID());

        level.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW,
            bug.getX(), bug.getY() + 0.3, bug.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.VEX_CHARGE,
            net.minecraft.sounds.SoundSource.NEUTRAL, 0.3f, 2.0f);
    }

    private static void tickBugAI(ServerPlayer player, ServerLevel level, SwarmData data) {
        Vec3 playerPos = player.position();
        List<LivingEntity> nearbyEnemies = level.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(15),
            e -> e != player && e.isAlive()
                && !(e.getCustomName() != null && e.getCustomName().getString().contains("Swarm Bug"))
        );

        data.bugUUIDs().forEach(uuid -> {
            if (!(level.getEntity(uuid) instanceof LivingEntity bug)) return;

            double distToPlayer = bug.distanceTo(player);

            if (!nearbyEnemies.isEmpty() && bug.tickCount % 20 == 0) {
                LivingEntity target = nearbyEnemies.stream()
                    .min((a, b) -> Double.compare(a.distanceTo(bug), b.distanceTo(bug)))
                    .orElse(null);
                if (target != null && bug instanceof Mob mobBug) {
                    mobBug.setTarget(target);
                    target.hurt(bug.damageSources().mobAttack(bug),
                        ChaosAddonConfig.get().swarmBugDamage);
                }
            }

            if (bug.tickCount % 10 == 0) {
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    bug.getBoundingBox().inflate(3),
                    item -> true
                ).forEach(item -> {
                    if (player.getInventory().add(item.getItem())) {
                        item.discard();
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                            item.getX(), item.getY(), item.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                    }
                });
            }

            if (distToPlayer > 20) {
                bug.teleportTo(playerPos.x + RNG.nextDouble() - 0.5,
                               playerPos.y, playerPos.z + RNG.nextDouble() - 0.5);
            }
        });
    }

    @SubscribeEvent
    public static void onBugDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            var name = event.getEntity().getCustomName();
            if (name != null && name.getString().contains("Swarm Bug")) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                    event.getEntity().getX(), event.getEntity().getY() + 0.5,
                    event.getEntity().getZ(), 8, 0.2, 0.2, 0.2, 0.1);
                level.playSound(null, event.getEntity().blockPosition(),
                    net.minecraft.sounds.SoundEvents.SLIME_DEATH,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 2.0f);
            }
        }
    }
}
