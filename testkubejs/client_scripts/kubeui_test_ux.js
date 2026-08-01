// Test script for KubeUI's Phase 21-30 UX features: tabs, modal dialogs, draggable/resizable
// windows, tooltips, enable/visible toggling, add/remove after open, and custom narration.
// Opened from the test menu (see kubeui_test_menu.js), which opens automatically on world join.

function openKubeUIUXDemo() {
    KubeUI.builder('UX Demo')
        .draggable()
        .resizable(60, 240)
        .tab('Controls', tab => tab
            .label('hint', 'Drag the title to move, drag the corner to resize.')
            .toggle('enableToggle', 'Enable target button', true, (screen, value) => {
                screen.setEnabled('target', value)
            })
            .toggle('visibleToggle', 'Show target button', true, (screen, value) => {
                screen.setVisible('target', value)
            })
            .button('Target Button', screen => console.log('Target clicked!'))
                .id('target')
                .tooltip('Hover tooltip on the target button')
            .divider().narration('Section divider between the target button and the add/remove demo')
            .button('Add Extra Button', screen => {
                screen.update(builder => builder
                    .button('Extra!', s => console.log('Extra button clicked'))
                    .id('extra'))
            })
            .button('Remove Extra Button', screen => {
                screen.update(builder => builder.remove('extra'))
            })
        )
        .tab('Dialogs', tab => tab
            .label('dialogHint', 'Buttons below open modal dialogs on top of this screen.')
            .button('Confirm', screen => {
                KubeUI.confirm('Are you sure?', 'This will do a thing.\nContinue?',
                    () => KubeUI.alert('Confirmed', 'You said yes.', null),
                    () => KubeUI.alert('Cancelled', 'You said no.', null))
            })
        )
        .open()
}
