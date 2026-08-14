# Design rationale

**Read this before modifying the code** several choices
look arbitrary but are load-bearing, and reverting them reintroduces bugs.

---

## 1. Filter cache

### The problem

`ItemFilterItems.getGhostStack` rebuilds an `ItemStack` from raw NBT on every call:
registry lookup by name, `ResourceLocation` allocation, string lowercasing, capability
gathering. `matches()` reads NBT roughly twenty times per call, four `getFlag` lookups
plus `getGhostStack` across all sixteen slots, and nested filters repeat that at every
level of nesting.

### Why the cache is keyed by identity, not content

A content-keyed cache was tried first and was **actively harmful**. NBT hashing and
equality walk the entire tag tree, so lookups cost more than the deserialization they
replaced. `NBTTagCompound.equals` alone reached 8.74% of the server thread and
`ItemFilterItems.matches` rose from 6.88% to 16.26%, worse than no cache at all.

Guava's `MapMaker().weakKeys()` gives identity comparison (`==`) plus automatic eviction,
which is what makes the cache cheap and unbounded-safe simultaneously.

### Why invalidation is required for the whole-filter cache but not the per-slot one

Two cache modes exist, selected by `filterFullCache`:

- **Per-slot** (`filterFullCache = false`) keys on each filter slot's child tag.
  `putGhostStack` builds a **new** child tag and calls `setTag` rather than editing in
  place, so an edited slot is automatically a cache miss. No invalidation needed because
  staleness is structurally impossible.

- **Whole-filter** (`filterFullCache = true`) keys on the filter's **root** tag, which
  Extra Utilities 2 edits **in place**. Object identity does not change, so the cache
  must be told explicitly. `putGhostStack` and `setFlag` are the only writers in
  `ItemFilterItems`. Verified by inspection of 1.9.9 and both are hooked.

The per-slot path is retained as a fallback for that reason, it is the safe mode
in case of failure.

### Why `FilterCache.Entry` must not hold its own key

`CACHE` has weak keys but holds values **strongly**. If `Entry` kept a reference to the
`NBTTagCompound` it was built from, that would be a strong path from the map back to its
own key, and the key could never be collected. The cache would grow for the life of the
server. The tag/entry pairing lives in `Memo` instead, which holds exactly one tag at a
time and is overwritten on every miss.

### Why the memo exists

All twenty NBT reads in a `matches()` call happen back-to-back on the same filter, so a
single-entry memo turns reads 2–20 into one reference comparison. `Memo` is immutable
with final fields, so a reader can never observe the tag from one filter paired with the
entry of another.

### Why cached stacks are returned directly rather than copied

Every caller of `getGhostStack` in Extra Utilities 2 only reads the result, filter
matching, tooltips, GUI slot rendering. Writes go through `putGhostStack`, which builds a
new tag. Copying on every read would return a large part of what the cache saves.

### Why `getFlag` and `setFlag` are hooked with `@Redirect` rather than `@Inject`

`ItemFilterItems.FLAG` is package-private, so a handler in package `quarryopt` cannot
declare a matching descriptor for `getFlag(ItemStack, FLAG)` or
`setFlag(ItemStack, FLAG, boolean)`. The redirects deal only in `NBTTagCompound`, `int`
and `ItemStack`.

`setFlag` redirects `NBTHelper.getOrInitTagCompound` specifically because `setFlag` has
three different write paths depending on the resulting value (`removeTag` when flags
reach zero, `setByte` when the value fits a byte, `setInteger` otherwise). Redirecting
any single write would miss the others. `getOrInitTagCompound` is the one call all three
paths pass through, before any mutation.

---

## 2. Drop capture

### Why no vanilla mixin

Forge already patches `Block.spawnAsEntity` with the required shortcut:

```java
if (captureDrops.get())
{
    capturedDrops.get().add(stack);
    return;
}
```

placed after the `isRemote` / `isEmpty` / `doTileDrops` / `restoringBlockSnapshots`
guards. That branch already executes on every drop in every Forge installation.
An earlier version injected its own equivalent into `spawnAsEntity`, which was 
redundant and added a vanilla mixin for no reason.

