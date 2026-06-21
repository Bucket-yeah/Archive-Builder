package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Wandering Gardener passives:
 * - green_blood:           regen on grass/leaves, faster on flowers, none on stone
 * - peaceful_soul:         cancel outgoing damage; tamed wolves protect; bonus egg/milk timers
 * - language_of_flowers:   auto bone-meal nearby plants + flower traps triggered on nearby enemies
 * - no_weapons:            weapons in mainhand convert to flowers
 * - woodcutting_damage:    undead deal 50% more damage (drawback)
 */
public class WanderingGardenerHandler {

    private static final Map<UUID, Integer> REGEN_TICK = new HashMap<>();
    private static final Map<UUID, Long> EGG_TIMER = new HashMap<>();
    private static final Map<UUID, Long> MILK_TIMER = new HashMap<>();
    private static final Map<UUID, Long> PLANT_TICK = new HashMap<>();

    // Plant Trap system
    private static final String TRAP_KEY = "chaos_garden_traps";
    private static final int MAX_TRAPS = 3;
    private static final long TRAP_COOLDOWN = 600L; // 30s between placing traps
    private static final Map<UUID, Long> LAST_TRAP_TIME = new HashMap<>();

    // Weapon conversion flowers
    private static final net.minecraft.world.item.Item[] FLOWERS = {
        Items.DANDELION, Items.POPPY, Items.AZURE_BLUET, Items.RED_TULIP,
        Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP, Items.OXEYE_DAISY,
        Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY
    };

    private static final java.util.Random RNG = new java.util.Random();

