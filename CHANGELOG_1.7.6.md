# Sovereign SMP v1.7.6 — Dragon, Stormvault, Hexcaller fixes

## Dragonfang / Dragon's Wrath
- Replaces the stationary, AI-disabled dragon with a live-AI dragon in HOVER phase, teleported along a scripted swooping flight path every two ticks. This allows wing animation and purposeful movement, while keeping the three existing damage strikes.
- The summoned dragon is tagged, invulnerable, silent, non-persistent, and cannot deal contact damage or change blocks.
- Scheduled cleanup removes it after 80 ticks, with a second fail-safe at 110 ticks. Dragons left over from previous builds are cleared at startup, plugin shutdown and on chunk load. If entity spawning is blocked, the scripted breath strikes continue without a dragon.

## Stormvault
- Replaces Wind Momentum passive with **Breeze Double Jump**: press jump again in midair to leap forward/upward; 5-second cooldown. Uses survival/adventure flight-toggle input while wielded; cleans up temporary flight permission on unequip, quit, disable, or game-mode change.
- Eye of the Tempest moves approximately 0.48 blocks/tick by default (substantially faster than before), picks a fresh random direction regularly, navigates around solid blocks, and roams within a configurable radius of 24 blocks.
- Updraft and Gale no longer rely on old Momentum stacks. Action bar shows double-jump readiness.
- New settings for jump cooldown, tornado speed, turn frequency, and roaming radius, with updated /sovereign config pages.

## Hexcaller
- Illager's Dominion creates **three expanding concentric rings** of Evoker fangs, each sweeping from the center to the edge of the ritual.
- The next ring is telegraphed. Players who are airborne when a shockwave reaches them avoid the scripted wave damage; time your jumps.
- Radius, ring spacing, wave interval, damage and cooldown remain configurable. The existing ultimate's Vexes and Hexbound finisher remain.

## Resource pack and item appearance
- Removes the custom item models **and** the custom animated tooltip backgrounds for Stormvault and Hexcaller from the combined resource pack.
- Stormvault shows as a vanilla Copper Spear; Hexcaller shows as a vanilla Blaze Rod. Their gradient names, enchantments, and colored lore remain.
- Existing authentic copies are automatically restyled on join/inventory refresh; `/sovereign restyle` forces a refresh.
- All other Frost and Sovereign animated tooltip textures, effects and models are preserved.

## Upgrade and test notes
- Back up `plugins/SovereignSMP/artifacts.yml` and `config.yml` before upgrading.
- Paper 1.21.11, Java 21; build using the included `.github/workflows/build.yml`.
- Only syntax, archive, resource-pack and YAML structural checks have been run in this environment. A real Paper compilation and server test are still required.
