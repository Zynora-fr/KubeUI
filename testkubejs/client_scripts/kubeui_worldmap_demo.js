// Reference example for the world map system. Everything here is
// client-side - waypoints/the map itself never need a server round trip except for explicit
// sharing.

// Villagers show up as a gold dot on the map/minimap.
KubeUI.registerMapIcon('minecraft:villager', 0xFFFFD700)

function openWorldMapDemo() {
    KubeUI.builder('World Map Demo')
        .elementSize(280, 20)
        .label('info', 'Open the full map, drag to pan, scroll to zoom, click "Layer" to cycle surface/caves/nether/end.')
        .label('info2', 'Villagers show up as gold dots. Explored areas persist across sessions.')
        .divider()
        .button('Open World Map', screen => {
            screen.close()
            KubeUI.worldMap()
        })
        .label('exportInfo', 'Once the map is open: /kubeui map export saves an image.')
        .label('waypointsInfo', '/kubeui map waypoints lists your waypoints and their ids.')
        .divider()
        .textField('shareTarget', '', 'PlayerName', (screen, value) => {})
        .textField('shareWaypointId', '', 'waypoint id (see the map screen)', (screen, value) => {})
        .button('Share Waypoint', screen => {
            let target = screen.getTextFieldValue('shareTarget')
            let id = screen.getTextFieldValue('shareWaypointId')
            if (target && id) {
                KubeUI.shareWaypoint(id, target)
            }
        })
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
