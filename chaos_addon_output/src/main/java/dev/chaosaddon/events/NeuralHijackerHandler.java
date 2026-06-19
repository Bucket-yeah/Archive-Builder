package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * Handles Neural Hijacker passives:
 * - memory_theft: copy random effect from attacked entity
 * - neural_network: hosts glow + auto-regen near them
 * - parasitic_body: auto-regen 1 HP every 10s near any host
 */
public class NeuralHijackerHandler {

    /** Maps player UUID -> set of infected entity UUIDs */
    static final Map<UUID, Set<UUID>> HIJACKED = new HashMap<>();
    /** Maps player UUID -> infection expiry game time */
    static final Map<UUID, Map<UUID, Long>> HIJACK_EXPIRY = new HashMap<>();

    private static final Random RNG = new Random();
    private static final int MAX_HOSTS = 3;
    private static final int INFECT_DURATION = 500; // 25s in ticks

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:neural_hijacker/neural_network")) return;

        UUID pid = player.getUUID();
        Set<UUID> hosts = HIJACKED.computeIfAbsent(pid, k -> new HashSet<>());
        Map<UUID, Long> expiries = HIJACK_EXPIRY.computeIfAbsent(pid, k -> new HashMap<>());
        long now = level.getGameTime();

        // Expire old infections
        expiries.entrySet().removeIf(e -> e.getValue() < now);
        hosts.retainAll(expiries.keySet());

        // Remove dead entities
        hosts.removeIf(uuid -> {
            var entity = level.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });

        boolean nearHost = false;
        for (UUID uuid : hosts) {
            if (!(level.getEntity(uuid) instanceof LivingEntity host)) continue;
            host.setGlowingTag(true);

            // Regen near host
            if (player.distanceTo(host) < 20) nearHost = true;

            // Particles on host
            if (player.tickCount % 5 == 0) {
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    host.getX(), host.getY() + 0.8, host.getZ(),
                    3, 0.3, 0.4, 0.3, 0.02);
            }
        }

        // Auto-regen near host
        if (nearHost && player.tickCount % 200 == 0 && player.getHealth() < player.getMaxHealth()) {
            player.heal(1.0f);
        }

        // Actionbar
        if (player.tickCount % 20 == 0) {
            player.displayClientMessage(
                Component.literal("🧠 Хосты: " + hosts.size() + "/" + MAX_HOSTS)
                    .withStyle(net.minecraft.ChatFormatting.GREEN),
                true);
        }
    }

    /** Memory theft: copy a random effect from attacked entity */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:neural_hijacker/memory_theft")) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        Collection<MobEffectInstance> effects = target.getActiveEffects();
        if (effects.isEmpty()) return;

        List<MobEffectInstance> effectList = new ArrayList<>(effects);
        MobEffectInstance chosen = effectList.get(RNG.nextInt(effectList.size()));
        MobEffect effect = chosen.getEffect().value();

        player.addEffect(new MobEffectInstance(chosen.getEffect(), 300, chosen.getAmplifier(), false, true));

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.4, 0.5, 0.4, 0.05);
            level.playSound(null, player.blockPosition(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5f, 1.8f);
        }
    }

    // ---------- Static helpers for NeuralHijackerCommands ----------

    public static boolean canInfect(ServerPlayer player) {
        UUID pid = player.getUUID();
        Set<UUID> hosts = HIJACKED.computeIfAbsent(pid, k -> new HashSet<>());
        return hosts.size() < MAX_HOSTS;
    }

    public static boolean infectTarget(ServerPlayer player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (target.getHealth() / target.getMaxHealth() > 0.60f) return false;

        UUID pid = player.getUUID();
        Set<UUID> hosts = HIJACKED.computeIfAbsent(pid, k -> new HashSet<>());
        Map<UUID, Long> expiries = HIJACK_EXPIRY.computeIfAbsent(pid, k -> new HashMap<>());

        if (hosts.size() >= MAX_HOSTS) return false;

        UUID tid = target.getUUID();
        hosts.add(tid);
        expiries.put(tid, level.getGameTime() + INFECT_DURATION);
        target.setGlowingTag(true);

        // Boost infected's damage
        if (target instanceof Mob mob) {
            var atk = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (atk != null) {
                net.minecraft.resources.ResourceLocation modId =
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chaos_addon", "hijack_dmg_boost");
                atk.removeModifier(modId);
                atk.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    modId, atk.getBaseValue() * 0.2,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // FX
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
}
