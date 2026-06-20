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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;

import java.util.List;

/**
 * Temporal Echo — new ability freed by merging stasis_field + time_loop.
 *
 * Spawns a bat "echo" at the player's current position.
 * Player gains Invisibility + Speed II for 5 sec (escape window).
 * Nearby enemies see the echo glowing — drawn to the decoy.
 * Echo despawns after 5 sec.
 */
public class TemporalEchoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_temporal_echo")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                ServerPlayer player = OriginGuard.requirePower(ctx,
                    "chaos_addon:time_wanderer/temporal_echo");
                if (player == null) return 0;

                ServerLevel level = player.serverLevel();

                // Spawn echo bat at player's current position
                Bat bat = EntityType.BAT.create(level);
                if (bat != null) {
                    bat.moveTo(player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot());
                    bat.addTag("chaos_temporal_echo");
                    bat.addTag("chaos_despawn_timer");
                    bat.getPersistentData().putInt("chaos_despawn_ticks", 100); // 5 sec
                    bat.getPersistentData().putString("chaos_echo_owner", player.getUUID().toString());
                    bat.setInvisible(false);
                    bat.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
                    bat.setNoAi(false); // let bat move naturally so it looks alive
                    level.addFreshEntity(bat);

                    // Make enemies near the echo glow (they can see it)
                    List<LivingEntity> nearbyEnemies = level.getEntitiesOfClass(
                        LivingEntity.class,
                        bat.getBoundingBox().inflate(15),
                        e -> e != player && e.isAlive() && !(e instanceof ServerPlayer));
                    for (LivingEntity enemy : nearbyEnemies) {
                        // Give each enemy a brief "distraction" — redirect their attention
                        enemy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, true));
                    }
                }

                // Player: Invisibility + Speed to escape
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,  100, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false));

                // Visual burst at spawn point
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.5, 0.8, 0.5, 0.05);
                level.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    30, 0.4, 0.6, 0.4, 0.12);

                level.playSound(null, player.blockPosition(),
                    SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.6f);

                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§5👻 Отголосок — прошлое прикрывает тебя на 5 сек!")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
                return 1;
            }));
    }
}
