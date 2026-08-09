# Changelog

## 0.4.0

Nothing has actually shipped since [v0.3.0](https://github.com/Zynora-fr/KubeUI/releases/tag/v0.3.0) -
everything below landed as one continuous, still-unreleased development cycle, so it's kept as a
single `0.4.0` entry instead of a new version number for every batch of work.

**Combat & status HUD:** `KubeUIActions.showBossBar(viewers, barId, name, health, maxHealth, phases)`
draws a fully custom boss bar, top-center - `phases` is a list of `{threshold, color, text}` objects,
so a fight's bar can change color/subtitle as health crosses a threshold, something vanilla's own
boss bar has no concept of. `KubeUIActions.defineStatus(id, name, icon)`/`.applyStatus(...)` add
purely scriptable statuses on top of real vanilla `MobEffect`s in one unified buff/debuff overlay -
a status here has no built-in behavior of its own, its entire "tick logic" lives in a script's own
`KubeUICombatEvents.statusTick(event => {...})` handler. `KubeUIActions.startCooldown(player, id,
durationTicks)` gives a script-defined cooldown (not tied to a real item, unlike vanilla's own
`Player#getCooldowns()`) a real HUD bar above the hotbar. A combat-log HUD (toggleable per player,
`KubeUIActions.setCombatLogEnabled(...)`) and an AOE/range indicator ring
(`KubeUIActions.showAoeIndicator(player, radius, color)`) round out the overlays. Every real hit is
tracked automatically off `LivingDamageEvent.Post` into a per-player "combat session" - once a
player goes a few seconds without landing or taking a hit, `KubeUICombatEvents.combatEnded` fires
with the session's damage dealt/taken and duration for a script's own post-fight recap, and
`KubeUIActions.combatHistory(player)` reports every tracked session back.

**Structures & dungeons:** `KubeUIActions.lootTableBrowser(player, lootTableId)` previews a real
vanilla `LootTable`'s probable contents - genuinely rolled 200 times and tallied (a loot pool's
internal weights aren't generically readable, and plenty of entries are conditional on context a
preview doesn't have), always presented as "probable", never a guaranteed drop list.
`KubeUIActions.defineStructure(id, name, difficulty, ...)` declares a scriptable info zone - opt-in
per player, a toast on approach. `KubeUIActions.defineDungeon(id, name, roomIds)` tracks room/chest/
boss progress per player, with real progression locking (a room only unlocks once the previous one
in the list has actually been visited) and an automatic completion-time leaderboard the moment every
room's visited and the boss goes down. `KubeUIActions.defineLootTable(id, entries)`/`.rollLoot(id,
difficultyMultiplier)` add a scriptable weighted-random loot table independent of vanilla's own (a
higher multiplier skews the odds toward rare entries, without hand-authoring a second table per
difficulty tier) - `KubeUIActions.startLootVote(...)`/`.castLootVote(...)` add real server-arbitrated
need/greed group loot voting on top of it (need always beats greed, ties broken randomly within the
winning group), the voting screen itself left to a script via the same generic server-push mechanism
every other custom screen in this mod already uses.

**Economy:** a scriptable, server-authoritative currency system independent of any physical item -
`KubeUIActions.registerCurrency(id)`, `.pay(player, currency, amount)`/`.charge(...)` for atomic
credit/debit that never leaves a silent negative balance, `.transferCurrency(from, to, currency,
amount)` with a configurable commission (`.setCurrencyTransferTax(...)`), and
`.exchangeItemForCurrency(...)`/`.exchangeCurrencyForItem(...)` to bridge it with real inventory
items. `KubeUIActions.defineShop(shopId, name, stock)` builds a full vendor - buy/sell, per-line
stock limits, and optional price fluctuation (a bounded random walk on a configurable interval) -
opened with `KubeUI.shop(shopId)`. `KubeUI.currencyHistory(currency)` shows a player's own
transaction log, `KubeUI.leaderboard(currency)` the richest players server-wide (built on the
existing sortable table widget), and the new `KubeUIEconomyEvents.transaction`/`.balanceChanged`
event group (KubeUI's first `server_scripts` event group) lets a script react to every balance
change. Every shop in the project (including the "real"/emerald and "validated" reference demos)
now runs on this same currency instead of ad-hoc raw-item or in-memory gold tracking, so balances
actually persist across restarts everywhere. The real `/money balance [currency]`/`pay`/`deposit`/
`withdraw` command (deposit/withdraw need OP) gives players and admins chat access to the same
system without needing a script, and `ServerEvents.commandRegistry` (KubeJS's own real command
event - see `kubeui_admin_commands_example.js`) is the documented way to add further custom admin
commands on top of `KubeUIActions.pay`/`.charge` without touching Java at all. Fixed a real bug
where a singleplayer world's registered currencies could disappear (`/money balance` reporting "no
currencies registered" even with a `KubeUIActions.registerCurrency(...)` script loaded): on an
integrated server, `server_scripts` run *before* `ServerAboutToStartEvent`, and the currency
ledger's own startup load was unconditionally clearing the in-memory registry that scripts had
just populated.

**Machines & automation:** `KubeUIActions.defineMachine(kind, name, inputItem, inputCount,
outputItem, outputCount, processTicks, energyPerTick, redstoneMode)` declares a recipe kind for the
one real, placeable `kubeui:machine` block (NeoForge's registries are frozen long before any
`server_scripts` reload runs, so a script can't register a genuinely separate block per kind the way
it can define one server-side) - `KubeUIActions.giveMachineItem(player, kind)` hands out a copy
already set to that kind, with the kind's own real display name stamped onto the item itself
(`DataComponents.CUSTOM_NAME`) so a Crusher and a Smelter already read as two different things in
the inventory/hotbar tooltip, not just after placing one and opening its screen - also now logs a
clear warning if a script calls this before `defineMachine` ever registered that kind, the real
cause behind a placed machine silently showing a generic name and a "No recipe" status. A machine
has four real slots (input/output/upgrade/fuel),
ticks its recipe while it has enough input and (if `energyPerTick > 0`) enough energy, and respects
`redstoneMode` (`"ignore"`/`"requireSignal"`/`"disableOnSignal"`). Energy comes from a real fuel
slot burning the same vanilla-furnace fuel table (`level.fuelValues()`) any real furnace does -
coal, charcoal, a lava bucket, planks, and everything else vanilla or another mod registers as fuel
all work, including the real "empty bucket left behind" behavior - or from
`KubeUIActions.chargeMachine(level, pos, amount)`, the real integration point for a future energy-
mod bridge. `KubeUIActions.registerMachineUpgrade(itemId, speedMultiplier, yieldBonus)` lets an item
dropped in the upgrade slot speed up processing and add bonus output. `.linkMachineOutput(level,
from, to)` chains one machine's output straight into another's input, and `.setMachineNetwork(...)`
groups machines for `KubeUI.machineNetworkStatus(networkId)`'s aggregated controller/stats view. The
machine screen lays the four slots out the same recognizable input-above-fuel-with-a-flame-between-
them, output-off-to-the-side-via-an-arrow shape a furnace-like screen always has, shows full slot
names, a live crafted-count, a small hand-drawn flame that lights up while fuel is actually burning,
a compact energy gauge, and the machine's real per-kind name in its title bar - all drawn as a
themed custom panel (matching every other KubeUI screen's colors) instead of a reused, mismatched
vanilla furnace sprite. A short status line ("No recipe" / "Blocked" / "No input" / "Needs fuel" /
"Output full" / "Running...") next to the crafted-count now says outright why a machine isn't
processing right now, instead of a player only ever finding out from a chat alert throttled to once
per 30 seconds. A block placed with no kind (or an unregistered one) - the real cause of "No
recipe" for anyone who only ever finds a generic, unconfigured `kubeui:machine` (creative inventory,
`/give`, ...) rather than one already given via `giveMachineItem` - now shows a real in-GUI picker
instead: every registered kind's real icon and name, synced the same way a trade screen's own offer
list already is, click one to become that kind on the spot. Fixed two real reported bugs along the
way: the screen's drawn slot frames and the
menu's actual item slots had drifted to different coordinates (putting every item and label in the
wrong spot), and the energy gauge was an oversized, disconnected bar with nothing tying it to the
fuel slot feeding it.

**World map:** `KubeUI.worldMap()` opens a full-screen, pannable (drag or arrow keys)/zoomable
(scroll wheel) map built on the same real per-block color sampling as the existing HUD minimap
widget (`.map(id, radius)`), backed by a persisted, progressively-revealed explored-cell cache -
areas you haven't actually visited stay blank rather than faked. `KubeUIMapIcons.registerIconProvider(...)`
lets other systems plot colored live-entity markers on it (`KubeUI.registerMapIcon(entityTypeId,
color)` covers the common case). Waypoints (`/kubeui map waypoints`, or "Add Waypoint Here" on the
map itself) persist across sessions and can be shared to another player over the network. The
screen is now styled like a familiar minimap-mod layout: a docked top toolbar, a docked waypoint
sidebar (jump-to and remove per entry, no command needed), a bordered viewport with a compass tick
and a scale bar, and a live coordinate/biome/zoom readout under the cursor. Each sidebar entry also
has a "Trk" toggle - like Journeymap's own per-waypoint "show on HUD" option, it keeps that
waypoint's direction/distance visible top-center of the screen even with the map closed (fixed a
real gap where the HUD indicator itself already existed but nothing ever let a player actually
enable it for a waypoint).

**Dialogue:** `KubeUIActions.defineDialogue(id, rootNodeId, nodes)` declares a branching NPC
conversation - portrait (auto-detected as an entity type or an item), typewriter-revealed text,
an optional per-node timer that falls through to a default choice, and per-choice `requires` gates
checked against real player state (quest progress, currency balance, inventory) - a choice that
fails its gate is simply never sent to the client, never shown disabled. Opened with
`KubeUI.dialogue(dialogueId, npcUuid)`, or automatically by right-clicking an entity tagged via
`KubeUIActions.tagDialogueNpc(entity, dialogueId)`/`/kubeui tag-dialogue-npc`.
`KubeUIActions.dialogueHistory(player, dialogueId)` reports every node a player has actually
reached, persisted across restarts, so an NPC can remember a past conversation.

**Storage:** `kubeui:storage_crate` - KubeUI's first real, placeable block, backed by a genuine
server-side container (not a client-side simulation). Right-clicking one opens a real chest-style
screen with a sort button (name/count/category), a live search field that dims non-matching slots,
and a settings panel for item whitelist/blacklist filters, owner-managed player authorization
(lock the crate to specific players), and linking it into a named network -
`KubeUI.storageNetworkView(networkId)` shows the combined contents of every currently-loaded crate
on that network from anywhere, without opening any of them physically. Every deposit/withdrawal is
logged (`KubeUI.storageHistory()`). `kubeui:backpack` is a wearable-inventory item with its own
18-slot container that survives death and changing hands correctly, since it's backed by the same
real item-container data a shulker box uses.

**Progression:** `KubeUIActions.defineSkillTree(id, nodes, options)` builds a real talent tree -
node prerequisites, a point cost per node, and real vanilla `AttributeModifier` effects (not just
a visual unlock) applied the moment a node is bought. Points come from leveling up
(`pointsPerLevel`), or from anywhere a script wants via `KubeUIActions.grantSkillPoints(...)`
(a quest reward, for instance). `classGroup` on a node lets a tree define mutually-exclusive
specializations - unlocking the first node of a group commits the player to it until they respec
(`KubeUI.skillTree(id)`'s Respec button, `KubeUIActions.respecSkillTree(...)`, optionally priced in
any currency from the new economy system). A permanent level/XP bar now sits at the bottom of the
HUD, the skill tree screen shows a title badge for how many nodes a player has unlocked, and
`KubeUI.skillLeaderboard(id)` ranks currently-online players by total points earned.

**Guilds & factions:** `KubeUIActions.createGuild(founder, guildId, name)` builds a real,
disk-persisted guild - a fixed owner/officer/member role hierarchy checked server-side on every
management action (`.inviteToGuild`/`.kickFromGuild`/`.setGuildRole`), a dedicated guild chat
channel (`.sendGuildChat`), collective XP/leveling with a leaderboard
(`.addGuildXp`/`.guildLevel`/`.guildLeaderboard`), a scriptable relationship between two guilds
(`.setGuildRelation`, `"ally"`/`"rival"`/`"neutral"`), a scriptable claimed territory with an
entry toast for non-members (`.claimGuildTerritory`), and a badge (color + icon, resolved the same
way a skill node's own icon is) a script can show wherever it wants. `.authorizeGuildOnStorage(...)`
bulk-authorizes every current guild member on an existing storage crate, reusing its real
per-player authorization list rather than a second access-control system.
`KubeUIGuildScriptEvents.scheduledEventTriggered(event => {...})` fires once a
`KubeUIActions.scheduleGuildEvent(...)` timer elapses, for a script to distribute rewards through
the existing quest-reward system.

**Social:** a persistent per-player friends list (`KubeUIActions.friendsList`/`.addFriend`),
stored directly on the player's own real persistent data rather than a second registry. A
temporary, session-only party system (`.createParty`/.inviteToParty`) with optional XP/loot-sharing
flags a script consults at the real moment it would grant either. A `/kubeui tpa`-style teleport
request that actually needs the target to accept (`.requestTeleport`/`.acceptTeleport`, a real
pending request that expires after a minute) instead of moving a player with no warning. Live
presence (`.presenceOf`, `"online"`/`"in_combat"` computed for real off the existing combat-session
tracker/a script-set custom status). Party and real proximity ("local") chat
(`.sendPartyChat`/`.sendLocalChat`) alongside guild and vanilla global chat, with a tabbed channel
switcher in the reference demo. A purely client-side, never-shared block/mute list
(`KubeUI.blockPlayer`/`.isPlayerBlocked`) and private contact notes (`.setContactNote`/
`.contactNote`), both using the same local, compressed-NBT persistence waypoints already
established. Opt-in friend-login toasts (`KubeUIActions.setFriendActivityNotifications`), and an
emote wheel built from a real circular button layout (`.absolute(x, y)`, already delivered)
broadcasting through the same real proximity chat rather than a dedicated new widget.

