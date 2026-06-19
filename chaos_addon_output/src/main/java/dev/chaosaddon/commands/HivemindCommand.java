package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.data.ParasiteData;
import dev.chaosaddon.init.ModAttachments;
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

/** Parasitic Mind – Collective Mind & Parasite Explosion combined command entry. */
public class HivemindCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_hivemind")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                ParasiteData data = player.getData(ModAttachments.PARASITE_DATA);

                data.infectedUUIDs().forEach(uuid -> {
                    if (!(level.getEntity(uuid) instanceof LivingEntity mob)) return;
                    mob.teleportTo(player.getX(), player.getY(), player.getZ());
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1, false, true));
                });

                level.sendParticles(ParticleTypes.EXPLOSION,
                    player.getX(), player.getY() + 1.0, player.getZ(), 10, 1.0, 1.0, 1.0, 0.1);
                level.sendParticles(ParticleTypes.WITCH,
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.0, 1.5, 1.0, 0.1);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_SPAWN,    SoundSource.HOSTILE, 1.0f, 0.8f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.5f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_parasite_explode")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                ParasiteData data = player.getData(ModAttachments.PARASITE_DATA);

                data.infectedUUIDs().forEach(uuid -> {
                    if (!(level.getEntity(uuid) instanceof LivingEntity mob)) return;
                    level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        mob.getBoundingBox().inflate(3),
                        e -> e != mob && e.isAlive())
                        .forEach(e -> e.hurt(mob.damageSources().magic(), 6.0f));
                    level.sendParticles(ParticleTypes.FLAME,
                        mob.getX(), mob.getY() + 0.5, mob.getZ(), 25, 0.5, 0.5, 0.5, 0.15);
                    level.sendParticles(ParticleTypes.SMOKE,
                        mob.getX(), mob.getY() + 0.5, mob.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
                    mob.kill();
                });
                data.infectedUUIDs().clear();

                level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.BLAZE_SHOOT,     SoundSource.HOSTILE, 0.8f, 0.8f);
                return 1;
            }));
    }
}
