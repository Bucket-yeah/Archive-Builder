package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.chaosaddon.events.BloodSmithHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.Random;

/**
 * Blood Smith commands:
 * - chaos_addon_blood_golem: spend 30 charges, summon iron golem ally
 * - chaos_addon_blood_blade: spend 15 charges, get Strength III + blood cloud
 */
public class BloodSmithCommands {

    private static final Random RNG = new Random();
    private static final ResourceLocation BLOOD_GOLEM_MOD =
        ResourceLocation.fromNamespaceAndPath("chaos_addon", "blood_golem_hp");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_blood_golem")
            .requires(src -> src.hasPermission(0))
            .executes(BloodSmithCommands::bloodGolem));

        dispatcher.register(Commands.literal("chaos_addon_blood_blade")
            .requires(src -> src.hasPermission(0))
            .executes(BloodSmithCommands::bloodBlade));

        dispatcher.register(Commands.literal("chaos_addon_blood_sacrifice")
            .requires(src -> src.hasPermission(0))
            .executes(BloodSmithCommands::bloodSacrifice));
    }

    private static int bloodGolem(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        int charges = BloodSmithHandler.getCharges(player);
        int cost = player.getHealth() <= 4.0f ? 15 : 30;

        if (charges < cost) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    "§4Недостаточно зарядов! Нужно " + cost + ", есть " + charges), false);
            return 0;
        }

        if (!BloodSmithHandler.spendCharges(player, cost)) return 0;

        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (golem == null) return 0;

        // Enhanced if many charges
        boolean enhanced = charges >= 60;
        golem.moveTo(player.getX() + RNG.nextGaussian() * 1.5,
            player.getY(), player.getZ() + RNG.nextGaussian() * 1.5, 0, 0);
        golem.addTag("chaos_blood_golem");
        golem.setPlayerCreated(true);

        // HP
        var maxHp = golem.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.removeModifier(BLOOD_GOLEM_MOD);
            double bonus = enhanced ? 40.0 - 100.0 : 25.0 - 100.0; // base is 100
            maxHp.addTransientModifier(new AttributeModifier(
                BLOOD_GOLEM_MOD, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
        golem.setHealth(enhanced ? 40.0f : 25.0f);

        // Extra attack damage if enhanced
        if (enhanced) {
            var atk = golem.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) {
                ResourceLocation atkMod = ResourceLocation.fromNamespaceAndPath("chaos_addon", "blood_golem_atk");
                atk.removeModifier(atkMod);
                atk.addTransientModifier(new AttributeModifier(
                    atkMod, 3.0, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // Timer: 40 seconds
        golem.getPersistentData().putInt("chaos_despawn_ticks", 800);
        level.addFreshEntity(golem);

        // FX
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2 / 8) * i;
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                player.getX() + Math.cos(angle) * 1.5,
                player.getY() + 1.0,
                player.getZ() + Math.sin(angle) * 1.5,
                3, 0, 0.5, 0, 0.05);
        }
        level.sendParticles(ParticleTypes.ENCHANT,
            golem.getX(), golem.getY() + 2.0, golem.getZ(),
            30, 0.5, 0.8, 0.5, 0.1);
        level.playSound(null, player.blockPosition(),
            SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8f, 0.9f);

        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                (enhanced ? "§c⚡ Улучшенный " : "§4") + "Кровавый Голем призван! (-" + cost + " зарядов)"),
            false);
        return 1;
    }

    private static int bloodBlade(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        int cost = player.getHealth() <= 4.0f ? 7 : 15;
        if (!BloodSmithHandler.spendCharges(player, cost)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    "§4Недостаточно зарядов! Нужно " + cost), false);
            return 0;
        }

        // Strength III for 6 seconds
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 2, false, true));

        // Mark player with blood blade tag for cloud effect
        player.addTag("chaos_blood_blade_active");
        player.getPersistentData().putInt("chaos_blood_blade_ticks", 120);

        // FX
        level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
            player.getX(), player.getY() + 1.2, player.getZ(),
            30, 0.4, 0.7, 0.4, 0.1);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
            player.getX(), player.getY() + 1.0, player.getZ(),
            15, 0.3, 0.5, 0.3, 0.05);
        level.playSound(null, player.blockPosition(),
            SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 0.8f, 0.7f);
        level.playSound(null, player.blockPosition(),
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4f, 1.5f);

        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal("§c🗡 Кровавый Клинок! (-" + cost + " зарядов)"),
            false);
        return 1;
    }

    private static int bloodSacrifice(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        // Cost: 40 charges + 4 HP sacrifice
        int cost = player.getHealth() <= 4.0f ? 20 : 40;
        if (!BloodSmithHandler.spendCharges(player, cost)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    "§4Недостаточно зарядов для жертвы! Нужно " + cost), false);
            return 0;
        }
        // Sacrifice HP
        if (player.getHealth() <= 4.0f) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4Слишком мало HP для жертвы!"), false);
            BloodSmithHandler.spendCharges(player, -cost); // refund
            return 0;
        }
        player.hurt(player.damageSources().magic(), 4.0f);

        // Summon "Blood Titan" — use Iron Golem with enhanced stats
        BlockPos pos = player.blockPosition().offset(3, 0, 0);
        IronGolem titan = EntityType.IRON_GOLEM.create(level);
        if (titan == null) return 0;

        titan.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        titan.addTag("chaos_blood_titan");
        titan.setGlowingTag(true);

        // 80 HP
        var maxHp = titan.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            ResourceLocation hpMod = ResourceLocation.fromNamespaceAndPath("chaos_addon", "blood_titan_hp");
            maxHp.removeModifier(hpMod);
            maxHp.addTransientModifier(new AttributeModifier(hpMod, -20.0, AttributeModifier.Operation.ADD_VALUE));
        }
        titan.setHealth(80.0f);

        // 10 attack damage
        var atk = titan.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            ResourceLocation atkMod = ResourceLocation.fromNamespaceAndPath("chaos_addon", "blood_titan_atk");
            atk.removeModifier(atkMod);
            atk.addTransientModifier(new AttributeModifier(atkMod, 4.0, AttributeModifier.Operation.ADD_VALUE));
        }

        titan.getPersistentData().putInt("chaos_despawn_ticks", 400); // 20 sec
        titan.getPersistentData().putBoolean("chaos_blood_titan", true);
        titan.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
        level.addFreshEntity(titan);

        // Apply weakness to player while titan lives
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1, false, true));

        // FX
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2 / 12) * i;
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                pos.getX() + Math.cos(angle) * 2.0, pos.getY() + 1.5, pos.getZ() + Math.sin(angle) * 2.0,
                5, 0, 1.0, 0, 0.1);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.5, 0.5, 0.5, 0.0);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.8f, 0.7f);
        level.playSound(null, pos, SoundEvents.IRON_GOLEM_DEATH, SoundSource.PLAYERS, 1.0f, 0.5f);

        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                "§4💀 Кровавый Титан призван! (-" + cost + " зарядов, -4❤ жертва)"), false);
        return 1;
    }
}
