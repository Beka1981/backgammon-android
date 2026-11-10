# Backgammon engineering guide

## Scope
Native Kotlin / Compose premium Backgammon. Never add backend connectivity, secrets, random game outcomes, or gameplay without an explicit milestone. Preserve the working Gradle wrapper, AGP, SDK and compiler versions.

## Boundaries
For now use one Gradle app module with extractable package boundaries:
- domain: immutable settings, theme definitions, repository contracts. No Android or Compose imports.
- engine: immutable game models, standard short-backgammon rules/reducer, injectable local dice, and normalized board geometry. No Android or Compose imports. Validate actions against whole-turn legal sequences; never bypass mandatory dice usage.
- data: DataStore implementations. Depends on domain, never presentation.
- designsystem: visual tokens and reusable rendering; consumes domain/engine models.
- animation: motion policy; visual animation must never generate game outcomes.
- presentation: ViewModels, immutable StateFlow UI state, stateless screens and Navigation 3.
- network: documented future transport boundary; no client or dependency until authorized.
- application container: explicit constructor injection. Adopt Hilt when multiple feature scopes justify it.

Data flows repository -> ViewModel -> lifecycle-aware Compose state. UI actions return through ViewModel; never write preferences from composables. Persist stable IDs, handle unknown values and storage failures. Keep a single DataStore instance per file. Use atomic preference edits.

## UI
Use original Canvas materials, centralized theme palettes and spacing/typography tokens. Do not scatter skin colors across screens. Keep game coordinates independent of pixels. Preserve aspect ratio, honor system insets, support rotation without manifest locks, and provide scrollable layouts for constrained windows and large font scales. Static board is a single accessible image; future interactive targets must expose semantic actions and at least 48dp targets without changing game geometry. Respect reduced motion and system animation scale. Strings belong in English and Georgian resources.

## Verification
Use Android Studio MCP read/search/edit tools (respect editor buffers). Run ./gradlew build test lint after changes. Add meaningful tests for geometry, persistence, migrations/fallbacks, and state handling. Add Compose previews for portrait, landscape, tablet and each skin. Report device/screenshot verification only when actually performed. No experimental dependencies without a concrete reason. No sub-agent delegation required.
