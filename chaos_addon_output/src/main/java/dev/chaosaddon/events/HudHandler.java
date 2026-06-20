package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Actionbar HUD for all 23 Chaos Addon origins.
 * Updates every 20 ticks (1 second).
 * Format: §ICON Resource: VALUE  §8│  §e[G]§7 Skill1  §e[V]§7 Skill2  §e[B]§7 Skill3
 */
public class HudHandler {

    private static final String SEP = " §8│ ";

    // Moon phase names (index 0-7)
    private static final String[] MOON = {
        "§e☀ Полная", "§7Убывающая", "§7Пол.убыв.", "§7Серп", 
        "§8Новолуние", "§7Серп", "§7Пол.раст.", "§7Растущая"
    };

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % 20 != 0) return;

        Component hud = buildHud(player, level);
        if (hud != null) {
            player.displayClientMessage(hud, true);
        }
    }

    private static Component buildHud(ServerPlayer player, ServerLevel level) {
        // ──────── BLOOD SMITH ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:blood_smith")) {
            int charges = BloodSmithHandler.getCharges(player);
            int max = 100;
            String bar = buildBar(charges, max, 10, "§c", "§8");
            boolean low = player.getHealth() <= 4.0f;
            String bonus = low ? " §4⬇ HP<3❤ → Скидка 50%!" : "";
            return line("§c🩸", "Заряды: " + charges + "/" + max + "  " + bar + bonus,
                "§e[G]§7 Голем", "§e[V]§7 Клинок", "§e[B]§7 Жертва");
        }

        // ──────── SWARM LORD ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:swarm_lord")) {
            int bugs = player.getData(ModAttachments.SWARM_DATA).bugUUIDs().size();
            int maxBugs = ChaosAddonConfig.get().swarmMaxBugs;
            String bar = buildBar(bugs, maxBugs, 8, "§6", "§8");
            return line("§6🐝", "Рой: " + bugs + "/" + maxBugs + "  " + bar,
                "§e[G]§7 Атака", "§e[V]§7 Крепость", "§e[B]§7 Щит");
        }

        // ──────── CHAOS ENGINEER ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:chaos_engineer")) {
            int energy = player.getPersistentData().getInt("chaos_engineer_energy");
            String bar = buildBar(energy, 64, 8, "§b", "§8");
            String hint = energy < 8 ? " §cМало для голема" : energy < 16 ? " §eМало для перегрузки" : " §a✓";
            return line("§b⚡", "Энергия: " + energy + "/64  " + bar + hint,
                "§e[G]§7 Ред.→HP", "§e[V]§7 Перегрузка", "§e[B]§7 Голем");
        }

        // ──────── NECROVORE ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:necrovore")) {
            int souls = player.getPersistentData().getInt("chaos_soul_count");
            int maxSouls = 50;
            String bar = buildBar(souls, maxSouls, 8, "§5", "§8");
            return line("§5💀", "Души: " + souls + "/" + maxSouls + "  " + bar,
                "§e[G]§7 Армия", "§e[V]§7 Некровзрыв", "§e[B]§7 Апокалипсис");
        }

        // ──────── LUNAR RENEGADE ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:lunar_renegade")) {
            int moon = level.getMoonPhase();
            String phase = MOON[moon % 8];
            boolean isDay = level.isDay();
            String dayInfo = isDay ? "  §c☀ Дн." : "  §b🌙 Ночь";
            return line("§b🌙", "Луна: " + phase + dayInfo,
                "§e[G]§7 Щит", "§e[V]§7 Рывок", "§e[B]§7 Затмение");
        }

        // ──────── NEURAL HIJACKER ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:neural_hijacker")) {
            int hosts = NeuralHijackerHandler.getHosts(player.getUUID()).size();
            int maxHosts = 3;
            String bar = buildBar(hosts, maxHosts, 6, "§a", "§8");
            String regen = hosts > 0 ? "  §a↑Реген" : "  §c↓Умирает";
            return line("§a🧠", "Хозяева: " + hosts + "/" + maxHosts + "  " + bar + regen,
                "§e[G]§7 Захват", "§e[V]§7 Взрыв", "§e[B]§7 Улей");
        }

        // ──────── PARASITIC MIND ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:parasitic_mind")) {
            int infected = player.getData(ModAttachments.PARASITE_DATA).infectedUUIDs().size();
            String bar = buildBar(infected, 5, 5, "§2", "§8");
            return line("§2🦠", "Заражено: " + infected + "/5  " + bar,
                "§e[G]§7 Заразить", "§e[V]§7 Команда", "§e[B]§7 Коллектив");
        }

        // ──────── PHANTOM ARCHAEOLOGIST ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:phantom_archaeologist")) {
            boolean hasBackup = player.getPersistentData().getBoolean("chaos_arch_has_save");
            int savedXp = player.getPersistentData().getInt("chaos_arch_total_xp");
            String status = hasBackup
                ? "§a✓ Резерв XP:" + savedXp
                : "§c✗ Нет резерва";
            return line("§6🏺", status,
                "§e[G]§7 Инв.ящик", "§e[V]§7 Рез.копия", "§e[B]§7 —");
        }

        // ──────── STAR ORACLE ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:star_oracle")) {
            boolean isNight = !level.isDay();
            String timeInfo = isNight ? "§e⭐ Ночь — усилен" : "§7☀ День — ослаблен";
            int meteors = player.getPersistentData().getInt("chaos_star_pending_meteors");
            String meteorInfo = meteors > 0 ? "  §c⬇" + meteors + " метеор(а)" : "";
            return line("§e⭐", timeInfo + meteorInfo,
                "§e[G]§7 Метеор", "§e[V]§7 Страж", "§e[B]§7 Апокалипсис");
        }

        // ──────── MIRROR PHANTOM ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:mirror_phantom")) {
            boolean mirror = player.getTags().contains("chaos_mirror_world");
            boolean stolen = player.getPersistentData().getInt("chaos_stolen_identity_ticks") > 0;
            String status = mirror ? "§d🪞 Зеркало!" : stolen ? "§5👤 Личность украдена" : "§7Обычно";
            return line("§d🪞", status,
                "§e[G]§7 Копия", "§e[V]§7 Зеркало", "§e[B]§7 Личность");
        }

        // ──────── ALCHEMICAL MONK ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:alchemical_monk")) {
            boolean imbalanced = OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/material_imbalance");
            String status = imbalanced ? "§6⚗ Дисбаланс активен" : "§7Баланс";
            return line("§6⚗", status,
                "§e[G]§7 Трансмут.", "§e[V]§7 Цена Созд.", "§e[B]§7 Цикл");
        }

        // ──────── BIOMORPH ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:biomorph")) {
            Holder<Biome> biomeHolder = level.getBiome(player.blockPosition());
            String biome = biomeHolder.unwrapKey()
                .map(k -> k.location().getPath().replace("_", " "))
                .orElse("Неизвестно");
            biome = capitalize(biome);
            return line("§2🌿", "Биом: §a" + biome,
                "§e[G]§7 Смена биома", "§e[V]§7 Адаптация", "§e[B]§7 —");
        }

        // ──────── DEEP GEOMANCER ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:deep_geomancer")) {
            BlockPos pos = player.blockPosition();
            boolean underground = pos.getY() < 60;
            String loc = underground ? "§a↓ Под землёй — усилен" : "§7↑ На поверхности";
            return line("§8⛏", loc,
                "§e[G]§7 Землетряс.", "§e[V]§7 Сейсм.пульс", "§e[B]§7 Каменный ком.");
        }

        // ──────── TIME WANDERER ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:time_wanderer")) {
            long dayTime = level.getDayTime() % 24000;
            String timeStr = String.format("День %d, %02d:%02d",
                level.getGameTime() / 24000 + 1,
                (int)(dayTime / 1000 + 6) % 24,
                (int)(dayTime % 1000 * 60 / 1000));
            return line("§3⏰", timeStr,
                "§e[G]§7 Дежавю", "§e[V]§7 Стазис", "§e[B]§7 —");
        }

        // ──────── EATER OF WORLDS ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:eater_of_worlds")) {
            int food = player.getFoodData().getFoodLevel();
            int xp = player.totalExperience;
            return line("§4🌑", "Еда: " + food + "/20  §bXP: " + xp,
                "§e[G]§7 Шёпот", "§e[V]§7 Поглощение", "§e[B]§7 Замедление");
        }

        // ──────── RADIOACTIVE PHANTOM ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:radioactive_phantom")) {
            int kills = player.getPersistentData().getInt("chaos_radio_kills");
            String status = kills >= 5 ? "§a⚡ Стек x" + kills + " — ВЗРЫВ!" : "§7☢ Стек: " + kills + "/5";
            return line("§2☢", status,
                "§e[G]§7 Рад.импульс", "§e[V]§7 Полураспад", "§e[B]§7 —");
        }

        // ──────── INFERNAL SHEPHERD ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:infernal_shepherd")) {
            boolean inNether = level.dimension().equals(net.minecraft.world.level.Level.NETHER);
            String loc = inNether ? "§c🔥 Незер — дом" : "§7Не в незере (-сила)";
            return line("§c🔥", loc,
                "§e[G]§7 Призвать", "§e[V]§7 Огн.барьер", "§e[B]§7 —");
        }

        // ──────── WANDERING GARDENER ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:wandering_gardener")) {
            int plants = countNearbyPlants(player, level);
            String status = plants > 5 ? "§a🌱 " + plants + " растений — усилен" : "§7🌱 " + plants + " растений";
            return line("§a🌿", status,
                "§e[G]§7 Лесник", "§e[V]§7 Цвет.взрыв", "§e[B]§7 Суперрост");
        }

        // ──────── DEEP NAVIGATOR ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:deep_navigator")) {
            String dimsRaw = player.getPersistentData().getString("chaos_navigator_dims");
            int portalCount = dimsRaw.isEmpty() ? 0 : dimsRaw.split(",").length;
            String dim = level.dimension().location().getPath();
            return line("§9🗺", "Порталов: " + portalCount + "  §7Измерение: §e" + dim,
                "§e[G]§7 Дим.прыжок", "§e[V]§7 Карта", "§e[B]§7 —");
        }

        // ──────── NIGHTMARE MIMIC ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:nightmare_mimic")) {
            boolean illusory = OriginHelper.hasPower(player, "chaos_addon:nightmare_mimic/illusory_flesh");
            return line("§5👁", illusory ? "§5Иллюзорная плоть" : "§7Мимик",
                "§e[G]§7 Иллюзия", "§e[V]§7 —", "§e[B]§7 —");
        }

        // ──────── ANCIENT SENTINEL ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:ancient_sentinel")) {
            int y = player.blockPosition().getY();
            String depthInfo = y < 0 ? "§6Глубина §e" + Math.abs(y) + "м — усилен" : "§7Высота §e" + y + "м";
            return line("§6🗿", depthInfo,
                "§e[G]§7 Жидк.урон", "§e[V]§7 Каменная форма", "§e[B]§7 —");
        }

        // ──────── DIMENSION JUDGE ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:dimension_judge")) {
            String dim = level.dimension().location().getPath();
            return line("§d⚖", "Измерение: §e" + dim,
                "§e[G]§7 Законность", "§e[V]§7 Реальн.сдвиг", "§e[B]§7 Уничтожение");
        }

        // ──────── MYCELIAL SYMBIONT ────────
        if (OriginHelper.hasOrigin(player, "chaos_addon:mycelial_symbiont")) {
            boolean inMushroom = level.getBiome(player.blockPosition()).unwrapKey()
                .map(k -> k.location().getPath().contains("mushroom")).orElse(false);
            String status = inMushroom ? "§a🍄 Биом грибов — бонус!" : "§7🍄 Симбионт";
            return line("§a🍄", status,
                "§e[G]§7 Мицелий", "§e[V]§7 Споры", "§e[B]§7 Симбиоз");
        }

        return null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Build a coloured bar: ▮▮▮▯▯▯ */
    private static String buildBar(int value, int max, int bars, String filledColor, String emptyColor) {
        int filled = max > 0 ? Math.min(bars, value * bars / max) : 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? filledColor + "▮" : emptyColor + "▯");
        }
        return sb.toString();
    }

    /** Assemble one actionbar line: Icon + info | [G] Skill [V] Skill [B] Skill */
    private static Component line(String icon, String info, String g, String v, String b) {
        String text = icon + " §f" + info + SEP + g + SEP + v + SEP + b;
        return Component.literal(text);
    }

    /** Count plant/crop blocks in a 5-block radius (for Wandering Gardener) */
    private static int countNearbyPlants(ServerPlayer player, ServerLevel level) {
        int count = 0;
        BlockPos origin = player.blockPosition();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    var state = level.getBlockState(origin.offset(dx, dy, dz));
                    if (state.is(net.minecraft.tags.BlockTags.CROPS)
                        || state.is(net.minecraft.tags.BlockTags.SAPLINGS)
                        || state.is(net.minecraft.tags.BlockTags.FLOWERS)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
