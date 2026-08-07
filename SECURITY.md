# Security

## Reporting a vulnerability

Open a [GitHub issue](https://github.com/Zynora-fr/KubeUI/issues/new/choose) marked private (or a
direct message to the maintainer) rather than a public issue - see [SUPPORT.md](SUPPORT.md) for the
same convention used for abuse reports. There's no bug bounty or dedicated security contact beyond
that; this is a single-maintainer indie mod, not a company.

## What's actually in scope

KubeUI adds a client<->server networking layer (`.requirePermission(gate)`,
`screen.runServerAction(...)`, `KubeUIActions.openRemote(...)`/`.broadcastUpdate(...)`) on top of
KubeJS/NeoForge. The trust boundary that matters is: **a script's `server_scripts` action handler
is the only thing that should ever be trusted to decide something has actually happened** - nothing
client-side (a widget being enabled, a client-sent value) should be trusted for anything that
matters (price, permission, outcome). This was already the documented design from the very first
release (see the README's "Server integration" section) - this file documents it explicitly rather
than only implicitly through example scripts.

## Audit

A focused pass over the networking/permission surface: server->client pushes and a
permission-gated widget widened what's reachable over the network compared to the original,
simpler client->server-only design an earlier audit had already covered. Two real findings, both
fixed as part of this pass:

- **`KubeUINetworking.PENDING_ACKS`** (client-side) grew without bound if a server never replied to
  a `runServerAction(..., onAck)` call (didn't implement that action, or was simply broken) - each
  such call left its `onAck` callback in the map forever. Fixed with a cap
  (`MAX_PENDING_ACKS = 500`): past that, a new call is still sent, just without a tracked callback,
  logged instead of silently growing.
- **`KubeUIActions.LAST_CALL_AT`** (server-side per-player throttle timestamps) was never cleared
  when a player disconnected, unlike the adjacent `OPEN_SCREENS` map which already was - fixed by
  clearing both from the same disconnect handler.

Everything else reviewed looked sound by construction rather than needing a fix:

- Every registered action handler already runs inside a try/catch (`KubeUINetworking.processAction`)
  - a bug in one script's handler can't take the server's network thread down with it.
  - Schema validation (`KubeUIActions.validate`) rejects a payload with a missing/wrong-typed field
  before it ever reaches a handler, so a handler doesn't need to defensively re-check NBT shapes
  itself.
- Per-action throttling (`tryConsumeThrottle`) is checked *before* schema validation and the handler
  - a spam-clicking client can't burn server work re-validating/re-running a handler faster than the
  registered interval.
- `.requirePermission(gate)` is explicitly documented (and coded) as **decorative on the client
  only** (a greyed-out widget) - the real check happens server-side in `KubeUIPermissions.check`
  whenever an action handler is invoked; nothing assumes a disabled-looking widget is actually
  enforcement.
- The in-game file editor (`KubeUIFileEditor`, `/kubeui editor`) resolves every path against
  `KubeJSPaths.CLIENT_SCRIPTS` and rejects anything that normalizes outside it (`resolveSafe`) - a
  typo in the "new file" field can't reach an arbitrary path on disk. This is client-local file
  access only (the player editing their own files), not something exposed over the network.

Not attempted: real multiplayer load testing under adversarial conditions (no dedicated
server + multiple real clients available in this environment) and opt-in runtime error reporting
(would need an actual reporting backend to send to, which doesn't exist; a "send report"
button with nowhere real to send it would be worse than not having one).

## Project continuity

KubeUI is MIT-licensed, which already guarantees anyone can fork and continue it without needing
anyone's permission if it's ever abandoned - that's the actual continuity plan; there's no separate
succession arrangement beyond what the license already provides. This is currently a
single-maintainer project, which is worth being upfront about rather than implying otherwise.
