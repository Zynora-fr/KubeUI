// Demonstrates KubeUI's list/data widgets: a sortable table, a reorderable list, a multi-select
// list, a grouped list, a paginated list, a collapsible tree, a bar/line chart, and a minimap.
// Opened from the test menu (see kubeui_test_menu.js), which opens automatically on world join.

let tableRows = [
    ['Diamond Sword', '128'],
    ['Iron Pickaxe', '64'],
    ['Golden Apple', '3'],
]

let reorderableItems = ['First', 'Second', 'Third', 'Fourth']

let paginatedItems = ['Row 1', 'Row 2', 'Row 3']

const TREE_DATA = [
    {
        name: 'Ores', children: [
            { name: 'Diamond', children: [] },
            { name: 'Emerald', children: [] },
        ]
    },
    {
        name: 'Wood', children: [
            { name: 'Oak', children: [] },
            { name: 'Spruce', children: [] },
        ]
    },
]

function onTableSort(screen, column) {
    tableRows = tableRows.slice().sort((a, b) => a[column].localeCompare(b[column]))
    screen.update(b => {
        b.remove('lootTable')
        b.table('lootTable', ['Item', 'Count'], [180, 80], tableRows, onTableSort)
    })
}

function renderReorderRow(row, item, index) {
    row.label('reorderItem' + index, item)
}

function onReorder(screen, from, to) {
    // The rows already moved live as the player dragged - this only keeps our own copy of the
    // data (and the status label) in sync with what's now on screen, e.g. for persistence later.
    let moved = reorderableItems.splice(from, 1)[0]
    reorderableItems.splice(to, 0, moved)
    screen.setLabel('reorderStatus', 'Order: ' + reorderableItems.join(', '))
}

function renderFeedRow(row, item) {
    row.label('feedItem_' + item, item)
}

function onLoadMoreFeed(screen) {
    paginatedItems.push('Row ' + (paginatedItems.length + 1))
    let stillMore = paginatedItems.length < 8
    screen.update(b => {
        b.remove('feedList')
        b.paginatedList('feedList', paginatedItems, stillMore ? onLoadMoreFeed : null, renderFeedRow)
    })
}

function openKubeUIDataWidgetsDemo() {
    KubeUI.builder('Data Widgets Demo')
        .elementSize(280, 24)
        .draggable()
        .tab('Table', tab => tab
            .table('lootTable', ['Item', 'Count'], [180, 80], tableRows, onTableSort)
            .label('tableHint', 'Click a column header to sort by it.')
        )
        .tab('Reorder & Select', tab => tab
            .label('reorderHint', 'Drag the handle on the left of a row to reorder it.')
            .reorderableList('reorderList', reorderableItems, renderReorderRow, onReorder)
            .label('reorderStatus', 'Order: ' + reorderableItems.join(', '))
            .divider()
            .label('selectHint', 'Click to select, ctrl-click to add, shift-click for a range.')
            .selectableList('pickList', ['Alpha', 'Beta', 'Gamma', 'Delta'], (row, item, index) => {
                row.label('selectItem' + index, item)
            }, (screen, selectedIndices) => {
                screen.setLabel('selectStatus', 'Selected: ' + selectedIndices.join(', '))
            })
            .label('selectStatus', 'Selected: (none)')
        )
        .tab('Grouped & Paginated', tab => tab
            .groupedList('shopList', [
                { name: 'Apple', category: 'Food' },
                { name: 'Bread', category: 'Food' },
                { name: 'Sword', category: 'Weapons' },
                { name: 'Bow', category: 'Weapons' },
            ], item => item.category, (row, item) => {
                row.label('shopItem_' + item.name, item.name)
            })
            .divider()
            .paginatedList('feedList', paginatedItems, onLoadMoreFeed, renderFeedRow)
        )
        .tab('Tree & Charts', tab => tab
            .tree('categoryTree', TREE_DATA, node => node.children, (row, node) => {
                row.label('treeNode_' + node.name, node.name)
            })
            .divider()
            .chart('barChart', 'bar', [4.0, 8.0, 2.0, 6.0, 9.0], ['Mon', 'Tue', 'Wed', 'Thu', 'Fri'])
                .height(100)
            .chart('lineChart', 'line', [1.0, 3.0, 2.0, 5.0, 4.0, 6.0])
                .height(100)
        )
        .tab('Map', tab => tab
            .label('mapHint', 'Real block colors, always centered on you - walk around to see it follow.')
            .map('worldMap', 64)
        )
        .open()
}
