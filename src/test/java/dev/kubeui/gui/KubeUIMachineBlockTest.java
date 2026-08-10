package dev.kubeui.gui;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KubeUIMachineBlockTest {
	@Test
	void machineBlockRendersAsModel() {
		var block = new KubeUIMachineBlock(BlockBehaviour.Properties.of());
		assertEquals(RenderShape.MODEL, block.getRenderShape(block.defaultBlockState()));
	}
}
