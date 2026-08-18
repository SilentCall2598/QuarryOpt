# Design rationale

**Read this before changing the Quarry Opt's code.** Some choices may look unusual but they were made to avoid bugs or performance regressions.

---

## 1. Filter cache

### The problem

`ItemFilterItems.getGhostStack` rebuilds an `ItemStack` from NBT every time it's called.

That involves registry lookups, `ResourceLocation` creation, string handling, and capability setup. `matches()` reads that same filter many times, and nested filters repeat that work all over again.

### Why the cache uses object identity

A cache based on NBT contents was originally tested first, but it was slower than no cache at all.

NBT hashing and equality checks walk the whole tag tree. In testing, `NBTTagCompound.equals` reached 8.74% of the server thread and `ItemFilterItems.matches` increased from 6.88% to 16.26%.

Using Guava's `MapMaker().weakKeys()` circumvents that via comparing keys by object identity (`==`) and  removes entries when the key isn't in use automatically.

### Why whole-filter caching needs invalidation

The mod has two cache modes.

**Per-slot cache (`filterFullCache = false`)**

Each slot is cached using its child NBT tag, and when Extra Utilities 2 edits a slot, it creates a new child tag. That automatically causes a cache miss so manual invalidation is not needed.

**Whole-filter cache (`filterFullCache = true`)**

The whole filter is cached using the root NBT tag. Extra Utilities 2 edits that root tag in place, so its object identity does not change. Because of that, Quarry Opt clears the cache when `putGhostStack` or `setFlag` edits the filter.

The per-slot mode is a fallback if Whole Filter Cache fails.

### Why `FilterCache.Entry` does not keep its key

`CACHE` uses weak keys but strong values.

If an `Entry` held a reference to its own `NBTTagCompound`, that value would keep the weak key alive forever and the cache could grow forever until restart.

The one-entry `Memo` keeps the temporary tag/entry pair instead.

### Why the memo exists

A single `matches()` call reads the same filter repeatedly.

The memo remembers the most recently used filter so most repeated reads only need a reference comparison, instead of another map lookup.

### Why cached stacks are returned directly

Extra Utilities 2 only reads the `ItemStack` returned by `getGhostStack` for filtering, tooltips, and GUI rendering. Filter changes go through `putGhostStack` so copying the cached stack on every read would just remove part of the performance gain.

### Why `getFlag` and `setFlag` use `@Redirect`

`ItemFilterItems.FLAG` is package-private so Quarry Opt cannot directly use that type in a normal injector method. The redirects work with accessible types such as `NBTTagCompound`, `int`, and `ItemStack` instead.

---

## 2. Drop capture

### Why there is no vanilla `Block.spawnAsEntity` mixin

Forge already has the capture behavior Quarry Opt needs inside `Block.spawnAsEntity`.

When Forge drop capture is active, the dropped stack is added directly to a list instead of creating an `EntityItem`.

An older Quarry Opt version added a similar behavior but it was redundant and removed.

### Why reflection is used

`captureDrops` and `capturedDrops` are Forge-added `protected static` fields and are looked up once during startup. After that, Quarry Opt only uses the resulting `ThreadLocal` references.

### Why the mixin targets `ItemCaptureHandler`

The start and stop hooks are both placed in one mixin targeting the same Extra Utilities 2 class. That means they apply together instead of risking one half applying without the other.

It also makes the optimization work for all XU2 users of `ItemCaptureHandler`:

- `TileQuarry`
- `TilePeacefulTable`
- `TileMine`

Though a runtime exception can still happen after capture starts and before it stops, which is why the watchdog exists.

### The end-of-tick watchdog

A capture should always start and stop within the same server tick.

One known failure path is:

```java
ItemCaptureHandler.startCapturing();
XPCaptureHandler.startCapturing(); // can throw
try {
    ...
}
```

The XP call happens before the `try` block, if it throws `stopCapturing()` is never reached. That can leave Forge or Extra Utilities 2's capture state active after the operation ends.

