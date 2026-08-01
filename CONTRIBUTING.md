# Contributing to KubeUI

## Reporting a bug

Open an [issue](https://github.com/Zynora-fr/KubeUI/issues/new/choose) using the bug report
template. The most useful thing you can include is a **minimal script** that reproduces it - a
5-line snippet is far easier to act on than "the shop screen is broken". Include:

- The KubeUI/KubeJS/NeoForge/Minecraft versions involved (see [README.md#versions](README.md#versions)).
- The exact error from `logs/latest.log` (or `crash-reports/`), if there is one.
- What you expected vs. what happened.

## Suggesting a feature

Open a [feature request](https://github.com/Zynora-fr/KubeUI/issues/new/choose). If it's a new
widget type or builder method, a short example of the script API you'd want to write helps more
than a prose description.

## Working on the code

1. Fork and clone the repo, then read the [README](README.md#building) for the JDK/build setup.
2. `./gradlew build` compiles and runs the unit tests; `./gradlew runClient` boots a dev client
   with [`testkubejs/`](testkubejs/) synced in - that's the fastest way to see a change working.
3. Match the existing style: no comments explaining *what* code does (only non-obvious *why*),
   tabs for Java indentation (see existing files), and every public method gets a `///` doc
   comment (see [README.md#project-layout](README.md#project-layout) for where things live).
4. If you're touching the Java side, verify the Minecraft/NeoForge/KubeJS APIs you're calling
   against real current sources rather than older tutorials - this codebase has been burned by
   API-shape assumptions before (see the `-sources.jar` files Gradle already downloads into
   `~/.gradle/caches`, and KubeJS's own source at
   [kube-mods/kubejs](https://github.com/kube-mods/kubejs)).
5. Add or update a `testkubejs/` script demonstrating the change where it makes sense - most of
   this project's confidence comes from those scripts actually being run, not just compiling.

## Pull requests

- Keep PRs focused - one change, one PR. Large unrelated cleanups make review slower, not faster.
- Label it (`feature`, `fix`, `documentation`, `chore`, `breaking`, ...) - that label drives the
  automated changelog (see [`.github/release-drafter.yml`](.github/release-drafter.yml)) and the
  version bump it implies (see [VERSIONING.md](VERSIONING.md)).
- CI (`.github/workflows/ci.yml`) runs a full build + a headless client boot check on every PR -
  it needs to pass before merge.

## Code of conduct

Be respectful, assume good faith, and keep discussion focused on the project. Reports of abusive
behavior can be sent to the maintainer via a GitHub issue marked private, or a direct message.
