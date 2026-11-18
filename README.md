<div align="center">

# 👁️ HerobrineLegend

### He is watching. You are not alone.

**A multiplayer-aware Herobrine horror plugin for Minecraft servers.**

[![Version](https://img.shields.io/badge/version-0.4.4--FINAL-darkred)](https://github.com/thenewgessiko/HerobrineLegend/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-green)
![PacketEvents](https://img.shields.io/badge/dependency-PacketEvents-orange)

</div>

---

## About

**HerobrineLegend** brings a mysterious, player-shaped Herobrine NPC to your world. Summon him through a ritual and experience unsettling sounds, hallucinations, stalking, jumpscares and eventually a full hunt. His behavior changes with each player's **Fear** level.

Unlike simple stalking-only experiences, HerobrineLegend uses **per-player Fear progression** and **one shared NPC** with fair target rotation across eligible players.

> **Version:** `0.4.4-FINAL` · **Author:** TheNewGessiko

## Features

- 👁️ **Packet-based Herobrine NPC** with a player-like appearance and skin.
- 🧠 **Individual Fear system** from 0 to 100; progression and hunting completion are tracked separately for each player.
- 🌫️ **Atmospheric events:** eerie sounds and hallucinations before Herobrine begins appearing physically.
- 👤 **Normal stalking:** Herobrine watches and vanishes when spotted.
- 😱 **Aggressive stalking:** eye contact can trigger a jumpscare and status effects.
- 💀 **Hunting:** at maximum Fear, Herobrine chases his target; six completed hunting appearances reset that player's Fear.
- 👥 **Multiplayer-aware:** one globally shared NPC, one target at a time, with target rotation.
- ⏱️ **Global appearance cooldown:** 30 seconds after an NPC disappearance, with a guaranteed sound event halfway through (15 seconds).
- 🔊 **Midpoint audio:** a randomly chosen eerie sound during stalking; a double-pair Warden heartbeat for the selected hunting target.
- 🔥 **Summoning and banishment ritual**, with visual effects and server-wide titles.
- ⚙️ **Configurable settings**, administrative commands and tab completion.
- 🌍 No forced night, weather changes or generated structures.

## Fear phases

| Fear | Behavior |
| --- | --- |
| **0–29** | Individual ambient sounds and hallucinations; no normal physical stalking encounters. |
| **30–64** | Normal stalking; Herobrine disappears when looked at. |
| **65–99** | Aggressive stalking and jumpscare behavior. |
| **100** | Hunting; six completed hunting encounters reset that player's Fear to 0. |

Fear is tracked **per player**. The physical NPC appearance timer is **global**, while low-Fear ambient events use individual timers. An encounter is not guaranteed to spawn immediately at the 30-second mark if its other requirements are not met.

## Requirements

- A compatible **Minecraft Java Edition 1.21.x** plugin server.
- **PacketEvents** installed as a separate server plugin (**required**).
- A Java runtime compatible with your server and the distributed JAR.

**Tested in gameplay:** Purpur (by the project author). The plugin has also been loaded on Paper and Spigot; full feature compatibility across every version/fork is not guaranteed. **Vanilla Minecraft servers do not load Bukkit-style plugins.**

> PacketEvents is declared as a hard dependency in `plugin.yml`. Install a compatible PacketEvents build **before** starting HerobrineLegend. The project currently compiles against PacketEvents `2.14.0`.

## Installation

1. Download the **compiled HerobrineLegend JAR** from [Releases](https://github.com/thenewgessiko/HerobrineLegend/releases).
2. Install a compatible **PacketEvents** plugin in your server's `plugins/` directory.
3. Place the HerobrineLegend JAR in the same `plugins/` directory.
4. Start or restart your server and check the console for successful plugin loading.
5. Edit `plugins/HerobrineLegend/config.yml` as needed, then use `/herobrine reload` for supported configuration changes.
6. Build the ritual to summon Herobrine.

## Summoning ritual

Build a **3 × 3 platform** with eight **Gold Blocks** surrounding one **Mossy Cobblestone** center. Place **Netherrack** directly above the center and **Redstone Torches** on top of the four **corner** gold blocks.

Top-down platform layout (`G` = Gold Block, `M` = Mossy Cobblestone):

```text
G  G  G
G  M  G
G  G  G
```

Above the four corner gold blocks, place redstone torches. Place the netherrack one block above `M`.

- **Summon:** throw **Crying Obsidian** onto the Netherrack.
- **Banish:** extinguish the ritual fire if present, then throw a **Diamond** onto the Netherrack.

The complete structure is required. Ritual actions affect the global Herobrine state.

## Commands

All commands require the **`herobrinelegend.admin`** permission (granted to operators by default).

| Command | Description |
| --- | --- |
| `/herobrine status` | Show whether Herobrine is summoned. |
| `/herobrine reload` | Reload plugin configuration. |
| `/herobrine fear <player>` | Show an online player's Fear level. |
| `/herobrine setfear <player> <amount>` | Set an online player's Fear (0–100). |
| `/herobrine jumpscare <player>` | Force a Herobrine appearance near an online player; requires an active ritual. |
| `/herobrine attack <player>` | Force a short Herobrine attack against an online player; requires an active ritual. |

Tab completion is available for subcommands, player names and Fear values.

## Configuration

Settings are stored in `plugins/HerobrineLegend/config.yml`. The default file includes options for ritual materials, appearance effects, sounds, Fear, stalking, hunting and combat. Some older configuration keys may remain for compatibility; the active behavior is determined by the current implementation.

**Current default behavior:** a **30-second global cooldown** between physical NPC appearances and a **guaranteed midpoint audio cue at 15 seconds**. Hunting audio is exclusive to the selected Fear-100 target.

## Releases and support

See [GitHub Releases](https://github.com/thenewgessiko/HerobrineLegend/releases) for downloadable builds and version notes. Report reproducible issues through [GitHub Issues](https://github.com/thenewgessiko/HerobrineLegend/issues), including server software/version, PacketEvents version and relevant console logs (remove private information first).

## Credits

**Created by TheNewGessiko.** Uses the **PacketEvents** library/plugin for packet-based NPC behavior. Inspired by the Herobrine legend and earlier community Herobrine plugins; this is an independent project.

---

<div align="center"><i>Some legends are better left undisturbed.</i> 👁️</div>
