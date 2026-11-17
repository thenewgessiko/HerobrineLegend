from pathlib import Path
p=Path('src/main/java/it/gesy/herobrine/HerobrineController.java'); s=p.read_text()
s=s.replace('import org.bukkit.entity.Player;', 'import org.bukkit.entity.Player;\nimport org.bukkit.potion.PotionEffect;\nimport org.bukkit.potion.PotionEffectType;')
s=s.replace('private enum Mode { NONE, STALKING, ATTACKING }','private enum Mode { NONE, STALKING, ATTACK_WINDUP, ATTACKING }')
s=s.replace('private boolean forcedAttack;', 'private boolean forcedAttack;\n    private int stalkingContactCooldown;')
s=s.replace('mode = Mode.ATTACKING;\n        forcedAttack = true;', 'mode = Mode.ATTACK_WINDUP;\n        forcedAttack = true;')
s=s.replace('        modeTicks++;\n\n        if (mode == Mode.STALKING)', '        modeTicks++;\n        if (stalkingContactCooldown > 0) stalkingContactCooldown--;\n\n        if (mode == Mode.STALKING)')
s=s.replace('''            if (plugin.getConfig().getBoolean("herobrine.stalking.vanish-when-looked-at", true)
                    && isTargetLookingAtHerobrine()) {
                despawn();''','''            if (npcLocation.distanceSquared(target.getLocation()) <= 2.25 && stalkingContactCooldown == 0) {
                stalkingContactCooldown = 60;
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, true));
                target.getWorld().strikeLightningEffect(target.getLocation());
                target.damage(1.0); // half a heart, independent of lightning damage
                despawn(false);
                nextAttemptTicks = 40;
                return;
            }
            if (plugin.getConfig().getBoolean("herobrine.stalking.vanish-when-looked-at", true)
                    && isTargetLookingAtHerobrine()) {
                despawn();''')
s=s.replace('''        if (mode == Mode.ATTACKING) {''','''        if (mode == Mode.ATTACK_WINDUP) {
            faceTarget();
            emitPresenceParticles();
            if (modeTicks >= 20) {
                mode = Mode.ATTACKING;
                modeTicks = 0;
            }
            return;
        }

        if (mode == Mode.ATTACKING) {''',1)
s=s.replace('''                mode = Mode.STALKING;''','''                mode = Mode.STALKING;''')
s=s.replace('''        mode = mayAttack && random.nextDouble() * 100.0 < attackChance
                ? Mode.ATTACKING : Mode.STALKING;''','''        mode = mayAttack && random.nextDouble() * 100.0 < attackChance
                ? Mode.ATTACK_WINDUP : Mode.STALKING;''')
s=s.replace('''        if (horrorManager != null) horrorManager.onEncounter(target, mode == Mode.ATTACKING);''','''        if (horrorManager != null) horrorManager.onEncounter(target, mode == Mode.ATTACK_WINDUP);''')
s=s.replace('''npc = new NPC(profile, ENTITY_IDS.getAndIncrement(), GameMode.SURVIVAL, Component.text("Herobrine"), null, null, null);''','''npc = new NPC(profile, ENTITY_IDS.getAndIncrement(), GameMode.SURVIVAL, Component.empty(), null, null, null);''')
# improve collision by handling vertical ascent separately from target's Y and use 0.6 width
s=s.replace('''        if (hasRoom(forward) && hasSupport(forward)) return forward;''','''        if (hasRoom(forward) && hasSupport(forward)) return forward;''')
s=s.replace('''            if (hasRoom(aboveOrigin) && hasRoom(jumped) && hasSupport(jumped)) {
                return jumped;
            }''','''            if (hasRoom(aboveOrigin) && hasRoom(jumped) && hasSupport(jumped)) {
                return jumped;
            }''')
s=s.replace('''            Vector step = delta.normalize().multiply(Math.min(speed, distance));''','''            // Horizontal steering: the target's vertical position must not steal jump speed.
            Vector horizontal = new Vector(delta.getX(), 0, delta.getZ());
            Vector step = horizontal.lengthSquared() < 0.0001 ? new Vector(0, 0, 0)
                    : horizontal.normalize().multiply(Math.min(speed, Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ())));''')
s=s.replace('''        boolean lightning = mode == Mode.STALKING && random.nextDouble() * 100 <''','''        boolean lightning = mode == Mode.STALKING && random.nextDouble() * 100 <''')
s=s.replace('''    private void despawn() {
        if (npc != null) playDespawnEffects();''','''    private void despawn() { despawn(true); }

    private void despawn(boolean effects) {
        if (npc != null && effects) playDespawnEffects();''')
s=s.replace('''        forcedAttack = false;
    }''','''        forcedAttack = false;
    }''')
p.write_text(s)
p=Path('src/main/java/it/gesy/herobrine/RitualManager.java');s=p.read_text();s=s.replace('''                    flame.setType(Material.FIRE);''','''                    flame.setType(Material.FIRE);
                    playRitualEffects(below.getLocation().add(0.5, 1, 0.5), true);''');s=s.replace('''                    state.deactivate();''','''                    playRitualEffects(below.getLocation().add(0.5, 1, 0.5), false);
                    state.deactivate();''');idx=s.index('    private boolean isRitualComplete(');s=s[:idx]+'''    /** Particle/sound choices reconstructed from the original 1.8 ritual bytecode. */
    private void playRitualEffects(Location at, boolean summon) {
        org.bukkit.World world = at.getWorld();
        if (world == null) return;
        if (summon) {
            world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, .5f);
            world.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, .7f);
            world.playSound(at, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 2f, .5f);
        } else {
            world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.5f);
            world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, .8f);
        }
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= (summon ? 60 : 8)) { cancel(); return; }
                if (summon) {
                    double angle = tick * .3, radius = 2.0 - tick / 30.0;
                    Location point = at.clone().add(Math.cos(angle) * radius, tick * .05, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.SOUL, point, 3, .1, .1, .1, .01);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, .1, .1, .1, .01);
                } else {
                    for (int i = 0; i < 8; i++) {
                        double angle = Math.PI / 4 * i;
                        Location point = at.clone().add(Math.cos(angle), 0, Math.sin(angle));
                        world.spawnParticle(Particle.END_ROD, point, 2, .1, .2, .1, .02);
                        world.spawnParticle(Particle.ENCHANT, point, 5, .2, .3, .2, .1);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

'''+s[idx:];p.write_text(s)
