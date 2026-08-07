# KubeUI starter template

A minimal, working `client_scripts`/`server_scripts` pair to start a new KubeUI-based project from,
instead of copying a whole demo script from `testkubejs/` and stripping it down by hand.

## Using it

1. Copy `client_scripts/my_screen.js` and `server_scripts/my_actions.js` into your own mod's
   `kubejs/client_scripts`/`kubejs/server_scripts` - or generate this same layout into a target
   directory with `node scripts/create-kubeui-script.js <target-dir>` from the KubeUI repo root.
2. Rename `my_addon:say_hi` (and the `my_addon:` prefix generally) to your own namespaced action
   id, e.g. `yourmod:something`.
3. Requires KubeUI installed as a mod - see the main [README](../../README.md) for supported
   versions.

## What's here

- `client_scripts/my_screen.js` - opens a screen on world join with a button that calls a
  server-side action.
- `server_scripts/my_actions.js` - the server-side handler for that action, the only place a real
  decision (price, permission, reward, ...) should ever be made - see the main README's
  [Server integration](../../README.md#server-integration) section.
