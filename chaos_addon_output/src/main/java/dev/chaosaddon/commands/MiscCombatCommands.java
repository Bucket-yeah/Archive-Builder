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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Misc combat commands grouped here to avoid file proliferation:
 * chaos_addon_ghast_summon, chaos_addon_volcanic_burst, chaos_addon_nether_portal,
 * chaos_addon_woven_nightmare, chaos_addon_reality_dream, chaos_addon_illusory_world,
 * chaos_addon_decay_wave, chaos_addon_radio_leap, chaos_addon_mutagen_blast,
 * chaos_addon_redstone_overload, chaos_addon_golem_assemble, chaos_addon_redstone_pulse,
 * chaos_addon_time_instability, chaos_addon_golden_touch (fallback), chaos_addon_verdict_annihilate_tick
 */
public class MiscCombatCommands {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ── Infernal Shepherd ──

        dispatcher.register(Commands.literal("chaos_addon_ghast_summon")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos pos = player.blockPosition().offset(0, 3, 0);
                Ghast ghast = EntityType.GHAST.create(level);
                if (ghast == null) return 0;
                ghast.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                ghast.addTag("chaos_infernal_pet");
                ghast.getPersistentData().putInt("chaos_despawn_ticks", 400); // 20s
                ghast.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
                level.addFreshEntity(ghast);
                level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 60, 1.0, 2.0, 1.0, 0.2);
                level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 0.6f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_volcanic_burst")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos origin = player.blockPosition();
                level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5),
                    e -> e != player && e.isAlive())
                    .forEach(e -> { e.hurt(player.damageSources().inFire(), 4.0f); e.setSecondsOnFire(10); });
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                    BlockPos gp = origin.offset(dx, -1, dz);
                    if (RNG.nextFloat() < 0.4f && level.isEmptyBlock(gp.above()))
                        level.setBlock(gp.above(), net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
                }
                level.sendParticles(ParticleTypes.FLAME, origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5, 60, 3.0, 2.0, 3.0, 0.3);
                level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 0.7f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_nether_portal")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos origin = player.blockPosition();
                // Build minimal 2×3 nether portal
                for (int y = 0; y < 3; y++) {
                    level.setBlock(origin.offset(0, y, 0), net.minecraft.world.level.block.Blocks.NETHER_PORTAL.defaultBlockState(), 3);
                    level.setBlock(origin.offset(1, y, 0), net.minecraft.world.level.block.Blocks.NETHER_PORTAL.defaultBlockState(), 3);
                }
                // Pull entities in
                level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(10),
                    e -> e != player && e.isAlive())
                    .forEach(e -> { Vec3 dir = origin.getCenter().subtract(e.position()).normalize().scale(1.5); e.setDeltaMovement(dir.x, 0.4, dir.z); });
                level.sendParticles(ParticleTypes.PORTAL, origin.getX() + 0.5, origin.getY() + 1.5, origin.getZ() + 0.5, 100, 1.0, 2.0, 1.0, 0.25);
                level.playSound(null, origin, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.0f, 0.7f);
                return 1;
            }));

        // ── Nightmare Mimic ──

        dispatcher.register(Commands.literal("chaos_addon_woven_nightmare")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                // Spawn 5 tiny phantoms (silverfish) as decoys
                for (int i = 0; i < 5; i++) {
                    double ox = (RNG.nextDouble() - 0.5) * 4;
                    double oz = (RNG.nextDouble() - 0.5) * 4;
                    BlockPos sp = player.blockPosition().offset((int) ox, 0, (int) oz);
                    var sf = net.minecraft.world.entity.EntityType.SILVERFISH.create(level);
                    if (sf == null) continue;
                    sf.moveTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
                    sf.addTag("chaos_mimic_decoy");
                    sf.getPersistentData().putInt("chaos_despawn_ticks", 200);
                    level.addFreshEntity(sf);
                }
                level.sendParticles(ParticleTypes.SPELL, player.getX(), player.getY() + 1.0, player.getZ(), 50, 3.0, 2.0, 3.0, 0.15);
                level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0f, 0.8f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_reality_dream")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                MobEffectInstance[] debuffs = {
                    new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, true),
                    new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, true),
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true),
                    new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, true),
                    new MobEffectInstance(MobEffects.WITHER, 60, 0, false, true)
                };
                level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(20),
                    e -> e != player && e.isAlive())
                    .forEach(e -> e.addEffect(debuffs[RNG.nextInt(debuffs.length)]));
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.5, player.getZ(), 60, 10.0, 4.0, 10.0, 0.06);
                level.playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.9f, 0.7f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_illusory_world")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(20),
                    e -> e != player && e.isAlive())
                    .forEach(e -> {
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 400, 0, false, true));
                        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION,  400, 0, false, true));
                    });
                level.sendParticles(ParticleTypes.ENCHANT_GLYPH, player.getX(), player.getY() + 2.0, player.getZ(), 80, 10.0, 5.0, 10.0, 0.1);
                level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 1.0f, 0.7f);
                return 1;
            }));

        // ── Radioactive Phantom ──

        dispatcher.register(Commands.literal("chaos_addon_decay_wave")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                Vec3 look = player.getLookAngle().normalize();
                level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(20, 5, 20),
                    e -> e != player && e.isAlive()).stream()
                    .filter(e -> { Vec3 toE = e.position().subtract(player.position()).normalize(); return toE.dot(look) > 0.6; })
                    .forEach(e -> {
                        e.hurt(player.damageSources().magic(), 6.0f);
                        e.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1, false, true));
                    });
                level.sendParticles(ParticleTypes.WITCH, player.getX() + look.x * 10, player.getY() + 1.0, player.getZ() + look.z * 10, 60, 5.0, 2.0, 5.0, 0.3);
                level.playSound(null, player.blockPosition(), SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.0f, 0.7f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_radio_leap")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                Vec3 look = player.getLookAngle().normalize().scale(10);
                double tx = player.getX() + look.x, ty = player.getY() + look.y, tz = player.getZ() + look.z;
                // Leave toxic cloud at old position
                BlockPos oldPos = player.blockPosition();
                player.teleportTo(tx, ty, tz);
                // Tag a fake entity tracker for cloud (use actual particle burst)
                level.sendParticles(ParticleTypes.CLOUD, oldPos.getX() + 0.5, oldPos.getY() + 1.0, oldPos.getZ() + 0.5, 30, 1.0, 1.5, 1.0, 0.04);
                level.sendParticles(ParticleTypes.SMOKE,  oldPos.getX() + 0.5, oldPos.getY() + 1.0, oldPos.getZ() + 0.5, 20, 0.8, 1.2, 0.8, 0.05);
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_mutagen_blast")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                MobEffectInstance[] effects = {
                    new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 2),
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 2),
                    new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 2),
                    new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0),
                    new MobEffectInstance(MobEffects.REGENERATION, 300, 1),
                    new MobEffectInstance(MobEffects.WITHER, 300, 1),
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 2),
                    new MobEffectInstance(MobEffects.BLINDNESS, 300, 0)
                };
                level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(15),
                    e -> e != player && e.isAlive())
                    .forEach(e -> { for (int i = 0; i < 2; i++) e.addEffect(effects[RNG.nextInt(effects.length)]); });
                level.sendParticles(ParticleTypes.EFFECT, player.getX(), player.getY() + 1.0, player.getZ(), 80, 8.0, 4.0, 8.0, 0.15);
                level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.7f);
                return 1;
            }));

        // ── Chaos Engineer ──

        dispatcher.register(Commands.literal("chaos_addon_redstone_overload")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(20),
                    e -> e != player && e.isAlive())
                    .forEach(e -> { e.hurt(player.damageSources().magic(), 3.0f); e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, true)); });
                level.sendParticles(ParticleTypes.ELECTRICAL_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 80, 10.0, 3.0, 10.0, 0.2);
                level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.8f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_golem_assemble")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                // Check for 4 iron blocks in inventory
                boolean hasIron = player.getInventory().countItem(Items.IRON_BLOCK) >= 4
                    || player.getInventory().countItem(Items.IRON_INGOT) >= 16;
                if (!hasIron) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cНужно 4 блока железа или 16 слитков!"));
                    return 0;
                }
                BlockPos pos = player.blockPosition().offset(2, 0, 0);
                var golem = EntityType.IRON_GOLEM.create(level);
                if (golem == null) return 0;
                golem.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                golem.addTag("chaos_engineer_golem");
                golem.getPersistentData().putInt("chaos_despawn_ticks", 1200);
                golem.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
                level.addFreshEntity(golem);
                level.sendParticles(ParticleTypes.ITEM_SLIME, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 30, 0.5, 1.0, 0.5, 0.1);
                level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0f, 0.7f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_redstone_pulse")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(30),
                    e -> e != player && e.isAlive())
                    .forEach(e -> e.hurt(player.damageSources().magic(), 3.0f));
                level.sendParticles(ParticleTypes.ELECTRICAL_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 100, 15.0, 5.0, 15.0, 0.3);
                level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.0, player.getZ(), 30, 10.0, 3.0, 10.0, 0.2);
                level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 0.6f);
                return 1;
            }));

        // ── Time Wanderer ──

        dispatcher.register(Commands.literal("chaos_addon_time_instability")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                int roll = RNG.nextInt(3);
                switch (roll) {
                    case 0 -> { player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2)); }
                    case 1 -> { player.hurt(player.damageSources().generic(), 2.0f); }
                    case 2 -> { player.getFoodData().eat(4, 2.0f); }
                }
                return 1;
            }));
    }
}