**Housing & claims:** `KubeUIActions.claimLand(owner, claimId, x1,y1,z1,x2,y2,z2, dimension)`
claims a real, disk-persisted bounding box for one player - the same real shape guild territory
already has, plus real per-claim members and arbitrary scriptable action permissions
(`.addClaimMember`/`.setClaimPermission`/`.claimPermissionAllowed`), temporary revocable visitor
access (`.inviteClaimVisit`/`.revokeClaimVisit`), and paid rentals through the existing currency
system with automatic real-time expiration (`.rentClaim`). `.mergeClaims`/`.splitClaim` combine or
divide claims (a real, deliberately simple bounding-box merge and an axis/coordinate split, not a
strict-adjacency system). An unauthorized entrant triggers a real toast to the owner
(`.setClaimLimits` caps how many/how large a player's claims can be). `KubeUIActions.setHome(...)`/
`.teleportHome(...)` add named personal spawn points beyond vanilla's single bed, stored directly
on the player's own persistent data. A real `/claims` command lists a player's own claims, and
`/home set`/`.remove`/`.list`/`.tp` (the last with a real cooldown) give chat access to homes
alongside the existing GUI - see "Real slash commands, JS-only" below.

**Settings hub:** `KubeUISettingsHub` (a new client-only global, same real "public standalone
class" shape `KubeUISidebar` already established) lets any mod - including third-party ones with no
hard dependency on KubeUI - register a settings section (`.register(modId, name, section => {...},
declaredDefaults)`). `.matching(query)` backs a searchable hub screen; `.conflicts()` flags two
sections that declared different default values for the same settings key. Named profiles
(`.setActiveProfile`/`.setValue`/`.getValue`) store real `profile -> scope -> key -> value` data - a
`scope` the caller chooses itself (`"global"` or a server address it picked), so per-server-vs-
global behavior is possible without KubeUI needing to infer "which server" on its own -
`.exportProfile`/`.importProfile` share one as portable JSON, the same real mechanism `/kubeui
export`/`import` already established. `.showOnboardingOnce(id, () => {...})` shows a wizard exactly
once per install. `KubeUIActions.serverDashboard(player)` gives operators a real snapshot of every
major KubeUI system active on the server (currencies, guilds, claims, dungeons, quests, online
players) - empty for anyone without real operator permissions.

**Machine screen fixes & redesign:** fixed a real bug where an already-kinded machine (e.g.
"Crusher") could show the kind picker again instead of its normal panel - the picker's status code
used to double as "kind not chosen yet" *and* "kind chosen but its recipe items don't currently
resolve", so the second case silently reopened the picker on an already-configured machine. The two
are now distinct statuses; an invalid kind shows a plain error instead of quietly asking to pick
again. The whole screen (and the kind picker) got a real visual pass - gradient panel/bars,
bevelled inset slots, and full-width chamfered picker cards with hover highlighting, all still
drawn from [`GuiGraphicsExtractor`](src/main/java/dev/kubeui/gui/KubeUIMachineScreen.java)
primitives, no texture asset. The boss bar HUD is noticeably shorter (thinner bar, tighter
spacing).

**Real slash commands, JS-only:** guild naming (`/guild create`/`.rename`/`.invite`/`.kick`/
`.info`), party management (`/party create`/`.invite`/`.leave`/`.list`), and the housing commands
above - all built entirely with KubeJS's own `ServerEvents.commandRegistry` in
`testkubejs/server_scripts/kubeui_guild_party_housing_commands.js`, not new Java command classes,
on top of a handful of small additive `KubeUIActions` methods (`.renameGuild`, `.guildNameOf`,
`.claimsOf`). A guild member's tag now also shows automatically in regular chat (not just the
dedicated guild channel), via a real chat-decoration hook
([`KubeUIChatEvents`](src/main/java/dev/kubeui/plugin/KubeUIChatEvents.java)).

**Tabbed social screen:** `/social` opens a Général/Teams/Party screen built from
`KubeUISocialHub` (a new client-only global, same "public standalone class" shape `KubeUISidebar`/
`KubeUISettingsHub` already established) - `.registerTab(id, label, order, tab => {...})` lets any
script add its own tab alongside the three defaults, with no Java change needed either way.

**Custom leaderboards:** `KubeUIActions.setLeaderboardScore(boardId, player, score)` /
`.leaderboardTop(boardId, limit)` / `.leaderboardScoreOf` / `.removeLeaderboardScore` /
`.clearLeaderboard` / `.leaderboardIds` - a general-purpose, disk-persisted score store a script can
build any leaderboard on top of (a minigame, a farm total, anything), alongside the already-specific
`.guildLeaderboard`/`.dungeonLeaderboard`.

**Guild/party/housing GUIs:** bare `/guild`, `/party`, `/home` and `/claims` (no subcommand) now
open a real screen instead of chat-only output - create/rename a guild and invite/kick members,
manage a party, set/remove/teleport-to homes (and see the claims list), all from
`testkubejs/client_scripts/kubeui_default_screens.js`. Any of the four
(`kubeui:guild_hub`/`kubeui:party_hub`/`kubeui:home_hub`/`kubeui:claims_hub`) can be replaced by a
pack's own screen with nothing more than `KubeUIRemoteScreens.register(sameId, yourOwnHandler)` in
a script that loads afterward - registering the same id again is a real, already-established
override (`KubeUIRemoteScreens` replaces, not appends), not a new mechanism built just for this.

**Guild/party rules are now scriptable, not fixed:** the guild tag shown in regular chat used to
be hardcoded in Java - it's now a real `server_scripts` event
(`KubeUIChatScriptEvents.decorate(event => {...})`, `event.getMessage()`/`.setMessage(...)`/
`.cancel()`) that Java only posts; the tag itself is applied by a script listening to it
(`kubeui_guild_party_housing_commands.js`), so changing the format, adding more decorations, or
dropping it entirely needs zero Java. The guild role hierarchy (`KubeUIActions.setGuildRoles([...])`,
default `["member", "officer", "owner"]`) and which role each action needs
(`.setGuildActionRequirement("invite"/"kick"/"setRole", role)`) are both script-settable now, as is
the leveling curve (`.setGuildLevelCurve(maxLevel, xpPerLevel)`). Party invites are leader-only by
default but `.setPartyAnyoneCanInvite(true)` opens that up, and `.setPartyMaxSize(n)` caps party
size (both unset/unlimited by default, matching the old fixed behavior exactly until a script says
otherwise) - "vraiment tout en JS, sans toucher au Java" (real, explicit ask).

**`KubeUIHud` - build your own HUD overlay, not just modal screens:** every HUD element in this
mod (boss bar, combat log, waypoint tracker, ...) used to be hardcoded Java with zero script
control over how it looks - "on peut créer nos propres GUI et UI en JS" for those too (real ask).
`KubeUIHud.setBar(id, {anchor, x, y, width, height, value, max, barColor, bgColor, borderColor,
label, labelColor})` and `.setLabel(id, {anchor, x, y, text, color, centered, shadow})` register
(or update - call again with the same id) a fully custom bar/label rendered every frame by a new,
general [`KubeUIHudRenderer`](src/main/java/dev/kubeui/plugin/KubeUIHudRenderer.java) -
`.removeBar`/`.removeLabel`/`.clear()` take them back off. `anchor` (`"topLeft"` (default)/
`"topCenter"`/`"topRight"`/`"bottomLeft"`/`"bottomCenter"`/`"bottomRight"`) resolves fresh every
frame against the real current screen size, so a script never has to compute absolute coordinates
itself. See `kubeui_hud_demo.js` for a real, live example (a custom health bar tracking the local
player, positioned like a boss bar) - entirely client-side, no server round trip needed.

Full documentation: [github.com/Zynora-fr/KubeUI](https://github.com/Zynora-fr/KubeUI#readme).

Requires KubeJS on NeoForge - see the README's Versions table for the exact
Minecraft/NeoForge/KubeJS versions this build targets.

## 0.3.0

**Theming & accessibility:** named theme presets (`"dark"`/`"light"`/`"high-contrast"`, or a custom
one via `KubeUI.registerThemePreset(...)`) that fade between each other instead of snapping; a
colorblind-safe default `.colorPicker()` palette; `.style({color, accent})` to override a single
element's colors beyond the global theme; `KubeUI.setFontScale(...)` to enlarge text independently
of box sizes; `KubeUISidebar.setIconPack(...)` to reskin the whole sidebar in one call;
`/kubeui theme preview <name>`; `Key`-suffixed translated variants of every text-bearing widget
(`.buttonKey()`, `.toggleKey()`, `.textFieldKey()`, `.textAreaKey()`, `.tooltipKey()`,
`.narrationKey()`, `.badgeKey()`); and a visible keyboard focus outline plus full keyboard
operability on every custom widget that was missing one (range slider, keybind capture field,
context menus).

**Update checking:** the player is told in chat, on joining a world, whether they're running the
latest KubeUI version - via NeoForge's own built-in update checker, sourced from the real
CurseForge project (no custom network code, no API key).

**Server networking:** the server can now push a screen to a specific player or broadcast one to
everyone (`KubeUIActions.openRemote(...)`/`.broadcastUpdate(...)`, received via
`KubeUIRemoteScreens.register(...)`); `.requirePermission(gate)` gates a widget behind NeoForge's
real permission API (LuckPerms or equivalent can plug in); `.screenId(id)` lets the server know
which screen a player currently has open (`KubeUIActions.getOpenScreenId(...)`); actions can be
throttled per player (`KubeUIActions.register(id, throttleMs, handler)`) and schema-validated
before they ever reach a handler; `screen.runServerAction(..., onAck)` confirms an action actually
ran; and the server can show/hide a sidebar icon per player.

**Config & persistence:** KubeUI now has a real disk-backed config (`config/kubeui-common.toml`) -
the personal scale/font scale/theme a player sets are remembered across restarts by default;
`.draggable()` window positions persist across game launches, not just within a session;
`/kubeui export`/`/kubeui import` moves a player's KubeUI preferences between installs;
`KubeUIActions.playerData(player)` gives a script real per-player server-side storage with no
setup; and `KubeUI.configScreen(schema)` builds a settings screen straight from a data description
instead of a hand-chained builder.

**In-game script editor:** `/kubeui editor` opens a real file manager/text editor for
`kubejs/client_scripts` without leaving the game - list, create, open, edit and delete any `.js`
file, then "Save & Reload" to see the change live immediately (the same reload behind `/kubejs
reload client-scripts`). Replaces two earlier attempts (a click-to-build visual editor that didn't
hold up in real play, then a single fixed canvas file meant to be edited externally) with actual
in-game text editing.

**Developer tooling:** `.when(condition, b => ...)`/`.repeat(count, (b, i) => ...)` builder helpers;
`KubeUI.describe(builder)` for a human-readable widget/id listing; `KubeUI.lint(builder)` to catch
duplicate ids before a screen ever opens; `KubeUI.toJson(builder)`/`.fromJson(json)` and
`screen.dumpTree()` for a JSON snapshot of a screen's layout (a representative widget subset);
error/warning messages now name the widget type and id involved; a slow script callback (50ms+) is
now logged instead of just silently making the game stutter; `/kubeui profile` and
`/kubeui stresstest` for build-time timing; and a real NeoForge "Config" button next to KubeUI in
the Mods list (not the Fabric-only ModMenu the idea was originally framed around).

**Robustness:** a runaway recursive layout helper now fails with a clear error instead of a
`StackOverflowError`; `.draggable()`/persisted widget state and pending server-action
acknowledgements are now capped instead of growing unboundedly for a long-running client or a
server that never replies; and a player's per-action throttle state is now actually cleared on
disconnect (a real gap a security-focused audit found alongside the above).

**Docs & community:** a [`TUTORIAL.md`](TUTORIAL.md) walkthrough from the first `KubeUI.builder(...)`
to a complete server-backed screen; a [`templates/starter/`](templates/starter/) starter
project (generatable with `node scripts/create-kubeui-script.js <dir>`); a bigger worked example
(a quest board with real per-player server-side progress, `testkubejs/*/kubeui_quest_board_example.js`);
and a [`SECURITY.md`](SECURITY.md) covering the project's security policy.

**Recipes:** `KubeUI.recipeScreen(recipeTypeId)` opens a screen showing every recipe of a given
type (vanilla or a script's own custom one) automatically, and `KubeUI.recipesFor(itemId, onResult)`
looks up every recipe that accepts a given item as an ingredient - both work generically across
recipe types, real server-side data (not guessed or client-only), shown in a properly-sized,
scrollable screen. `.recipeSlot(id, itemIds, onClick)` is a new JEI-style widget that cycles
through a group of acceptable items instead of showing just one.

**Custom recipes:** `/kubeui recipe-designer` opens an in-game screen to define your own
crafting/furnace/blast-furnace/smoker/stonecutter/smithing-table recipes - pick a kind, then
arrange real items (any item from your own inventory, dragged in like the real thing) in that
kind's actual vanilla interface, and left-click the result slot to save. Saving/deleting doesn't
reload automatically anymore (a full data-pack reload on every click was a real lag spike) - a
"Reload Recipes" button in the designer applies everything you've changed in one go.

**Custom trading:** `/kubeui trader-designer` opens an in-game screen to build a custom trader
entirely without scripts - add trades in a real item-slot GUI (place the cost items and the result,
left-click the result to add it), toggle whether it has AI and whether it can move, then give
yourself the finished trader as a real item (using the actual villager spawn egg icon) and
right-click a block to spawn it, exactly like a vanilla spawn egg. Trading with it uses the real
villager trading screen too, laid out just like a real villager with a profession - a trader with
several trades shows them as a real list of cost/result icons (handy since one trader having
multiple trades is the common case), and picking a row pulls its cost items from your own inventory
into the payment slots automatically, same as clicking an offer on a real villager - left-click the
result to receive it and pay. A trader's trades are baked directly onto the spawned villager itself,
so they're still there after closing and reopening the game - not lost on a server restart like an
earlier build.
`KubeUIActions.registerTradePool(poolId, trades, condition?)` still defines a
weighted-random pool of trades from a script (with configurable stock/restocking and optional
reputation/quest gating) for anyone who wants that lower-level control, and
`KubeUIActions.tagTradePool(entity, poolId)` turns any entity into a trader that way instead.
`/kubeui villager-trades <target>` inspects an entity's current trades/stock either way, and
`KubeUIActions.tradeHistory(player)` reports what a player has already bought.

**Quests:** `KubeUIActions.defineQuest(id, { title, description, requires, objectives, rewards })`
defines a real, server-tracked quest - built-in objective types cover collecting items, killing a
kind of entity, visiting a position or structure, and reaching an XP level, and any other type is a
plain counter your own script bumps from whatever event it wants
(`KubeUIActions.incrementQuestObjective(...)`), no callback registration needed. `requires` chains
quests together (a quest only becomes available once its prerequisites are completed), and
progress lives on your own player data - it's still there after closing the game entirely.
`KubeUI.questLog()`/`/kubeui quest-log` (also reachable as the shorter `/quest`) shows every quest
and your progress on each, grouped by status; a small always-on-screen tracker (`Track` button in
the log) shows your current objective without needing the log open. `KubeUIActions.tagQuestGiver(entity, questIds)`/
`/kubeui tag-quest-giver` turns any entity into a quest giver - right-clicking it opens a real
Accept/Turn-in screen, server-verified on every click so nothing can be granted twice.
`/quest accept <questId>`/`/quest complete <questId>` do the same without needing a giver entity
at all, for a server that wants quests reachable purely by id.
`/kubeui quest-editor` composes a quest entirely in-game, no script required, and - unlike a
script, which re-declares its quests every boot - saves it for real so it survives a server
restart too.

**Look & feel:** `KubeUIScreenBuilder.windowBackground(texture)` gives a screen a real nine-slice
panel drawn behind its whole content (not just a decorative strip) - the quest screens, recipe
designer, trader designer, script editor, and recipe browser each now have their own real,
custom-drawn panel texture instead of the plain default look: a dark panel with rounded corners
and a soft glowing accent border, each screen's own accent color, sharing one consistent style so
it's obvious at a glance which screen is open. The custom trader's trade/payment screens no longer
reuse vanilla's villager trading texture either - they're now the same custom-drawn panel style,
with real hand-drawn slot frames instead of texture-baked ones.

**Fixes:** a script's `runServerAction` handler is now actually wrapped in a try/catch server-side
(a prior gap where a bug in one could take down the packet-handling thread); the quest board
reference example now actually takes the required items from the player's inventory on completion
instead of only checking a flag; a custom smelting/blasting/smoking recipe from the recipe designer
now actually clears any existing recipe for the same input first, so it can't be silently shadowed
by an existing one (e.g. a custom log recipe losing out to vanilla's own log-to-charcoal recipe);
the trader designer's trade list and the recipe browser's ingredient arrow were sized wrong (a
label with no explicit width silently claims the whole panel's width) and could overflow well past
the screen on some window sizes - every built-in screen has been re-audited for the same mistake;
a custom trader's trade-offer list built its menu slots in the wrong order internally, which could
select the wrong offer when clicking a row in the list (not just a display glitch - the payment
slots that filled in could genuinely be for a different trade than the one clicked).

Full documentation: [github.com/Zynora-fr/KubeUI](https://github.com/Zynora-fr/KubeUI#readme).

Requires KubeJS on NeoForge - see the README's Versions table for the exact
Minecraft/NeoForge/KubeJS versions this build targets.

## 0.2.0

**New widgets:** rich text, status badges, star ratings, loading spinners, nine-slice panel
backgrounds, live entity/block previews, rebindable keybind fields, sortable data tables,
collapsible trees, bar/line charts, a Journeymap-style minimap always centered on the player,
reorderable/selectable/grouped/paginated list variants, split panes, accordions, breadcrumbs,
multi-step wizards, range sliders, stepped sliders, date pickers, searchable and multi-select
dropdowns, a debounced search box, and a resource picker.

**Layout:** `grow` (flexbox-style leftover space distribution in rows), `absolute`/`zIndex`
positioning outside the normal layout flow, responsive width breakpoints
(`.width({default, small, tiny})`) beyond plain percentages, and split panes.

**Rendering:** a real GPU `renderScale` (unlike the pixel-size `setScale`, this scales the fully
laid-out screen including text, and corrects mouse input to match), selectable screen backgrounds
(`"dirt"`/`"blur"`/`"none"`/a custom texture), and slide/scale animation types with an
`easeInOut` easing option alongside the existing fade.

**Windows:** edge-snapping while dragging, minimizable title bars, and non-modal windows so
several independent KubeUI screens can be shown at once instead of the usual one-at-a-time
replacement.

**Interaction:** generic drag-and-drop between any two widgets (not just list reordering),
per-screen hotkeys, static and dynamically-computed right-click context menus, undoable text
fields (Ctrl+Z/Ctrl+Y), double-click actions, delayed hover-preview popovers, momentum scrolling,
`screen.shake()` for refused actions, and cross-fading between two screens
(`screen.transitionTo(...)`).

**Also:** `KubeUI.toast(...)` for non-modal notifications, `KubeUI.registerCommand(...)` to open a
screen from a plain `/command`, and `KubeUI.reserveSafeArea(...)`/`.clearSafeArea(...)` for
coexisting with another mod's screen-edge HUD.

**Fixes:** list drag-to-reorder now moves rows live instead of only reordering after the drag
ends, scroll position no longer resets mid-drag, and the minimap no longer lags and stays properly
centered on the player as they move.

Full documentation: [github.com/Zynora-fr/KubeUI](https://github.com/Zynora-fr/KubeUI#readme).

Requires KubeJS on NeoForge - see the README's Versions table for the exact
Minecraft/NeoForge/KubeJS versions this build targets.

## 0.1.0 - Initial release

First public release. A KubeJS addon for building interactive, widget-based GUIs from
`client_scripts` - no Java required.

**Widgets:** buttons, labels, toggles, text fields/areas, sliders, dropdowns, radio groups,
checkbox groups, number spinners, color pickers, progress bars, item icons, images, dividers.

**Layout:** rows, grids, scroll panels (with an automatic fallback for screens taller than the
window), tabs, anchoring, percentage-based sizing (`.width("50%")`), per-element
width/height/padding/alignment.

**Screens & UX:** draggable and resizable windows, fade in/out animation, custom fonts, a global
color theme and a personal UI scale (`/kubeui scale`) independent of Minecraft's own GUI Scale,
confirm/alert dialogs, tooltips, tab order, narration for accessibility.

**Server integration:** `screen.runServerAction(id, data)` for building server-authoritative
features (the server decides everything - price, permissions, results - the client only ever
sends an id), plus a `KubeUIEvents` event group other scripts/addons can hook into.

**Sidebar:** `KubeUISidebar` - a Paladium-style icon bar next to the survival inventory screen,
for menus that should always be one click away.

**Debugging:** `/kubeui debug`, `/kubeui outline`, `/kubeui screenshot`.

**For Java mods:** `KubeUIWidgets.register(...)` to add custom widget types.

Full documentation: [github.com/Zynora-fr/KubeUI](https://github.com/Zynora-fr/KubeUI#readme).

Requires KubeJS on NeoForge - see the README's Versions table for the exact
Minecraft/NeoForge/KubeJS versions this build targets.
