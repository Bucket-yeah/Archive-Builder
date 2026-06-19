package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class GeneralPowerHandler {

    private static final Map<UUID, Integer> HIT_COUNTERS   = new java.util.HashMap<>();
    private static final Map<UUID, Long>    JUDGE_HIT_TIME = new java.util.HashMap<>();
    private static final Random RNG = new Random();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        ChaosAddonConfig cfg = ChaosAddonConfig.get();

        // Illusory Flesh glow FX
        if (OriginHelper.hasPower(player, "chaos_addon:nightmare_mimic/illusory_flesh")) {
            if (player.tickCount % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 25, 0, true, false));
                level.sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    8, 0.5, 0.8, 0.5, 0.05);
                level.sendParticles(ParticleTypes.WITCH,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    4, 0.4, 0.6, 0.4, 0.0);
            }
        }

        // Stone Flesh altitude damage
        if (OriginHelper.hasPower(player, "chaos_addon:deep_geomancer/stone_flesh")) {
            if (player.blockPosition().getY() > cfg.geoHeightLimit) {
                if (player.tickCount % cfg.geoHighAltitudeInterval == 0) {
                    player.hurt(player.damageSources().generic(), cfg.geoHighAltitudeDamage);
                    level.sendParticles(ParticleTypes.WHITE_ASH,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        10, 0.5, 0.8, 0.5, 0.05);
                }
            }
        }

        // Green Blood bad-biome damage
        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/green_blood")) {
            String biome = level.getBiome(player.blockPosition())
                .unwrapKey().map(k -> k.location().toString()).orElse("");
            boolean badBiome = biome.contains("desert") || biome.contains("nether")
                || biome.contains("savanna") || biome.contains("badlands")
                || biome.contains("end");
            if (badBiome && player.tickCount % cfg.gardenBadBiomeInterval == 0) {
                player.hurt(player.damageSources().generic(), cfg.gardenBadBiomeDamage);
            }
        }

        // Mad Whisper: every 60 ticks, 15% chance per nearby mob to re-target random entity
        if (OriginHelper.hasPower(player, "chaos_addon:eater_of_worlds/mad_whisper")) {
            if (player.tickCount % 60 == 0) {
                List<LivingEntity> nearby = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(12),
                    e -> e != player && e.isAlive() && e instanceof Mob);
                if (!nearby.isEmpty()) {
                    List<LivingEntity> targets = level.getEntitiesOfClass(
                        LivingEntity.class, player.getBoundingBox().inflate(12),
                        e -> e.isAlive());
                    for (LivingEntity mob : nearby) {
                        if (RNG.nextFloat() < 0.15f && !targets.isEmpty()) {
                            LivingEntity newTarget = targets.get(RNG.nextInt(targets.size()));
                            if (newTarget != mob && mob instanceof Mob m) {
                                m.setTarget(newTarget);
                                level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                                    mob.getX(), mob.getY() + 1.5, mob.getZ(),
                                    3, 0.3, 0.3, 0.3, 0.0);
                            }
                        }
                    }
                }
            }
        }

        // Royal Pheromone: every 40 ticks, clear target on nearby arthropods that target the player
        if (OriginHelper.hasPower(player, "chaos_addon:swarm_lord/royal_pheromone")) {
            if (player.tickCount % 40 == 0) {
                level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(10),
                    e -> e.isAlive() && e.getType().is(EntityTypeTags.ARTHROPOD))
                    .forEach(mob -> {
                        if (mob.getTarget() == player) {
                            mob.setTarget(null);
                            level.sendParticles(ParticleTypes.HEART,
                                mob.getX(), mob.getY() + 1.5, mob.getZ(),
                                2, 0.3, 0.2, 0.3, 0.0);
                        }
                    });
            }
        }
    }

    /**
     * Royal Pheromone: cancel arthropod attacks on the Swarm Lord.
     */
    @SubscribeEvent
    public static void onRoyalPheromoneAttack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:swarm_lord/royal_pheromone")) return;
        if (event.getSource().getEntity() instanceof Mob attacker
                && attacker.getType().is(EntityTypeTags.ARTHROPOD)) {
            event.setCanceled(true);
        }
    }

    /**
     * Stasis Field: entities tagged "chaos_stasis" take 2x damage.
     */
    @SubscribeEvent
    public static void onStasisDamage(LivingIncomingDamageEvent event) {
        if (!event.getEntity().getTags().contains("chaos_stasis")) return;
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && OriginHelper.hasPower(player, "chaos_addon:time_wanderer/stasis_field")) {
            event.setAmount(event.getAmount() * 2.0f);
        }
    }

    /**
     * Illusory Flesh: 40% chance to cancel incoming damage, spawning a decoy Silverfish.
     */
    @SubscribeEvent
    public static void onIllusoryFlesh(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:nightmare_mimic/illusory_flesh")) return;
        if (event.getAmount() <= 0) return;

        if (RNG.nextFloat() < 0.40f) {
            event.setCanceled(true);
            if (!(player.level() instanceof ServerLevel level)) return;

            var sf = net.minecraft.world.entity.EntityType.SILVERFISH.create(level);
            if (sf != null) {
                sf.moveTo(player.getX() + (RNG.nextDouble() - 0.5) * 1.5,
                          player.getY(), player.getZ() + (RNG.nextDouble() - 0.5) * 1.5, 0, 0);
                sf.addTag("chaos_mimic_decoy");
                sf.getPersistentData().putInt("chaos_despawn_ticks", 100);
                level.addFreshEntity(sf);
            }

            level.sendParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(),
                20, 0.8, 1.0, 0.8, 0.1);
            level.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.7f, 1.4f);
        }
    }

    /**
     * Time Wanderer Déjà Vu: every 5th hit taken is completely cancelled.
     */
    @SubscribeEvent
    public static void onDejaVu(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:time_wanderer/deja_vu")) return;

        int count = HIT_COUNTERS.merge(player.getUUID(), 1, Integer::sum);
        if (count % 5 == 0) {
            event.setCanceled(true);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    12, 0.4, 0.6, 0.4, 0.05);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.5f);
            }
        }
    }

    /**
     * Dimension Judge Lawfulness: cannot attack unless they were recently hit.
     */
    @SubscribeEvent
    public static void onJudgeAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:dimension_judge/lawfulness")) return;

        long now = player.level().getGameTime();
        Long lastHit = JUDGE_HIT_TIME.get(player.getUUID());

        if (lastHit == null || now - lastHit > 100) {
            if (event.getTarget() instanceof LivingEntity target) {
                event.setCanceled(true);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.FLASH,
                        target.getX(), target.getY() + 1.0, target.getZ(), 1, 0, 0, 0, 0);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onJudgeReceivesHit(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:dimension_judge/lawfulness")) return;
        JUDGE_HIT_TIME.put(player.getUUID(), player.level().getGameTime());
    }

    /**
     * Radioactive Phantom: kill grants 2 hunger.
     */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:radioactive_phantom/flesh_rot")) return;
        player.getFoodData().eat(2, 1.0f);
    }

    /**
     * Ancient Sentinel: instant drown damage in any liquid.
     */
    @SubscribeEvent
    public static void onSentinelTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:ancient_sentinel/mountain_stride")) return;
        if (player.isInWater() || player.isInLava()) {
            if (player.tickCount % 10 == 0) {
                player.hurt(player.damageSources().drown(), 4.0f);
            }
        }
    }

    /**
     * Dimension Judge Annihilate: countdown on tagged entities, kill on expiry.
     */
    @SubscribeEvent
    public static void onAnnihilateTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.getTags().contains("chaos_annihilate")) return;

        int ticks = entity.getPersistentData().getInt("chaos_annihilate_tick");
        if (ticks <= 0) {
            entity.removeTag("chaos_annihilate");
            entity.getPersistentData().remove("chaos_annihilate_tick");
            entity.kill();
            if (entity.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    entity.getX(), entity.getY() + 1.0, entity.getZ(),
                    60, 1.0, 1.5, 1.0, 0.2);
                level.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    40, 0.8, 1.0, 0.8, 0.3);
                level.playSound(null, entity.blockPosition(),
                    SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 1.0f, 0.8f);
            }
        } else {
            entity.getPersistentData().putInt("chaos_annihilate_tick", ticks - 1);
            if (ticks % 10 == 0 && entity.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                    entity.getX(), entity.getY() + 1.5, entity.getZ(),
                    5, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    /**
     * Stasis tag expiry: remove chaos_stasis tag after 160 ticks.
     */
    @SubscribeEvent
    public static void onStasisTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.getTags().contains("chaos_stasis")) return;

        int remaining = entity.getPersistentData().getInt("chaos_stasis_ticks");
        if (remaining <= 0) {
            entity.removeTag("chaos_stasis");
            entity.getPersistentData().remove("chaos_stasis_ticks");
        } else {
            entity.getPersistentData().putInt("chaos_stasis_ticks", remaining - 1);
        }
    }

    /**
     * Despawn helper: tagged mobs with a despawn counter (all races).
     */
    @SubscribeEvent
    public static void onEntityDespawnTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.getTags().contains("chaos_mimic_decoy")
            && !entity.getTags().contains("chaos_infernal_pet")
            && !entity.getTags().contains("chaos_engineer_golem")
            && !entity.getTags().contains("chaos_blood_golem")
            && !entity.getTags().contains("chaos_celestial_guardian")
            && !entity.getTags().contains("chaos_mirror_copy")) return;

        int ticks = entity.getPersistentData().getInt("chaos_despawn_ticks");
        if (ticks <= 0) {
            if (entity.level() instanceof ServerLevel level) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    entity.getX(), entity.getY() + 1.0, entity.getZ(),
                    10, 0.4, 0.5, 0.4, 0.02);
            }
            entity.kill();
        } else {
            entity.getPersistentData().putInt("chaos_despawn_ticks", ticks - 1);
        }
    }

    /**
     * Mirror World timer: remove tag after 8 seconds.
     * Blood Blade timer: remove tag after 6 seconds.
     */
    @SubscribeEvent
    public static void onPlayerTagTimers(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Mirror World timer
        if (player.getTags().contains("chaos_mirror_world")) {
            int ticks = player.getPersistentData().getInt("chaos_mirror_world_ticks");
            if (ticks <= 0) {
                player.removeTag("chaos_mirror_world");
                player.getPersistentData().remove("chaos_mirror_world_ticks");
            } else {
                player.getPersistentData().putInt("chaos_mirror_world_ticks", ticks - 1);
            }
        }

        // Blood Blade timer — leave cloud when player hits something
        if (player.getTags().contains("chaos_blood_blade_active")) {
            int ticks = player.getPersistentData().getInt("chaos_blood_blade_ticks");
            if (ticks <= 0) {
                player.removeTag("chaos_blood_blade_active");
                player.getPersistentData().remove("chaos_blood_blade_ticks");
            } else {
                player.getPersistentData().putInt("chaos_blood_blade_ticks", ticks - 1);
                // Trail particles while active
                if (ticks % 5 == 0) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_DRIPSTONE_LAVA,
                        player.getX(), player.getY() + 0.8, player.getZ(),
                        3, 0.2, 0.3, 0.2, 0.02);
                }
            }
        }
    }

    /**
     * Blood Blade: when player attacks, leave a blood cloud at the hit location.
     */
    @SubscribeEvent
    public static void onBloodBladeAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getTags().contains("chaos_blood_blade_active")) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        // Create cloud of blood particles at target
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_DRIPSTONE_LAVA,
            target.getX(), target.getY() + 0.5, target.getZ(),
            20, 0.6, 0.4, 0.6, 0.05);

        // Tag the area — periodic damage to mobs in blood cloud
        // Store cloud position in target's data for 3 seconds
        target.addTag("chaos_blood_cloud");
        target.getPersistentData().putInt("chaos_blood_cloud_ticks", 60);
    }

    /**
     * Blood cloud tick: damage entities in cloud.
     */
    @SubscribeEvent
    public static void onBloodCloudTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.getTags().contains("chaos_blood_cloud")) return;

        int ticks = entity.getPersistentData().getInt("chaos_blood_cloud_ticks");
        if (ticks <= 0) {
            entity.removeTag("chaos_blood_cloud");
            entity.getPersistentData().remove("chaos_blood_cloud_ticks");
        } else {
            entity.getPersistentData().putInt("chaos_blood_cloud_ticks", ticks - 1);
            if (ticks % 20 == 0) {
                entity.hurt(entity.damageSources().magic(), 1.0f);
                if (entity.level() instanceof ServerLevel level) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_DRIPSTONE_LAVA,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        8, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
    }
}
