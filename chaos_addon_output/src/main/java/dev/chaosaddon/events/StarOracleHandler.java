package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Random;

/**
 * Handles Star Oracle passives:
 * - star_foresight: warning particles when a mob targets the player and is close
 * - cosmic_body: no hunger, immunity to poison/wither, +75% melee damage received
 * - star_aura: periodic mini-meteor shower
 */
public class StarOracleHandler {

    private static final Random RNG = new Random();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Cosmic body: suppress hunger
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/cosmic_body")) {
            player.getFoodData().setFoodLevel(20);
        }

        // Star Foresight: detect targeting mobs within 12 blocks, show warning
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/star_foresight")) {
            if (player.tickCount % 10 == 0) {
                List<Mob> targeting = level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(10),
                    e -> e.isAlive() && e.getTarget() == player);
                if (!targeting.isEmpty()) {
                    // Warning ring of particles
                    for (int i = 0; i < 8; i++) {
                        double angle = (Math.PI * 2 / 8) * i;
                        double px = player.getX() + Math.cos(angle) * 1.5;
                        double pz = player.getZ() + Math.sin(angle) * 1.5;
                        level.sendParticles(ParticleTypes.END_ROD,
                            px, player.getY() + 0.5, pz,
                            1, 0, 0.3, 0, 0.0);
                    }
                    level.sendParticles(ParticleTypes.FLASH,
                        player.getX(), player.getY() + 1.5, player.getZ(),
                        1, 0, 0, 0, 0);
                }
            }
        }

        // Star Aura: meteor shower every 20 seconds
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/star_aura")) {
            if (player.tickCount % 400 == 0) {
                fireMeteorShower(player, level, 5, 15);
            }
        }
    }

    /** Cosmic body: amplify melee damage, suppress fire damage halving */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:star_oracle/melee_vulnerability")) return;

        String dmgType = event.getSource().getMsgId();
        boolean isMelee = dmgType.equals("mob") || dmgType.equals("player");
        boolean isFire = dmgType.contains("fire") || dmgType.contains("lava") || dmgType.equals("onFire");
        if (isMelee || isFire) {
            event.setAmount(event.getAmount() * 1.75f);
        }

        // Immunity to poison / wither
        if (OriginHelper.hasPower(player, "chaos_addon:star_oracle/cosmic_body")) {
            String id = event.getSource().getMsgId();
            if (id.equals("wither") || id.equals("magic")) {
                event.setCanceled(true);
            }
        }
    }

    /** Fires N meteors (explosion + particles) at random positions around the player */
    public static void fireMeteorShower(ServerPlayer player, ServerLevel level, int count, int radius) {
        for (int i = 0; i < count; i++) {
            int dx = RNG.nextInt(radius * 2 + 1) - radius;
            int dz = RNG.nextInt(radius * 2 + 1) - radius;
            int tx = (int) player.getX() + dx;
            int tz = (int) player.getZ() + dz;
            int ty = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, tx, tz);

            // Particle trail from sky
            for (int j = 0; j < 5; j++) {
                level.sendParticles(ParticleTypes.FIREWORK,
                    tx + 0.5, ty + 5 - j, tz + 0.5,
                    3, 0.2, 0, 0.2, 0.05);
            }

            // Damage mobs near impact
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(tx - 2, ty - 1, tz - 2, tx + 2, ty + 3, tz + 2),
                e -> e != player && e.isAlive());
            for (LivingEntity v : victims) {
                v.hurt(player.damageSources().magic(), 2.0f);
            }

            // Impact particles
            level.sendParticles(ParticleTypes.EXPLOSION,
                tx + 0.5, ty + 0.5, tz + 0.5, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME,
                tx + 0.5, ty + 0.5, tz + 0.5, 12, 0.5, 0.3, 0.5, 0.05);
        }

        level.playSound(null, player.blockPosition(),
            SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 0.6f);
    }
}
