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
    public int   swarmWaterVulnInterval     = 20;   // ticks between water damage checks
    public float swarmWaterVulnDamage       = 0.5f; // HP per interval in water

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
    public float monkTransmuteHpCost        = 3.0f;     // HP cost per transmutation
    public float monkTransmuteHpThreshold   = 4.0f;     // min HP to allow transmutation
    public int   monkReactionCooldown       = 200;      // ticks between potion-block reactions
    public float monkOverloadBonusPerEffect = 0.20f;    // damage bonus per active potion effect
    public float monkOverloadMaxBonus       = 1.50f;    // cap on overloaded_damage bonus (150%)

    // ───────────────────────────── PHANTOM ARCHAEOLOGIST ───────────────────────
    @ConfigEntry.Category("phantom_archaeologist")
    public int   archCacheMemoryCooldown    = 300;
    public int   archDefragCooldown         = 6000;
    public int   archScannerCooldown        = 1200;
    public float archSoundDamage            = 0.5f;
    public int   chunkVisionInterval        = 60;    // ticks between structure scans
    public float soundSensitivityMultiplier = 1.5f;  // explosion damage multiplier
    public int   archSpawnLockTicks         = 600;   // post-death item-pickup lock duration
    public float archXpPreservation         = 0.60f; // fraction of XP preserved on death (mind_backup)

    // ───────────────────────────── TIME WANDERER ───────────────────────────────
    @ConfigEntry.Category("time_wanderer")
    public int   timeAccelCooldown          = 1200;
    public int   timeDominionCooldown       = 4800; // merged stasis+time_loop
    public int   timeEchoCooldown           = 2400; // new temporal echo
    public int   timeInstabilityInterval    = 200;
    public int   timeRewindWindow           = 600;
    public int   timeXpLossOnRewind         = 10;
    public int   stasisFieldRadius          = 8;
    public int   stasisFieldDuration        = 100;  // 5 seconds
    public float stasisFieldDamage          = 1.0f;

    // ───────────────────────────── BIOMORPH ────────────────────────────────────
    @ConfigEntry.Category("biomorph")
    public int   bioShiftCooldown           = 3600;
    public int   bioBurstCooldown           = 1200;
    public int   bioCaptureCooldown         = 2400;
    public float bioTransitionDamage        = 1.0f;
    public float bioImprintBonus            = 0.05f;
    public int   bioImprintTime             = 6000;
    public int   bioTier2Ticks              = 1200;  // ticks in biome to reach tier 2
    public int   bioTier3Ticks              = 3600;  // ticks in biome to reach tier 3
    public int   bioShockGraceTicks         = 200;   // min ticks between biome-change shocks
    public int   bioWrongBiomeCheckInterval = 100;   // ticks between mining fatigue checks
    public int   bioWrongBiomeFatigueLevel  = 0;     // Mining Fatigue amplifier (0 = level I)

    // ───────────────────────────── PARASITIC MIND ──────────────────────────────
    @ConfigEntry.Category("parasitic_mind")
    public int   paraInfectCooldown         = 400;
    public int   paraInfectDuration         = 1200;
    public int   paraMaxTargets             = 3;
    public int   paraHivemindCooldown       = 1800;
    public int   paraExplosionCooldown      = 2400;
    public float paraWithdrawalDamage       = 5.0f;
    public int   telepathicNetworkInterval  = 20;   // ticks between telepathic direction updates
    public int   parasiteSenseInterval      = 40;   // ticks between parasite sense HP readouts
    public int   parasiteSenseRange         = 20;   // block radius for parasite sense

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
    public int   noValuablesInterval        = 60;   // ticks between valuable item degradation checks
    public float noValuablesChance          = 0.08f; // chance per tick to shrink non-damageable valuables
    public int   falseLootInterval          = 3600; // ticks between trap bait drops

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
    public int   mountainEchoInterval       = 80;   // ticks between cave scans
    public int   mountainEchoRadius         = 18;   // horizontal scan radius in blocks
    public int   sentinelSeismicRadius      = 50;   // block radius for seismic sense ping
    public int   sentinelStoneStackInterval = 100;  // ticks between stone armor stack gains
    public int   sentinelMaxStoneStacks     = 10;   // max stone armor stacks
    public int   sentinelElytraInterval     = 10;   // ticks between elytra-flight damage ticks
    public float sentinelElytraDamage       = 1.0f; // HP per interval when gliding

    // ───────────────────────────── RADIOACTIVE PHANTOM ─────────────────────────
    @ConfigEntry.Category("radioactive_phantom")
    public int   radioDecayWaveCooldown     = 1200;
    public int   radioJumpCooldown          = 300;
    public int   radioMutagenCooldown       = 2400;
    public float radioAuraDamage            = 0.5f;
    public int   radioAuraInterval          = 100;
    public int   radioAuraRadius            = 5;
    public float radioDayWaveDamage         = 6.0f;
    public float radioRainDamage            = 0.5f;  // HP per 20t when raining
    public float radioSelfDamage            = 0.2f;  // passive self-irradiation damage
    public int   radioSelfDamageInterval    = 400;   // ticks between self-damage
    public int   materialDecayInterval      = 200;   // ticks between gear decay checks
    public int   materialDecayAmount        = 4;     // durability removed per item per decay
    public int   geigerScanBonus            = 5;     // extra radius beyond radioAuraRadius for geiger
    public int   radioOverloadThreshold     = 5;     // nearby entities triggering overload
    public int   radioOverloadDisplayInterval = 200; // ticks between overload message displays
    public int   radioTrailInterval         = 60;    // ticks between trail zone updates
    public int   radioTrailExpiry           = 600;   // ticks a trail zone remains active (30 s)
    public int   radioTrailMaxEntries       = 10;    // max saved trail positions

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
    public float judgeLastVerdictThreshold  = 0.20f; // HP fraction triggering auto-verdict
    public float judgeLastVerdictRadius     = 15.0f; // block radius to find target
    public int   judgeLastVerdictCooldown   = 6000;  // ticks between auto-verdict fires
    public int   judgeAllSeeingEyeInterval  = 60;    // ticks between all_seeing_eye pings
    public int   judgeAllSeeingEyeRadius    = 50;    // block radius for seismic-sense ping
    public int   judgeAllSeeingEyeNearby    = 30;    // block radius for HP display

    // ───────────────────────────── MYCELIAL SYMBIONT ───────────────────────────
    @ConfigEntry.Category("mycelial_symbiont")
    public int   mossHarvestCooldown        = 600;
    public int   mossGrowNodeCooldown       = 600;
    public int   mossTeleportCooldown       = 1800;
    public int   mossFogCooldown            = 1200;
    public int   mossMaxRange               = 20;
    public float mossDetachDamage           = 0.5f;
    public int   mossDetachInterval         = 20;
    public int   mossBuffRadius             = 15;        // block radius for network buff
    public float waterKillsMossDamage       = 1.0f;     // HP per interval while in water/rain
    public int   waterKillsMossInterval     = 20;       // ticks between water damage
    public int   waterKillsMossWarnInterval = 200;      // ticks between rain warning in GeneralPowerHandler
    // bloom (ultimate ability)
    public int   mossBloomCooldown          = 6000;     // ticks (mirrors bloom.json cooldown_ticks)
    public int   mossBloomRadius            = 12;       // block radius for bloom effect
    public int   mossBloomAllyRegenDuration = 200;      // ticks of Regen II for allies (10 s)
    public int   mossBloomEnemyDebuffDuration = 160;    // ticks of Slowness+Weakness for enemies (8 s)
    public int   mossBloomSuperNodeDuration = 600;      // ticks the supernode lasts (30 s)

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
    public int   geoOreVisionInterval       = 80;   // ticks between ore direction pings
    public int   geoOreVisionRadius         = 20;   // block radius for ore scan
    public int   geoEarthHearingInterval    = 40;   // ticks between earth_hearing pings
    public int   geoEarthHearingRadius      = 30;   // block radius for entity detection
    public int   geoAltitudeDamageInterval  = 40;   // ticks between altitude_damage effect checks
    public int   geoStoneFleshRegenInterval = 160;  // ticks between stone_flesh passive regen pulses
    public float geoStoneFleshRegenThreshold= 8.0f; // min HP for stone_flesh standing regen to fire

    // ───────────────────────────── WANDERING GARDENER ──────────────────────────
    @ConfigEntry.Category("wandering_gardener")
    public int   gardenGrowthCooldown       = 600;
    public int   gardenRainCooldown         = 3600;
    public int   gardenDryadCooldown        = 2400;
    public int   gardenMaxTamed             = 5;
    public float gardenWoodcutDamage        = 3.0f;
    public float gardenBadBiomeDamage       = 0.5f;
    public int   gardenBadBiomeInterval     = 200;
    public int   gardenGrassRegenInterval   = 80;   // ticks between good-biome grass regen heals
    public int   gardenLifeBloomCooldown    = 6000;
    public int   gardenMaxTraps             = 3;         // max simultaneous plant traps
    public int   gardenTrapCooldown         = 600;       // ticks between trap placements
    public int   gardenTrapExpireTicks      = 6000;      // ticks until placed trap expires
    public float gardenWolfRadius           = 20.0f;     // block radius to find tamed wolves
    public float gardenEnemyRadius          = 15.0f;     // block radius to find enemies for wolves
    public int   gardenEggInterval          = 600;       // ticks between chicken egg handouts
    public int   gardenMilkInterval         = 1200;      // ticks between cow milk handouts
    public float gardenTrapTriggerRadius    = 2.0f;      // block radius for trap trigger
    public float gardenTrapDamage           = 4.0f;      // magic damage on trap trigger
    public int   gardenBoneMealInterval     = 100;       // ticks between bone-meal pulses
    public int   gardenBoneMealRadius       = 5;         // block radius for bone-meal
    public int   gardenGrowthAuraInterval   = 60;        // ticks between growth aura heals
    public float gardenGrowthAuraRadius     = 12.0f;     // block radius for growth aura
    public int   gardenWeaponConvertCooldown= 60;        // ticks between weapon→flower conversions
    public float gardenWoodcuttingMult      = 1.5f;      // incoming damage multiplier from undead
    public float gardenThornReflect         = 0.5f;      // fraction of incoming damage reflected

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
    public int   bloodOverloadWarnTicks     = 400;  // ticks at max charges before overload warning
    public int   bloodOverloadExplodeTicks  = 600;  // ticks at max charges before discharge explosion
    public float bloodOverloadDamage        = 8.0f; // HP lost in overload explosion
    public int   bloodArmorRegenInterval    = 200;  // ticks between Absorption refreshes
    public float bloodLowHpThreshold        = 6.0f; // HP (≤ this = "low HP") for survival_instinct

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
