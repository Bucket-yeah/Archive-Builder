package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.chaosaddon.events.NeuralHijackerHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Neural Hijacker commands:
 * - chaos_addon_neural_seizure: infect nearest low-HP entity
 * - chaos_addon_hiveburst: all infected rush nearest enemy and explode
 */
public class NeuralHijackerCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_neural_seizure")
            .requires(src -> src.hasPermission(0))
            .executes(NeuralHijackerCommands::neuralSeizure));

        dispatcher.register(Commands.literal("chaos_addon_hiveburst")
            .requires(src -> src.hasPermission(0))
            .executes(NeuralHijackerCommands::hiveburst));
    }

    private static int neuralSeizure(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        if (!NeuralHijackerHandler.canInfect(player)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4Максимум 3 хоста!"), false);
            return 0;
        }

        // Find nearest entity with HP < 60%
        Optional<LivingEntity> target = level.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(12),
            e -> e != player && e.isAlive()
                && e.getHealth() / e.getMaxHealth() < 0.60f
                && !e.getTags().contains("chaos_hijacked"))
            .stream()
            .min(Comparator.comparingDouble(e -> e.distanceTo(player)));

        if (target.isEmpty()) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§6Нет подходящих целей рядом (HP < 60%)"),
                false);
            return 0;
        }

        LivingEntity t = target.get();
        if (NeuralHijackerHandler.infectTarget(player, t)) {
            t.addTag("chaos_hijacked");
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§a🧠 Захват: " + t.getType().getDescription().getString()),
                false);
            return 1;
        }
        return 0;
    }

    private static int hiveburst(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        Set<UUID> hosts = NeuralHijackerHandler.getHosts(player.getUUID());
        if (hosts.isEmpty()) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4Нет захваченных хостов!"), false);
            return 0;
        }

        // Find nearest enemy
        Optional<LivingEntity> nearestEnemy = level.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(30),
            e -> e != player && e.isAlive() && !hosts.contains(e.getUUID()))
            .stream()
            .min(Comparator.comparingDouble(e -> e.distanceTo(player)));

        int count = 0;
        for (UUID uuid : hosts) {
            if (!(level.getEntity(uuid) instanceof LivingEntity host)) continue;

            // Rush to nearest enemy
            if (nearestEnemy.isPresent() && host instanceof net.minecraft.world.entity.Mob mob) {
                mob.setTarget(nearestEnemy.get());
            }

            // Explosion damage at host position
            List<LivingEntity> victims = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(host.getX() - 6, host.getY() - 2, host.getZ() - 6,
                         host.getX() + 6, host.getY() + 4, host.getZ() + 6),
                e -> e != player && !hosts.contains(e.getUUID()) && e.isAlive());
            for (LivingEntity v : victims) {
                v.hurt(player.damageSources().explosion(player, player), 5.0f);
            }

            // Spore cloud at explosion point
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                host.getX(), host.getY() + 0.5, host.getZ(),
                40, 1.5, 1.0, 1.5, 0.05);
            level.sendParticles(ParticleTypes.EXPLOSION,
                host.getX(), host.getY() + 0.5, host.getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, host.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.2f);

            // Spore infection area — mobs near cloud get infected
            level.getEntitiesOfClass(LivingEntity.class,
                new AABB(host.getX() - 5, host.getY() - 1, host.getZ() - 5,
                         host.getX() + 5, host.getY() + 3, host.getZ() + 5),
                e -> e != player && !hosts.contains(e.getUUID()) && e.isAlive()
                    && !e.getTags().contains("chaos_hijacked"))
                .forEach(e -> {
                    if (java.util.concurrent.ThreadLocalRandom.current().nextFloat() < 0.25f
                            && NeuralHijackerHandler.canInfect(player)) {
                        NeuralHijackerHandler.infectTarget(player, e);
                        e.addTag("chaos_hijacked");
                    }
                });

            host.removeTag("chaos_hijacked");
            host.kill();
            count++;
        }

        hosts.clear();

        level.playSound(null, player.blockPosition(),
            SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5f, 1.8f);
        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal("§2💥 Взорвано хостов: " + count), false);
        return 1;
    }
}
