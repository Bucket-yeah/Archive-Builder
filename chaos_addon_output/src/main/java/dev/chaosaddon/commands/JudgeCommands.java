package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/** chaos_addon_verdict, chaos_addon_annihilate, chaos_addon_equalize */
public class JudgeCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("chaos_addon_verdict")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                Optional<LivingEntity> target = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(15),
                    e -> e != player && e.isAlive())
                    .stream().min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)));

                if (target.isEmpty()) return 0;
                LivingEntity t = target.get();

                float diff = t.getHealth() - player.getHealth();
                if (diff > 0) {
                    t.hurt(player.damageSources().magic(), diff);
                    level.sendParticles(ParticleTypes.FLASH, t.getX(), t.getY() + 1.0, t.getZ(), 1, 0, 0, 0, 0);
                } else {
                    t.heal(-diff);
                    level.sendParticles(ParticleTypes.HEART, t.getX(), t.getY() + 1.0, t.getZ(), 10, 0.5, 0.5, 0.5, 0.0);
                }

                level.playSound(null, player.blockPosition(),
                    SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 1.0f, 1.0f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_annihilate")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                Optional<LivingEntity> target = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(30),
                    e -> e != player && e.isAlive())
                    .stream().min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)));

                if (target.isEmpty()) return 0;
                LivingEntity t = target.get();

                // Item 18 fix: deal % of current HP instead of guaranteed kill.
                // Dangerous vs weakened targets, less decisive vs bosses/tanks.
                float pct = dev.chaosaddon.config.ChaosAddonConfig.get().judgeAnnihilatePercent;
                float dmg = t.getHealth() * pct;
                t.hurt(player.damageSources().magic(), dmg);

                level.sendParticles(ParticleTypes.PORTAL,
                    t.getX(), t.getY() + 1.0, t.getZ(), 40, 0.5, 1.0, 0.5, 0.2);
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    t.getX(), t.getY() + 0.5, t.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 1.0f, 0.5f);

                // Recoil: Judge pays with HP (no longer drops to 0.5)
                player.hurt(player.damageSources().magic(), dmg * 0.25f);

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§4§l⚖ Аннигиляция — " + (int)(pct * 100) + "% HP цели (" + (int)dmg + " урона)!"));
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_equalize")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                List<ServerPlayer> nearby = level.players().stream()
                    .filter(p -> p.distanceTo(player) <= 30)
                    .toList();

                if (nearby.isEmpty()) return 0;

                float totalHp = (float) nearby.stream().mapToDouble(LivingEntity::getHealth).sum();
                float avgHp = totalHp / nearby.size();

                for (ServerPlayer p : nearby) {
                    if (p.getHealth() > avgHp) {
                        p.hurt(p.damageSources().generic(), p.getHealth() - avgHp);
                    } else {
                        p.heal(avgHp - p.getHealth());
                    }
                }

                level.sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 2.0, player.getZ(),
                    80, 15.0, 5.0, 15.0, 0.1);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.8f);
                return 1;
            }));
    }
}
