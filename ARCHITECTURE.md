# Milestone 01 — foundation

## Dependency direction

Presentation -> domain <- data. Design system -> domain + engine.
Animation policy -> domain settings. The application container composes these pieces.
One Android Gradle module is intentional: package boundaries keep extraction possible
without creating empty Gradle modules. Domain and engine contain no Android/Compose imports.

- domain/settings: immutable preferences, repository contract, stable enum values.
- domain/theme: BoardTheme, ARGB palette, registry, theme repository contract.
- engine: pure Kotlin standard short-backgammon rules, immutable models, deterministic reducer,
  injectable local dice, normalized 1000 × 700 geometry and the unchanged starting illustration.
- data: a single Preferences DataStore, codec and theme/settings repository implementations.
- presentation: activity-scoped SettingsViewModel, StateFlow, lifecycle-aware collection,
  stateless Board/Settings screens, serializable Navigation 3 destinations.
- designsystem: spacing, application palette, reusable Canvas renderer with an overlay slot.
- ui/theme: Material bridge, typography and shapes.
- animation: finite effect categories and speed/reduced-motion policy.
- network: documented future boundary only; no client, endpoint or backend dependency.

The small application container provides explicit constructor injection. Hilt can replace
this at a genuine feature/scope boundary. Use cases and remote interfaces should be introduced
when they encapsulate actual behavior; no speculative forwarding abstractions are needed yet.

## State and persistence

UI action -> queued ViewModel command -> atomic repository edit -> persisted flow -> UI state.
There is one source of truth, with no optimistic theme value that can diverge from disk.
Every update transforms the latest persisted settings, preserving unrelated and unknown keys.
Theme IDs and preference keys form the v1 schema. Unknown IDs/enum values fall back safely.
IO failures are visible: read retry or a save failure message. A failed write retains the
last committed visual theme. Preferences are loaded before the interactive screens appear.

Navigation uses rememberNavBackStack and serializable keys for recreation/process restoration.
Lazy grid and scroll states use Compose saveable defaults. No orientation lock or configChanges.
English and Georgian are app-local resources; changing language updates the visible UI without
restarting the activity. Both languages are bundled (language splitting is disabled), so the
offline switcher works after App Bundle installation. This does not yet integrate the Android
system per-app language picker.

## Rendering and motion

The board is a static illustration with all 24 standard points, central bar, empty bearing-off
trays, correctly counted starting stacks and fixed dice (3 and 5). There is no dice generator.
Geometry and hit regions use logical coordinates; Canvas scales only at the rendering boundary.
Point hit regions intentionally exclude the central dice band. Overlapping checker centers
compress deterministically for stacks up to 15. Future gameplay must map pointer coordinates
back to these logical bounds and provide semantic actions and accessible expanded hit targets.

Original procedural grain, shaded checker discs, bevel rings, inlaid triangles and gradients
use only skin palette data. The renderer accepts an overlay slot for future moving pieces;
the future board state must be supplied as immutable engine data when gameplay is introduced.

Current theme crossfades and navigation fades use MotionPolicy; Compose honors system animation
scale. Reduced motion sets every category duration to zero. Future checker/dice overlays should
use keyed Animatable objects with graphicsLayer transforms, cancellation on retarget/disposal
and a snap to authoritative engine positions after completion/cancellation. Pointer drag should
follow input directly. No animation result may update a game outcome. Avoid particles, blur,
infinite loops and extra layers until device measurements justify their cost.

## Dependency choices

Existing wrapper, AGP, Kotlin compiler, Compose BOM and SDK levels are preserved.
Stable Navigation 3 1.0.0, DataStore 1.2.0, Lifecycle 2.9.4, Coroutines 1.10.2 and Serialization
1.9.0 are pinned to avoid forcing a compiler/BOM migration. Activity Compose is raised to
1.11.0 for Navigation 3's modern back-navigation integration. No experimental dependency
or screenshot-test plugin is introduced. Preview configurations cover all skins on phone,
landscape and tablet, plus Georgian and large text Settings.

## Testing and next milestone

Local tests cover geometry, stack bounds, starting counts, theme fallbacks, every motion category,
real file-backed DataStore reopen, concurrent updates, unknown key preservation and ViewModel
read/write failure recovery. Unit tests do not substitute for device checks of navigation,
restoration, TalkBack, visual contrast, font scaling or animation performance.

Next: review portrait/landscape/tablet screenshots and interaction accessibility, then implement
a pure Kotlin rules engine with legal move generation and exhaustive position tests before
adding interactive movement. Audio, haptic playback, full animation choreography, gameplay
and Supabase are deliberately outside this milestone.

## Task 03 — rules engine

The pure Kotlin engine is now implemented independently of the unchanged static UI. Its reducer
accepts explicit opening rolls, supplied dice, checker moves, turn completion and reset. Full
ordered legal sequences include intermediate snapshots and enforce compulsory dice usage.
Board construction preserves exactly fifteen checkers per player; game results distinguish
single, gammon and backgammon. LocalDice is optional and separate from deterministic transitions.
See [engine/README.md](app/src/main/java/ge/gogichaishvili/backgammon/engine/README.md) for
coordinates, API, lifecycle and future ViewModel integration. No backend or animations were added.
