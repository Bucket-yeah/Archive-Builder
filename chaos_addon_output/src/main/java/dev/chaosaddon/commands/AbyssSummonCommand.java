package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.warden.Warden;

import java.util.Random;

/**
 * Eater of Worlds – Abyss Summon: spawns a Warden-equivalent in radius 50.
 * The Warden is vanilla; boosted HP is set via attributes on spawn.
 */
public class AbyssSummonCommand {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_abyss_summon")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos origin   = player.blockPosition();
                int radius = 50;

                // Find a random spawn point
                BlockPos spawnPos = origin.offset(
                    RNG.nextInt(radius * 2 + 1) - radius,
                    0,
                    RNG.nextInt(radius * 2 + 1) - radius
                );

                Warden warden = EntityType.WARDEN.create(level);
                if (warden == null) return 0;
                warden.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                warden.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0);
                warden.setHealth(200.0f);
                warden.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);
                level.addFreshEntity(warden);

                // Set it to despawn after 60 seconds (1200 ticks)
                warden.addTag("chaos_abyss_entity");
                warden.getPersistentData().putInt("chaos_despawn_ticks", 1200);

                // Dramatic FX
                level.sendParticles(ParticleTypes.SMOKE,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5,
                    80, 1.0, 1.5, 1.0, 0.05);
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                    60, 0.8, 1.0, 0.8, 0.1);
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 1.5, spawnPos.getZ() + 0.5,
                    30, 0.5, 0.5, 0.5, 0.05);
                level.playSound(null, spawnPos, SoundEvents.WARDEN_SONIC_BOOM,  SoundSource.HOSTILE, 1.0f, 0.6f);
                level.playSound(null, spawnPos, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 0.8f, 0.5f);
                return 1;
            }));
    }
}
