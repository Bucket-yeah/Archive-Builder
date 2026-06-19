package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Mirror Phantom commands:
 * - chaos_addon_perfect_copy: spawn a copy of the nearest mob
 * - chaos_addon_mirror_world: enable damage reflection zone for 8 seconds
 */
public class MirrorPhantomCommands {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_perfect_copy")
            .requires(src -> src.hasPermission(0))
            .executes(MirrorPhantomCommands::perfectCopy));

        dispatcher.register(Commands.literal("chaos_addon_mirror_world")
            .requires(src -> src.hasPermission(0))
            .executes(MirrorPhantomCommands::mirrorWorld));

        dispatcher.register(Commands.literal("chaos_addon_stolen_identity")
            .requires(src -> src.hasPermission(0))
            .executes(MirrorPhantomCommands::stolenIdentity));
    }

    private static int perfectCopy(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        // Find nearest mob within 8 blocks
        Optional<LivingEntity> nearest = level.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(8),
            e -> e != player && e.isAlive() && !(e instanceof ServerPlayer))
            .stream()
            .min(Comparator.comparingDouble(e -> e.distanceTo(player)));

        if (nearest.isEmpty()) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4Нет существ рядом!"), false);
            return 0;
        }

        LivingEntity original = nearest.get();

        // Create a copy of the same entity type
        LivingEntity copy = (LivingEntity) original.getType().create(level);
        if (copy == null) return 0;

        copy.moveTo(
            original.getX() + (RNG.nextDouble() - 0.5) * 2,
            original.getY(),
            original.getZ() + (RNG.nextDouble() - 0.5) * 2,
            RNG.nextFloat() * 360, 0);

        // Copy HP pool
        copy.setHealth(original.getHealth());

        // Tag as mirror copy
        copy.addTag("chaos_mirror_copy");
        copy.getPersistentData().putInt("chaos_despawn_ticks", 300); // 15 seconds
        copy.setGlowingTag(true);

        // Make it aggressive
        if (copy instanceof Mob mob) {
            // Target the closest enemy of player if possible
            Optional<LivingEntity> enemy = level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(20),
                e -> e != player && e != copy && e.isAlive())
                .stream()
                .filter(e -> !(e instanceof ServerPlayer) || level.getPlayers(p -> p != player).contains(e))
                .min(Comparator.comparingDouble(e -> e.distanceTo(player)));
            enemy.ifPresent(e -> mob.setTarget((LivingEntity) e));
        }

        level.addFreshEntity(copy);

        // FX — enchant explosion effect
        level.sendParticles(ParticleTypes.ENCHANT,
            copy.getX(), copy.getY() + 1.0, copy.getZ(),
            50, 0.8, 1.0, 0.8, 0.15);
        level.sendParticles(ParticleTypes.GLOW,
            copy.getX(), copy.getY() + 1.0, copy.getZ(),
            20, 0.4, 0.6, 0.4, 0.0);
        level.sendParticles(ParticleTypes.WITCH,
            copy.getX(), copy.getY() + 1.0, copy.getZ(),
            10, 0.3, 0.5, 0.3, 0.0);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(),
            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.2f);

        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                "§d🪞 Создана копия: " + original.getType().getDescription().getString() +
                " (15 сек)"), false);
        return 1;
    }

    private static int mirrorWorld(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        // Add mirror tag
        player.addTag("chaos_mirror_world");
        player.getPersistentData().putInt("chaos_mirror_world_ticks", 160); // 8 seconds

        // Invisibility while mirror is up
        player.addEffect(new MobEffectInstance(
            MobEffects.INVISIBILITY, 160, 0, false, false));

        // FX
        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2 / 16) * i;
            level.sendParticles(ParticleTypes.ENCHANT,
                player.getX() + Math.cos(angle) * 3.0, player.getY() + 1.0,
                player.getZ() + Math.sin(angle) * 3.0,
                3, 0, 0.3, 0, 0.02);
        }
        level.sendParticles(ParticleTypes.FLASH,
            player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.8f, 1.0f);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.6f, 0.9f);

        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal("§d🪞 Зеркальный Мир активен (8 сек)!"), false);
        return 1;
    }

    private static int stolenIdentity(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        // Find nearest living entity within 10 blocks
        Optional<LivingEntity> nearest = level.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(10),
            e -> e != player && e.isAlive())
            .stream()
            .min(Comparator.comparingDouble(e -> e.distanceTo(player)));

        if (nearest.isEmpty()) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4Нет цели рядом!"), false);
            return 0;
        }

        LivingEntity target = nearest.get();

        // Steal HP, speed, damage for 15 sec (300 ticks)
        float targetMaxHp = target.getMaxHealth();
        float currentHpRatio = player.getHealth() / player.getMaxHealth();

        // Health modifier
        ResourceLocation hpMod = ResourceLocation.fromNamespaceAndPath("chaos_addon", "stolen_identity_hp");
        ResourceLocation spdMod = ResourceLocation.fromNamespaceAndPath("chaos_addon", "stolen_identity_spd");
        ResourceLocation atkMod = ResourceLocation.fromNamespaceAndPath("chaos_addon", "stolen_identity_atk");

        var maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.removeModifier(hpMod);
            double hpDelta = targetMaxHp - player.getMaxHealth();
            if (hpDelta != 0) {
                maxHpAttr.addTransientModifier(new AttributeModifier(
                    hpMod, hpDelta, AttributeModifier.Operation.ADD_VALUE));
                player.setHealth(Math.min(player.getMaxHealth(), targetMaxHp * currentHpRatio + 2));
            }
        }

        // Speed modifier from target
        var speedAttr = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            var playerSpeedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (playerSpeedAttr != null) {
                playerSpeedAttr.removeModifier(spdMod);
                double spdDelta = speedAttr.getValue() - playerSpeedAttr.getBaseValue();
                if (Math.abs(spdDelta) > 0.01) {
                    playerSpeedAttr.addTransientModifier(new AttributeModifier(
                        spdMod, spdDelta, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // Attack damage from target
        var atkAttr = target.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkAttr != null) {
            var playerAtk = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (playerAtk != null) {
                playerAtk.removeModifier(atkMod);
                double atkDelta = atkAttr.getValue() - playerAtk.getBaseValue();
                if (atkDelta > 0) {
                    playerAtk.addTransientModifier(new AttributeModifier(
                        atkMod, atkDelta, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // Mark expiry in NBT
        player.getPersistentData().putInt("chaos_stolen_identity_ticks", 300); // 15 sec

        // FX
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
            player.getX(), player.getY() + 1.0, player.getZ(), 60, 1.5, 2.0, 1.5, 0.1);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
            player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.0, 1.5, 1.0, 0.08);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.6f, 0.8f);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ENDER_DRAGON_HURT, SoundSource.PLAYERS, 0.5f, 1.4f);

        String targetName = target.getType().getDescription().getString();
        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                "§5🪞 Украдена личность: " + targetName + " (15 сек)!"), false);
        return 1;
    }
}
