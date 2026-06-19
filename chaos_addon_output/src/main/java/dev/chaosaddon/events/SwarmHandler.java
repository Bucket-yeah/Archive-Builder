package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.SwarmData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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

        // Remove dead/gone bugs
        data.bugUUIDs().removeIf(uuid -> {
            var entity = level.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });

        int bugCount = data.bugUUIDs().size();

        // Water kills all bugs
        if (player.isInWater()) {
            int count = bugCount;
            data.bugUUIDs().forEach(uuid -> {
                var entity = level.getEntity(uuid);
                if (entity != null) entity.kill();
            });
            data.bugUUIDs().clear();
            if (count > 0) {
                float dmg = count * cfg.swarmWaterBugDamage;
                player.hurt(player.damageSources().generic(), dmg);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 0.5f, 1.8f);
            }
            return;
        }

        // Spawn bugs
        if (player.tickCount % cfg.swarmSpawnInterval == 0 && bugCount < cfg.swarmMaxBugs) {
            spawnBug(player, level, data);
            bugCount++;
        }

        // Weakness when fewer than 4 bugs
        if (player.tickCount % 40 == 0) {
            if (bugCount < 4) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0, true, false));
            }
        }

        tickBugAI(player, level, data);

        // Swarm Sense: highlight low-HP entities, show HP in name
        if (OriginHelper.hasPower(player, "chaos_addon:swarm_lord/swarm_sense")
                && player.tickCount % 20 == 0) {
            level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(15),
                e -> e != player && e.isAlive() && e.getHealth() < e.getMaxHealth() * 0.40f)
                .forEach(e -> {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false));
                    // Show HP in custom name
                    int hp = (int) e.getHealth();
                    int maxHp = (int) e.getMaxHealth();
                    e.setCustomName(Component.literal("§c❤ " + hp + "/" + maxHp));
                    e.setCustomNameVisible(true);
                });
        }

        // Royal Pheromone: arthropods follow as pets + spore_blossom_air particles
        if (OriginHelper.hasPower(player, "chaos_addon:swarm_lord/royal_pheromone")
                && player.tickCount % 40 == 0) {
            level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20),
                e -> e.isAlive() && e.getType().is(EntityTypeTags.ARTHROPOD))
                .forEach(mob -> {
                    if (mob.getTarget() == player) mob.setTarget(null);
                    // Make arthropod follow player
                    if (mob.distanceTo(player) > 5) {
                        Vec3 dir = player.position().subtract(mob.position()).normalize().scale(0.3);
                        mob.setDeltaMovement(dir.x, mob.getDeltaMovement().y, dir.z);
                    }
                    // Yellow spore_blossom_air particles
                    if (player.tickCount % 20 == 0) {
                        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                            mob.getX(), mob.getY() + 1.2, mob.getZ(),
                            3, 0.3, 0.4, 0.3, 0.02);
                    }
                });
        }

        // Actionbar: bug count
        if (player.tickCount % 20 == 0) {
            String bar = buildBugBar(bugCount, cfg.swarmMaxBugs);
            player.displayClientMessage(
                Component.literal("🐛 Рой: " + bar + " " + bugCount + "/" + cfg.swarmMaxBugs)
                    .withStyle(bugCount < 4 ? ChatFormatting.RED : ChatFormatting.GREEN),
                true);
        }
    }

    private static String buildBugBar(int current, int max) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < max; i++) sb.append(i < current ? "■" : "□");
        sb.append("]");
        return sb.toString();
    }

    private static void spawnBug(ServerPlayer player, ServerLevel level, SwarmData data) {
        BlockPos pos = player.blockPosition().offset(
            RNG.nextInt(3) - 1, 0, RNG.nextInt(3) - 1);

        LivingEntity bug;
        if (RNG.nextFloat() < 0.75f) {
            Silverfish sf = EntityType.SILVERFISH.create(level);
            if (sf == null) return;
            sf.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            sf.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
            level.addFreshEntity(sf);
            bug = sf;
        } else {
            CaveSpider cs = EntityType.CAVE_SPIDER.create(level);
            if (cs == null) return;
            cs.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            cs.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
            level.addFreshEntity(cs);
            bug = cs;
        }

        try { ScaleTypes.BASE.getScaleData(bug).setScale(BUG_SCALE); } catch (Exception ignored) {}

        bug.setCustomName(Component.literal("§aSwarm Bug"));
        bug.setCustomNameVisible(false);
        data.bugUUIDs().add(bug.getUUID());

        level.sendParticles(ParticleTypes.GLOW,
            bug.getX(), bug.getY() + 0.3, bug.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
        level.playSound(null, pos, SoundEvents.VEX_CHARGE,
            SoundSource.NEUTRAL, 0.3f, 2.0f);
    }

    private static void tickBugAI(ServerPlayer player, ServerLevel level, SwarmData data) {
        Vec3 playerPos = player.position();
        List<LivingEntity> nearbyEnemies = level.getEntitiesOfClass(
            LivingEntity.class, player.getBoundingBox().inflate(15),
            e -> e != player && e.isAlive()
                && !(e.getCustomName() != null && e.getCustomName().getString().contains("Swarm Bug")));

        data.bugUUIDs().forEach(uuid -> {
            if (!(level.getEntity(uuid) instanceof LivingEntity bug)) return;

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

            // Bugs collect items for player
            if (bug.tickCount % 10 == 0) {
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    bug.getBoundingBox().inflate(3), item -> true)
                    .forEach(item -> {
                        if (player.getInventory().add(item.getItem())) {
                            item.discard();
                            level.sendParticles(ParticleTypes.ENCHANT,
                                item.getX(), item.getY(), item.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                        }
                    });
            }

            if (bug.distanceTo(player) > 20) {
                bug.teleportTo(playerPos.x + RNG.nextDouble() - 0.5,
                               playerPos.y, playerPos.z + RNG.nextDouble() - 0.5);
            }
        });
    }

    @SubscribeEvent
    public static void onBugDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        var name = event.getEntity().getCustomName();
        if (name != null && name.getString().contains("Swarm Bug")) {
            level.sendParticles(ParticleTypes.ITEM_SLIME,
                event.getEntity().getX(), event.getEntity().getY() + 0.5,
                event.getEntity().getZ(), 8, 0.2, 0.2, 0.2, 0.1);
            level.sendParticles(ParticleTypes.CRIT,
                event.getEntity().getX(), event.getEntity().getY() + 0.5,
                event.getEntity().getZ(), 5, 0.3, 0.3, 0.3, 0.1);
            level.playSound(null, event.getEntity().blockPosition(),
                SoundEvents.SLIME_DEATH, SoundSource.NEUTRAL, 0.5f, 2.0f);
        }
    }

    // Swarm Shield: bugs absorb incoming damage (hive_fortress / swarm_shield)
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSwarmShieldDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:swarm_lord/swarm_shield")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        SwarmData data = player.getData(ModAttachments.SWARM_DATA);
        if (data.bugUUIDs().isEmpty()) return;

        // Each bug absorbs up to 0.5 damage — sacrifice a bug for > 4 damage
        float damage = event.getAmount();
        int bugs = data.bugUUIDs().size();
        float absorbPer = 0.5f;
        float totalAbsorb = Math.min(damage * 0.5f, bugs * absorbPer);
        event.setAmount(damage - totalAbsorb);

        // If damage was heavy, sacrifice one bug
        if (damage >= 4.0f && bugs > 0) {
            var it = data.bugUUIDs().iterator();
            if (it.hasNext()) {
                var bugUuid = it.next();
                var bug = level.getEntity(bugUuid);
                if (bug != null) bug.kill();
                it.remove();
            }
            player.displayClientMessage(
                Component.literal("§cЖук пожертвовал собой!").withStyle(ChatFormatting.RED), false);
        }
    }

    // Arthropod kill → +1 bug (Royal Pheromone bonus)
    @SubscribeEvent
    public static void onArthropodKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:swarm_lord/royal_pheromone")) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!event.getEntity().getType().is(EntityTypeTags.ARTHROPOD)) return;

        SwarmData data = player.getData(ModAttachments.SWARM_DATA);
        if (data.bugUUIDs().size() < ChaosAddonConfig.get().swarmMaxBugs) {
            spawnBug(player, level, data);
            player.displayClientMessage(
                Component.literal("§a+1 жук из убитого членистоногого!"), false);
        }
    }
}
