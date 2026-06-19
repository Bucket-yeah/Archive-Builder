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
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Lunar Renegade – Eclipse: blinds nearby players, gives berserker stats. */
public class EclipseCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_eclipse")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer caster)) return 0;
                ServerLevel level = caster.serverLevel();

                // Caster gets berserker combo
                caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,    600, 3, false, true));
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,  600, 2, false, true));
                caster.addEffect(new MobEffectInstance(MobEffects.GLOWING,         600, 0, false, true));

                // Nearby players get blinded + weakened
                List<? extends Player> nearby = level.players();
                for (Player p : nearby) {
                    if (p == caster) continue;
                    if (p.distanceTo(caster) > 30) continue;
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 600, 0, false, true));
                    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,  600, 1, false, true));
                }

                // Dramatic FX
                level.sendParticles(ParticleTypes.CLOUD,
                    caster.getX(), caster.getY() + 2.0, caster.getZ(),
                    60, 5.0, 2.0, 5.0, 0.05);
                level.sendParticles(ParticleTypes.SONIC_BOOM,
                    caster.getX(), caster.getY() + 1.5, caster.getZ(),
                    3, 1.0, 0.5, 1.0, 0.1);
                level.playSound(null, caster.blockPosition(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 0.5f);
                level.playSound(null, caster.blockPosition(),
                    SoundEvents.PORTAL_TRIGGER,       SoundSource.PLAYERS, 0.8f, 0.6f);
                return 1;
            }));
    }
}
