package dev.chaosaddon.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

@Config(name = "chaos_addon")
public class ChaosAddonConfig implements ConfigData {

    public static ChaosAddonConfig INSTANCE;

    // ───────────────────────────── EATER OF WORLDS ─────────────────────────────
    @ConfigEntry.Category("eater_of_worlds")
    @ConfigEntry.Gui.Tooltip
    public int   eaterChaoticAuraInterval   = 600;
    public float eaterChaoticAuraChance     = 0.20f;
    public int   eaterChaoticAuraRadius     = 10;
    public int   eaterRealityShiftCooldown  = 2400;
    public int   eaterAbyssSummonCooldown   = 2400;
    public int   eaterTimeLoopCooldown      = 3600;
    public int   eaterXpCostPerAbility      = 5;
    public float eaterSunDamage             = 0.5f;
    public int   eaterSunDamageInterval     = 100;
    public float eaterSunBonusPerEffect     = 0.1f; // extra damage per active mob effect

    // ───────────────────────────── SWARM LORD ──────────────────────────────────
    @ConfigEntry.Category("swarm_lord")
    public int   swarmSpawnInterval         = 400;
    public int   swarmMaxBugs               = 10;
    public float swarmBugDamage             = 0.5f;
    public int   swarmAttackCooldown        = 600;
    public int   swarmHiveCooldown          = 3600;
    public int   swarmShieldCooldown        = 1200;
    public float swarmShieldHp              = 10.0f;
    public int   swarmShieldDuration        = 300;
    public float swarmWaterBugDamage        = 1.0f;
    public float swarmFireDamageMult        = 2.0f;

    // ───────────────────────────── DEEP NAVIGATOR ──────────────────────────────
    @ConfigEntry.Category("deep_navigator")
    public int   navPortalRadius            = 200;
    public int   navRiftCooldown            = 1200;
    public int   navAnchorCooldown          = 4800;
    public int   navVoidPortalCooldown      = 3600;
    public float navDecayDamage             = 0.5f;
    public int   navDecayInterval           = 600;
    public int   navDecayPortalRange        = 100;
    /** Slow passive regen (Regen 0 duration) when far from portal — replaces pure damage. */
    public int   navFarRegenDuration        = 80;  // ticks of Regen I (0.5❤ per 2.5 sec)

    // ───────────────────────────── ALCHEMICAL MONK ─────────────────────────────
    @ConfigEntry.Category("alchemical_monk")
    public int   monkGoldTouchCooldown      = 200;
    public int   monkPhilExplosionCooldown  = 2400;
    public int   monkTransmuteCooldown      = 1200;
    public float monkCraftDamageCost        = 1.0f;
    public float monkResourceChance         = 0.05f;
    public float monkTransmuteHpCost        = 4.0f;

    // ───────────────────────────── PHANTOM ARCHAEOLOGIST ───────────────────────
    @ConfigEntry.Category("phantom_archaeologist")
    public int   archCacheMemoryCooldown    = 300;
    public int   archDefragCooldown         = 6000;
    public int   archScannerCooldown        = 1200;
    public float archSoundDamage            = 0.5f;

    // ───────────────────────────── TIME WANDERER ───────────────────────────────
    @ConfigEntry.Category("time_wanderer")
    public int   timeAccelCooldown          = 1200;
    public int   timeDominionCooldown       = 4800; // merged stasis+time_loop
    public int   timeEchoCooldown           = 2400; // new temporal echo
    public int   timeInstabilityInterval    = 200;
    public int   timeRewindWindow           = 600;
    public int   timeXpLossOnRewind         = 10;

    // ───────────────────────────── BIOMORPH ────────────────────────────────────
    @ConfigEntry.Category("biomorph")
    public int   bioShiftCooldown           = 3600;
    public int   bioBurstCooldown           = 1200;
    public int   bioCaptureCooldown         = 2400;
    public float bioTransitionDamage        = 1.0f;
    public float bioImprintBonus            = 0.05f;
    public int   bioImprintTime             = 6000;

    // ───────────────────────────── PARASITIC MIND ──────────────────────────────
    @ConfigEntry.Category("parasitic_mind")
    public int   paraInfectCooldown         = 400;
    public int   paraInfectDuration         = 1200;
    public int   paraMaxTargets             = 3;
    public int   paraHivemindCooldown       = 1800;
    public int   paraExplosionCooldown      = 2400;
    public float paraWithdrawalDamage       = 5.0f;

