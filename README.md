# HerobrineLegend 0.4.0 — experimental horror expansion

An original implementation for Spigot using PacketEvents. This is **not a fork or a decompiled copy** of the original Herobrine plugin.

## Implemented in this prototype
- Original 0.3.1 ritual and player-NPC stalking/chase system
- Configurable fear level per online player (in-memory), decay and brief blindness
- Random client-side hallucinations: footsteps, heartbeat, breath particles, distant digging
- Encounter-triggered fear and configurable sounds/particles
- Five-second chase and configurable damage
- No weather, time, or structure modifications

## Known gaps
Not yet feature-equivalent to the original 1.8 plugin: no full environment scheduler, multi-NPC encounters, group-based cooldowns, persistent fear, or advanced effects. The runtime has **not been tested**. Compile and test on a disposable server first.

## Build
GitHub Actions compiles using Java 25 and Maven. Artifact is `herobrinelegend-0.4.0.jar` if successful. PacketEvents must be installed on the server.

## Config upgrades
Back up `plugins/HerobrineLegend/config.yml` and merge the new `horror:` section manually, because Bukkit `saveDefaultConfig()` does not overwrite an existing config file.

## Fear-based combat (0.4.1)
Herobrine diventa ostile quando la paura raggiunge 100 e rimane tale fino a quando scende sotto 70. Gli attacchi sono possibili solo in stato ostile; ogni inseguimento dura cinque secondi.
