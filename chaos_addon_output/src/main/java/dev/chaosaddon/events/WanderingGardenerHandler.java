package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Wandering Gardener passives:
 * - green_blood: regen on grass/leaves, faster on flowers, none on stone
 * - peaceful_soul: cancel outgoing damage; tamed wolves protect; bonus egg/milk timers
 * - flower_tongue: actionbar plant info; plants "grow toward" player (bone-meal nearby plants)
 */
public class WanderingGardenerHandler {

    private static final Map<UUID, Integer> REGEN_TICK = new HashMap<>();
    private static final Map<UUID, Long> EGG_TIMER = new HashMap<>();
    private static final Map<UUID, Long> MILK_TIMER = new HashMap<>();
    private static final Map<UUID, Long> PLANT_TICK = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        long now = level.getGameTime();

        // ── Green Blood: regen based on block below ──
        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/green_blood")) {
            BlockPos below = player.blockPosition().below();
            BlockState bs = level.getBlockState(below);
            boolean onGrass = bs.is(Blocks.GRASS_BLOCK) || bs.is(Blocks.MOSS_BLOCK)
                || bs.is(Blocks.PODZOL) || bs.is(Blocks.MYCELIUM);
            boolean onLeaves = bs.is(net.minecraft.tags.BlockTags.LEAVES);
            boolean onFlower = bs.is(net.minecraft.tags.BlockTags.FLOWERS)
                || bs.is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)
                || bs.is(net.minecraft.tags.BlockTags.TALL_FLOWERS);
            boolean onStoneOrDesert = bs.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || bs.is(Blocks.SAND) || bs.is(Blocks.SANDSTONE)
                || bs.is(Blocks.RED_SANDSTONE) || bs.is(Blocks.GRAVEL);

            int period = onFlower ? 40 : (onGrass || onLeaves) ? 80 : 0; // ticks between regen

            if (onStoneOrDesert && now % 40 == 0) {
                // Double hunger drain
                player.getFoodData().eat(-1, 0);
            }

            if (period > 0 && now % period == 0 && player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0f);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    6, 0.4, 0.5, 0.4, 0.02);
            }

            // Actionbar: plant info
            String block = bs.getBlock().getDescriptionId();
            String surface = onFlower ? "§a🌸 Цветущая земля" : onGrass ? "§2🌿 Трава" :
                onLeaves ? "§2🍃 Листва" : onStoneOrDesert ? "§7🪨 Камень/пустыня" : "§8?";
            player.displayClientMessage(
                Component.literal(surface + (period > 0 ? " §a[Регенерация]" : " §c[Голод x2]")), true);
        }

        // ── Peaceful Soul: tamed wolves attack enemies ──
        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/peaceful_soul") && now % 40 == 0) {
            List<Wolf> wolves = level.getEntitiesOfClass(Wolf.class,
                player.getBoundingBox().inflate(20), w -> w.isAlive() && w.isTame());
            List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(15),
                e -> e.isAlive() && e != player && !(e instanceof Player) && !(e instanceof Wolf));

            if (!enemies.isEmpty()) {
                LivingEntity target = enemies.get(0);
                wolves.forEach(wolf -> {
                    if (wolf.getOwnerUUID() != null
                        && wolf.getOwnerUUID().equals(player.getUUID())) {
                        wolf.setTarget(target);
                    }
                });
            }

            // Chicken egg timer: every 30s give an egg from nearby chickens
            Long lastEgg = EGG_TIMER.getOrDefault(player.getUUID(), 0L);
            if (now - lastEgg >= 600) {
                List<Chicken> chickens = level.getEntitiesOfClass(Chicken.class,
                    player.getBoundingBox().inflate(20), c -> c.isAlive());
                if (!chickens.isEmpty()) {
                    player.getInventory().add(new ItemStack(Items.EGG, chickens.size()));
                    EGG_TIMER.put(player.getUUID(), now);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        player.getX(), player.getY() + 1.5, player.getZ(),
                        5, 0.5, 0.5, 0.5, 0.05);
                }
            }

            // Cow milk timer: every 60s give milk
            Long lastMilk = MILK_TIMER.getOrDefault(player.getUUID(), 0L);
            if (now - lastMilk >= 1200) {
                List<Cow> cows = level.getEntitiesOfClass(Cow.class,
                    player.getBoundingBox().inflate(20), c -> c.isAlive());
                if (!cows.isEmpty()) {
                    player.getInventory().add(new ItemStack(Items.MILK_BUCKET));
                    MILK_TIMER.put(player.getUUID(), now);
                }
            }
        }

        // ── Flower Tongue: bone-meal nearby plants periodically ──
        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/flower_tongue") && now % 100 == 0) {
            BlockPos origin = player.blockPosition();
            int radius = 5;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        BlockState bs = level.getBlockState(pos);
                        if (bs.is(net.minecraft.tags.BlockTags.CROPS)
                            || bs.is(net.minecraft.tags.BlockTags.SAPLINGS)) {
                        if (bs.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock growable
                                && growable.isValidBonemealTarget(level, pos, bs)) {
                            growable.performBonemeal(level, level.random, pos, bs);
                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                3, 0.3, 0.3, 0.3, 0.05);
                        }
                    }
                    }
                }
            }
        }
    }

    // ── Peaceful Soul: cancel all outgoing attack damage ──
    @SubscribeEvent
    public static void onOutgoingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/peaceful_soul")) return;

        // Cancel weapon damage completely
        event.setAmount(0);
    }
}
