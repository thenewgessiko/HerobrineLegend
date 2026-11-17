# HerobrineLegend 0.4.4-FINAL — cave-only appearance audio fix

- All Fear 30–100 appearances play only the configured `soundscape.events.cave` sound (default `AMBIENT_CAVE`).
- Ignores legacy `appearance.eerie-events` lists for NPC appearances, preventing old config files from selecting `distant-breaking` (`BLOCK_STONE_BREAK`).
- Low-Fear 0–29 entity/eerie alternating pool unchanged.
- Stalking delayed Enderman scream, hunting, timers, Fear, ritual titles and workflow unchanged.
- If you changed `soundscape.events.cave.sound` in an existing server config, set it back to `AMBIENT_CAVE`.
