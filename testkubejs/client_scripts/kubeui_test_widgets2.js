// Test script for KubeUI's newer display/input widgets: rich text, badge, rating, spinner, toast,
// accordion, breadcrumb, wizard, panel background, entity/block preview, searchable/multi-select
// dropdown, date picker, range slider, stepped slider, keybind capture, search box, context menu,
// resource picker, and rich item tooltips.
// Opened from the test menu (see kubeui_test_menu.js), which opens automatically on world join.

function openKubeUIWidgets2Demo() {
    KubeUI.builder('More Widgets Demo')
        .elementSize(280, 24)
        .draggable()
        .tab('Display', tab => tab
            .richText('rich1', Text.of('Bold red').red().bold().append(Text.of(' + plain + ')).append(Text.of('gold italic').gold().italic()))
            .badge('New', 0xFF2ECC71)
            .badge('Sold out', 0xFFE74C3C)
            .rating('stars', 5, 3, (screen, value) => screen.setLabel('starsStatus', 'Rating: ' + value + '/5'))
            .label('starsStatus', 'Rating: 3/5')
            .spinner('loadingSpinner')
            .button('Fire a toast', screen => KubeUI.toast('Something happened!', 2500))
            .accordion('More info (click to expand)', accordion => accordion
                .label('accordionContent', 'This content was hidden until the accordion was expanded.'))
            .breadcrumb(['Category', 'Sub-category', 'Item'], (screen, index) => {
                screen.setLabel('crumbStatus', 'Jumped to step ' + index)
            })
            .label('crumbStatus', 'Click a non-last breadcrumb step')
            .panelBackground('minecraft:textures/item/diamond.png')
                .height(24)
            .entityPreview('minecraft:zombie')
                .height(64)
            .blockPreview('minecraft:diamond_block', 1)
            .item('minecraft:netherite_sword', 1)
                .richTooltip([
                    Text.of('Netherite Sword').gold().bold(),
                    Text.of('A rich, multi-line tooltip.').gray(),
                    Text.of('+7 Attack Damage').darkGreen()
                ])
        )
        .tab('Wizard', tab => tab
            .wizard(['Name', 'Confirm'], (step, index) => {
                if (index === 0) {
                    step.label('wizardHint', 'Step 1: enter a name (simulated).')
                        .textField('wizardName', 'Player', 'Enter a name', (screen, value) => {})
                } else {
                    step.label('wizardConfirm', 'Step 2: review and finish.')
                }
            })
        )
        .tab('Sliders & Dates', tab => tab
            .rangeSlider('priceRange', 0, 100, 20, 80, (screen, low, high) => {
                screen.setLabel('rangeStatus', 'Range: ' + low.toFixed(0) + ' - ' + high.toFixed(0))
            })
            .label('rangeStatus', 'Range: 20 - 80')
            .steppedSlider('difficulty', ['Easy', 'Normal', 'Hard', 'Nightmare'], 'Normal', (screen, value) => {
                screen.setLabel('difficultyStatus', 'Difficulty: ' + value)
            })
            .label('difficultyStatus', 'Difficulty: Normal')
            .datePicker('eventDate', 2026, 8, 3, (screen, value) => {
                screen.setLabel('dateStatus', 'Date: ' + value)
            })
            .label('dateStatus', 'Date: 2026-08-03')
            .keybindCapture('rebind', 0, (screen, keyCode) => {
                screen.setLabel('keybindStatus', 'New key code: ' + keyCode)
            })
            .label('keybindStatus', 'Click the field above, then press a key')
        )
        .tab('Search & Pickers', tab => tab
            .searchableDropdown('fruit', ['Apple', 'Banana', 'Cherry', 'Date', 'Elderberry', 'Fig', 'Grape'], 'Apple', (screen, value) => {
                screen.setLabel('fruitStatus', 'Picked: ' + value)
            })
            .label('fruitStatus', 'Picked: Apple')
            .multiSelectDropdown('toppings', ['Cheese', 'Pepperoni', 'Mushroom', 'Onion', 'Olives'], ['Cheese'], (screen, values) => {
                screen.setLabel('toppingsStatus', 'Toppings: ' + values.join(', '))
            })
            .label('toppingsStatus', 'Toppings: Cheese')
            .searchBox('itemFilter', (screen, query) => {
                screen.setLabel('filterStatus', 'Debounced query: "' + query + '"')
            })
            .label('filterStatus', 'Debounced query: ""')
            .resourcePicker('itemPicker', 'item', 'minecraft:diamond', (screen, value) => {
                screen.setLabel('pickerStatus', 'Picked item: ' + value)
            })
            .label('pickerStatus', 'Picked item: minecraft:diamond')
        )
        .tab('Context Menu', tab => tab
            .label('ctxHint', 'Right-click the button below.')
            .button('Right-click me', screen => {})
                .contextMenu(['Copy', 'Rename', 'Delete'], (screen, item) => {
                    screen.setLabel('ctxStatus', 'Chose: ' + item)
                })
            .label('ctxStatus', 'Chose: (nothing yet)')
        )
        .open()
}
