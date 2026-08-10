package dev.kubeui.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeUIMachineBlockTest {
	@Test
	void modelRenderSupportIsEnabled() {
		assertTrue(KubeUIBlockRenderSupport.useModelRenderShape());
	}
}
