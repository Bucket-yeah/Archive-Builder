package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * chaos_addon_bloom — "Мицелиальное Цветение"
 * Mycelial Symbiont ultimate ability (tertiary key, 5-min cooldown).
 *
 * Mechanics:
 * - Allies in radius → Regeneration II for {@code mossBloomAllyRegenDuration} ticks
 * - Enemies in radius → Slowness III + Weakness II for {@code mossBloomEnemyDebuffDuration} ticks
 * - Self → Regeneration I + Speed I (same duration)
 * - Supernode: {@code "chaos_moss_supernode_expiry"} NBT stores expiry game-tick;
 *   while active, MycelialSymbiontHandler doubles the moss_network buff radius.
 */
public class BloomCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_bloom")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                if (!OriginHelper.hasPower(player, "chaos_addon:mycelial_symbiont/bloom")) return 0;

                ServerLevel level = player.serverLevel();
                ChaosAddonConfig cfg = ChaosAddonConfig.get();
                int radius = cfg.mossBloomRadius;

                List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    e -> e.isAlive() && e != player);

                int healed = 0;
                int debuffed = 0;

                for (LivingEntity e : entities) {
                    boolean isAlly = (e instanceof TamableAnimal animal && animal.isTame())
                        || e.getTags().contains("chaos_gardener_pet")
                        || e.getTags().contains("chaos_hive_ally")
                        || (e instanceof Player p
                            && player.getTeam() != null
                            && player.getTeam().isAlliedTo(p.getTeam()));

                    if (isAlly) {
                        e.addEffect(new MobEffectInstance(
                            MobEffects.REGENERATION, cfg.mossBloomAllyRegenDuration, 1, false, true));
                        healed++;
                    } else {
                        e.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, cfg.mossBloomEnemyDebuffDuration, 2, false, true));
                        e.addEffect(new MobEffectInstance(
                            MobEffects.WEAKNESS, cfg.mossBloomEnemyDebuffDuration, 1, false, true));
                        debuffed++;
                    }
                }

                player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, cfg.mossBloomAllyRegenDuration, 0, false, true));
                player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, cfg.mossBloomAllyRegenDuration, 0, false, true));

                long supernodeExpiry = level.getGameTime() + cfg.mossBloomSuperNodeDuration;
                player.getPersistentData().putLong("chaos_moss_supernode_expiry", supernodeExpiry);

                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    200, radius * 0.6, 4.0, radius * 0.6, 0.08);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    60, radius * 0.5, 2.0, radius * 0.5, 0.05);

                level.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 0.5f);

                player.sendSystemMessage(Component.literal(
                    "§a🍄 МИЦЕЛИАЛЬНОЕ ЦВЕТЕНИЕ! §2Радиус §a" + radius
                    + " §2блоков: §a+" + healed + " §2исцелено, §c-" + debuffed
                    + " §2ослаблено. §2Суперузел активен §a"
                    + (cfg.mossBloomSuperNodeDuration / 20) + "с§2!"));
                return 1;
            }));
    }
}