### Why reflection is safe here

`captureDrops` and `capturedDrops` are Forge-added `protected static` fields, not vanilla
members, so they are not SRG-obfuscated and reflection by name is stable at runtime. The
lookup happens once at preInit, everything afterwards is plain `ThreadLocal` access.

### Why the mixin targets `ItemCaptureHandler` rather than `TileQuarry`

Arming at the same abstraction that drains keeps both halves inside one mixin class
targeting one class, so a partial mixin application cannot load with the arm hook present
and the drain hook absent.

It also means the optimization covers every Extra Utilities 2 machine that captures block
drops — `TileQuarry`, `TilePeacefulTable` and `TileMine`, which is a cool plus.

Note this is narrower than "armed but never drained is impossible". Both hooks can apply
perfectly and a runtime exception can still prevent `stopCapturing` from being reached,
which is why the watchdog exists.

### The end-of-tick watchdog

`TileQuarry` does:

```java
ItemCaptureHandler.startCapturing();   // we arm here, at RETURN
XPCaptureHandler.startCapturing();     // throws if XP capture is already active
try { ... }                            // the XP call is OUTSIDE this try
```

If the XP call throws, nothing reaches `stopCapturing`. Left alone Forge's
`captureDrops` flag stays set for the rest of the JVM run and **nothing anywhere in the
world drops items**.

A capture window is entirely contained within one tick, verified across all three
internal callers, each of which starts and stops within a single method body. So still
owning a capture at `ServerTickEvent` END is definitively a leak.

The watchdog also clears Extra Utilities 2's own item and XP captures. `ownedByUs` is true '
only between our arm point (RETURN of `startCapturing`) and our drain point (HEAD of `stopCapturing`), 
and `ItemCaptureHandler.capturing` is written nowhere else in 1.9.9. So ownership at end 
of tick is proof that XU2 is stranded too. A stranded `ItemCaptureHandler` cancels 
every `EntityItem` on the thread; a stranded `XPCaptureHandler` cancels every `EntityXPOrb`.

`XPCaptureHandler.startCapturing` throws **because** its `ThreadLocal` is
non-null, and it throws before writing to it so the condition survives the exception.
Clearing only the item capture would let the next quarry operation fail identically.
`MixinXPCaptureHandler` injects at **HEAD** rather than RETURN for that reason, in the
case that matters, `startCapturing` throws, and a RETURN hook would never run.

---

## 3. Chunk reset radius

### Why 3×3 is sufficient

`releaseChunk` resets a 5×5 block of chunks in one tick, inside a synchronized block,
loading chunks from disk (`ChunkIOExecutor.syncChunkLoad`) purely to overwrite them.

Only three things write blocks in that area:

1. The quarry mines exactly **one** chunk. `TileQuarry.setBlockPos` uses `chunkPos.x/z`
   directly with x,z in 0..15, so the dig area never leaves `(cx, cz)`.
2. `prepareNewChunk` generates a **2×2**: `(cx-1..cx, cz-1..cz)`.
3. Population spills one chunk positive, `populate(x, z)` decorates the 16×16 area
   starting at `(x*16+8, z*16+8)`, so it writes into `(x+1, z+1)`.

The widest possible written area is therefore `(cx-1..cx+1, cz-1..cz+1)`, a 3×3.
Slots are 6 chunks apart (`DIST_BETWEEN_CHUNKS`, applied by `adjustChunkRef`), so a 3×3
cannot reach the next slot at `cx+6` either.

### Why Nether/End jobs are excluded

The above holds only for ordinary biome generation. When `ALLOW_SPECIAL_DIMS` is on and a
quarry targets a Nether or End biome, `prepareNewChunk` runs its own `dx/dz -2..2` loop
writing end stone or netherrack across the full 5×5, and `addBorderColumnLine` writes
bedrock columns at `(cx-1)*16 - 1`, which lands in chunk `cx-2`.

