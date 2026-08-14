# QuarryOpt

Server-side performance patch for Extra Utilities 2 Quantum Quarries.

Cuts quarry cost on the server thread by roughly **40%**, same mining speed, same yield,
same energy use. Clients don't need it if installed on a server.

## What it does

| Optimization | What changes |
| **Filter cache** | Filter contents are read once and reused, instead of being rebuilt from NBT on every single item check |
| **Drop capture** | Quarry drops go straight into Extra Utilities 2's list through Forge's own capture mechanism, instead of becoming item entities that are created and immediately deleted |
| **Chunk reset** | Chunk cleanup on quarry chunk swap covers the 3×3 area that can actually have been modified, instead of a 5×5 |

## Performance

Two 5-minute Spark profiles, same server, same session, matched conditions. The only
variable was the config toggle. Figures are share of total server-thread time.

| Metric | Off | On | Change |
| `TileQuarry.update` | 6.50% | 3.90% | **−40%** |
| `ItemFilterItems.matches` | 2.13% | 0.82% | −62% |
| `StackHelper.loadFromNBT` | 1.35% | 0.00% | −100% |
| `Block.spawnAsEntity` | 1.39% | 0.07% | −95% |
| `EntityItem.<init>` | 0.78% | 0.08% | −90% |

These figures come from one server running two quarries with nested filters and an
area-mining enchantment. A setup with plain, unfiltered quarries has far less overhead to
remove and will see a smaller improvement.

Chunk reset, measured on a separate 32-quarry stress test: **`releaseChunk` 8.41% → 3.07% (−64%)**.

## Requirements

- Extra Utilities 2, a hard dependency, the mod will not load without it
- [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) 10.7 or compatible

## Installation

Drop `quarryopt-1.0.0.jar` and MixinBooter into the server's `mods` folder and restart.

Clients do not need this mod if used on a server.

## Configuration

`config/quarryopt.cfg` is read at startup, so changes need a restart. Each optimization can
be turned off independently, which makes it easy to rule this mod in or out while troubleshooting.

| Option | Default | Description |
| `filterCache` | `true` | Cache filter contents |
| `filterFullCache` | `true` | Cache whole filters at once rather than slot by slot |
| `dropCapture` | `true` | Route quarry drops through Forge's capture mechanism |
| `chunkResetRadius` | `1` | `1` = 3×3 cleanup, `2` = 5×5 (stock behaviour) |

## Compatibility

Captured quarry drops no longer fire `EntityJoinWorldEvent`, because no item entity is
created. Drop-modifying effects are unaffected — Fortune, ore doubling and auto-smelt all
run earlier, on `BlockEvent.HarvestDropsEvent`, and behave identically.

Every optimization falls back to stock Extra Utilities 2 behaviour if it can't apply cleanly.

## License

MIT — see [LICENSE](LICENSE).
