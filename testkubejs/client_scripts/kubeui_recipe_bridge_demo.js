// Demonstrates KubeUI.recipeScreen(recipeTypeId) and KubeUI.recipesFor(itemId, onResult) - both
// query real recipes server-side (recipes only exist queryably there) and reply over the network,
// so opening either screen takes a moment rather than being instant. Works against real vanilla
// recipe data (smelting) - a script can use the exact same two calls against its own custom recipe
// schema (registered via ServerEvents.recipeSchemaRegistry) the same way.

function openRecipeBridgeDemo() {
    KubeUI.builder('Recipe Bridge Demo')
        .label('info', 'Both buttons ask the server for real recipe data, then open a screen.')
        .button('Show all smelting recipes', screen => {
            KubeUI.recipeScreen('minecraft:smelting')
        })
        .button('What can raw iron become?', screen => {
            KubeUI.recipesFor('minecraft:raw_iron', (screen2, recipes) => {
                let b = KubeUI.builder('Recipes using Raw Iron')
                    .elementSize(280, 20)
                b.label('count', recipes.length + ' matching recipe(s):')
                b.divider()
                for (let i = 0; i < recipes.length; i++) {
                    let recipe = recipes[i]
                    let output = recipe.getStringOr('output', '')
                    b.row(row => {
                        row.recipeSlot('slot_' + i, ['minecraft:raw_iron']).width(20)
                        row.label('arrow_' + i, '  ->  ').width(30)
                        if (output) {
                            row.recipeSlot('out_' + i, [output]).width(20)
                        }
                        row.label('id_' + i, recipe.getStringOr('id', '?'))
                    })
                }
                b.divider()
                b.button('Close', s => s.close())
                b.open()
            })
        })
        .button('Close', screen => screen.close())
        .open()
}
