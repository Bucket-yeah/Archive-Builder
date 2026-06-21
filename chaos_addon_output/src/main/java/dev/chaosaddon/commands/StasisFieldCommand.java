package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.config.ChaosAddonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Stasis Field (Time Wanderer ultimate):
 * Creates a radius zone where all living entities are slowed 80% (Slowness X)
 * and receive a small magic hit for the stasis duration.
 * The caster also gets brief Slowness III as a drawback.
 */
public class StasisFieldCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_stasis")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                ChaosAddonConfig cfg = ChaosAddonConfig.get();

                int radius   = cfg.stasisFieldRadius;
                int duration = cfg.stasisFieldDuration;

                List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    e -> e != player && e.isAlive()
                );

                for (LivingEntity e : entities) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 9, false, true));
                    e.hurt(e.damageSources().magic(), cfg.stasisFieldDamage);
                    e.addTag("chaos_stasis");
                }

                // Caster drawback: brief Slowness III
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration / 2, 2, false, true));

                level.sendParticles(ParticleTypes.GLOW,
                    player.getX(), player.getY() + 0.8, player.getZ(),
                    60, radius * 0.5, 1.0, radius * 0.5, 0.0);
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20, radius * 0.3, 0.5, radius * 0.3, 0.02);

                level.playSound(null, player.blockPosition(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 0.6f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.7f);

                player.displayClientMessage(
                    Component.literal("§9❄ Стазис-поле! §7" + entities.size() + " целей заморожено на "
                        + (duration / 20) + "с")
                        .withStyle(ChatFormatting.BLUE), true);

                return 1;
            }));
    }
}
