# Changelog

## 0.5.1+26.3

- Port to Minecraft 26.3 (Wilderness Bound).
- Allowed minecarts on parallel tracks to pass each other smoothly without colliding or pushing each other apart (ignoring vehicle and passenger bounding box collisions between different track segments while preserving collisions between carts on the same segment).
- Fixed converging / merging track switches: joining ties now correctly register secondary incoming tracks (`prevAlt`) and sync their switch blockstate.
- Enabled bidirectional switch routing: when a minecart travels in reverse through a converging track tie (joining two tracks), redstone power now toggles which incoming branch (`prev` vs `prevAlt`) the minecart takes.
- Added the ability to toggle smoothing on track ties during building:
  - Toggle default player smoothing preference via `/splinecart smoothing on|off|toggle` or `/splinecart smoothing` to query.
  - Sneak + right-click in the air with Track Ties or Track item to quickly toggle default smoothing mode.
  - Sneak + right-click directly on any track tie (with empty hand, Track item, or Track Ties item) to toggle smoothing on that specific tie.
  - Right-clicking connected track ties with an empty hand now also toggles smoothing.
  - HUD displays real-time track tie smoothing status (`Tie Smoothing: ON / OFF`) and shortcut hints when aiming at a track tie during building.
  - Track building commands (`/splinecart build line`, `/splinecart build path`, `/splinecart connect`) now respect player smoothing preferences.
- Track ties with smoothing enabled now automatically adjust their orientation to connected tracks:
  - Start, end, intermediate, and switch ties automatically calculate track heading and curve tangents.
  - In-world track tie blocks rotate (`POINTING`) so that their sleepers, rail clips, and directional arrows visually align with the connected tracks.
  - Orientation dynamically adjusts whenever tracks are placed, connected, removed, or when smoothing is toggled on.

## 0.5.0+26.2

- Backport to Minecraft 26.2.
- Added railway switches (turnouts / zwrotnice) to Track Ties.
- Track Ties now accept up to two outgoing and two incoming track splines.
- When multiple tracks connect to a tie, it acts as a switch controlled by redstone signal:
  - The switch tie stops acting as a detector rail so passing carts do not lock or override its switch state.
  - Signal 0 (unpowered): minecarts take the primary branch.
  - Signal > 0 (powered): minecarts switch to the alternate branch immediately, even for consecutive minecarts.
- Both branches are rendered simultaneously in full detail with their own supports, banking, and overlays.

## 0.5.0+26.3

- Added railway switches (turnouts / zwrotnice) to Track Ties.
- Track Ties now accept up to two outgoing and two incoming track splines.
- When multiple tracks connect to a tie, it acts as a switch controlled by redstone signal:
  - The switch tie stops acting as a detector rail so passing carts do not lock or override its switch state.
  - Signal 0 (unpowered): minecarts take the primary branch.
  - Signal > 0 (powered): minecarts switch to the alternate branch immediately, even for consecutive minecarts.
- Both branches are rendered simultaneously in full detail with their own supports, banking, and overlays.

## 0.4.18+26.3

- Fixed world creation hanging on datapack load: `minecraft:chain` is now `minecraft:iron_chain`, and recipe unlock advancements use `recipes`.

## 0.4.17+26.3

- Fixed a startup crash: Entity.calculateViewVector is static in 26.3.

## 0.4.16+26.3

- Port to Minecraft 26.3.

## 0.4.15+26.2

- Smoothed track ties connected with track from both sides: intermediate ties act strictly as anchors only, dynamically adjusting track angles and directions to create continuous, kink-free curves.
- Applies strictly to standard Track (`splinecart:track`) connected from both sides; Chain Drive Track and Magnetic Track retain fixed block orientations.
- Track ties remain unchanged in the world (anchors only) without altering blockstate pointing, preventing chain drive or magnetic track from attaching to inverted tie directions.

## 0.4.14+26.2

- Added dedicated Wood Track Ties (`splinecart:wood_track_ties`) and Steel Track Ties (`splinecart:steel_track_ties`).
- Distinct textures and inventory icons matching wooden sleepers vs. steel connectors with 3rd rail.
- Added directional arrow on all track ties indicating the track heading direction.
- Track routes automatically inherit sleepers and supports from the track tie type used.

## 0.4.13+26.2

- Horizontal segments follow `/splinecart supports`: wooden ties for `wood`,
  original metal connector pads plus 3-rail construction for `steel`. Climbs keep
  the original metal connectors instead of retextured wooden sleepers.

## 0.4.12+26.2

- Unpowered magnetic track now brakes minecarts to a full stop, including on slopes.
- Detector output from Track Ties no longer feeds back into magnetic track power.

## 0.4.11+26.2

- Track Ties now output a redstone signal when a minecart passes through them.
- Adjacent redstone dust lights up, and comparators read a full signal of 15.

## 0.4.10+26.2

- Fixed a client crash (`IllegalClassLoadError`) when Mixin tried to load `SplineCameraHandoff` from the mixin package during camera updates.

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
