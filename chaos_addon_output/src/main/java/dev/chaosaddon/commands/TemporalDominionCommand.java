package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.util.OriginGuard;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Temporal Dominion — merged replacement for stasis_field + time_loop.
 *
 * Effect: freezes all entities in radius 15 for 10 sec + Confusion.
 * Entities tagged chaos_stasis take double damage (handled by GeneralPowerHandler).
 * Player gets Slowness IV for 5 sec (shorter than old stasis — can still act).
 * Cost: CD 4800 ticks (4 min) handles by the power JSON.
 */
public class TemporalDominionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_temporal_dominion")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                ServerPlayer player = OriginGuard.requirePower(ctx,
                    "chaos_addon:time_wanderer/temporal_dominion");
                if (player == null) return 0;

                ServerLevel level = player.serverLevel();
                int radius = 15;
                int freezeTicks = 200; // 10 sec

                List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    e -> e != player && e.isAlive());

                for (LivingEntity e : entities) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, freezeTicks, 9, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.CONFUSION,         freezeTicks, 0, false, true));
                    e.addTag("chaos_stasis");
                    e.addTag("chaos_time_frozen");
                }

                // Player is slowed but not frozen — can still act
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3, false, true));

                // Particles: outer ring sweep
                for (int i = 0; i < 3; i++) {
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                        player.getX(), player.getY() + 0.8, player.getZ(),
                        25, radius * 0.5, 1.2, radius * 0.5, 0.02);
                    level.sendParticles(ParticleTypes.SCULK_SOUL,
                        player.getX(), player.getY() + 1.2, player.getZ(),
                        20, radius * 0.4, 1.0, radius * 0.4, 0.02);
                    level.sendParticles(ParticleTypes.GLOW,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        30, radius * 0.45, 1.5, radius * 0.45, 0.0);
                }

                level.playSound(null, player.blockPosition(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 0.6f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.9f, 0.5f);

                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§b⏸ Временное Господство — " + entities.size() + " существ заморожено!")
                        .withStyle(net.minecraft.ChatFormatting.AQUA), true);
                return 1;
            }));
    }
}
