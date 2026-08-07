package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// Backs `.nonModal()` (`KubeUIScreenBuilder#open`): the real `Minecraft.screen` every non-modal
/// KubeUI window actually lives inside, since Minecraft only ever has one. Each window is a
/// completely ordinary [KubeUIScreen] (its own drag/resize/context-menu/etc. state, built via
/// [KubeUIScreen#buildDetached] the same way [KubeUIScreenInjector] builds an injected panel) -
/// this class only aggregates their rendering and routes input to whichever one a click/key
/// belongs to, rather than reimplementing any of that logic itself.
public class KubeUIMultiWindowHost extends Screen {
	private final List<KubeUIScreen> windows = new ArrayList<>();
	private KubeUIScreen focusedWindow;

	public KubeUIMultiWindowHost() {
		super(Component.literal("KubeUI"));
	}

	/// Adds a new window, brought to the front (drawn/hit-tested first). Safe to call before or
	/// after this host's own `init()` - a window added after is built immediately instead of
	/// waiting for the next `init()`/`resize()`.
	public void addWindow(KubeUIScreenBuilder builder) {
		var window = new KubeUIScreen(builder);
		window.setHost(this);
		windows.add(window);
		if (this.width > 0 || this.height > 0) {
			window.buildDetached(this.width, this.height);
		}
	}

	void removeWindow(KubeUIScreen window) {
		windows.remove(window);
		if (focusedWindow == window) {
			focusedWindow = null;
		}
		if (windows.isEmpty()) {
			net.minecraft.client.Minecraft.getInstance().setScreen(null);
		}
	}

	boolean isEmpty() {
		return windows.isEmpty();
	}

	List<KubeUIScreen> windows() {
		return windows;
	}

	@Override
	protected void init() {
		super.init();
		for (var window : windows) {
			window.buildDetached(width, height);
		}
	}

	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		super.resize(minecraft, width, height);
		for (var window : windows) {
			window.buildDetached(width, height);
		}
	}

	@Override
	public void render(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float delta) {
		var graphics = new GuiGraphicsExtractor(realGraphics);
		for (var window : windows) {
			window.extractScaledRenderState(graphics, mouseX, mouseY, delta);
		}
	}

	@Override
	public void tick() {
		super.tick();
		for (var window : windows) {
			window.tick();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Last-added = topmost = checked (and brought further to front on a hit) first.
		for (int i = windows.size() - 1; i >= 0; i--) {
			var window = windows.get(i);
			if (window.mouseClicked(mouseX, mouseY, button)) {
				focusedWindow = window;
				windows.remove(i);
				windows.add(window);
				return true;
			}
		}
		focusedWindow = null;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		return focusedWindow != null && focusedWindow.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return focusedWindow != null && focusedWindow.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		for (int i = windows.size() - 1; i >= 0; i--) {
			if (windows.get(i).mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scancode, int modifiers) {
		return (focusedWindow != null && focusedWindow.keyPressed(keyCode, scancode, modifiers)) || super.keyPressed(keyCode, scancode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scancode, int modifiers) {
		return focusedWindow != null && focusedWindow.keyReleased(keyCode, scancode, modifiers);
	}

	@Override
	public boolean charTyped(char codepoint, int modifiers) {
		return focusedWindow != null && focusedWindow.charTyped(codepoint, modifiers);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}
}