Rather than tracking which slots were special jobs, the mixin checks the flag that makes
them possible at all (per call). `ALLOW_SPECIAL_DIMS` comes from the Extra Utilities 2
option *Quantum Quarry: Enable Nether/End biome generation (has been buggy)*, off by
default.

### Why `@ModifyConstant`

Six lines, no reimplementation of the surrounding synchronized block or the
`diggingChunks` bookkeeping. Setting `chunkResetRadius` to `2` restores the original
behavior because both injections then return the values they were given.

---

## 4. Mixin registration and failure policy

### Late registration

`QuarryOptLateMixins` implements MixinBooter's `ILateMixinLoader`. Every mixin target is
an Extra Utilities 2 class, and registering those **early** does not just fail it
poisons the class loader's negative cache, so Extra Utilities 2 then crashes with
`ClassNotFoundException` loading its own class.

MixinBooter 10.7 discovers late loaders by scanning `ASMDataTable` for implementations of
`ILateMixinLoader`. Implementing the interface is the requirement, the `@LateMixin` 
annotation referenced in newer MixinBooter documentation does not exist in 10.7.

### `"required": false` is not a universal soft fallback

Non-required means **target-resolution** failures degrade safely, a method that cannot be
found causes the mixin to be dropped and original behavior to stand.

It does **not** make everything soft. When a target is found but an injector's
`require`/`allow` count is violated, Mixin throws `InjectionError`, which extends `Error`
and bypasses the soft-failure path entirely.

That is deliberate for the correctness-critical injectors. Each was chosen by the cost of
its absence:

| Injector | `require` | Rationale |
|---|---|---|
| `cachedGhostStack` | 1 | Absence is dangerous |
| `invalidateOnPut` | 1 | Absence causes stale filter contents |
| `invalidateOnFlag` | 1 | Absence causes stale filter flags |
| `armForgeCapture` / `drainForgeCapture` | 1 | Absence of either half is catastrophic |
| chunk reset constants | 2 | Benign if half-applied, but an `allow` violation would modify an unintended constant in code that rewrites world state |
| `cachedFlags` | 0 | Absence is harmless, `getFlag` reads NBT as before, correct but slower |
| `rememberXpCapture` | 0 | Emergency-path only |

Making a benign injector `require = 1` would convert harmless degradation into a fatal
startup error.

### `MixinStatus` and `postApply`

`QuarryOptMixinPlugin.postApply` runs only after a mixin has applied cleanly, which makes
it a supported positive signal. An earlier version checked for injected handlers by their
source method names via reflection. That was wrong, because Mixin conforms injector
handler names during application and there is no promise that the original name survives.

**`MixinStatus` caches `true` only, never `false`.** `postApply` fires when a target class
is first *loaded*, which is lazy, so `false` means "not confirmed yet" rather than
"failed" and can become `true` later. Caching a `false` would permanently
disable an optimization whose mixin applied perfectly.

For the same reason, every gate reads its flag from inside the very class whose
transformation sets it, and the status report is deferred to server start.
The flags are **not** reliable at preInit.

The System property mirror exists because the plugin and the mod's runtime classes are
normally loaded by the same class loader on 1.12.2 LaunchWrapper, but if that ever
stopped holding, the runtime copy would read its own fields as `false` forever, a
failure indistinguishable from a real mixin failure. System properties are per-JVM rather
than per class loader.

---

## 5. Known upstream bug (not patched)

`ItemFilterItems.matches`, ore dictionary branch:

```java
int[] ghostOreIDs  = OreDictionary.getOreIDs(ghostStack);
int[] targetOreIDs = OreDictionary.getOreIDs(ghostStack);   // should be target
```

Both fetch the ghost stack. With *use ore dictionary* enabled, a filter therefore matches
any target as long as its own ghost stack has at least one ore dictionary entry.

Deliberately not patched, fixing it would change what filters accept and could break
setups built around current behavior. It is a gameplay change, not an optimization.

It is also worth knowing when reading profiles, filters with that flag set return early
far more often than they appear to.
