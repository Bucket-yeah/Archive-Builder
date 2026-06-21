---
name: NeoOrigins 2.2.5 resource API
description: How to read, add, set, and deduct neoorigins:resource values from Java; JSON format; storage key convention.
---

## JSON format (ref: necromancer_essence.json inside the jar)
```json
{
  "type": "neoorigins:resource",
  "min": 0,
  "max": 100,
  "start_value": 0,
  "regen_rate": 0,
  "regen_interval": 20,
  "hud_render": {
    "label": "Label",
    "color": "#RRGGBB"
  },
  "name": "...",
  "description": "...",
  "hidden": false
}
```

## Storage key
The storage key used by `ResourcePower.getValue(player, key)` and `ResourcePower.deduct(player, key, n)`
is the **full power ID string**, e.g. `"chaos_addon:blood_smith/blood_charge_bar"`.
(Confirmed by decompiling `ResourcePower.storageKey(player, config)` — it calls `config.powerId()`.)

## Java API (all server-side, ServerPlayer only)

### Read
```java
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
int value = ResourcePower.getValue(player, "chaos_addon:ns/power_id");
```

### Deduct (spend — returns false if insufficient)
```java
boolean ok = ResourcePower.deduct(player, "chaos_addon:ns/power_id", amount);
```

### Add with clamp / set directly
```java
import com.cyberday1.neoorigins.compat.CompatAttachments;
CompatAttachments.ResourceState state = player.getData(CompatAttachments.resourceState());
state.clampedAdd("chaos_addon:ns/power_id", delta, min, max); // add clamped
state.set("chaos_addon:ns/power_id", value);                   // set absolute
state.get("chaos_addon:ns/power_id", defaultValue);            // alternative read
```

### Sync HUD to client (required after any write)
```java
CompatAttachments.syncResourceValuesToClient(player);
```

## ResourceState full method list
`get(key, default)`, `has(key)`, `set(key, val)`, `remove(key)`,
`clampedAdd(key, delta, min, max)`, `clampedAddAll(wildcardKey, delta, min, max)`,
`setAll(wildcardKey, val)`, `getAny(wildcardKey, default)`, `getAll()`,
`isDirty()`, `clearDirty()`.

## Overload / cooldown timers
Keep these as plain NBT (`putLong`/`getLong`) — they are not display values and don't need the resource system.

**Why:** `neoorigins:resource` only has no public "add" method in ResourcePower — must go through the attachment directly. Overload timers are per-player server-state with no HUD representation.

**How to apply:** Whenever migrating an NBT counter to neoorigins:resource, follow the Blood Smith pattern: RESOURCE_KEY constant = power ID string; addCharges via clampedAdd+sync; spendCharges via ResourcePower.deduct; setCharges (drain) via attachment.set+sync; keep cooldown timestamps as NBT.
