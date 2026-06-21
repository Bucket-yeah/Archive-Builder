---
name: NeoForge 1.21.1 API quirks
description: Compile errors and their fixes specific to NeoForge 21.1.x / MC 1.21.1
---

## MobEffect / MobEffects

- `MobEffects.*` fields are `Holder<MobEffect>`, pass directly to `MobEffectInstance(holder, ...)` — do NOT call `.value()`.
- Sets that need to store effect holders: type as `Set<Holder<MobEffect>>` and store `MobEffects.X` directly (not `.value()`).
- `player.getEffect(holder)` / `player.removeEffect(holder)` accept `Holder<MobEffect>` directly.
- `MobEffects.SPEED` does NOT exist — use `MobEffects.MOVEMENT_SPEED`.

**Why:** In 1.21, effects were wrapped in Holder; existing code using `.value()` works via overload but storing `.value()` in a typed Set<Holder> causes a compile error.

## Block Particles

- `ServerLevel.sendParticles(ParticleTypes.BLOCK, x,y,z, count, dx,dy,dz, speed, BlockState)` — this 10-arg overload does NOT exist.
- Fix: `new BlockParticleOption(ParticleTypes.BLOCK, blockState)` then call `sendParticles(option, x,y,z, count, dx,dy,dz, speed)`.

**Why:** `ParticleTypes.BLOCK` is `ParticleType<BlockParticleOption>` not `ParticleOptions`; the extra BlockState arg was old Forge API.

## SoundEvents

- Both `SoundEvents.X` (Holder<SoundEvent>) and `SoundEvents.X.value()` (SoundEvent) work in `playSound()` — NeoForge provides overloads for both.
- `SoundEvents.SCULK_SENSOR_CLICKING` does NOT exist — use `SoundEvents.SCULK_BLOCK_SPREAD` or similar.

## MobEffectEvent.Applicable — denying effects

- `Event.Result.DENY` does NOT exist on `net.neoforged.bus.api.Event` in NeoForge 21.1.72.
- `MobEffectEvent.Applicable.setResult(DENY)` will not compile.
- **Fix:** Use a periodic tick check (`player.tickCount % 5 == 0`) to call `player.removeEffect(holder)` immediately after the effect is applied. Functionally equivalent for always-deny semantics.

**Why:** NeoForge migrated off `Event.Result` in the NeoEventBus refactor; the enum was removed from the base `Event` class.

## Removed Events

- `LivingAttackEvent` removed — use `LivingIncomingDamageEvent`.
- `builtInRegistryHolder()` does NOT exist on raw `MobEffect` — only valid on registry objects accessed through the registry API.
- `PlayerSleepInBedEvent` does NOT exist in NeoForge 1.21.1 — use `PlayerInteractEvent.RightClickBlock` and check `blockState.getBlock() instanceof BedBlock` instead.

## Bat Entity

- `Bat` is in `net.minecraft.world.entity.ambient.Bat`, NOT in `animal`. Same for other ambient mobs like Bats.

## ItemStack Serialisation

- MC 1.21.1: `ItemStack.save(RegistryAccess)` returns `Tag` (not void). Store result; cast to `CompoundTag` to put into NBT.
- Loading: `ItemStack.parseOptional(HolderLookup.Provider, Tag)` works with the returned `Tag`.

## NeoOriginsAPI

- `NeoOriginsAPI.origins(ServerPlayer)` does NOT exist in NeoOrigins 2.2.5 — only `NeoOriginsAPI.powers(sp)` exists.
- To check if a player has an origin, check if they have any power whose id starts with `"chaos_addon:<originName>/"`.

**Why:** The API only exposes powers, not origins directly. Matching by prefix works because all powers in an origin share that namespace.

## Entity Creation

- `EntityType.IRON_GOLEM.create(level)` returns `IronGolem` but safe to cast to `Mob` since IronGolem extends PathfinderMob extends Mob.
- Always null-check the result of `.create()`.

## Player Respawn Event

- `PlayerEvent.PlayerRespawnEvent` may not exist in NeoForge 1.21.1 — avoid or use a try/compile check.
