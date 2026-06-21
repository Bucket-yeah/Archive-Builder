package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Infernal Shepherd passives:
 * - fire_diplomacy: nether mobs neutral; gold in hand = follow; zombie piglin death triggers revenge
 * - lava_feeding: hunger restored in lava; Strength II after 30s continuous lava
 * - lava_lord: fire trail when leaving lava; lava walking (convert lava to magma temporarily)
 * - overworld_death_penalty: partial item save (item 14 fix) — offhand item is preserved on death
 */
public class InfernalShepherdHandler {

    private static final Map<UUID, Boolean> WAS_IN_LAVA   = new HashMap<>();
    private static final Map<UUID, Integer> LAVA_TICKS    = new HashMap<>();
    private static final Map<UUID, Long>    STRENGTH_GIVEN = new HashMap<>();

    private static final String SAVED_ITEM_KEY = "chaos_infernal_saved_item";

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/lava_feeding")) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        long now = level.getGameTime();
        boolean inLava = player.isInLava();

        // Lava Feeding: +1 hunger every 2s in lava
        if (inLava && now % cfg.inferLavaFeedInterval == 0) {
            player.getFoodData().eat(cfg.inferLavaFeedAmount, cfg.inferLavaFeedSaturation);
        }
        // Water drains hunger
        if (player.isInWater() && now % 40 == 0) {
            player.getFoodData().eat(-2, 0);
        }

        // Track continuous lava time for Strength II buff
        if (inLava) {
            int ticks = LAVA_TICKS.getOrDefault(player.getUUID(), 0) + 1;
            LAVA_TICKS.put(player.getUUID(), ticks);
            if (ticks >= cfg.inferLavaStrengthTicks) {
                Long lastStrength = STRENGTH_GIVEN.getOrDefault(player.getUUID(), 0L);
                if (now - lastStrength >= cfg.inferLavaBuffInterval) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, cfg.inferLavaStrengthDuration, 1, false, true));
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
                BlockPos pos = player.blockPosition();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos firePos = pos.offset(dx, 0, dz);
                        if (level.getBlockState(firePos).isAir()) {
                            level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                }
                player.getPersistentData().putInt("infernal_trail_ticks", cfg.inferTrailDuration);
            }
            int trailTicks = player.getPersistentData().getInt("infernal_trail_ticks");
            if (trailTicks > 0) {
                player.getPersistentData().putInt("infernal_trail_ticks", trailTicks - 1);
                if (trailTicks % cfg.inferTrailDamageInterval == 0) {
                    List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(cfg.inferTrailRadius), e -> e != player && e.isAlive());
                    nearby.forEach(e -> {
                        e.hurt(level.damageSources().inFire(), cfg.inferTrailDamage);
                        e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), cfg.inferTrailFireTicks));
                    });
                }
            }
        }
        WAS_IN_LAVA.put(player.getUUID(), inLava);

        // Lava Lord: walk on lava
        if (OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/lava_lord") && !inLava) {
            BlockPos below = player.blockPosition().below();
            if (level.getBlockState(below).getFluidState().getType()
                    == net.minecraft.world.level.material.Fluids.LAVA.getSource()) {
                level.setBlock(below, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                level.scheduleTick(below, Blocks.MAGMA_BLOCK, 80);
            }
        }

        // Fire Diplomacy: nether mobs neutral + follow with gold in hand
        if (OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/fire_diplomacy") && now % cfg.inferDiplomacyInterval == 0) {
            AABB range = player.getBoundingBox().inflate(cfg.inferDiplomacyRadius);
            boolean hasGold = player.getMainHandItem().is(Items.GOLD_INGOT)
                || player.getOffhandItem().is(Items.GOLD_INGOT)
                || player.getMainHandItem().is(Items.GOLDEN_SWORD)
                || player.getMainHandItem().is(Items.GOLDEN_AXE);

            level.getEntitiesOfClass(Mob.class, range, e -> e.isAlive()).forEach(mob -> {
                if (mob instanceof ZombifiedPiglin || mob instanceof Ghast || mob instanceof Blaze) {
                    if (!mob.isPersistenceRequired()) {
                        mob.setTarget(null);
                        if (hasGold && mob.distanceTo(player) > cfg.inferDiplomacyFollowDist) {
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

    /**
     * Overworld Death Penalty — Partial Save (item 14 fix):
     * When the Shepherd dies in the Overworld, save their offhand item to persistent NBT.
     * It is returned on respawn, giving a "sacred item" insurance mechanic.
     */
    @SubscribeEvent
    public static void onShepherdDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/overworld_death_penalty")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Only trigger in Overworld
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        // Save offhand item (or main-hand if offhand is empty) to NBT before inventory drops
        ItemStack toSave = player.getOffhandItem().isEmpty()
            ? player.getMainHandItem()
            : player.getOffhandItem();

        if (!toSave.isEmpty()) {
            // MC 1.21.1: save() returns Tag, not void
            net.minecraft.nbt.Tag serialized = toSave.save(player.registryAccess());
            if (serialized instanceof CompoundTag ct) {
                player.getPersistentData().put(SAVED_ITEM_KEY, ct);
                player.sendSystemMessage(Component.literal(
                    "§6🔥 «" + toSave.getHoverName().getString()
                    + "» сохранён огнём — будет возвращён при возрождении."));
            }
        }
    }

    /** Return the saved item on respawn. */
    @SubscribeEvent
    public static void onShepherdRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/overworld_death_penalty")) return;

        CompoundTag nbt = player.getPersistentData();
        if (!nbt.contains(SAVED_ITEM_KEY)) return;

        CompoundTag itemTag = nbt.getCompound(SAVED_ITEM_KEY);
        nbt.remove(SAVED_ITEM_KEY);

        ItemStack saved = ItemStack.parseOptional(player.registryAccess(), itemTag);
        if (!saved.isEmpty()) {
            player.getInventory().add(saved);
            player.sendSystemMessage(Component.literal(
                "§6🔥 Сохранённый предмет возвращён: «"
                + saved.getHoverName().getString() + "»."));
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

        LivingEntity killer = dead.getKillCredit() instanceof LivingEntity le ? le : null;
        if (killer == null || shepherds.contains(killer)) return;

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