    // ───────────────────────────── CHAOS ENGINEER ──────────────────────────────
    @ConfigEntry.Category("chaos_engineer")
    public int   engOverloadCooldown        = 600;
    public int   engGolemCooldown           = 2400;
    public int   engPulseCooldown           = 1200;
    public float engRedstoneFoodValue       = 2.0f;
    public float engNoRedstoneMultiplier    = 1.5f; // was 2.0 — reduced (item 22 fix)

    // ───────────────────────────── LUNAR RENEGADE ──────────────────────────────
    @ConfigEntry.Category("lunar_renegade")
    public int   lunarDashCooldown          = 300;
    public int   lunarEclipseCooldown       = 6000;
    public int   lunarShieldCooldown        = 1200;
    public float lunarShieldHp              = 8.0f;
    public float lunarShieldReflect         = 0.5f;
    public float lunarSunDamage             = 0.5f;
    public int   lunarSunDamageInterval     = 100;
    public float lunarDaySpeedPenalty       = -0.40f;

    // ───────────────────────────── INFERNAL SHEPHERD ───────────────────────────
    @ConfigEntry.Category("infernal_shepherd")
    public int   inferGhastCooldown         = 1200;
    public int   inferLavaBurstCooldown     = 600;
    public int   inferPortalCooldown        = 3600;
    public float inferLavaBurstDamage       = 4.0f;
    public int   inferLavaBurstRadius       = 5;

    // ───────────────────────────── NIGHTMARE MIMIC ─────────────────────────────
    @ConfigEntry.Category("nightmare_mimic")
    public int   mimicNightmareCooldown     = 400;
    public int   mimicRealityCooldown       = 2400;
    public int   mimicIllusionCooldown      = 3600;
    public float mimicPhantomChance         = 0.40f;
    public int   mimicPhantomCount          = 5;

    // ───────────────────────────── ANCIENT SENTINEL ────────────────────────────
    @ConfigEntry.Category("ancient_sentinel")
    public int   sentinelQuakeCooldown      = 1200;
    public int   sentinelCrystalCooldown    = 2400;
    public int   sentinelEarthquakeCooldown = 3600;
    public float sentinelQuakeDamage        = 3.0f;
    public float sentinelEqDamage           = 5.0f;
    public int   sentinelCrystalDuration    = 400;
    /** Gradual liquid damage (replaces old 4.0f/10t instant death). */
    public float sentinelLiquidDamage       = 0.5f; // 0.25 hearts per tick cycle
    public int   sentinelLiquidInterval     = 60;   // every 3 seconds — escapable

    // ───────────────────────────── RADIOACTIVE PHANTOM ─────────────────────────
    @ConfigEntry.Category("radioactive_phantom")
    public int   radioDecayWaveCooldown     = 1200;
    public int   radioJumpCooldown          = 300;
    public int   radioMutagenCooldown       = 2400;
    public float radioAuraDamage            = 0.5f;
    public int   radioAuraInterval          = 100;
    public int   radioAuraRadius            = 5;
    public float radioDayWaveDamage         = 6.0f;

    // ───────────────────────────── DIMENSION JUDGE ─────────────────────────────
    @ConfigEntry.Category("dimension_judge")
    public int   judgeSentenceCooldown      = 600;
    public int   judgeAnnihilateCooldown    = 6000;
    public float judgeAnnihilatePercent     = 0.40f;
    public int   judgeBalanceCooldown       = 3600;
    public int   judgeConfiscationCooldown  = 1200;
    public int   judgeHigherJudgmentCooldown= 6000;
    public float judgeHigherJudgmentRadius  = 15.0f;
    public float judgeHigherJudgmentDmgPerHit = 2.0f;

    // ───────────────────────────── MYCELIAL SYMBIONT ───────────────────────────
    @ConfigEntry.Category("mycelial_symbiont")
    public int   mossHarvestCooldown        = 600;
    public int   mossGrowNodeCooldown       = 600;
    public int   mossTeleportCooldown       = 1800;
    public int   mossFogCooldown            = 1200;
    public int   mossMaxRange               = 20;
    public float mossDetachDamage           = 0.5f;
    public int   mossDetachInterval         = 20;

