package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Random;

/**
 * Handles Necrovore passives:
 * - soul_drop: when a mob dies within 12 blocks, drop a "Soul" item
 * - undead_diplomacy: zombies/skeletons in radius ignore the player
 * - death_feast: rotten flesh gives 6 saturation; kills restore hunger
 * - Soul HP bonus: up to +30 max HP from collected souls
 */
public class NecrovoreHandler {

    private static final Random RNG = new Random();
    private static final ResourceLocation SOUL_HP_MOD =
        ResourceLocation.fromNamespaceAndPath("chaos_addon", "necrovore_soul_hp");

    public static final String SOUL_TAG = "chaos_soul_item";
    private static final int SOUL_RANGE = 12;
    private static final int MAX_SOULS = 30;

    // ---------- Soul drop on mob death ----------
    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof ServerPlayer) return; // not on player death

        LivingEntity dead = event.getEntity();

        for (ServerPlayer player : level.players()) {
            if (!OriginHelper.hasPower(player, "chaos_addon:necrovore/soul_drop")) continue;
            if (player.distanceTo(dead) > SOUL_RANGE) continue;

            // Spawn soul item at death location
            ItemStack soul = createSoulItem();
            ItemEntity ie = new ItemEntity(level,
                dead.getX(), dead.getY() + 0.3, dead.getZ(), soul);
            ie.setPickUpDelay(10);
            level.addFreshEntity(ie);

            // FX
            level.sendParticles(ParticleTypes.SOUL,
                dead.getX(), dead.getY() + 0.8, dead.getZ(),
                10, 0.3, 0.4, 0.3, 0.05);
            level.playSound(null, dead.blockPosition(),
                SoundEvents.SCULK_BLOCK_BREAK, SoundSource.PLAYERS, 0.6f, 1.3f);
            break;
        }
    }

    // ---------- Per-tick passives ----------
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Undead diplomacy
        if (OriginHelper.hasPower(player, "chaos_addon:necrovore/undead_diplomacy")) {
            if (player.tickCount % 40 == 0) {
                level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(20),
                    e -> e.isAlive() && (e instanceof Zombie || e instanceof Skeleton))
                    .forEach(mob -> {
                        if (mob.getTarget() == player) {
                            mob.setTarget(null);
                        }
                        if (player.tickCount % 100 == 0) {
                            level.sendParticles(ParticleTypes.SMOKE,
                                mob.getX(), mob.getY() + 1.5, mob.getZ(),
                                2, 0.2, 0.2, 0.2, 0.01);
                        }
                    });
            }
        }

        // Soul HP bonus — count soul items in inventory, apply attribute
        if (OriginHelper.hasPower(player, "chaos_addon:necrovore/soul_drop")
                && player.tickCount % 20 == 0) {
            int soulCount = countSoulsInInventory(player);
            soulCount = Math.min(soulCount, MAX_SOULS);
            applySoulHpBonus(player, soulCount);

            // Actionbar
            player.displayClientMessage(
                Component.literal("☠ Душ: " + soulCount + "/" + MAX_SOULS +
                    " | +" + soulCount + " HP")
                    .withStyle(ChatFormatting.DARK_PURPLE),
                true);
        }

        // Undead ally despawn ticker
        level.getEntitiesOfClass(Mob.class,
            player.getBoundingBox().inflate(60),
            e -> e.getTags().contains("chaos_necro_raised"))
            .forEach(mob -> {
                int ticks = mob.getPersistentData().getInt("chaos_despawn_ticks");
                if (ticks <= 0) {
                    mob.kill();
                    level.sendParticles(ParticleTypes.SOUL,
                        mob.getX(), mob.getY() + 1.0, mob.getZ(),
                        8, 0.3, 0.5, 0.3, 0.05);
                } else {
                    mob.getPersistentData().putInt("chaos_despawn_ticks", ticks - 1);
                    if (ticks % 20 == 0) {
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            mob.getX(), mob.getY() + 0.5, mob.getZ(),
                            2, 0.2, 0.3, 0.2, 0.02);
                    }
                }
            });
    }

    // ---------- Death feast: kill restores hunger ----------
    @SubscribeEvent
    public static void onKillRestoreHunger(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:necrovore/death_feast")) return;
        player.getFoodData().eat(1, 0.5f);
    }

    // ---------- Helpers ----------
    public static ItemStack createSoulItem() {
        ItemStack soul = new ItemStack(Items.AMETHYST_SHARD);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SOUL_TAG, true);
        soul.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        soul.set(DataComponents.CUSTOM_NAME,
            Component.literal("⚜ Душа Существа")
                .withStyle(ChatFormatting.DARK_PURPLE));
        return soul;
    }

    public static boolean isSoulItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().getBoolean(SOUL_TAG);
    }

    public static int countSoulsInInventory(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (isSoulItem(stack)) count += stack.getCount();
        }
        return count;
    }

    private static void applySoulHpBonus(ServerPlayer player, int soulCount) {
        var instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) return;
        instance.removeModifier(SOUL_HP_MOD);
        if (soulCount > 0) {
            instance.addTransientModifier(new AttributeModifier(
                SOUL_HP_MOD, soulCount, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
