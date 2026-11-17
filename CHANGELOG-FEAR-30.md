# Fear progression / HerobrineLegend 0.4.2

- The random NPC encounter candidate list excludes players below 30 fear.
- Forced `attack` and `jumpscare` still bypass the 30-fear threshold, but require active altar.
- The ambient horror loop remains available below 30 fear.
- A successful audible hallucination adds `fear.triggers.hear-sound` (default 5).
- A randomly played ambient sound also adds `fear.triggers.hear-sound` (default 5).
- Visual breath particles do not add fear.
- Ambient sound pool includes heartbeat, footsteps, ghast, phantom, warden, cave and pitched Enderman scream.
- Ambient Enderman scream uses pitch 1.15; despawn uses its separate low-pitched Enderman stare/teleport sounds.
- The original 1.8 config documents sound and fear triggers but does not specify a 30-fear gate: that is a new user-requested rule.

LIMITATIONS: no Maven/server execution performed; changes require GitHub Actions build and gameplay verification.
