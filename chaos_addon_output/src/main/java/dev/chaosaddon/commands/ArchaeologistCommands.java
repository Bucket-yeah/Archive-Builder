package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.data.TimeData;
import dev.chaosaddon.init.ModAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** chaos_addon_cache_memory, chaos_addon_defrag, chaos_addon_chunk_scan */
public class ArchaeologistCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Cache Memory: save → restore state
        dispatcher.register(Commands.literal("chaos_addon_cache_memory")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                TimeData data = player.getData(ModAttachments.TIME_DATA);

                if (!data.hasSave()) {
                    net.minecraft.nbt.CompoundTag _pnbt = new net.minecraft.nbt.CompoundTag();
                    player.saveWithoutId(_pnbt);
                    net.minecraft.nbt.ListTag inv = _pnbt.getList("Inventory", 10);
                    data.save(inv, player.getHealth(), player.getFoodData().getFoodLevel(),
                        player.getX(), player.getY(), player.getZ());

                    player.sendSystemMessage(Component.literal("§a§lСостояние сохранено!"));
                    level.sendParticles(ParticleTypes.ENCHANT,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        25, 0.5, 1.0, 0.5, 0.1);
                    level.playSound(null, player.blockPosition(),
                        SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 1.3f);
                } else {
                    net.minecraft.nbt.ListTag inv = data.savedInventory();
                    if (inv != null) player.getInventory().load(inv);
                    player.setHealth(data.savedHealth());
                    player.getFoodData().eat(data.savedFood() - player.getFoodData().getFoodLevel(), 0.0f);
                    player.teleportTo(data.savedX(), data.savedY(), data.savedZ());
                    data.clear();

                    player.sendSystemMessage(Component.literal("§b§lСостояние восстановлено!"));
                    level.sendParticles(ParticleTypes.GLOW,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        30, 0.5, 1.0, 0.5, 0.0);
                    level.playSound(null, player.blockPosition(),
                        SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0f, 1.2f);
                }
                return 1;
            }));

        // Defrag: kill all non-player entities in current chunk
        dispatcher.register(Commands.literal("chaos_addon_defrag")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos pos = player.blockPosition();

                LevelChunk chunk = level.getChunkAt(pos);
                AABB chunkBounds = new AABB(
                    chunk.getPos().getMinBlockX(), level.getMinBuildHeight(), chunk.getPos().getMinBlockZ(),
                    chunk.getPos().getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getPos().getMaxBlockZ() + 1
                );
                level.getEntitiesOfClass(LivingEntity.class, chunkBounds,
                    e -> e != player && e.isAlive())
                    .forEach(LivingEntity::kill);

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                            BlockPos bp = new BlockPos(chunk.getPos().getMinBlockX() + x, y,
                                chunk.getPos().getMinBlockZ() + z);
                            net.minecraft.world.level.block.state.BlockState bs = level.getBlockState(bp);
                            if (bs.getBlock() instanceof net.minecraft.world.level.block.TorchBlock) {
                                level.removeBlock(bp, false);
                            }
                        }
                    }
                }

                level.playSound(null, pos, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 1.0f, 0.8f);
                player.sendSystemMessage(Component.literal("§c§lЧанк дефрагментирован!"));
                return 1;
            }));

        // Chunk Scanner: shows chunk info in chat
        dispatcher.register(Commands.literal("chaos_addon_chunk_scan")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                LevelChunk chunk = level.getChunkAt(player.blockPosition());

                AABB chunkBounds = new AABB(
                    chunk.getPos().getMinBlockX(), level.getMinBuildHeight(), chunk.getPos().getMinBlockZ(),
                    chunk.getPos().getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getPos().getMaxBlockZ() + 1
                );
                int entityCount = level.getEntitiesOfClass(LivingEntity.class, chunkBounds,
                    e -> true).size();

                player.sendSystemMessage(Component.literal(String.format(
                    "§e§lЧанк: [%d, %d] | Мобов: %d | Загружен: %s",
                    chunk.getPos().x, chunk.getPos().z,
                    entityCount,
                    level.isPositionEntityTicking(player.blockPosition()) ? "§aДа" : "§cНет"
                )));
                level.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 2.0, player.getZ(),
                    15, 0.3, 0.5, 0.3, 0.05);
                return 1;
            }));
    }
}
