# Sovereign SMP v1.7.7 — Dragon display + gravity rework

- **Dragonfang Wrath:** removed short-lived vanilla Ender Dragon; uses an animated, flying, temporary dragon silhouette made of vanilla BlockDisplays/ItemDisplays with flapping wings and 3 scripted breath strikes. No real Ender Dragon boss remains. Automated cleanup on timeout, reload, and chunk loading.
- **Dragonfang Breath:** spawned DragonFireball no longer passed to `EntityShootBowEvent#setProjectile` after creation. Avoids the false "could not spawn" error. Its vanilla crossbow arrow is cleaned up separately after firing.
- **Dragonfang enchantments:** Quick Charge II, Multishot, Infinity (unsafe enchant, Infinity implemented via Paper crossbow loading event so arrows are not consumed (at least one arrow still needed to load)), Piercing IV and Power III; unbreakable.
- **Stormvault:** Lunge III; randomized noisy tornado stays within 5 blocks from its spawn point. Old server configs with larger radius migrate to 5.
- **Mace:** Density III, Wind Burst II, Fire Aspect II, unbreakable. Event Horizon instant aimed-target acquisition + traveling purple gravity pulse, then persistent pull toward caster. Gravitational Collapse grounded targets are held for 1.5 seconds and airborne targets are driven down and Slowness IV for 2 seconds; ability damage remains configurable.
- Existing authentic item tokens and all seven Sovereign artifacts unchanged. `/sovereign restyle` updates old item enchantments.
- Source-only beta: Paper compilation and multiplayer testing still needed.
