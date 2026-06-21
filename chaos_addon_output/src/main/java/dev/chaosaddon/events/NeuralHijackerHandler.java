package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * Handles Neural Hijacker passives (spec-accurate):
 * - parasitic_body: max HP = 10❤, regen 1❤/10s near host, -0.5❤/20s without host
 * - memory_theft: copy a random effect from the attacked entity (15s)
 * - neural_network: hosts glow through walls, particles
 */
public class NeuralHijackerHandler {

    /** player UUID → set of infected entity UUIDs */
    static final Map<UUID, Set<UUID>> HIJACKED = new HashMap<>();
    /** player UUID → infection expiry game time */
    static final Map<UUID, Map<UUID, Long>> HIJACK_EXPIRY = new HashMap<>();

    private static final Random RNG = new Random();

    // ── Per-tick passives ──────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:neural_hijacker/neural_network")) return;

        UUID pid = player.getUUID();
        Set<UUID> hosts = HIJACKED.computeIfAbsent(pid, k -> new HashSet<>());
        Map<UUID, Long> expiries = HIJACK_EXPIRY.computeIfAbsent(pid, k -> new HashMap<>());
        long now = level.getGameTime();

        // Expire old infections and dead entities
        expiries.entrySet().removeIf(e -> e.getValue() < now);
        hosts.retainAll(expiries.keySet());
        hosts.removeIf(uuid -> {
            var entity = level.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });

        boolean nearHost = false;

        for (UUID uuid : hosts) {
            if (!(level.getEntity(uuid) instanceof LivingEntity host)) continue;
            host.setGlowingTag(true);

            if (player.distanceTo(host) < 20) nearHost = true;

            if (player.tickCount % 5 == 0) {
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    host.getX(), host.getY() + 0.8, host.getZ(),
                    3, 0.3, 0.4, 0.3, 0.02);
            }

            // Weaker neural control: don't fully clear AI, just prevent targeting player
            // and apply Weakness + Slowness (simulates confused, weak host not full puppet)
            if (host instanceof Mob mob) {
                if (mob.getTarget() instanceof ServerPlayer sp && sp == player) {
                    mob.setTarget(null);
                }
            }
            ChaosAddonConfig cfg = ChaosAddonConfig.get();
            if (player.tickCount % cfg.neuralHostDebuffInterval == 0) {
                host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, cfg.neuralHostDebuffDuration, 1, false, false));
                host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, cfg.neuralHostDebuffDuration, 1, false, false));
            }
        }

        // ── Parasitic Body: regen near host, damage without host ──
        if (OriginHelper.hasPower(player, "chaos_addon:neural_hijacker/parasitic_body")) {
            ChaosAddonConfig cfg = ChaosAddonConfig.get();
            if (nearHost && player.tickCount % cfg.neuralHostRegenInterval == 0
                    && player.getHealth() < player.getMaxHealth()) {
                player.heal(cfg.neuralHostRegenAmount);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    5, 0.3, 0.4, 0.3, 0.0);
            }

            if (hosts.isEmpty() && player.tickCount % cfg.neuralStarveInterval == 0) {
                player.hurt(player.damageSources().starve(), cfg.neuralStarveDamage);
                level.sendParticles(ParticleTypes.WITCH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    6, 0.3, 0.5, 0.3, 0.05);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.4f, 0.8f);
            }
        }
    }

    // ── Memory theft: copy random effect from attacked entity ───────────────────
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:neural_hijacker/memory_theft")) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        Collection<MobEffectInstance> effects = target.getActiveEffects();
        if (effects.isEmpty()) return;

        List<MobEffectInstance> effectList = new ArrayList<>(effects);
        MobEffectInstance chosen = effectList.get(RNG.nextInt(effectList.size()));

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        player.addEffect(new MobEffectInstance(
            chosen.getEffect(), cfg.neuralMemoryTheftDuration, chosen.getAmplifier(), false, true));

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.4, 0.5, 0.4, 0.05);
            level.playSound(null, player.blockPosition(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5f, 1.8f);
        }
    }

    // ── Static helpers for commands ────────────────────────────────────────────

    public static boolean canInfect(ServerPlayer player) {
        Set<UUID> hosts = HIJACKED.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        return hosts.size() < ChaosAddonConfig.get().neuralMaxHosts;
    }

    public static boolean infectTarget(ServerPlayer player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) return false;

        UUID pid = player.getUUID();
        Set<UUID> hosts = HIJACKED.computeIfAbsent(pid, k -> new HashSet<>());
        Map<UUID, Long> expiries = HIJACK_EXPIRY.computeIfAbsent(pid, k -> new HashMap<>());

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        if (hosts.size() >= cfg.neuralMaxHosts) return false;

        UUID tid = target.getUUID();
        hosts.add(tid);
        expiries.put(tid, level.getGameTime() + cfg.neuralHostDuration);
        target.setGlowingTag(true);

        // CRITICAL FIX: Clear mob AI goals immediately so the mob never attacks the owner
        if (target instanceof Mob mobInit) {
            mobInit.setTarget(null);
            mobInit.goalSelector.getAvailableGoals().clear();
            mobInit.targetSelector.getAvailableGoals().clear();
        }

        // Boost infected mob's attack damage by 20%
        if (target instanceof Mob mob) {
            var atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) {
                ResourceLocation modId = ResourceLocation.fromNamespaceAndPath("chaos_addon", "hijack_dmg_boost");
                atk.removeModifier(modId);
                atk.addTransientModifier(new AttributeModifier(
                    modId, atk.getBaseValue() * ChaosAddonConfig.get().neuralInfectedAttackBonus,
                    AttributeModifier.Operation.ADD_VALUE));
            }
        }

        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            target.getX(), target.getY() + 1.2, target.getZ(),
            30, 0.5, 0.7, 0.5, 0.05);
        level.sendParticles(ParticleTypes.GLOW,
            target.getX(), target.getY() + 1.0, target.getZ(),
            15, 0.4, 0.6, 0.4, 0.0);
        level.playSound(null, target.blockPosition(),
            SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0f, 0.8f);
        level.playSound(null, target.blockPosition(),
            SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 0.7f, 1.3f);
        return true;
    }

    public static Set<UUID> getHosts(UUID playerUUID) {
        return HIJACKED.computeIfAbsent(playerUUID, k -> new HashSet<>());
    }

    public static int getHostCount(ServerPlayer player) {
        return getHosts(player.getUUID()).size();
    }
}
