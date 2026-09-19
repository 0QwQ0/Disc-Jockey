# Maintaining the 1.21.11 branch

This branch is an **independently maintained line** for Minecraft 1.21.11, not a mirror of `main`.
It carries the same feature set as the 26.2 line, adapted to a different API generation.

## Why it needs its own branch

1.21.11 is the **last obfuscated Minecraft release**. From 26.1 onwards the game ships unobfuscated, which
changed the whole toolchain: the new `net.fabricmc.fabric-loom` plugin does not remap, mappings are gone
and the sources are compiled for Java 25. None of that applies here, so this branch keeps the classic
remapping setup.

| | `main` (26.2) / `26.3` | `1.21.11` (this branch) |
|---|---|---|
| Minecraft | 26.2 / 26.3 (unobfuscated) | 1.21.11 (obfuscated) |
| Loom | `net.fabricmc.fabric-loom` 1.16+, no remapping | `fabric-loom` **1.17.21**, remaps |
| Mappings | none needed | `loom.officialMojangMappings()` |
| Gradle | 9.4.0 | **9.5.1** (Loom 1.17.21 rejects Gradle 9.4 through its variant metadata) |
| Java | 25 | **21** (compiled with `options.release = 21`; a JDK 25 can do this, no JDK 21 install needed) |
| Loader / API | 0.19.x / 0.15x+26.x | 0.18.4 / 0.141.1+1.21.11 |
| Cloth Config | 26.2.155 / 26.3.158 | 21.11.153 |
| Dependencies in build.gradle | `implementation` | `modImplementation` / `modApi` (they must be remapped) |

Language: Java 21 does **not** allow unnamed lambda parameters (`_`). The 26.x sources use them, so every
occurrence on this branch was renamed to `unused`. Keep that in mind when copying code from `main`.

## Feature sync workflow

1. Features are developed and verified in game on `main` (26.2) first.
2. They are then ported to `26.3` and to this branch as **separate adaptation commits**.
3. **Never merge `main` into this branch.** The API generations differ; a merge would drag in 26.x-only
   APIs (for example `GuiGraphicsExtractor`, `extractRenderState`, `StartLevelTick`) that do not exist here.

Porting this branch forward is mostly mechanical: build, read the javac errors and check the real
signature with `javap` on the mapped 1.21.11 jar in `~/.gradle/caches/fabric-loom` instead of guessing.

### API differences already handled here (26.x → 1.21.11)

| 26.x | 1.21.11 |
|---|---|
| `GuiGraphicsExtractor` | `GuiGraphics` |
| `Screen#extractRenderState` / `extractBackground` | `Screen#render` / `renderBackground` |
| list entry `extractContent(...)` | `renderContent(...)` |
| `context.text(...)` / `centeredText(...)` / `item(...)` | `drawString(...)` / `drawCenteredString(...)` / `renderItem(...)` |
| `keymapping.v1.KeyMappingHelper` | `keybinding.v1.KeyBindingHelper` |
| `command.v2.ClientCommands` | `command.v2.ClientCommandManager` |
| `ClientTickEvents.StartLevelTick` | `ClientTickEvents.StartWorldTick` |
| `client.gui.setScreen(...)` | `client.setScreen(...)` |
| `gui.hud.getChat().addClientSystemMessage(...)` | `gui.getChat().addMessage(...)` |
| `gui.toastManager()` | `getToastManager()` |
| `Blocks.WOOL.white()` | `Blocks.WHITE_WOOL` |

`net.minecraft.resources.Identifier`, `GuiGraphics`, `MouseButtonEvent`, `KeyEvent` and
`ObjectSelectionList` all already exist under those names in the 1.21.11 official mappings, so no renaming
was needed for them. `HudElementRegistry` (rendering.v1.hud) also exists in Fabric API for 1.21.11 and is
used instead of `HudRenderCallback` because it keeps the original "drawn last" HUD ordering.

## Known gaps on this branch

- **No trumpet instrument.** `NoteBlockInstrument.TRUMPET`, `TRUMPET_EXPOSED`, `TRUMPET_WEATHERED` and
  `TRUMPET_OXIDIZED` were added in 26.1, so they do not exist here; the four entries were removed from
  `Note.java` and the instrument table is back to the classic sixteen. Songs that use NBS instrument ids
  16-19 cannot be played with copper blocks on 1.21.11.
- The guitar instrument uses `Blocks.WHITE_WOOL` (the copper/wool registry changes came later).

## Release naming

The mod version is shared across all three lines; the Minecraft version is carried by the tag and the
asset name, for example `v1.9.4-mc1.21.11` / `disc_jockey-1.9.4-mc1.21.11.jar`.

## Verification status

Compilation and packaging are verified here, including that the mixin was remapped to real intermediary
targets (`method_43207`, `field_3729` on `class_638`). The build was also **verified in game on a 1.21.11
client** (playlist, playback controls, double click, lyrics output and preview, packet rate readout, HUD
overlay and the omnidirectional note block sounds option).
