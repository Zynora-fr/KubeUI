package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
	public void resize(int width, int height) {
		super.resize(width, height);
		for (var window : windows) {
			window.buildDetached(width, height);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
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
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Last-added = topmost = checked (and brought further to front on a hit) first.
		for (int i = windows.size() - 1; i >= 0; i--) {
			var window = windows.get(i);
			if (window.mouseClicked(event, doubleClick)) {
				focusedWindow = window;
				windows.remove(i);
				windows.add(window);
				return true;
			}
		}
		focusedWindow = null;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return focusedWindow != null && focusedWindow.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return focusedWindow != null && focusedWindow.mouseReleased(event);
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
	public boolean keyPressed(KeyEvent event) {
		return (focusedWindow != null && focusedWindow.keyPressed(event)) || super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		return focusedWindow != null && focusedWindow.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return focusedWindow != null && focusedWindow.charTyped(event);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}
}
