# Tutorial: your first KubeUI screen

A step-by-step walkthrough for a scripter who's never touched KubeUI before, from nothing to a
complete, server-authoritative screen. If you just want a working starting point to copy, see
[`templates/starter/`](templates/starter/) instead - this file is for actually understanding each
piece along the way.

## 0. Setup

You need KubeJS and KubeUI installed as mods, and a `kubejs/client_scripts/` folder to write JS
into (KubeJS creates this the first time it loads - see the main [README](README.md#building) if
you're setting up a dev environment for KubeUI itself rather than just using it as a player/pack
maker).

## 1. The smallest possible screen

Create `kubejs/client_scripts/tutorial.js`:

```js
KubeUI.builder('My First Screen')
    .label('hello', 'Hello, KubeUI!')
    .button('Close', screen => screen.close())
    .open()
```

Run `/kubejs reload client-scripts` (or join a world) - nothing shows up yet, because this only
*builds and opens* the screen the moment the file loads, not on any particular trigger. Wrap it in
an event so it opens when you actually want it to:

```js
function openTutorialScreen() {
    KubeUI.builder('My First Screen')
        .label('hello', 'Hello, KubeUI!')
        .button('Close', screen => screen.close())
        .open()
}

ClientEvents.loggedIn(event => {
    openTutorialScreen()
})
```

Reload, join a world - the screen opens. `.label(id, text)` is a text line; `.button(text, onClick)`
runs `onClick(screen)` when clicked - here, `screen.close()`.

## 2. Reacting to input

`screen` (passed to every callback) is a handle to the *already-open* screen - use it to update
other widgets instead of rebuilding everything:

```js
function openTutorialScreen() {
    KubeUI.builder('My First Screen')
        .label('status', 'Pick something')
        .button('Say hello', screen => screen.setLabel('status', 'Hello!'))
        .toggle('flag', 'Enable a thing', false, (screen, value) => {
            screen.setLabel('status', 'Thing: ' + value)
        })
        .button('Close', screen => screen.close())
        .open()
}
```

Every stateful widget has a matching `screen.getXyz`/`setXyz` pair - see the main README's
[widget table](README.md#using-it-from-a-kubejs-script) for the full list.

## 3. Layout

Elements stack vertically by default. `.row(...)` lays a group out horizontally instead - it takes
a callback that receives a *nested* builder:

```js
KubeUI.builder('Layout Example')
    .label('title', 'Pick a difficulty')
    .row(row => row
        .button('Easy', screen => screen.setLabel('title', 'Easy selected'))
        .button('Normal', screen => screen.setLabel('title', 'Normal selected'))
        .button('Hard', screen => screen.setLabel('title', 'Hard selected')))
    .button('Close', screen => screen.close())
    .open()
```

`.grid(columns, ...)` and `.scrollPanel(maxHeight, ...)` work the same way - see
[Layout](README.md#layout) in the main README.

## 4. Making it real: server-side actions

Everything so far only exists on the client - a player could see/change it, but nothing is
*trusted*. For anything that matters (a price, a permission, a reward), the decision has to be made
server-side. Create `kubejs/server_scripts/tutorial.js`:

```js
KubeUIActions.register('tutorial:say_hi', (player, data) => {
    player.tell('§aHello, ' + player.username + '! This came from the server.')
})
```

And call it from the client screen:

```js
.button('Say hi to the server', screen => {
    screen.runServerAction('tutorial:say_hi', {})
})
```

The client only ever sends the action id (`'tutorial:say_hi'`) and whatever data you choose to
include - never a decision. A real example (a shop where the server, not the client, decides the
price and whether the purchase succeeds) is in
[`testkubejs/server_scripts/kubeui_shop_real.js`](testkubejs/server_scripts/kubeui_shop_real.js) +
its client half; a bigger one (a small quest board with real persisted per-player progress) is in
[`testkubejs/*/kubeui_quest_board_example.js`](testkubejs/server_scripts/kubeui_quest_board_example.js).

## 5. Where to go next

- The main [README](README.md) is the full reference - every widget, layout option, and script API.
- [`testkubejs/client_scripts/kubeui_test_menu.js`](testkubejs/client_scripts/kubeui_test_menu.js)
  is a menu linking every demo script in this repo - open it in a dev environment
  (`./gradlew runClient`) and click through them to see a feature before reading its docs.
- [`templates/starter/`](templates/starter/) is this same client/server pair as a copyable starting
  point, generatable with `node scripts/create-kubeui-script.js <target-dir>`.
