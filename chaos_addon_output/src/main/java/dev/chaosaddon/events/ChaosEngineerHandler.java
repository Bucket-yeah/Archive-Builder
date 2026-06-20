package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Vector3f;
import java.util.List;
import java.util.Set;

/**
 * Handles Chaos Engineer passives:
 * - energy_exchange: food = 0 effect; eating redstone → +2 HP + energy; energy bar in actionbar
 * - redstone_vision: glow redstone-related blocks
 * - conductor: lightning strike → +energy + Strength I instead of damage
 */
public class ChaosEngineerHandler {

    public static final String ENERGY_KEY = "chaos_energy";
    private static final int MAX_ENERGY = 100;
    private static final int ENERGY_PER_REDSTONE = 10;

    private static final Set<Block> REDSTONE_BLOCKS = Set.of(
        Blocks.REDSTONE_WIRE, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
        Blocks.REPEATER, Blocks.COMPARATOR, Blocks.OBSERVER,
        Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.PISTON_HEAD,
        Blocks.DISPENSER, Blocks.DROPPER, Blocks.HOPPER,
        Blocks.LEVER, Blocks.DETECTOR_RAIL, Blocks.POWERED_RAIL,
        Blocks.TRIPWIRE_HOOK, Blocks.TARGET, Blocks.DAYLIGHT_DETECTOR,
        Blocks.REDSTONE_LAMP, Blocks.TNT
    );

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/energy_exchange")) return;

        int energy = player.getPersistentData().getInt(ENERGY_KEY);
        energy = Math.min(MAX_ENERGY, Math.max(0, energy));

        // Build energy bar
        int bars = energy / 5; // 20 bars total
        String bar = "§c[" +
            "§e" + "█".repeat(bars) +
            "§8" + "░".repeat(20 - bars) +
            "§c] §f" + energy + "/100 ⚡";

        player.displayClientMessage(Component.literal(bar), true);

