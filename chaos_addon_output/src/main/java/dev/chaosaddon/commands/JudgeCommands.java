package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.events.DimensionJudgeHandler;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * chaos_addon_verdict          — "Приговор"    (LMB primary)
 * chaos_addon_confiscation     — "Конфискация" (RMB secondary)
 * chaos_addon_higher_judgment  — "Высший Суд"  (ternary ultimate)
 */
public class JudgeCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ── "Приговор": mark target with Glowing; if they attacked player → Strength II ──
        dispatcher.register(Commands.literal("chaos_addon_verdict")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                Optional<LivingEntity> opt = level.getEntitiesOfClass(
                        LivingEntity.class, player.getBoundingBox().inflate(15),
                        e -> e != player && e.isAlive())
                    .stream()
                    .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)));

                if (opt.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§4⚖ Нет целей для Приговора."));
                    return 0;
                }
                LivingEntity t = opt.get();

                // Always mark with Glowing (20 s)
                t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, true));

                // Check attacker registry
                Map<UUID, Integer> counts = DimensionJudgeHandler.ATTACKER_COUNTS
                    .getOrDefault(player.getUUID(), Map.of());
                boolean guilty = counts.containsKey(t.getUUID());

                if (guilty) {
                    // Boost player's next attacks (Strength II, 8 s)
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 1, false, true));
                    player.displayClientMessage(
                        Component.literal("§4⚖ §lПриговор: ВИНОВЕН — Мощь II на 8 сек!"), true);
                } else {
                    player.displayClientMessage(
                        Component.literal("§4⚖ Приговор вынесен — цель под наблюдением..."), true);
                }

                level.sendParticles(ParticleTypes.FLASH,
                    t.getX(), t.getY() + 1.0, t.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 1.0f, 1.0f);
                return 1;
            }));

        // ── "Конфискация": weaken + disarm target, strip positive effects ──
        dispatcher.register(Commands.literal("chaos_addon_confiscation")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                Optional<LivingEntity> opt = level.getEntitiesOfClass(
                        LivingEntity.class, player.getBoundingBox().inflate(15),
                        e -> e != player && e.isAlive())
                    .stream()
                    .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)));

                if (opt.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§4⚖ Нет целей для Конфискации."));
                    return 0;
                }
                LivingEntity t = opt.get();

                // Apply debuffs (10 s)
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1, false, true));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1, false, true));
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, true));

                // Strip positive effects
                t.removeEffect(MobEffects.DAMAGE_BOOST);
                t.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                t.removeEffect(MobEffects.MOVEMENT_SPEED);
                t.removeEffect(MobEffects.REGENERATION);
                t.removeEffect(MobEffects.ABSORPTION);
                t.removeEffect(MobEffects.FIRE_RESISTANCE);

                level.sendParticles(ParticleTypes.WITCH,
                    t.getX(), t.getY() + 1.0, t.getZ(), 25, 0.4, 0.8, 0.4, 0.05);
                level.sendParticles(ParticleTypes.FLASH,
                    t.getX(), t.getY() + 1.0, t.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.9f);

                player.displayClientMessage(
                    Component.literal("§4⚖ §lКонфискация! §r§7Цель ослаблена и замедлена."), true);
                return 1;
            }));

        // ── "Высший Суд": proportional AoE damage to attackers by hit count ──
        dispatcher.register(Commands.literal("chaos_addon_higher_judgment")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                Map<UUID, Integer> counts = DimensionJudgeHandler.ATTACKER_COUNTS
                    .getOrDefault(player.getUUID(), Map.of());

                if (counts.isEmpty()) {
                    player.sendSystemMessage(
                        Component.literal("§4⚖ Никто не нападал на тебя — суд пуст."));
                    return 0;
                }

                float dmgPerHit = dev.chaosaddon.config.ChaosAddonConfig.get().judgeHigherJudgmentDmgPerHit;
                float maxDmg    = 20.0f;
                float radius    = dev.chaosaddon.config.ChaosAddonConfig.get().judgeHigherJudgmentRadius;

                List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    e -> e != player && e.isAlive() && counts.containsKey(e.getUUID()));

                int judged = 0;
                for (LivingEntity e : nearby) {
                    int hits = counts.getOrDefault(e.getUUID(), 0);
                    float dmg = Math.min(hits * dmgPerHit, maxDmg);
                    e.hurt(player.damageSources().magic(), dmg);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0, false, true));
                    level.sendParticles(ParticleTypes.FLASH,
                        e.getX(), e.getY() + 1.0, e.getZ(), 1, 0, 0, 0, 0);
                    judged++;
                }

                // Clear the attacker log for this player
                DimensionJudgeHandler.ATTACKER_COUNTS.remove(player.getUUID());

                level.sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 2.0, player.getZ(),
                    100, radius * 0.6, 4.0, radius * 0.6, 0.15);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 1.2f, 0.6f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 0.5f);

                player.displayClientMessage(
                    Component.literal("§4⚖ §l⚖ Высший Суд! §r§7" + judged + " виновных наказаны!"), true);
                return 1;
            }));
    }
}
