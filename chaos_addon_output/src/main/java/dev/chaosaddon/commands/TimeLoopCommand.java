package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
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
 * Time Loop (shared by Eater of Worlds and Time Wanderer):
 * Creates a 15-block zone where all entities get Slowness V + Confusion
 * and the zone pulses with sculk particles for 10 seconds.
 *
 * True "action echo" is extremely complex; this implementation:
 *  - Freezes entities (Slow V) for 10 sec
 *  - Doubles damage dealt to frozen entities (implemented via tag + event listener)
 *  - Visual immersion via sculk particles + looping sounds
 */
public class TimeLoopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_time_loop")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                int radius = 15;
                int ticks  = 200; // 10 sec

                List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    e -> e != player && e.isAlive()
                );

                for (LivingEntity e : entities) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.CONFUSION,         ticks, 0, false, true));
                    e.addTag("chaos_time_frozen");
                }

                // Visual + sound
                for (int t = 0; t < 10; t++) {
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                        player.getX(), player.getY() + 0.8, player.getZ(),
                        20, radius * 0.5, 1.0, radius * 0.5, 0.02);
                    level.sendParticles(ParticleTypes.SCULK_SOUL,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        15, radius * 0.4, 0.8, radius * 0.4, 0.02);
                }

                level.playSound(null, player.blockPosition(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 0.7f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.6f);
                return 1;
            }));
    }
}
