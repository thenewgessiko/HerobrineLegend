# Jump & Sound patch — HerobrineLegend 0.4.2

- Replaced `ENTITY_ENDERMAN_SCREAM` with `ENTITY_ENDERMAN_STARE` on NPC despawn.
- Kept simultaneous `ENTITY_ENDERMAN_TELEPORT`, both pitch 0.55.
- Added `combat.chase.auto-jump: true` to config.
- Changed collision resolution to prioritize one-block stepping over sliding.
- Explicitly checks headroom at the start and destination of the step.

## Limits
The packet NPC does not have vanilla physics. The one-block rise is currently a
single movement packet (not a smooth jump animation). This does not implement
pathfinding around obstacles, stairs, slabs, liquids, or complex geometry.
Build and in-game tests are required; ZIP integrity alone is not compilation.