At the end of every server tick, the watchdog checks whether Quarry Opt still owns an active capture. 

If it does it clears:

- Forge drop capture
- Extra Utilities 2 item capture
- Extra Utilities 2 XP capture

A valid capture should never still be active at the end of a tick, so it's treated as a leak.

`MixinXPCaptureHandler` hooks `startCapturing` at `HEAD` because the important failure happens when that method throws before returning.

---

## 3. Chunk reset radius

### Why 3x3 is enough

Extra Utilities 2 normally resets a 5x5 chunk area when the quarry moves.

For normal biome generation, the quarry can only modify a 3x3 area:

1. The quarry mines one chunk.
2. `prepareNewChunk` can generate a 2x2 area.
3. Population can spill one chunk in the positive X/Z directions.

So the largest possible modified area is:

`(cx-1..cx+1, cz-1..cz+1)` which is a complicated way to say 3x3.

Quarry work areas are also six chunks apart so a 3x3 reset cannot reach the next slot.

### Why Nether/End jobs still use 5x5

When Extra Utilities 2's special Nether/End generation option is enabled, the quarry can write across the full 5x5 area, and because of that, Quarry Opt keeps the original 5x5 reset for those jobs.

### Why `@ModifyConstant` is used

The original method already contains the correct reset logic, Quarry Opt only changes the two radius constants instead of rewriting the whole method.

Setting `chunkResetRadius` back to `2` restores the original 5x5 behavior.

---

## 4. Mixin registration and failure policy

### Late registration

`QuarryOptLateMixins` implements MixinBooter's `ILateMixinLoader`.

All Quarry Opt mixins target Extra Utilities 2 classes so they need to be registered late.

Registering them early can make the class loader cache a failed lookup before Extra Utilities 2 loads its own classes.

MixinBooter 10.7 discovers late loaders through `ILateMixinLoader`. The newer `@LateMixin` annotation does not exist in this version.

### `"required": false` does not make every failure safe

A non-required mixin can safely fall back when its target cannot be found.

It does **not** make injector-count errors safe. If an injector is expected to match a specific number of locations and does not, Mixin can throw `InjectionError`.

That is intentional for hooks where as silently failing would be dangerous and it wouldn't tell you.

| Injector | `require` | Reason |
|---|---:|---|
| `cachedGhostStack` | 1 | Required for the cache path |
| `invalidateOnPut` | 1 | Missing it could leave stale filter contents |
| `invalidateOnFlag` | 1 | Missing it could leave stale filter flags |
| `armForgeCapture` / `drainForgeCapture` | 1 | Both sides of capture must apply |
| chunk reset constants | 2 | Prevents modifying the wrong constants |
| `cachedFlags` | 0 | Safe to fall back to normal NBT reads |
| `rememberXpCapture` | 0 | Only used for emergency cleanup |

### `MixinStatus` and `postApply`

`QuarryOptMixinPlugin.postApply` marks a mixin active only after it applies successfully.

`MixinStatus` permanently caches only `true`.

A `false` value could simply mean the target class hasn't loaded yet, because class loading is lazy. Caching `false` permanently could disable a working optimization before its target class has even been transformed.

The system-property copy of the status flags is a fallback in case the Mixin plugin and runtime classes are ever loaded by different class loaders.

---

## 5. Known possible upstream bug (not patched intentionally)

Extra Utilities 2 possibly has a bug in the ore-dictionary branch of `ItemFilterItems.matches`:

```java
int[] ghostOreIDs  = OreDictionary.getOreIDs(ghostStack);
int[] targetOreIDs = OreDictionary.getOreIDs(ghostStack); // likely meant to use target
```

Both lines read `ghostStack`.

That can make ore-dictionary filtering behave more broadly than expected.

Quarry Opt intentionally does not fix this because changing filter behavior would be a gameplay change, not a performance optimization, and existing setups may depend on the current behavior.

Thanks for reading. Fun fact if you made it this far, it took all the way until v8.7 before the final 1.0.0 release was ready.