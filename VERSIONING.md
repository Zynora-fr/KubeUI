# Versioning

KubeUI's own version (`mod_version` in [`gradle.properties`](gradle.properties), currently `0.3.0`)
follows [semver](https://semver.org/): `MAJOR.MINOR.PATCH`.

- **MAJOR** - a breaking change to the *script-facing* API: a method removed/renamed, a signature
  changed in a way existing scripts would need to update for, or a behavior change scripts could
  reasonably have depended on. Bumping this is the signal "read the changelog before updating".
- **MINOR** - new widgets, new builder methods, new globals (`KubeUISidebar`, new events, ...) -
  anything additive that doesn't break an existing script.
- **PATCH** - bug fixes, performance/robustness work, documentation - no script-facing API change.

This mirrors ordinary semver for a library; the twist is what counts as "the API" - it's
everything documented in the [README](README.md) as callable from a `client_scripts`/
`server_scripts` file (`KubeUI.*`, `KubeUISidebar.*`, `KubeUIActions.*`, `KubeUIEvents.*`, and the
methods on the `screen`/`KubeUIContext` object handed to callbacks). Internal Java classes
(`KubeUIScreen`, the individual widget classes, etc.) are not part of that contract and can change
shape freely between any versions, including patch releases.

## Minecraft/NeoForge/KubeJS compatibility

KubeUI targets **one** Minecraft version at a time (see [README.md](README.md#why-neoforge-only)
for why - KubeJS itself doesn't ship builds spanning multiple major Minecraft versions from a
single codebase). Practically:

- A new Minecraft version that changes GUI/layout APIs enough to require real code changes gets a
  **new major KubeUI version** on a version bump of its own, tracked against that Minecraft
  version - not a patch of the previous line. There's no promise of the same KubeUI major version
  working across Minecraft versions.
- Within a single Minecraft version, KubeJS updates that don't break KubeUI's plugin hooks
  (`KubeJSPlugin`, `EventGroup`, `CustomPacketPayload`, ...) don't require a KubeUI release at all;
  ones that do get a patch or minor release depending on whether KubeUI's own script API also had
  to change.
- The exact Minecraft/NeoForge/KubeJS trio a given KubeUI release was built and tested against is
  always in `gradle.properties` (`minecraft_version`, `neoforge_version`, `kubejs_version`) at that
  tag, and summarized in the [README's Versions table](README.md#versions) for the current one.
  Renovate (see [`renovate.json`](renovate.json)) keeps that trio proposed-in-sync as new releases
  land, but bumping it is still a deliberate, reviewed merge - not automatic.

## Pre-1.0

Everything before `1.0.0` is explicitly unstable: any release, including a patch, may include a
script-facing breaking change if it's needed to get the API right before committing to it long
term.

## Deprecation policy

Once KubeUI reaches `1.0.0`, a script-facing method scheduled for removal is marked `@Deprecated`
(with a doc comment pointing at its replacement) for at least one **MINOR** version before it's
actually removed in a **MAJOR** bump - never removed in the same release it's first deprecated in.

Before `1.0.0` this window doesn't apply - see "Pre-1.0" above, a pre-1.0 release may remove or
change something without a deprecation period. Even then, a removal significant enough to surprise
an existing script gets called out explicitly in [`CHANGELOG.md`](CHANGELOG.md), not left as a
silent diff someone has to notice on their own.