    // ───────────────────────────── DEEP GEOMANCER ──────────────────────────────
    @ConfigEntry.Category("deep_geomancer")
    public int   geoExtractCooldown         = 200;
    public int   geoShiftCooldown           = 1200;
    public int   geoFistCooldown            = 600;
    public float geoExtractHpCost           = 1.0f;
    public float geoFistDamage              = 4.0f;
    public int   geoFistKnockback           = 5;
    public int   geoHeightLimit             = 80;
    public float geoHighAltitudeDamage      = 1.0f;
    public int   geoHighAltitudeInterval    = 600;
    public int   geoUltimateCooldown        = 6000;
    public int   geoClawCooldown            = 160;
    public float geoClawBonusDamage         = 3.0f;
    public int   geoDashCooldown            = 400;
    public int   geoDashMaxThickness        = 8;

    // ───────────────────────────── WANDERING GARDENER ──────────────────────────
    @ConfigEntry.Category("wandering_gardener")
    public int   gardenGrowthCooldown       = 600;
    public int   gardenRainCooldown         = 3600;
    public int   gardenDryadCooldown        = 2400;
    public int   gardenMaxTamed             = 5;
    public float gardenWoodcutDamage        = 3.0f;
    public float gardenBadBiomeDamage       = 0.5f;
    public int   gardenBadBiomeInterval     = 200;
    public int   gardenLifeBloomCooldown    = 6000;

    // ══════════════════ v3.0.0 NEW RACES (previously unconfigurable) ══════════

    // ───────────────────────────── NECROVORE ───────────────────────────────────
    @ConfigEntry.Category("necrovore")
    public int   necroDeadLegionCooldown    = 2400;
    public int   necroDevourSoulCooldown    = 600;
    public int   necroRaiseCooldown         = 1800;
    public int   necroMaxUndead             = 5;
    public float necroSoulDropChance        = 0.30f;
    public float necroDeathFeastHeal        = 2.0f; // HP healed on kill
    public int   necroSoulDropXp            = 5;

    // ───────────────────────────── NEURAL HIJACKER ─────────────────────────────
    @ConfigEntry.Category("neural_hijacker")
    public int   neuralHivemindCooldown     = 1800;
    public int   neuralSeizureCooldown      = 600;
    public int   neuralHiveBurstCooldown    = 3600;
    public int   neuralMaxHosts             = 1;    // quality over quantity vs parasitic_mind
    public int   neuralHostDuration         = 2400; // ticks host control lasts
    public float neuralMemoryTheftChance    = 0.50f;
    public float neuralParasiticBodyDamage  = 0.5f; // no-armor penalty per 30s

    // ───────────────────────────── BLOOD SMITH ─────────────────────────────────
    @ConfigEntry.Category("blood_smith")
    public int   bloodBladeCooldown         = 300;
    public int   bloodGolemCooldown         = 2400;
    public int   bloodBankCooldown          = 1200;
    public int   bloodSacrificeCooldown     = 600;
    public int   bloodMaxCharges            = 10;
    public float bloodArmorPerCharge        = 0.5f; // armor points per blood charge
    public float bloodBladeHpCost           = 1.0f; // HP to activate blade
    public int   bloodRegenInterval         = 200;  // natural regen blocked; timer for check

    // ───────────────────────────── STAR ORACLE ─────────────────────────────────
    @ConfigEntry.Category("star_oracle")
    public int   starMeteorCooldown         = 1200;
    public int   starApocalypseCooldown     = 12000;
    public int   starForesightCooldown      = 600;
    public float starMeleeVulnerability     = 1.5f; // incoming melee dmg multiplier
    public float starAuraDamage             = 0.5f;
    public int   starAuraInterval           = 100;
    public int   starAuraRadius             = 8;
    public int   starGuardianCooldown       = 3600;

    // ───────────────────────────── MIRROR PHANTOM ──────────────────────────────
    @ConfigEntry.Category("mirror_phantom")
    public int   mirrorDisguiseCooldown     = 1200;
    public int   mirrorCopyCooldown         = 2400;
    public int   mirrorWorldCooldown        = 6000;
    public int   mirrorDeathEchoCooldown    = 3600;
    public float mirrorSunVulnerability     = 1.5f; // sun damage multiplier
    public float mirrorFragileFormMult      = 1.3f; // all incoming damage multiplier
    public int   mirrorIdentityDuration     = 1200; // ticks stolen identity lasts

    // ─────────────────────────────────────────────────────────────────────────

    public static void onLoad(FMLLoadCompleteEvent event) {
        AutoConfig.register(ChaosAddonConfig.class, GsonConfigSerializer::new);
        INSTANCE = AutoConfig.getConfigHolder(ChaosAddonConfig.class).getConfig();
    }

    public static ChaosAddonConfig get() {
        return INSTANCE != null ? INSTANCE : new ChaosAddonConfig();
    }
}
