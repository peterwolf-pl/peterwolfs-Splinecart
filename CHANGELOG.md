# Changelog

## 0.4.13+26.2

- Sleepers follow `/splinecart supports`: wooden ties for `wood`, steel ties for
  `steel`/`metal`, instead of switching by slope. `auto` still uses wood on flat
  segments and metal connectors on climbs.

## 0.4.12+26.2

- Unpowered magnetic track now brakes minecarts to a full stop, including on slopes.
- Detector output from Track Ties no longer feeds back into magnetic track power.

## 0.4.11+26.2

- Track Ties now output a redstone signal when a minecart passes through them.
- Adjacent redstone dust lights up, and comparators read a full signal of 15.

## 0.4.10+26.2

- Smooth spline-to-vanilla rail camera handoff: bake orientation when leaving a
  track follower so first-person view does not twitch.

## 0.4.9+26.2

- Fixed minecarts braking when a spline joins a vanilla rail, including detector rails.
- Fixed minecarts catching rotation and stalling on detector rails next to Track Ties.
- Smooth spline-to-vanilla rail handoff: keep motion continuous and bake camera orientation so the view does not twitch.
- Steel is the default support mode for new tracks (`/splinecart supports steel`,
  also `stal` / `slup` / `pillar`), including command-built and manually linked tracks.
- Steel construction uses a third rail under the sleepers and thin dark-gray
  pillars every 10-15 blocks, narrowed and pulled into the third rail at the top.
- Pillar spacing is measured along the whole spline so chain-drive segments no
  longer place a support at every tie.
- Track Ties can be placed in mid-air one block from the player: under the feet
  when looking down, directly in front when looking ahead, above the head when
  looking up.
- Placement orientation follows look direction: down is floor/horizontal, ahead
  is wall/sideways, up is ceiling/upside down.

## 0.4.8+26.2

- Added `/splinecart` server commands for building spline tracks (`build line`,
  `build path`, and `connect`) with all three track types.
- Added per-player support modes (`none`, `wood`, `metal`, `auto`) via
  `/splinecart supports`, applied to both command-built and manually linked tracks.
- Added configurable support placement: no supports, wooden fences, or iron bars.
- Added `/splinecart banking on|off|toggle` for smoothly banking newly built
  spline tracks through curves, including cart and camera orientation.

## 0.4.6+26.2

- Fixed Survival crafting recipes not appearing in the recipe book.
- Added recipe unlock advancements for Track Ties, Track, Chain Drive Track, and Magnetic Track.
- Moved all crafting recipes to the Redstone recipe book tab.

## 0.4.5+26.2

- Fixed long spline track sections disappearing when the endpoint chunk is not loaded on the client.
- Preserved sleeper, connector, and overlay rendering for long-distance spline segments.

## 0.4.3+26.1.2

- Exposed signed client-side spline motion for compatible locomotive renderers.
- Fixed Minecart Chain locomotive orientation flipping on curved spline rails.

## 0.4.2+26.1.2

- Fixed minecarts immediately remounting an incomplete spline follower when
  leaving the positive end of a spline for vanilla rails.
- Fixed Minecart Chain locomotives stopping and flickering during that handoff.

## 0.4.1+26.1.2

- Added an optional along-track movement bridge for propulsion and train-constraint mods.
- Fixed Minecart Chain locomotives fighting the spline follower, bouncing, or losing power.

## 0.4.0+26.1.2

- Added regularly spaced wooden sleepers to horizontal spline rails.
- Added oriented steel cross-links and rail clamps to non-horizontal spline rails.
- Added server-authoritative placement for all vanilla minecart items on straight,
  horizontal spline sections, including centerline snapping and collision checks.

## 0.3.3+26.1.2

- Fixed spline follower network updates snapping every two ticks instead of
  using Minecraft 26.1.2's entity interpolation system.
- Decoupled smooth orientation updates from follower position updates.

## 0.3.2+26.1.2

- Fixed speed-dependent lateral minecart shaking on spline tracks by separating
  render interpolation correction from the passenger's physical track offset.