        // Particle FX based on energy level
        if (energy > 80 && player.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.4, 0.8, 0.4, 0.05);
        }

        // Passive energy drain: -1 every 10 seconds when using actives (handled in commands)
        // Passive hunger suppression: keep hunger from dropping below 0 HP damage
        if (player.getFoodData().getFoodLevel() <= 0 && player.tickCount % 40 == 0) {
            // Engineer doesn't starve, but at 0 energy they're weaker
            if (energy <= 0) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
            }
        }

        // ── Redstone Vision: highlight nearby redstone blocks every 2s ──
        if (OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/redstone_vision")
                && player.tickCount % 40 == 0) {
            BlockPos center = player.blockPosition();
            DustParticleOptions redDust = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.05f), 1.0f);
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-10, -4, -10), center.offset(10, 4, 10))) {
                if (REDSTONE_BLOCKS.contains(level.getBlockState(p).getBlock())) {
                    level.sendParticles(redDust,
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                        3, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }

        // ── No-Redstone Penalty HUD warning ──
        if (OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/no_redstone_penalty")
                && player.tickCount % 40 == 0) {
            boolean hasRedstone = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(Items.REDSTONE)) { hasRedstone = true; break; }
            }
            if (!hasRedstone) {
                player.displayClientMessage(
                    Component.literal("§c⚡ НЕТ РЕДСТОУНА — урон x2!").withStyle(net.minecraft.ChatFormatting.RED),
                    true);
            }
        }

        // T015: Base Automation
        tickBaseAutomation(player, level);
    }

    /** Cancel food eating — engineer only eats redstone (handled via command) */
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/energy_exchange")) return;

        ItemStack item = event.getItem();
        // Cancel normal food — replace with original item
        if (item.has(net.minecraft.core.component.DataComponents.FOOD) && !item.is(Items.ROTTEN_FLESH)) {
            // Give the food item back (they can't eat it)
            player.getInventory().add(item.copy());
            // Reset hunger to what it was (no saturation gained)
            // This is handled by returning the result unchanged in item use, but we can at least remove the effect
            player.sendSystemMessage(Component.literal("§c⚡ Инженер не ест обычную еду!"));
        }
    }

    /** Eat redstone: +2 HP, +10 energy */
    public static int eatRedstone(ServerPlayer player) {
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/energy_exchange")) return 0;

        // Find redstone in inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.is(Items.REDSTONE)) continue;

            slot.shrink(1);
            player.heal(2.0f);

            int energy = player.getPersistentData().getInt(ENERGY_KEY);
            energy = Math.min(MAX_ENERGY, energy + ENERGY_PER_REDSTONE);
            player.getPersistentData().putInt(ENERGY_KEY, energy);

            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    15, 0.4, 0.8, 0.4, 0.08);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
            return energy;
        }
        return -1; // no redstone found
    }

    /** No Potions: potions are incompatible with redstone circuitry — they explode when drunk. */
    @SubscribeEvent
    public static void onNoPotions(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/no_potions")) return;
        net.minecraft.world.item.ItemStack item = event.getItem();
        if (!item.is(net.minecraft.world.item.Items.POTION)) return;
        event.setCanceled(true); // prevent drinking
        if (player.level() instanceof ServerLevel level) {
            level.explode(null, player.getX(), player.getY() + 1, player.getZ(),
                0.8f, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            player.hurt(player.damageSources().explosion(null, null), 2.0f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.8, 0.5, 0.1);
            player.sendSystemMessage(Component.literal("§c⚡ ВЗРЫВ! §7Зелье несовместимо с электросхемой!"));
        }
    }

    /** No-Redstone Penalty: double incoming damage when no redstone in inventory. */
    @SubscribeEvent
    public static void onNoRedstonePenalty(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/no_redstone_penalty")) return;
        boolean hasRedstone = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.REDSTONE)) { hasRedstone = true; break; }
        }
        if (!hasRedstone) {
            event.setAmount(event.getAmount() * 2.0f);
        }
    }

    /** Conductor: lightning strike → energy + Strength I instead of damage */
    @SubscribeEvent
    public static void onLightningDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/conductor")) return;

        String src = event.getSource().getMsgId();
        if (!src.equals("lightningBolt") && !src.equals("lightning")) return;

        event.setCanceled(true);

        int energy = player.getPersistentData().getInt(ENERGY_KEY);
        energy = Math.min(MAX_ENERGY, energy + 25);
        player.getPersistentData().putInt(ENERGY_KEY, energy);

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true));

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                50, 0.8, 1.5, 0.8, 0.15);
            level.playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.5f);
            player.sendSystemMessage(Component.literal("§e⚡ +25 энергии от молнии! Сила I!"));
        }
    }

    /** T015: Base Automation — when energy ≥ 20, nearby dispensers (8 blocks) auto-fire at enemies.
     *  Fires every 40 ticks. Costs 5 energy per dispenser activation. */
    private static void tickBaseAutomation(ServerPlayer player, ServerLevel level) {
        if (player.tickCount % 40 != 0) return;

        int energy = player.getPersistentData().getInt(ENERGY_KEY);
        if (energy < 20) return;

        BlockPos center = player.blockPosition();
        int dispensersFired = 0;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DispenserBlock)) continue;

            // Check for nearby enemies to target
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(12),
                e -> !(e instanceof net.minecraft.world.entity.player.Player) && e.isAlive());

            if (nearby.isEmpty()) continue;

            // Fire the dispenser using game logic
            level.blockEvent(pos, state.getBlock(), 1, 0);
            dispensersFired++;

            int newEnergy = Math.max(0, player.getPersistentData().getInt(ENERGY_KEY) - 5);
            player.getPersistentData().putInt(ENERGY_KEY, newEnergy);

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                5, 0.3, 0.3, 0.3, 0.05);

            if (newEnergy <= 0) break;
        }

        if (dispensersFired > 0 && player.tickCount % 200 == 0) {
            player.displayClientMessage(
                Component.literal("§e⚡ Автоматика: §f" + dispensersFired + " диспенсеров активировано"),
                true);
        }
    }

    /** Check if engineer has enough energy for an action */
    public static boolean hasEnergy(ServerPlayer player, int cost) {
        return player.getPersistentData().getInt(ENERGY_KEY) >= cost;
    }

    /** Consume energy (returns false if insufficient) */
    public static boolean consumeEnergy(ServerPlayer player, int cost) {
        int energy = player.getPersistentData().getInt(ENERGY_KEY);
        if (energy < cost) return false;
        player.getPersistentData().putInt(ENERGY_KEY, energy - cost);
        return true;
    }
}
