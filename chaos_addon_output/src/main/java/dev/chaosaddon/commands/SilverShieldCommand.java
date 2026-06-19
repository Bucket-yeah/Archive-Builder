package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.util.OriginHelper;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lunar Renegade – Silver Shield (chaos_addon_silver_shield).
 * Absorbs 8 HP (16 on new moon, 4 on full moon), reflects 40% damage (80% on full moon).
 * Duration 8s or until absorbed. Configured via Lunar moon phase.
 */
public class SilverShieldCommand {

    /** Maps player UUID -> shield HP remaining */
    public static final Map<UUID, Float> SHIELD_HP = new HashMap<>();
    /** Maps player UUID -> reflection fraction */
    public static final Map<UUID, Float> SHIELD_REFLECT = new HashMap<>();
    /** Maps player UUID -> shield expiry game tick */
    public static final Map<UUID, Long> SHIELD_EXPIRY = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_silver_shield")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                if (!OriginHelper.hasPower(player, "chaos_addon:lunar_renegade/silver_light")) return 0;

                ServerLevel level = player.serverLevel();
                long now = level.getGameTime();
                int moonPhase = level.getMoonPhase();

                float shieldHp;
                float reflectFrac;
                // 0=full, 4=new, others=intermediate
                if (moonPhase == 0) {
                    shieldHp = 8.0f;   // full moon: aggressive mode
                    reflectFrac = 0.80f;
                } else if (moonPhase == 4) {
                    shieldHp = 16.0f;  // new moon: defensive mode
                    reflectFrac = 0.40f;
                } else {
                    shieldHp = 8.0f;
                    reflectFrac = 0.40f;
                }

                SHIELD_HP.put(player.getUUID(), shieldHp);
                SHIELD_REFLECT.put(player.getUUID(), reflectFrac);
                SHIELD_EXPIRY.put(player.getUUID(), now + 160); // 8s

                // Absorption effect for visual HP
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 180,
                    (int)(shieldHp / 4) - 1, false, true));

                // Particles
                for (int i = 0; i < 16; i++) {
                    double angle = (Math.PI * 2 / 16) * i;
                    level.sendParticles(ParticleTypes.SNOWFLAKE,
                        player.getX() + Math.cos(angle) * 1.2,
                        player.getY() + 1.0,
                        player.getZ() + Math.sin(angle) * 1.2,
                        1, 0, 0.1, 0, 0.02);
                    level.sendParticles(ParticleTypes.END_ROD,
                        player.getX() + Math.cos(angle) * 1.0,
                        player.getY() + 0.5,
                        player.getZ() + Math.sin(angle) * 1.0,
                        1, 0, 0.05, 0, 0.01);
                }
                level.playSound(null, player.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.9f, 1.2f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.6f, 1.4f);

                String shieldInfo = moonPhase == 0 ? "§6[Полнолуние: 4❤ / 80%]" :
                    moonPhase == 4 ? "§7[Новолуние: 16❤ / 40%]" : "§f[8❤ / 40%]";
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§b✦ Серебряный Щит активирован " + shieldInfo));
                return 1;
            }));
    }
}
