// KubeUI type definitions, for editors/tools that understand ProbeJS-style .d.ts hints
// (e.g. the ProbeJS KubeJS addon's IDE autocomplete).
//
// Hand-written, best-effort - NOT generated or verified by an actual ProbeJS install (it isn't
// part of this dev environment). If it drifts from the real Java API in src/main/java/dev/kubeui,
// the Java source (and its /// doc comments) is the source of truth, not this file.
//
// Covers: KubeUIScreenBuilder (the `KubeUI` global) and KubeUIContext (the `screen` parameter
// passed to every callback). Client scripts only.

/** Alignment presets used by KubeUI.builder(...).anchor(...) and .align(...). */
type KubeUIAnchorPreset = 'center' | 'top' | 'bottom' | 'left' | 'right' | 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right'
type KubeUIAlign = 'left' | 'center' | 'right'

/** Passed to every widget callback (click, change, ...) and to onOpen/onClose/list item renderers. */
declare class KubeUIContext {
    setLabel(id: string, text: string): void

    getTextFieldValue(id: string): string | null
    setTextFieldValue(id: string, value: string): void

    getTextAreaValue(id: string): string | null
    setTextAreaValue(id: string, value: string): void

    getSliderValue(id: string): number
    setSliderValue(id: string, value: number): void

    getDropdownValue(id: string): string | null
    setDropdownValue(id: string, value: string): void

    getRadioValue(id: string): string | null
    setRadioValue(id: string, value: string): void

    getNumberValue(id: string): number
    setNumberValue(id: string, value: number): void

    /** Opaque ARGB int, e.g. 0xFFff5555. */
    getColor(id: string): number
    /** `color` may be a large literal (> Int32 range) - passed through as-is. */
    setColor(id: string, color: number): void

    getCheckboxGroupValues(id: string): string[]

    getToggleValue(id: string): boolean
    /** Unlike the other setters, this *does* fire the toggle's onChange (Checkbox has no silent setter). */
    setToggleValue(id: string, value: boolean): void

    getProgress(id: string): number
    setProgress(id: string, value: number, max?: number): void

    setEnabled(id: string, enabled: boolean): void
    setVisible(id: string, visible: boolean): void

    /** Mutates the screen's own builder, then rebuilds. Use with .button(...)/.remove(id)/etc. */
    update(mutator: (builder: KubeUIScreenBuilder) => void): void

    /** Gives count of item via a client-sent /give command (needs cheats/op) - see KubeUIActions for a validated alternative. */
    giveItem(item: string, count: number): void

    /** Sends `action` + `data` to the server; handled by KubeUIActions.register(action, ...) in server_scripts. */
    runServerAction(action: string, data: object): void

    /** Closes the screen (after a fade-out if .animated() was used). */
    close(): void
}

declare class KubeUIScreenBuilder {
    static builder(title: string, persistKey?: string): KubeUIScreenBuilder
    static close(): void
    static confirm(title: string, message: string, onYes?: () => void, onNo?: () => void): void
    static alert(title: string, message: string, onClose?: () => void): void
    static setTheme(titleColor: number, accentColor: number, textColor: number): void
    static resetTheme(): void

    // --- Widgets (Phases 1-12) ---
    button(text: string, onClick: (screen: KubeUIContext) => void): this
    label(id: string, text: string): this
    labelKey(id: string, langKey: string, fallbackText: string): this
    toggle(id: string, text: string, initial: boolean, onChange: (screen: KubeUIContext, value: boolean) => void): this
    textField(id: string, initialValue: string, hint: string | null, onChange: (screen: KubeUIContext, value: string) => void): this
    slider(id: string, min: number, max: number, initial: number, onChange: (screen: KubeUIContext, value: number) => void): this
    dropdown(id: string, options: string[], initial: string, onChange: (screen: KubeUIContext, value: string) => void): this
    radioGroup(id: string, options: string[], initial: string, onChange: (screen: KubeUIContext, value: string) => void): this
    textArea(id: string, initialValue: string, hint: string | null, height: number, onChange: (screen: KubeUIContext, value: string) => void): this
    image(texture: string, width: number, height: number): this
    item(item: string, count: number, onClick?: (screen: KubeUIContext) => void): this
    progressBar(id: string, value: number, max: number): this
    divider(): this
    spacer(height: number): this
    number(id: string, min: number, max: number, initial: number, onChange: (screen: KubeUIContext, value: number) => void): this
    /** `initial` is an opaque ARGB value, e.g. 0xFFff5555. */
    colorPicker(id: string, initial: number, onChange: (screen: KubeUIContext, value: number) => void): this
    checkboxGroup(id: string, options: string[], initialSelected: string[], onChange: (screen: KubeUIContext, values: string[]) => void): this

    // --- Layout (Phases 13-20) ---
    row(children: (row: KubeUIScreenBuilder) => void): this
    grid(columns: number, children: (grid: KubeUIScreenBuilder) => void): this
    scrollPanel(maxHeight: number, children: (panel: KubeUIScreenBuilder) => void): this
    list(id: string, items: unknown[], renderer: (row: KubeUIScreenBuilder, item: unknown, index: number) => void): this
    anchor(preset: KubeUIAnchorPreset, marginX?: number, marginY?: number): this

    // --- Per-element modifiers (apply to the most-recently-added element) ---
    width(width: number): this
    height(height: number): this
    align(horizontal: KubeUIAlign): this
    padding(all: number): this
    padding(horizontal: number, vertical: number): this
    padding(left: number, top: number, right: number, bottom: number): this
    tooltip(text: string): this
    tabOrder(order: number): this
    narration(text: string): this
    sound(soundId: string): this
    id(id: string): this

    // --- Screens & UX (Phases 21-30) ---
    tab(name: string, children: (tab: KubeUIScreenBuilder) => void): this
    draggable(): this
    resizable(minHeight: number, maxHeight: number): this
    remove(id: string): this

    // --- Behavior & polish (Phases 31-40) ---
    animated(durationMs?: number): this
    font(fontId: string): this
    onOpen(callback: (screen: KubeUIContext) => void): this
    onClose(callback: (screen: KubeUIContext) => void): this
    bind(supplier: () => unknown, onChange: (screen: KubeUIContext, value: unknown) => void): this
    /** Widgets registered by a **Java** mod via KubeUIWidgets.register(type, factory). */
    custom(type: string, ...args: unknown[]): this

    elementSize(width: number, buttonHeight: number): this
    open(): void
}

/** Global `KubeUI` binding (client_scripts only). */
declare const KubeUI: typeof KubeUIScreenBuilder

/** Global `KubeUIActions` binding (server_scripts only) - see KubeUIContext#runServerAction. */
declare const KubeUIActions: {
    register(id: string, handler: (player: unknown, data: unknown) => void): void
}

/** `KubeUIEvents.screenOpen(event => {...})` / `.screenClose(...)` - fires for every KubeUI screen. */
declare const KubeUIEvents: {
    screenOpen(callback: (event: { title: string; screen: KubeUIContext }) => void): void
    screenClose(callback: (event: { title: string; screen: KubeUIContext }) => void): void
}
