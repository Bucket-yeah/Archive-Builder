package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Necrovore – Dead Legion (chaos_addon_dead_legion).
 * Costs 10 soul stacks. Summons 6 zombie warriors (20 HP, 4 damage) for 30s.
 * On warrior death: poison explosion radius 3.
 * If 10+ kills in 30s: reset cooldown.
 */
public class DeadLegionCommand {

    private static final Random RNG = new Random();
    private static final int WARRIOR_HP = 20;
    private static final int WARRIOR_COUNT = 6;
    private static final int DURATION_TICKS = 600; // 30s
    private static final int SOUL_COST = 10;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_dead_legion")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                if (!OriginHelper.hasPower(player, "chaos_addon:necrovore/dead_legion")) return 0;

                ServerLevel level = player.serverLevel();
                int souls = player.getPersistentData().getInt("chaos_soul_count");

                if (souls < SOUL_COST) {
                    player.sendSystemMessage(Component.literal(
                        "§c💀 Недостаточно душ! Нужно " + SOUL_COST + ", есть " + souls));
                    return 0;
                }

                // Consume souls
                player.getPersistentData().putInt("chaos_soul_count", souls - SOUL_COST);

                List<Zombie> warriors = new ArrayList<>();
                for (int i = 0; i < WARRIOR_COUNT; i++) {
                    double angle = (Math.PI * 2 / WARRIOR_COUNT) * i;
                    double spawnX = player.getX() + Math.cos(angle) * 3.0;
                    double spawnZ = player.getZ() + Math.sin(angle) * 3.0;
                    BlockPos spawnPos = BlockPos.containing(spawnX, player.getY(), spawnZ);

                    Zombie warrior = EntityType.ZOMBIE.create(level);
                    if (warrior == null) continue;

                    warrior.moveTo(spawnX, player.getY(), spawnZ, (float)(angle * 180 / Math.PI), 0);
                    warrior.setCustomName(Component.literal("§4⚔ Мёртвый Воин"));
                    warrior.setCustomNameVisible(true);
                    warrior.setHealth(WARRIOR_HP);

                    // Buff the warrior
                    warrior.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION_TICKS, 1, false, false));

                    // Track for death explosion — stored in NBT
                    warrior.getPersistentData().putBoolean("chaos_dead_legion_warrior", true);
                    warrior.getPersistentData().putString("chaos_necrovore_owner",
                        player.getUUID().toString());

                    // Schedule despawn
                    warrior.getPersistentData().putInt("chaos_despawn_ticks", DURATION_TICKS);

                    level.addFreshEntity(warrior);
                    warriors.add(warrior);

                    // Spawn FX pillar
                    level.sendParticles(ParticleTypes.SOUL,
                        spawnX, player.getY(), spawnZ,
                        15, 0.3, 1.2, 0.3, 0.04);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        spawnX, player.getY() + 0.5, spawnZ,
                        10, 0.2, 0.8, 0.2, 0.06);
                }

                // Set warriors to attack nearby enemies
                level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(20),
                    e -> !e.getUUID().equals(player.getUUID()) && e.isAlive() && !warriors.contains(e))
                    .stream().findFirst()
                    .ifPresent(target -> warriors.forEach(w -> w.setTarget(target)));

                level.playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.7f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 0.8f, 1.2f);

                player.sendSystemMessage(Component.literal(
                    "§4☠ Мёртвый Легион призван! 6 воинов на 30 секунд."));
                return 1;
            }));
    }
}
