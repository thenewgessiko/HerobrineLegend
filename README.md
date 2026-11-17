# HerobrineLegend 0.4.2 — development source

This is an independent implementation, based on the earlier 0.4.1 prototype (not a bytecode patch). It is **not** a complete clone of Herobrine 1.8.

Implemented in source: PacketEvents NPC spawn, relative movement and facing, player-arm swing packet, basic stalking and five-second chase, damage, fear thresholds with hysteresis, ritual prototype, atmospheric prototype, and status/fear/setfear/reload commands.

**Not implemented:** original `/herobrine jumpscare`, full original config compatibility, multiple concurrent NPCs, original state machine parity, exact original altar parity, server integration test.

Build via GitHub Actions (`.github/workflows/build.yml`) with Java 25 and Maven; this environment cannot resolve the Spigot/PacketEvents dependencies or run a server. The `26.3-R0.1-SNAPSHOT` Spigot API coordinate is inherited from the prior prototype and must be confirmed against your server.

Use on a test server only after a successful build. `mvn package` must pass before claiming a usable JAR.


## Comando di test: attacco forzato

`/herobrine attack <player>` richiede `herobrinelegend.admin`. Forza un NPC
all'inseguimento del giocatore per 5 secondi, anche senza altare attivo e
indipendentemente dalla paura. Non modifica il valore fear. Un eventuale NPC
attivo viene sostituito (questa build di sviluppo gestisce un NPC alla volta).
Se non esiste una posizione di spawn sicura, il comando segnala l'errore.

**Verifica in-game ancora necessaria**: installare una build compilata su un
server di prova con PacketEvents e provare il comando con un giocatore online.


## Ritual and admin commands (revised)

- Base: 3x3 gold blocks with mossy cobblestone in the middle; netherrack directly above the center.
- Four redstone torches: above the four corner gold blocks (diagonal corners of the 3x3).
- Throw crying obsidian onto the netherrack: visual lightning strikes and fire is lit above netherrack; Herobrine becomes active.
- Extinguish that fire, then throw a diamond onto the netherrack: Herobrine is banished.
- `/herobrine attack <player>` requires an active altar; bypasses only the fear threshold.
- `/herobrine jumpscare <player>` is not implemented in this source version yet; when added it must also require an active altar.
- Caveat: the item landing watcher currently relies on Bukkit `Item#isOnGround()`; test item landing above a lit fire and redstone torch survival in-game.
