# Sipatokkong Project Agent Guide

## App Description
**Sipatokkong** (Klub Belajar Sipatokkong) is a lightweight Android application designed to ensure therapy continuity for children from underprivileged families. It bridges the gap between volunteer therapist visits and home care by connecting:
*   **Volunteer Therapists:** Who assign weekly homework with mandatory demo media.
*   **Parents:** Who perform the therapy at home, upload proof, and provide behavioral feedback.
*   **Foundation Admins:** Who manage accounts and oversee progress for donor reporting.

The app is built with a focus on **low-literacy, low-bandwidth, and low-income users**, prioritizing data frugality and simplicity.

## Project Status Tracking
We maintain a living document called `project_status.md` in the root directory. This file serves as the single source of truth for:
*   **Technical Progress:** What has been implemented and what remains.
*   **Architecture Decisions:** Major technical choices and their rationale.
*   **Domain Constraints:** Hard requirements that influence development.
*   **Change Log:** A history of significant updates to the project.

Always refer to `project_status.md` before starting a new task to understand the current state and boundaries of the project.

### Automated enforcement

Two hooks in `.claude/` keep this document honest, so it cannot quietly drift:

*   **`SessionStart`** injects `project_status.md` into the agent's context automatically at the
    start of every session — no one has to remember to open it.
*   **`Stop`** refuses to let the agent end its turn while any watched source file
    (`app/src`, `supabase`, `gradle`, root Gradle scripts) is newer than `project_status.md`.

Both are stdlib Python scripts: hook-capable machines here have neither `jq` nor PowerShell 7,
and the commands are wrapped in `|| true` so a machine without `python` on PATH degrades to a
no-op rather than blocking anyone's work.

### Updating the Change Log

Add the newest entry at the **top** of the Change Log. Never delete older entries.

```markdown
### [YYYY-MM-DD] - Short Title
* **What changed:** files or areas touched, and why this approach was chosen
* **Verified:** only what was actually run — build succeeded, tests passed, tried on device
* **Not yet verified:** everything else, stated plainly
```

**Separate "verified" from "not yet verified" honestly.** This is the whole point of the
document. "It compiles" is not "it works". If something has not been run, say so — a future
session will otherwise assume it was, and build on a false premise.

Write for a reader with no access to the conversation that produced the change.

## Coding Conventions

### Language & UI
*   **Kotlin First:** All new development must be in idiomatic Kotlin.
*   **Jetpack Compose:** The UI is entirely declarative using Compose and Material 3.
*   **Material Icons Extended:** Use extended icons for better UX clarity.

### Architecture
*   **Manual DI (Service Locator):** We use a simple `ServiceLocator` object (located in `core.di`) for dependency injection. Do not introduce Dagger/Hilt unless specifically requested.
*   **MVVM Pattern:** Each screen should have a corresponding `ViewModel` that exposes state via `StateFlow`.
*   **Repository Pattern:** Data operations (Auth, DB, Storage) are encapsulated in repository classes.

### Backend & Security
*   **Supabase:** The backend is powered by Supabase (Auth, Postgres, Storage, Realtime).
*   **RLS-First Security:** All authorization is handled via **Row Level Security (RLS)** in Postgres. The client uses the `anon` key; permissions are determined by the user's JWT.
*   **Manual Session Management:** Authentication flows are managed via a custom `SessionManager`.

### Media & Data
*   **Bandwidth Frugality:** All media (photos/videos) **must** be compressed on the client side before upload.
*   **Target Size:** Images ≤ 200 KB, Videos ≤ 30s at 480p.
*   **Storage Path Convention:** Files are stored in private buckets using `{anak_id}/{yyyy}/{mm}/{uuid}.{ext}`.

### Post-Task Protocol (MANDATORY)
After completing any implementation, refactoring, or significant change, the agent **MUST**:
1.  **Update `project_status.md`**: Reflect the current state of implementation, update the Change Log, and note any new architectural decisions.
2.  **Verify Alignment**: Ensure the "Technical Progress" section accurately represents what is now live in the codebase.