    /** Called by command/power to place a plant trap at current position */
    public static boolean placePlantTrap(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        UUID pid = player.getUUID();
        long now = level.getGameTime();
        if (now - LAST_TRAP_TIME.getOrDefault(pid, 0L) < TRAP_COOLDOWN) {
            long left = (TRAP_COOLDOWN - (now - LAST_TRAP_TIME.getOrDefault(pid, 0L))) / 20;
            player.sendSystemMessage(Component.literal("§cЛовушка перезаряжается: §e" + left + "с"));
            return false;
        }
        List<long[]> traps = loadTraps(player);
        if (traps.size() >= MAX_TRAPS) {
            player.sendSystemMessage(Component.literal("§cМаксимум §e" + MAX_TRAPS + " §cловушки активны!"));
            return false;
        }
        BlockPos pos = player.blockPosition();
        traps.add(new long[]{pos.getX(), pos.getY(), pos.getZ(), now + 6000}); // 5 min expire
        saveTraps(player, traps);
        LAST_TRAP_TIME.put(pid, now);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.05);
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.PLAYERS, 0.8f, 1.5f);
        player.sendSystemMessage(Component.literal("§a🌸 Растительная ловушка установлена! §8(" + traps.size() + "/" + MAX_TRAPS + ")"));
        return true;
    }

    private static List<long[]> loadTraps(ServerPlayer player) {
        List<long[]> traps = new ArrayList<>();
        String raw = player.getPersistentData().getString(TRAP_KEY);
        if (raw.isBlank()) return traps;
        for (String entry : raw.split(";")) {
            String[] parts = entry.split(",");
            if (parts.length == 4) {
                try {
                    traps.add(new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                        Long.parseLong(parts[2]), Long.parseLong(parts[3])});
                } catch (NumberFormatException ignored) {}
            }
        }
        return traps;
    }

    private static void saveTraps(ServerPlayer player, List<long[]> traps) {
        StringBuilder sb = new StringBuilder();
        for (long[] t : traps) sb.append(t[0]).append(",").append(t[1]).append(",").append(t[2])
            .append(",").append(t[3]).append(";");
        player.getPersistentData().putString(TRAP_KEY, sb.toString());
    }

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

            int period = onFlower ? 40 : (onGrass || onLeaves) ? 80 : 0;

            if (onStoneOrDesert && now % 40 == 0) {
                player.getFoodData().eat(-1, 0);
            }

            if (period > 0 && now % period == 0 && player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0f);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    6, 0.4, 0.5, 0.4, 0.02);
            }

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
                    if (wolf.getOwnerUUID() != null && wolf.getOwnerUUID().equals(player.getUUID())) {
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

        // ── Plant Traps: check for enemies near active traps ──
        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/language_of_flowers") && now % 10 == 0) {
            List<long[]> traps = loadTraps(player);
            boolean changed = traps.removeIf(t -> t[3] < now);
            List<long[]> triggeredTraps = new ArrayList<>();
            for (long[] trap : traps) {
                BlockPos tPos = new BlockPos((int)trap[0], (int)trap[1], (int)trap[2]);
                List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(tPos).inflate(2.0),
                    e -> e != player && e.isAlive() && !(e instanceof Player));
                if (!victims.isEmpty()) {
                    triggeredTraps.add(trap);
                    for (LivingEntity v : victims) {
                        v.hurt(player.damageSources().magic(), 4.0f);
                        v.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
                        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                    }
                    level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                        tPos.getX() + 0.5, tPos.getY() + 1.0, tPos.getZ() + 0.5, 30, 1.0, 0.5, 1.0, 0.05);
                    level.playSound(null, tPos, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 1.0f, 0.5f);
                    player.displayClientMessage(Component.literal(
                        "§a🌸 Ловушка сработала! §7(-4❤ + Яд)"), true);
                }
            }
            if (!triggeredTraps.isEmpty() || changed) {
                traps.removeAll(triggeredTraps);
                saveTraps(player, traps);
            }
        }

        // ── Language of Flowers: bone-meal nearby plants periodically ──
        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/language_of_flowers") && now % 100 == 0) {
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

        // ── growth_aura: allies and tamed creatures near gardener slowly regen when not in combat ──
        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/growth_aura") && now % 60 == 0) {
            boolean inCombat = player.getPersistentData().getLong("chaos_garden_last_hit") > 0
                && (now - player.getPersistentData().getLong("chaos_garden_last_hit")) < 100;
            if (!inCombat) {
                level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(12),
                    e -> e != player && e.isAlive()
                        && ((e instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isTame())
                            || (e instanceof ServerPlayer)))
                    .forEach(ally -> {
                        if (ally.getHealth() < ally.getMaxHealth()) {
                            ally.heal(0.5f);
                            if (ally.level() instanceof ServerLevel sl) {
                                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                    ally.getX(), ally.getY() + 1.0, ally.getZ(),
                                    3, 0.3, 0.3, 0.3, 0.02);
                            }
                        }
                    });
            }
        }

        // ── no_weapons: convert held weapons to flowers ──
        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/no_weapons")) {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                var item = held.getItem();
                boolean isWeapon = item instanceof SwordItem || item instanceof AxeItem
                    || item instanceof BowItem || item instanceof CrossbowItem;
                if (isWeapon) {
                    long lastConvert = player.getPersistentData().getLong("chaos_garden_weapon_cd");
                    if (now - lastConvert >= 60) {
                        player.getPersistentData().putLong("chaos_garden_weapon_cd", now);
                        // Drop the weapon and give a flower
                        level.addFreshEntity(new ItemEntity(level,
                            player.getX(), player.getY() + 0.5, player.getZ(), held.copy()));
                        player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
                        player.getInventory().add(new ItemStack(FLOWERS[RNG.nextInt(FLOWERS.length)]));
                        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                            player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.5, 0.4, 0.05);
                        level.playSound(null, player.blockPosition(),
                            SoundEvents.GRASS_PLACE, SoundSource.PLAYERS, 0.8f, 1.5f);
                        player.sendSystemMessage(Component.literal(
                            "§a🌸 Оружие превратилось в цветок! Садовник не может сражаться."));
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
        event.setAmount(0);
    }

    // ── Thorn Reflect + Undead Drawback + Combat Tracker: when Gardener is hit ──
    @SubscribeEvent
    public static void onGardenerHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/peaceful_soul")) return;
        // Track last-hit time for growth_aura "not in combat" check
        if (player.level() instanceof ServerLevel sl) {
            player.getPersistentData().putLong("chaos_garden_last_hit", sl.getGameTime());
        }

        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity le ? le : null;
        if (attacker == null || attacker == player) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // woodcutting_damage drawback: undead deal 50% more damage to Gardener
        if (attacker instanceof Mob mob && mob.getType().is(EntityTypeTags.UNDEAD)
                && OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/woodcutting_damage")) {
            event.setAmount(event.getAmount() * 1.5f);
            if (player.tickCount % 40 == 0) {
                player.displayClientMessage(
                    Component.literal("§c☠ Нежить особо опасна для садовника! (+50% урона)"), true);
            }
        }

        // Thorn Reflect: reflect 50% back to attacker
        float reflect = event.getAmount() * 0.5f;
        attacker.hurt(player.damageSources().magic(), reflect);

        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            player.getX(), player.getY() + 1.0, player.getZ(),
            10, 0.4, 0.5, 0.4, 0.03);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
            5, 0.3, 0.4, 0.3, 0.0);
    }
}
