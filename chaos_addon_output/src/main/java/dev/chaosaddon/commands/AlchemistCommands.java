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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** chaos_addon_golden_touch, chaos_addon_phil_blast, chaos_addon_transmute_flesh */
public class AlchemistCommands {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Golden Touch: block at look → gold ore for 30s
        dispatcher.register(Commands.literal("chaos_addon_golden_touch")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                Vec3 look = player.getLookAngle();
                BlockPos target = null;
                for (int step = 1; step <= 10; step++) {
                    Vec3 pt = player.getEyePosition().add(look.scale(step));
                    BlockPos pos = new BlockPos((int) pt.x, (int) pt.y, (int) pt.z);
                    if (!level.getBlockState(pos).isAir()) { target = pos; break; }
                }
                if (target == null) return 0;

                var prevState = level.getBlockState(target);
                if (prevState.isAir() || prevState.liquid()) return 0;

                level.setBlock(target, Blocks.GOLD_ORE.defaultBlockState(), 3);

                // Schedule revert after 600 ticks (30 sec) via command
                final BlockPos finalTarget = target;
                level.getServer().schedule(new net.minecraft.util.thread.ReentrantBlockableEventLoop.ScheduledTask(() ->
                    level.setBlock(finalTarget, prevState, 3)) {});

                level.sendParticles(ParticleTypes.GLOW,
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                    20, 0.5, 0.5, 0.5, 0.0);
                level.playSound(null, target, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 0.8f, 1.3f);
                return 1;
            }));

        // Philosopher's Blast: consume all materials → valuable item
        dispatcher.register(Commands.literal("chaos_addon_phil_blast")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                // Consume all non-armor, non-tool items from inventory
                var inv = player.getInventory();
                int consumed = 0;
                for (int i = 0; i < 36; i++) {
                    if (!inv.getItem(i).isEmpty()) {
                        inv.removeItem(i, inv.getItem(i).getCount());
                        consumed++;
                    }
                }

                // Result based on how much was consumed
                ItemStack result;
                if (consumed >= 15) {
                    result = new ItemStack(Items.NETHERITE_SCRAP);
                } else if (consumed >= 8) {
                    result = new ItemStack(Items.DIAMOND, 3);
                } else {
                    result = new ItemStack(Items.EMERALD, 5);
                }
                player.getInventory().add(result);

                level.sendParticles(ParticleTypes.EXPLOSION,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    5, 0.5, 0.5, 0.5, 0.1);
                level.sendParticles(ParticleTypes.FIREWORK,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    30, 0.8, 1.0, 0.8, 0.15);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 0.7f);
                return 1;
            }));

        // Flesh Transmutation: sacrifice HP → diamonds or netherite
        dispatcher.register(Commands.literal("chaos_addon_transmute_flesh")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                if (player.getHealth() < 6.0f) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cМало HP! Нужно > 3❤."));
                    return 0;
                }
                ServerLevel level = player.serverLevel();

                // Cost: 4 hearts
                player.hurt(player.damageSources().generic(), 4.0f);

                // Result: netherite or diamonds
                ItemStack result;
                if (RNG.nextFloat() < 0.3f) {
                    result = new ItemStack(Items.NETHERITE_INGOT);
                } else {
                    result = new ItemStack(Items.DIAMOND, 2);
                }
                player.getInventory().add(result);

                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    35, 0.8, 1.2, 0.8, 0.1);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.0f, 1.1f);
                return 1;
            }));
    }
}
