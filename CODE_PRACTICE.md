# Coding Best Practice

## Source Code Files
- All source code and script files always end with a blank line

## Kotlin / Java Source Code
- Always use explicit imports (`import com.sunnychung...`). Wildcard imports (`*`) are forbidden.
- Use `com.sunnychung.application.easytransfer` as the project base package. Never place source files, classes, or subpackages directly under `com.sunnychung.application`.
- Commit Kotlin formatting conventions: 4-space indentation, trailing commas where Kotlin style encourages, and idiomatic use of `when`, null-safety, and collection helpers.

## Shared Compose UI
- Share UI among all supporting platforms via Compose Framework unless specifically requested to exclude
- Every route-level screen composable must have a directly attached `@Preview` annotation and sensible default preview data/no-op callbacks so IDE preview tooling can invoke it without parameters.
- Keep previews colocated with their screen. Centralized device or scenario previews are useful additions, but do not replace the screen's own `@Preview` annotation.
- Keep composables layout-agnostic: always expose a `Modifier` parameter instead of relying on `BoxScope`/`ColumnScope` specific APIs unless unavoidable (e.g. WindowScope).
- Leverage `verticalArrangement` and `horizontalArrangement`, plus padding modifiers, instead of inserting many `Spacer`s.
- Keep composables small and focused. Pass only the data they require (prefer `data class` props over entire state objects).
- Extract reusable UI patterns into dedicated composables (e.g. a styled `AppTextField` should live in a helper composable, not inline in many places).

## General Guidance
- Prefer simple, robust solutions over clever but fragile ones. If a straightforward approach works without bugs, choose it.
- Keep logging concise and meaningful (`log.v/d/i/w/e`). Avoid leaking PII or noisy stack traces.
- Avoid duplicated code
