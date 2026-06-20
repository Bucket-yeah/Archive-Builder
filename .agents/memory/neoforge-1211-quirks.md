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

## Removed Events

- `LivingAttackEvent` removed — use `LivingIncomingDamageEvent`.
- `builtInRegistryHolder()` does NOT exist on raw `MobEffect` — only valid on registry objects accessed through the registry API.

## Entity Creation

- `EntityType.IRON_GOLEM.create(level)` returns `IronGolem` but safe to cast to `Mob` since IronGolem extends PathfinderMob extends Mob.
- Always null-check the result of `.create()`.

## Player Respawn Event

- `PlayerEvent.PlayerRespawnEvent` may not exist in NeoForge 1.21.1 — avoid or use a try/compile check.
