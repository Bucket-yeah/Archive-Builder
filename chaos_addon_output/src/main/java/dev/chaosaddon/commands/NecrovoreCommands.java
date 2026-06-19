package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.chaosaddon.events.NecrovoreHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * Necrovore commands:
 * - chaos_addon_raise_dead: spend a Soul item, spawn raised undead
 * - chaos_addon_devour_soul: consume up to 3 Souls for HP + Strength
 */
public class NecrovoreCommands {

    private static final Random RNG = new Random();
    private static final int MAX_RAISED = 5;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_raise_dead")
            .requires(src -> src.hasPermission(0))
            .executes(NecrovoreCommands::raiseDead));

        dispatcher.register(Commands.literal("chaos_addon_devour_soul")
            .requires(src -> src.hasPermission(0))
            .executes(NecrovoreCommands::devourSoul));
    }

    private static int raiseDead(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        // Check max raised limit
        List<Mob> currentRaised = level.getEntitiesOfClass(Mob.class,
            player.getBoundingBox().inflate(60),
            e -> e.getTags().contains("chaos_necro_raised"));
        if (currentRaised.size() >= MAX_RAISED) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4Максимум 5 поднятых существ!"), false);
            return 0;
        }

        // Find and consume one soul item from inventory
        boolean consumed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (NecrovoreHandler.isSoulItem(stack)) {
                stack.shrink(1);
                consumed = true;
                break;
            }
        }

        if (!consumed) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4Нет Душ в инвентаре!"), false);
            return 0;
        }

        // Spawn random undead near player
        Mob raised = spawnRaisedUndead(level, player);
        if (raised == null) return 0;

        raised.addTag("chaos_necro_raised");
        raised.getPersistentData().putInt("chaos_despawn_ticks", 1200); // 60 seconds
        raised.setGlowingTag(true);

        // FX
        level.sendParticles(ParticleTypes.SOUL,
            raised.getX(), raised.getY() + 0.5, raised.getZ(),
            20, 0.5, 0.7, 0.5, 0.07);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
            raised.getX(), raised.getY() + 0.5, raised.getZ(),
            10, 0.3, 0.5, 0.3, 0.05);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(),
            SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.5f);
        return 1;
    }

    private static Mob spawnRaisedUndead(ServerLevel level, ServerPlayer player) {
        double x = player.getX() + (RNG.nextDouble() - 0.5) * 4;
        double z = player.getZ() + (RNG.nextDouble() - 0.5) * 4;
        double y = player.getY();

        Mob mob;
        int type = RNG.nextInt(3);
        if (type == 0) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie == null) return null;
            mob = zombie;
        } else if (type == 1) {
            Skeleton skeleton = EntityType.SKELETON.create(level);
            if (skeleton == null) return null;
            mob = skeleton;
        } else {
            Zombie zombie2 = EntityType.ZOMBIE.create(level);
            if (zombie2 == null) return null;
            mob = zombie2;
        }

        mob.moveTo(x, y, z, RNG.nextFloat() * 360, 0);
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 0, false, false));
        level.addFreshEntity(mob);
        return mob;
    }

    private static int devourSoul(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        int consumed = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && consumed < 3; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (NecrovoreHandler.isSoulItem(stack)) {
                stack.shrink(1);
                consumed++;
            }
        }

        if (consumed == 0) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4Нет Душ!"), false);
            return 0;
        }

        // Each soul: +3 HP regen + Strength II 6s
        player.heal(consumed * 3.0f);
        player.addEffect(new MobEffectInstance(
            MobEffects.DAMAGE_BOOST, 120 * consumed, 1, false, true));

        // FX
        level.sendParticles(ParticleTypes.SOUL,
            player.getX(), player.getY() + 1.0, player.getZ(),
            20 * consumed, 0.4, 0.7, 0.4, 0.1);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
            player.getX(), player.getY() + 1.0, player.getZ(),
            15, 0.5, 0.8, 0.5, 0.05);
        level.playSound(null, player.blockPosition(),
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.3f);
        level.playSound(null, player.blockPosition(),
            SoundEvents.SCULK_BLOCK_BREAK, SoundSource.PLAYERS, 0.5f, 0.8f);

        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                "§5Поглощено " + consumed + " душ! +" + (consumed * 3) + " HP"),
            false);
        return 1;
    }
}
