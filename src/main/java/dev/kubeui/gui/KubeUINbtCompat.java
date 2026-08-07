package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

/// 26.1.2's `CompoundTag` has convenience getters like `tag.getStringOr(key, fallback)` and an
/// `Optional`-returning `getCompound(key)`. 1.21.1's real `CompoundTag` only has bare getters
/// that silently default to the zero-value (`0`/`false`/`""`) when a key is missing or mistyped,
/// with no way to pass a custom fallback, and `getCompound(key)` returns a fresh, unlinked empty
/// tag rather than an `Optional` when absent. These static helpers reproduce the 26.1.2 semantics
/// on top of the real 1.21.1 accessors, so call sites only need converting from method-chain
/// style (`tag.getStringOr(key, fallback)`) to static-call style
/// (`KubeUINbtCompat.getStringOr(tag, key, fallback)`) rather than having their logic rewritten.
final class KubeUINbtCompat {
	private KubeUINbtCompat() {
	}

	static String getStringOr(CompoundTag tag, String key, String fallback) {
		return tag.contains(key, 8) ? tag.getString(key) : fallback;
	}

	static int getIntOr(CompoundTag tag, String key, int fallback) {
		return tag.contains(key, 99) ? tag.getInt(key) : fallback;
	}

	static boolean getBooleanOr(CompoundTag tag, String key, boolean fallback) {
		return tag.contains(key, 99) ? tag.getBoolean(key) : fallback;
	}

	static long getLongOr(CompoundTag tag, String key, long fallback) {
		return tag.contains(key, 99) ? tag.getLong(key) : fallback;
	}

	static double getDoubleOr(CompoundTag tag, String key, double fallback) {
		return tag.contains(key, 99) ? tag.getDouble(key) : fallback;
	}

	/// Gets the compound at `key`, creating and storing an empty one first if absent - for the
	/// common "get-or-create the live, mutable sub-tag" pattern.
	static CompoundTag getCompoundOrCreate(CompoundTag parent, String key) {
		if (!parent.contains(key, 10)) {
			parent.put(key, new CompoundTag());
		}
		return parent.getCompound(key);
	}

	/// Gets the compound at `key`, or `null` if absent - for call sites that explicitly want to
	/// distinguish "missing" from "present but empty" rather than getting a throwaway empty tag.
	static CompoundTag getCompoundOrNull(CompoundTag parent, String key) {
		return parent.contains(key, 10) ? parent.getCompound(key) : null;
	}
}
