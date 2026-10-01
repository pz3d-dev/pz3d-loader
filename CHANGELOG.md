# pz3d Loader changelog

## Unreleased

- Use Gradle for builds and CI; keep portable tests under `tests/` and local environment tooling outside the repository.

## 1.0.1

- Remove the portrait and logo spacing; use `pz3d Loader` throughout the UI, code, packaging and integration.

## [Unreleased]

### Added
- Signed Workshop-delivered loader updates with offline verification, downgrade protection, deferred replacement after game exit and a backup of the previous JAR.
- Native selection tests for missing, disabled, incompatible and damaged pz3d packages; repeated activation and both JVM entry points; real main-menu checks through Mod Studio.
- Windows native launcher bridge and installer for ProjectZomboid64.exe, with bootstrap ownership detection for native JVM agents.
- Specialized PZ3D Java agent with mandatory offline verification of the existing Ed25519 signature.
- Runtime adapter for the shared PZ3D JAR, native mod-list activation and Lua exposure, and verified advice installation.
- Signed nested-core bootstrap packages with pz3d Loader priority in either agent order, and a ZombieBuddy fallback when pz3d Loader is absent.
- Compact text-only pz3d Loader indicator, explicit PZ3D startup states, native English text resources, and placement below ZombieBuddy's wrapped mod list.
- Independent launch profiles and ownership-checked uninstall scripts for PowerShell 7 on Windows, macOS and Linux.
- Isolated Byte Buddy dependency, checksum-pinned compilation, deterministic archives, JVM integration tests and a three-platform CI matrix.

### Changed
- Name the Windows bridge pz3dLoader.dll and load it with -agentlib:pz3dLoader.
- Expose pz3d Loader agent presence to Lua independently of core startup so pz3d can distinguish missing loaders from a failed or inactive engine.
- Display pz3d Loader 1.0 and the native pz3d mod version, use quieter panel colors and concise active/inactive states, and request a restart when an already loaded Java core is disabled.
- Read ZombieBuddy's package-private watermark metadata correctly so pz3d Loader stacks below it instead of covering menu buttons in the opposite corner.
- Isolate indicator resources from vanilla UI.json on the game classpath; preserve native translations and restore missing indicator text after early catalog initialization.
- Use lowercase pz3d in the indicator and clearer installation, activation and troubleshooting messages; record the naming rule in contributor instructions.
- Require Project Zomboid 42.21 or newer before loading PZ3D; show an unsupported-version indicator on older games and remove the old ArrayList mod-loading hook.
- Discover enabled PZ3D through the game-selected version/common folders; remove loader.properties and the installer ModInfo argument. Keep signature checks mandatory at runtime.
- Use the effective enabled mod list after native dependency resolution.
- Always verify package signatures; remove the `development` configuration option and installer switch. Tests use isolated loader copies with ephemeral test keys instead of a runtime bypass.
- Move loader classes to `com.pavelvoronin.pz3dLoader` and the bootstrap to its `.bootstrap` package; remove the obsolete `Pz3d` installer-mode alias.
- Replace the indicator monogram with the approved face logo, stored as a transparent 128px PNG and displayed at 64px or 128px without status tinting.
- Display `pz3d Loader` as the indicator title and remove the redundant loader-active label; keep the PZ3D status below it.
- Remove Russian indicator translations and use the native English fallback for all game languages.
- Use light blue for pending loader states and greener active/loaded text; keep the icon color constant across states.
- Name the loader artifact `pz3dLoader.jar` in builds, launch profiles and CI packages; retain pz3d Loader as the UI name.
- Preserve an existing ZombieBuddy agent and its settings when installing a pz3d Loader launch profile. Older flat PZ3D packages continue to use ZombieBuddy when both loaders are present.
- Name the loader pz3d Loader in the installer, diagnostics and CI artifacts.
- Fork ZombieBuddy at a403dafae4e8a37e1ccefda1936927782709f82d; retain its MIT notice and the patch matching helpers used by PZ3D.
- Remove the general mod loader, author registry, approval UI, unrelated game patches, Workshop content and native DLL installer.

### Maintenance
- Run loader ownership, mod-state and signed-update tests on Linux using native Java paths and temporary artifacts on the Linux filesystem.
- Preserve the original full-resolution face artwork for future website use, outside the packaged loader resources.
- Keep the production bootstrap, signing tool and separate PZ3D 0.4.1 test package outside the PZ3D checkout. Verify real-core startup, native Lua exposure, early reservation, missing registration and tamper rejection against ZombieBuddy 2.3.2; no fallback is attempted after startup failure.
- Reject an already loaded core, and install hooks on game classes that another agent loaded earlier.
- Validate native UI API signatures and generate an offline layout preview. On-screen appearance in a running game remains to be checked; unknown ZombieBuddy layouts use the opposite corner.
- Retain the isolated nested-core bootstrap experiment with unchanged ZombieBuddy 2.3.2, covering pz3d Loader priority, early and repeated startup, unrelated mods, and startup failures. The fallback adapter uses a version-specific internal ZombieBuddy API.
- Keep integration experimental: PZ3D retains its required ZombieBuddy dependency. Profile installation no longer requires an installed mod; the game controls mod availability at runtime.
- Local validation covers Windows JVM bootstraps with the real PZ3D JAR and original ZombieBuddy. Full in-game world/render validation and macOS/Linux execution remain separate checks; the CI matrix has not yet run remotely.
