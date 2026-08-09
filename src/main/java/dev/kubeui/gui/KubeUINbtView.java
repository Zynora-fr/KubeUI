package dev.kubeui.gui;

import dev.latvian.mods.kubejs.util.NBTSerializable;
import dev.latvian.mods.rhino.Context;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/// JS-facing view over a raw [CompoundTag], recreating the `getXxxOr`/`getListOrEmpty`/
/// `getCompoundOrEmpty` convenience API that Minecraft's `CompoundTag` gained natively in later
/// versions (this mod's other, 26.1.2 target already has them as real methods - no shim needed
/// there). 1.21.1's real `CompoundTag` only has bare getters with no custom fallback and no
/// `Or`-suffixed methods at all (see [KubeUINbtCompat], the Java-side static-call equivalent used
/// by this mod's own internals) - since KubeJS's own `CompoundTag` &rarr; JS conversion falls back
/// to real reflection-based method lookup only for property names that aren't themselves a stored
/// NBT key, a plain `CompoundTag` crossing into JS here would expose its NBT keys but none of these
/// convenience methods. Every place raw NBT action/screen payload data crosses into a JS callback
/// ([KubeUIActionHandler#handle], [KubeUIRemoteScreens#register]) hands out this wrapper instead of
/// the raw tag, so a script written as `data.getStringOr('key', 'fallback')` - identical whether
/// it's running against this version of the mod or the 26.1.2 one - keeps working unmodified.
///
/// Implements KubeJS's own [NBTSerializable] so the reverse direction also works: a script that
/// hands a [KubeUINbtView] (or a list of them, e.g. `KubeUIActions.claimsOf(player)`'s result)
/// straight back into `KubeUIActions.openRemote(player, screenId, data)` needs KubeJS's JS-to-NBT
/// coercion (`NBTWrapper.wrap`) to recognize it - otherwise it silently falls through to `null` and
/// the entry just vanishes from the outgoing payload instead of erroring.
public final class KubeUINbtView implements NBTSerializable {
	private final CompoundTag tag;

	public KubeUINbtView(CompoundTag tag) {
		this.tag = tag;
	}

	@Override
	public Tag toNBT(Context cx) {
		return tag;
	}

	public boolean contains(String key) {
		return tag.contains(key);
	}

	public String getStringOr(String key, String fallback) {
		return KubeUINbtCompat.getStringOr(tag, key, fallback);
	}

	public int getIntOr(String key, int fallback) {
		return KubeUINbtCompat.getIntOr(tag, key, fallback);
	}

	public boolean getBooleanOr(String key, boolean fallback) {
		return KubeUINbtCompat.getBooleanOr(tag, key, fallback);
	}

	public long getLongOr(String key, long fallback) {
		return KubeUINbtCompat.getLongOr(tag, key, fallback);
	}

	public double getDoubleOr(String key, double fallback) {
		return KubeUINbtCompat.getDoubleOr(tag, key, fallback);
	}

	public float getFloatOr(String key, float fallback) {
		return KubeUINbtCompat.getFloatOr(tag, key, fallback);
	}

	public KubeUINbtView getCompoundOrEmpty(String key) {
		return new KubeUINbtView(KubeUINbtCompat.getCompoundOrEmpty(tag, key));
	}

	/// Elements that aren't themselves a compound (a list of plain strings/numbers, say) are
	/// dropped rather than wrapped - every current use of this across `testkubejs` reads a list of
	/// `{...}` entries, and there's no meaningful `KubeUINbtView` to hand back for a bare primitive.
	public List<KubeUINbtView> getListOrEmpty(String key) {
		var out = new ArrayList<KubeUINbtView>();
		for (Tag element : KubeUINbtCompat.getListOrEmpty(tag, key)) {
			if (element instanceof CompoundTag compound) {
				out.add(new KubeUINbtView(compound));
			}
		}
		return out;
	}

	public void putString(String key, String value) {
		tag.putString(key, value);
	}

	public void putInt(String key, int value) {
		tag.putInt(key, value);
	}

	public void putBoolean(String key, boolean value) {
		tag.putBoolean(key, value);
	}

	public void putLong(String key, long value) {
		tag.putLong(key, value);
	}

	public void putDouble(String key, double value) {
		tag.putDouble(key, value);
	}

	public void putFloat(String key, float value) {
		tag.putFloat(key, value);
	}

	public void remove(String key) {
		tag.remove(key);
	}

	/// The underlying raw tag, for Java call sites that need direct NBT access rather than the
	/// `Or`-suffixed convenience API (e.g. re-copying data server-side).
	public CompoundTag raw() {
		return tag;
	}
}
