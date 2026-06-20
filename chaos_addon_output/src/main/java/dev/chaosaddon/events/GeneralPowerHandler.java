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
import java.util.HashMap;

public class GeneralPowerHandler {

    private static final Random RNG = new Random();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        ChaosAddonConfig cfg = ChaosAddonConfig.get();

        // ── Illusory Flesh glow FX ──
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

        // ── Stone Flesh altitude damage ──
        if (OriginHelper.hasPower(player, "chaos_addon:deep_geomancer/stone_flesh")) {
            if (player.blockPosition().getY() > cfg.geoHeightLimit) {
                if (player.tickCount % cfg.geoHighAltitudeInterval == 0) {
                    player.hurt(player.damageSources().generic(), cfg.geoHighAltitudeDamage);
                    level.sendParticles(ParticleTypes.WHITE_ASH,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        10, 0.5, 0.8, 0.5, 0.05);
                }
            }
            // Stone Flesh regen: standing on any stone-type block — uses tags for mod compat
            if (player.tickCount % 160 == 0 && player.getHealth() >= 8.0f) {
                var stateBelow = level.getBlockState(player.blockPosition().below());
                boolean onStone = stateBelow.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                    || stateBelow.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)
                    || stateBelow.is(net.minecraft.tags.BlockTags.STONE_BRICKS)
                    || stateBelow.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)
                    || stateBelow.is(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE)
                    || stateBelow.is(net.minecraft.world.level.block.Blocks.SMOOTH_STONE);
                if (onStone) {
                    player.heal(1.0f);
                }
            }
        }

        // ── Seismic Sense (Ancient Sentinel): detect nearby player footsteps when still ──
        if (OriginHelper.hasOrigin(player, "chaos_addon:ancient_sentinel")
                && !player.isSprinting() && !player.isCrouching() && player.onGround()
                && player.tickCount % 20 == 0) {
            List<net.minecraft.world.entity.player.Player> nearbyPlayers =
                level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                    player.getBoundingBox().inflate(50),
                    e -> e != player && e.isAlive() && e.onGround());
            if (!nearbyPlayers.isEmpty()) {
                var closest = nearbyPlayers.stream()
                    .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
                    .orElse(null);
                if (closest != null) {
                    double dist = closest.distanceTo(player);
                    String dir = getCardinalDirection(player, closest);
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§6🌍 Сейсмо: " + dir + " §8(" + (int)dist + "м)")
                            .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                    level.sendParticles(
                        new net.minecraft.core.particles.BlockParticleOption(
                            ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
                        player.getX(), player.getY() + 0.05, player.getZ(),
                        8, 0.5, 0.05, 0.5, 0.0);
                }
            }
        }

        // ── Green Blood bad-biome damage + grass regen ──
        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/green_blood")) {
            String biome = level.getBiome(player.blockPosition())
                .unwrapKey().map(k -> k.location().toString()).orElse("");
            boolean badBiome = biome.contains("desert") || biome.contains("nether")
                || biome.contains("savanna") || biome.contains("badlands")
                || biome.contains("end");
            if (badBiome && player.tickCount % cfg.gardenBadBiomeInterval == 0) {
                player.hurt(player.damageSources().generic(), cfg.gardenBadBiomeDamage);
            }
            // Grass regen
            boolean goodBiome = biome.contains("forest") || biome.contains("jungle")
                || biome.contains("meadow") || biome.contains("flower") || biome.contains("plains");
            if (goodBiome && player.tickCount % 80 == 0) {
                player.heal(1.0f);
            }
        }

        // ── Mad Whisper handled in ChaoticAuraHandler ──

        // ── Royal Pheromone: cancel arthropod attacks ──
        // (handled in onRoyalPheromoneAttack below)

        // ── Time Wanderer instability: every 160 ticks, one random event ──
        if (OriginHelper.hasPower(player, "chaos_addon:time_wanderer/temporal_instability")) {
            if (player.tickCount % cfg.timeInstabilityInterval == 0) {
                int roll = RNG.nextInt(3);
                switch (roll) {
                    case 0 -> { // HP change ±3
                        float delta = (RNG.nextBoolean() ? 1 : -1) * (1 + RNG.nextFloat() * 3);
                        if (delta > 0) player.heal(delta);
                        else player.hurt(player.damageSources().generic(), -delta);
                        level.sendParticles(ParticleTypes.HEART,
                            player.getX(), player.getY() + 1.5, player.getZ(),
                            4, 0.4, 0.3, 0.4, 0.0);
                    }
                    case 1 -> { // Hunger change ±4
                        if (RNG.nextBoolean()) player.getFoodData().eat(4, 2.0f);
                        else player.causeFoodExhaustion(4.0f);
                        level.sendParticles(ParticleTypes.SMOKE,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            8, 0.4, 0.5, 0.4, 0.03);
                    }
                    case 2 -> { // Random effect 5s
                        MobEffectInstance[] effects = {
                            new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 1, false, true),
                            new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true),
                            new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, true),
                            new MobEffectInstance(MobEffects.ABSORPTION, 100, 1, false, true),
                            new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, true),
                            new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, true),
                            new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, true),
                            new MobEffectInstance(MobEffects.WITHER, 60, 0, false, true)
                        };
                        player.addEffect(effects[RNG.nextInt(effects.length)]);
                        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            12, 0.4, 0.8, 0.4, 0.05);
                    }
                }
                level.playSound(null, player.blockPosition(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.4f, 1.5f);
            }
        }

        // ── Dimension Judge: auto "Last Verdict" at < 20% HP ──
        if (OriginHelper.hasPower(player, "chaos_addon:dimension_judge/balance_of_powers")) {
            if (player.getHealth() / player.getMaxHealth() < 0.20f) {
                long lastUsed = player.getPersistentData().getLong("chaos_judge_final_cd");
                long now = level.getGameTime();
                if (now - lastUsed >= 6000) { // 5 min cooldown
                    player.getPersistentData().putLong("chaos_judge_final_cd", now);
                    // Find nearest enemy and deal damage equal to difference of max HP - player current HP
                    List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(15),
                        e -> e != player && e.isAlive());
                    if (!enemies.isEmpty()) {
                        LivingEntity nearest = enemies.stream()
                            .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
                            .orElse(null);
                        if (nearest != null && !(nearest instanceof ServerPlayer nearestPlayer
                                && player.getTeam() != null
                                && player.getTeam().isAlliedTo(nearestPlayer.getTeam()))) {
                            float judgeDmg = player.getMaxHealth() - player.getHealth();
                            nearest.hurt(player.damageSources().magic(), judgeDmg);
                            level.sendParticles(ParticleTypes.FLASH,
                                nearest.getX(), nearest.getY() + 1.0, nearest.getZ(),
                                3, 0, 0, 0, 0);
                            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                                nearest.getX(), nearest.getY() + 1.0, nearest.getZ(),
                                30, 0.5, 0.8, 0.5, 0.1);
                            level.playSound(null, player.blockPosition(),
                                SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 0.8f, 1.5f);
                            player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal(
                                    "§4⚖ ПОСЛЕДНИЙ ПРИГОВОР: " + (int) judgeDmg + "❤ урона!")
                                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
                        }
                    }
                }
            }
        }

        // ── Ancient Sentinel: liquid damage + stone armor stacks while standing still ──
        if (OriginHelper.hasPower(player, "chaos_addon:ancient_sentinel/mountain_stride")) {
            if (player.isInWater() || player.isInLava()) {
                if (player.tickCount % 10 == 0) {
                    player.hurt(player.damageSources().drown(), 4.0f);
                }
            }
            if (!player.isCrouching() && !player.isSprinting() && player.onGround()) {
                if (player.tickCount % 100 == 0) {
                    int stacks = player.getPersistentData().getInt("chaos_stone_stacks");
                    if (stacks < 10) {
                        stacks++;
                        player.getPersistentData().putInt("chaos_stone_stacks", stacks);
                        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                            120, stacks - 1, true, false));
                    }
                }
            }
        }
    }

    /** Royal Pheromone: cancel arthropod attacks on the Swarm Lord. */
    @SubscribeEvent
    public static void onRoyalPheromoneAttack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:swarm_lord/royal_pheromone")) return;
        if (event.getSource().getEntity() instanceof Mob attacker
                && attacker.getType().is(EntityTypeTags.ARTHROPOD)) {
            event.setCanceled(true);
        }
    }

    /** Stasis Field: entities tagged "chaos_stasis" take 2x damage. */
    @SubscribeEvent
    public static void onStasisDamage(LivingIncomingDamageEvent event) {
        if (!event.getEntity().getTags().contains("chaos_stasis")) return;
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && OriginHelper.hasPower(player, "chaos_addon:time_wanderer/stasis_field")) {
            event.setAmount(event.getAmount() * 2.0f);
        }
    }

    /** Illusory Flesh: 40% chance to cancel incoming damage, spawning a decoy. */
    @SubscribeEvent
    public static void onIllusoryFlesh(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:nightmare_mimic/illusory_flesh")) return;
        if (event.getAmount() <= 0) return;

        if (RNG.nextFloat() < 0.40f) {
            event.setCanceled(true);
            if (!(player.level() instanceof ServerLevel level)) return;

            // Track uses and spawn a permanent decoy every 3 activations
            int uses = player.getPersistentData().getInt("chaos_illusory_uses") + 1;
            player.getPersistentData().putInt("chaos_illusory_uses", uses);

            var sf = net.minecraft.world.entity.EntityType.SILVERFISH.create(level);
            if (sf != null) {
                sf.moveTo(player.getX() + (RNG.nextDouble() - 0.5) * 1.5,
                          player.getY(), player.getZ() + (RNG.nextDouble() - 0.5) * 1.5, 0, 0);
                sf.addTag("chaos_mimic_decoy");
                // Every 3 uses: permanent-ish decoy (60s), otherwise 4s
                boolean isPermanent = (uses % 3 == 0);
                int despawnTicks = isPermanent ? 1200 : 80;
                sf.getPersistentData().putInt("chaos_despawn_ticks", despawnTicks);
                if (isPermanent) {
                    sf.addTag("chaos_mimic_permanent");
                    sf.getPersistentData().putString("chaos_mimic_owner", player.getUUID().toString());
                    int decoys = player.getPersistentData().getInt("chaos_mimic_decoys") + 1;
                    player.getPersistentData().putInt("chaos_mimic_decoys", Math.min(decoys, 5));
                }
                level.addFreshEntity(sf);
            }

            level.sendParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(),
                20, 0.8, 1.0, 0.8, 0.1);
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.4, 0.6, 0.4, 0.05);
            level.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.7f, 1.4f);
        }
    }

    /**
     * Time Wanderer Déjà Vu: every 4th hit taken is completely cancelled.
     * On trigger: sculk_soul particles + +50% speed for 1s (slipstream).
     */
    @SubscribeEvent
    public static void onDejaVu(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:time_wanderer/deja_vu")) return;

        int count = player.getPersistentData().getInt("chaos_deja_vu_hits") + 1;
        player.getPersistentData().putInt("chaos_deja_vu_hits", count);
        if (count % 4 == 0) {
            event.setCanceled(true);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    12, 0.4, 0.6, 0.4, 0.05);
                // Slipstream: Speed II for 1 second
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, false, true));
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.5f);
            }
        }
    }

    /**
     * Dimension Judge Lawfulness: cannot attack unless recently hit.
     * Each consecutive counter-attack in chain: +2 HP bonus damage, max x3.
     */
    @SubscribeEvent
    public static void onJudgeAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:dimension_judge/lawfulness")) return;

        long now = player.level().getGameTime();
        long lastHit = player.getPersistentData().getLong("chaos_judge_hit_time");

        if (lastHit == 0 || now - lastHit > 100) {
            if (event.getTarget() instanceof LivingEntity) {
                event.setCanceled(true);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.FLASH,
                        event.getTarget().getX(), ((LivingEntity) event.getTarget()).getY() + 1.0,
                        event.getTarget().getZ(), 1, 0, 0, 0, 0);
                }
            }
        } else {
            // Counter-attack: apply chain bonus damage
            int chain = player.getPersistentData().getInt("chaos_judge_chain");
            long lastChainTime = player.getPersistentData().getLong("chaos_judge_chain_time");
            // Reset chain if more than 3s since last chain hit
            if (now - lastChainTime > 60) chain = 0;
            chain = Math.min(chain + 1, 3);
            player.getPersistentData().putInt("chaos_judge_chain", chain);
            player.getPersistentData().putLong("chaos_judge_chain_time", now);

            if (chain > 1 && event.getTarget() instanceof LivingEntity target) {
                float bonusDmg = (chain - 1) * 2.0f;
                target.hurt(player.damageSources().magic(), bonusDmg);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        (int)(bonusDmg * 5), 0.3, 0.5, 0.3, 0.1);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onJudgeReceivesHit(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:dimension_judge/lawfulness")) return;
        player.getPersistentData().putLong("chaos_judge_hit_time", player.level().getGameTime());
    }

    /** Radioactive Phantom: kill grants 2 hunger (handled in RadioactiveHandler). */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:radioactive_phantom/flesh_rot")) return;
        player.getFoodData().eat(2, 1.0f);
    }

    /** Ancient Sentinel: stone armor stacks take damage on hit. */
    @SubscribeEvent
    public static void onSentinelHit(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:ancient_sentinel/mountain_stride")) return;
        // Reset stone stacks on hit
        int stacks = player.getPersistentData().getInt("chaos_stone_stacks");
        if (stacks > 0) {
            player.getPersistentData().putInt("chaos_stone_stacks", 0);
        }
        // Crystal Carapace: reflect damage to attacker
        if (player.getTags().contains("chaos_crystal_carapace")) {
            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                attacker.hurt(player.damageSources().magic(), 2.0f);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.CRIT,
                        attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
                        8, 0.3, 0.5, 0.3, 0.1);
                }
            }
        }
    }

    /** Dimension Judge Annihilate: countdown on tagged entities, kill on expiry. */
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

    /** Stasis tag expiry. */
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

    /** Crystal Carapace timer. */
    @SubscribeEvent
    public static void onCrystalCarapaceTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getTags().contains("chaos_crystal_carapace")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int ticks = player.getPersistentData().getInt("chaos_crystal_ticks");
        if (ticks <= 0) {
            player.removeTag("chaos_crystal_carapace");
            player.getPersistentData().remove("chaos_crystal_ticks");
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        } else {
            player.getPersistentData().putInt("chaos_crystal_ticks", ticks - 1);
            // Keep 90% resistance while active
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 25, 3, true, false));
            if (ticks % 20 == 0) {
                player.heal(1.0f);
                level.sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    8, 0.4, 0.8, 0.4, 0.05);
            }
        }
    }

    /** T026: Centralized despawn ticker — all summoned/managed chaos entities carry
     *  "chaos_managed_entity" tag; one handler covers them all instead of individual checks. */
    @SubscribeEvent
    public static void onEntityDespawnTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        // Unified tag check — replaces the previous 9-item list
        if (!entity.getTags().contains("chaos_managed_entity")
            && !entity.getTags().contains("chaos_mimic_decoy")) return;

        int ticks = entity.getPersistentData().getInt("chaos_despawn_ticks");
        if (ticks <= 0) {
            if (entity.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SMOKE,
                    entity.getX(), entity.getY() + 1.0, entity.getZ(),
                    10, 0.4, 0.5, 0.4, 0.02);
                // Decrement mimic permanent decoy counter when permanent decoy expires
                if (entity.getTags().contains("chaos_mimic_permanent")) {
                    String ownerStr = entity.getPersistentData().getString("chaos_mimic_owner");
                    if (!ownerStr.isEmpty()) {
                        try {
                            var owner = level.getPlayerByUUID(UUID.fromString(ownerStr));
                            if (owner instanceof ServerPlayer sp) {
                                int d = sp.getPersistentData().getInt("chaos_mimic_decoys");
                                if (d > 0) sp.getPersistentData().putInt("chaos_mimic_decoys", d - 1);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            entity.kill();
        } else {
            entity.getPersistentData().putInt("chaos_despawn_ticks", ticks - 1);
        }
    }

    /** Mirror World timer + Blood Blade timer. */
    @SubscribeEvent
    public static void onPlayerTagTimers(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        if (player.getTags().contains("chaos_mirror_world")) {
            int ticks = player.getPersistentData().getInt("chaos_mirror_world_ticks");
            if (ticks <= 0) {
                player.removeTag("chaos_mirror_world");
                player.getPersistentData().remove("chaos_mirror_world_ticks");
            } else {
                player.getPersistentData().putInt("chaos_mirror_world_ticks", ticks - 1);
            }
        }

        if (player.getTags().contains("chaos_blood_blade_active")) {
            int ticks = player.getPersistentData().getInt("chaos_blood_blade_ticks");
            if (ticks <= 0) {
                player.removeTag("chaos_blood_blade_active");
                player.getPersistentData().remove("chaos_blood_blade_ticks");
            } else {
                player.getPersistentData().putInt("chaos_blood_blade_ticks", ticks - 1);
                if (ticks % 5 == 0) {
                    level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                        player.getX(), player.getY() + 0.8, player.getZ(),
                        3, 0.2, 0.3, 0.2, 0.02);
                }
            }
        }

        // Eater of Worlds: full hunger + no XP → regen from own mind
        if (OriginHelper.hasPower(player, "chaos_addon:eater_of_worlds/hunger_of_infinity")) {
            if (player.getFoodData().getFoodLevel() >= 20
                    && player.experienceLevel <= 0
                    && player.tickCount % 100 == 0) {
                if (player.experienceLevel >= 1) player.giveExperienceLevels(-1);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));
            }
        }
    }

    /** Blood Blade: attack leaves blood cloud at target. */
    @SubscribeEvent
    public static void onBloodBladeAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getTags().contains("chaos_blood_blade_active")) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
            target.getX(), target.getY() + 0.5, target.getZ(),
            20, 0.6, 0.4, 0.6, 0.05);

        target.addTag("chaos_blood_cloud");
        target.getPersistentData().putInt("chaos_blood_cloud_ticks", 60);
    }

    // ── Cardinal direction helper for Seismic Sense ───────────────────────────
    private static String getCardinalDirection(net.minecraft.world.entity.Entity from, net.minecraft.world.entity.Entity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "→ В";
        if (angle < 67.5)  return "↘ ЮВ";
        if (angle < 112.5) return "↓ Ю";
        if (angle < 157.5) return "↙ ЮЗ";
        if (angle < 202.5) return "← З";
        if (angle < 247.5) return "↖ СЗ";
        if (angle < 292.5) return "↑ С";
        return "↗ СВ";
    }

    /** Blood cloud tick: deal 1 HP/s + Slowness II to entities in cloud. */
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
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
                if (entity.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        8, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
    }
}
