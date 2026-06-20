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
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;

import java.util.List;
import java.util.Random;

/**
 * Necrovore commands:
 * - chaos_addon_raise_dead: spend 5 souls (NBT counter), spawn raised undead (up to 5)
 * - chaos_addon_devour_soul: spend 3 souls, AOE explosion + HP regen + Strength II
 * - chaos_addon_dead_legion: spend 10 souls, summon 6 zombie warriors for 30s
 */
public class NecrovoreCommands {

    private static final Random RNG = new Random();
    private static final int MAX_RAISED = 5;
    private static final String RAISE_COST = "5";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_raise_dead")
            .requires(src -> src.hasPermission(0))
            .executes(NecrovoreCommands::raiseDead));

        dispatcher.register(Commands.literal("chaos_addon_devour_soul")
            .requires(src -> src.hasPermission(0))
            .executes(NecrovoreCommands::devourSoul));

        dispatcher.register(Commands.literal("chaos_addon_dead_legion")
            .requires(src -> src.hasPermission(0))
            .executes(NecrovoreCommands::deadLegion));
    }

    // ── Восстание Мёртвых (5 душ) ──────────────────────────────────────────────
    private static int raiseDead(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        int souls = NecrovoreHandler.getSouls(player);
        if (souls < 5) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§4Нужно 5 душ! Текущих: " + souls), false);
            return 0;
        }

        List<Mob> currentRaised = level.getEntitiesOfClass(Mob.class,
            player.getBoundingBox().inflate(60),
            e -> e.getTags().contains("chaos_necro_raised"));
        if (currentRaised.size() >= MAX_RAISED) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§4Максимум 5 поднятых существ!"), false);
            return 0;
        }

        NecrovoreHandler.spendSouls(player, 5);

        Mob raised = spawnRaisedUndead(level, player, 1200);
        if (raised == null) return 0;

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

    // ── Пожрать Душу (3 души) ──────────────────────────────────────────────────
    private static int devourSoul(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        int souls = NecrovoreHandler.getSouls(player);
        if (souls < 3) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§4Нужно 3 души! Текущих: " + souls), false);
            return 0;
        }

        // Find closest raised zombie to sacrifice
        List<Mob> raised = level.getEntitiesOfClass(Mob.class,
            player.getBoundingBox().inflate(20),
            e -> e.isAlive() && e.getTags().contains("chaos_necro_raised"));

        if (!raised.isEmpty()) {
            // Sacrifice closest
            Mob sacrifice = raised.get(0);
            // AOE explosion: 6❤ + Wither II in radius 6
            level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                sacrifice.getBoundingBox().inflate(6),
                e -> e != player && e != sacrifice && e.isAlive())
                .forEach(e -> {
                    e.hurt(player.damageSources().magic(), 12.0f);
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1, false, true));
                });

            level.sendParticles(ParticleTypes.EXPLOSION,
                sacrifice.getX(), sacrifice.getY() + 0.5, sacrifice.getZ(),
                1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                sacrifice.getX(), sacrifice.getY() + 0.5, sacrifice.getZ(),
                30, 1.0, 0.5, 1.0, 0.1);
            level.playSound(null, sacrifice.blockPosition(),
                SoundEvents.ZOMBIE_DEATH, SoundSource.HOSTILE, 1.0f, 0.7f);
            level.playSound(null, sacrifice.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.4f);
            sacrifice.kill();
        }

        NecrovoreHandler.spendSouls(player, 3);

        // Regen +6❤ + Strength II 8s
        player.heal(12.0f);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 1, false, true));

        level.sendParticles(ParticleTypes.SOUL,
            player.getX(), player.getY() + 1.0, player.getZ(),
            20, 0.4, 0.7, 0.4, 0.1);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
            player.getX(), player.getY() + 1.0, player.getZ(),
            15, 0.5, 0.8, 0.5, 0.05);
        level.playSound(null, player.blockPosition(),
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.3f);

        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
            "§5💀 Поглощено 3 души! +6❤ и Сила II"), false);
        return 1;
    }

    // ── Мёртвый Легион (10 душ) ────────────────────────────────────────────────
    private static int deadLegion(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        int souls = NecrovoreHandler.getSouls(player);
        if (souls < 10) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§4Нужно 10 душ! Текущих: " + souls), false);
            return 0;
        }

        NecrovoreHandler.spendSouls(player, 10);

        // Summon 6 zombie warriors (20❤ each, 30 seconds)
        for (int i = 0; i < 6; i++) {
            Mob warrior = spawnRaisedUndead(level, player, 600);
            if (warrior != null) {
                warrior.addTag("chaos_necro_warrior");
                // 20❤ HP
                warrior.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                    .setBaseValue(40.0);
                warrior.setHealth(40.0f);
                warrior.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_BOOST, 600, 1, false, false));
                warrior.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE, 600, 0, false, false));
            }
        }

        level.sendParticles(ParticleTypes.SOUL,
            player.getX(), player.getY() + 1.0, player.getZ(),
            40, 1.0, 0.5, 1.0, 0.1);
        level.playSound(null, player.blockPosition(),
            SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.8f, 0.8f);
        level.playSound(null, player.blockPosition(),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 0.6f);

        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
            "§5💀 Мёртвый Легион вызван! 6 воинов на 30 секунд"), false);
        return 1;
    }

    // ── Helper ─────────────────────────────────────────────────────────────────
    private static Mob spawnRaisedUndead(ServerLevel level, ServerPlayer player, int despawnTicks) {
        double x = player.getX() + (RNG.nextDouble() - 0.5) * 4;
        double z = player.getZ() + (RNG.nextDouble() - 0.5) * 4;
        double y = player.getY();

        Mob mob = (RNG.nextBoolean()
            ? EntityType.ZOMBIE.create(level)
            : EntityType.SKELETON.create(level));
        if (mob == null) return null;

        mob.moveTo(x, y, z, RNG.nextFloat() * 360, 0);
        mob.addTag("chaos_necro_raised");
        mob.getPersistentData().putInt("chaos_despawn_ticks", despawnTicks);
        mob.setGlowingTag(true);
        level.addFreshEntity(mob);
        return mob;
    }
}
