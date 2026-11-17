# HerobrineLegend 0.4.2 — audio/fear and ritual fix

- Fixed nested probability: ambient sound roll no longer depends on successful hallucination roll.
- Audio is checked independently every 6 seconds (75% chance by default).
- Sound is played at the listener's location, rather than 0-8 blocks away.
- Fear +5 only after an enabled, valid sound actually plays.
- Audible hallucinations contribute fear only when sound playback succeeds.
- Ritual activation and deactivation are delayed by 20 ticks after item reaches altar.
- Ritual structure, active state, and flame state are rechecked at execution time.
- Forced attack/jumpscare remain gated by altar state.

Important: old config.yml files are NOT automatically overwritten by saveDefaultConfig().
Add `soundscape.ambient.interval-seconds: 6`, set `chance-percent: 75.0`,
and add `ritual.transition-delay-ticks: 20` manually, or back up and regenerate
config.yml. Runtime defaults cover absent keys for these three options.

Not compiled or tested in-game in this environment. GitHub Actions must verify compilation.
