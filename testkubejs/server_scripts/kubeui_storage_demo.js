// Reference example for the storage system. No script API to
// register here - KubeUIStorageBlock/Item are real Java-registered blocks/items
// (kubeui:storage_crate, kubeui:backpack), obtainable via /give like any other item:
//   /give @s kubeui:storage_crate
//   /give @s kubeui:backpack
// Right-click a placed storage_crate to open it (real vanilla ChestMenu + sort/search/settings).
// The settings screen's "Set Network" links it to a named network - place a second one and link it
// to the same name, then use the client demo's "View Network" button (or KubeUI.storageNetworkView
// (networkId) from any script) to see their combined contents without opening either physically.
console.log('[kubeui storage demo] kubeui:storage_crate and kubeui:backpack are registered items - /give @s kubeui:storage_crate')
