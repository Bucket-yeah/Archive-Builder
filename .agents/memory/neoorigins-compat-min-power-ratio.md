---
name: NeoOrigins COMPAT_MIN_POWER_RATIO filter
description: NeoOrigins 2.2.5 hides addon origins where fewer than 50% of their listed power IDs exist in PowerDataManager after loading.
---

## Rule
NeoOrigins 2.2.5 `AdminConfig.COMPAT_MIN_POWER_RATIO` defaults to **0.5 (50%)**.  
`OriginsMultipleExpander.isMultipleType()` returns `true` for `"neoorigins:multiple"` (also for `"origins:multiple"`, `"apace:multiple"`).  
When PowerDataManager loads a power with type `neoorigins:multiple`, it **removes** the original power ID from the registry and adds the expanded sub-power IDs instead (e.g. `chaos_addon:origin/visual_marker` → removed, `chaos_addon:origin/visual_marker/color` + `.../particles` added).  
`PowerDataManager.hasPower(original_id)` returns `false` after expansion (only checks `powers` and `injectedPowers` maps).  
After loading all origins, `OriginDataManager.apply()` counts `loadedPowers / totalPowers` for each non-neoorigins origin. If ratio < 0.5, the origin is **silently hidden** with only an INFO log: `"[Compat] Hiding origin {} — only {}/{} powers loaded"`.

**Why:** NeoOrigins treats `neoorigins:multiple` as a compat/legacy type from Origins (Fabric), expanding it to sub-powers. The original IDs are lost, reducing the apparent load ratio. Origins with many `neoorigins:multiple` powers and few total powers can fall below 50%.

**How to apply:**  
- For each `chaos_addon` origin: count `neoorigins:multiple` powers and total powers.  
- Ensure `(total - multiple_count) / total >= 0.5`.  
- Fix: split each `neoorigins:multiple` power file into two separate single-type power files (`visual_color.json` + `visual_particles.json`) instead of one multiple wrapper. Update origin JSON to list both new IDs. Delete old `visual_marker.json`.  
- Do NOT use `neoorigins:multiple` as a wrapper if it drops ratio below 50%.  
- blood_smith at exactly 50% (5/10) is fine — the check is `>=`.
