package dev.kubeui.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KubeUILayoutMathTest {
	@Test
	void resolveWidthFallsBackWhenUnset() {
		assertEquals(200, KubeUILayoutMath.resolveWidth(null, 200));
	}

	@Test
	void resolveWidthUsesOverrideWhenSet() {
		assertEquals(80, KubeUILayoutMath.resolveWidth(80, 200));
	}

	@Test
	void resolveHeightFallsBackWhenUnset() {
		assertEquals(20, KubeUILayoutMath.resolveHeight(null, 20));
	}

	@Test
	void resolveHeightUsesOverrideWhenSet() {
		assertEquals(40, KubeUILayoutMath.resolveHeight(40, 20));
	}

	@Test
	void clampIntWithinRangeIsUnchanged() {
		assertEquals(5, KubeUILayoutMath.clampInt(5, 0, 10));
	}

	@Test
	void clampIntBelowMinIsRaised() {
		assertEquals(0, KubeUILayoutMath.clampInt(-5, 0, 10));
	}

	@Test
	void clampIntAboveMaxIsLowered() {
		assertEquals(10, KubeUILayoutMath.clampInt(99, 0, 10));
	}

	@Test
	void clampIntHandlesInvertedBoundsByPreferringMin() {
		// Mirrors Math.max(min, Math.min(max, v)) - if min > max, min wins.
		assertEquals(10, KubeUILayoutMath.clampInt(5, 10, 0));
	}

	@Test
	void parseIntAcceptsPlainNumber() {
		assertEquals(42, KubeUILayoutMath.parseInt("42"));
	}

	@Test
	void parseIntTrimsWhitespace() {
		assertEquals(7, KubeUILayoutMath.parseInt("  7  "));
	}

	@Test
	void parseIntRejectsGarbage() {
		assertNull(KubeUILayoutMath.parseInt("not a number"));
	}

	@Test
	void parseIntRejectsEmptyString() {
		assertNull(KubeUILayoutMath.parseInt(""));
	}

	@Test
	void parseIntRejectsNull() {
		assertNull(KubeUILayoutMath.parseInt(null));
	}

	@Test
	void parseHexColorAcceptsWithHash() {
		assertEquals(0xFF5555, KubeUILayoutMath.parseHexColor("#FF5555"));
	}

	@Test
	void parseHexColorAcceptsWithoutHash() {
		assertEquals(0x00FF00, KubeUILayoutMath.parseHexColor("00FF00"));
	}

	@Test
	void parseHexColorIsCaseInsensitive() {
		assertEquals(0xABCDEF, KubeUILayoutMath.parseHexColor("abcdef"));
	}

	@Test
	void parseHexColorRejectsWrongLength() {
		assertNull(KubeUILayoutMath.parseHexColor("#FFF"));
		assertNull(KubeUILayoutMath.parseHexColor("#FF55550"));
	}

	@Test
	void parseHexColorRejectsNonHexCharacters() {
		assertNull(KubeUILayoutMath.parseHexColor("#GGGGGG"));
	}

	@Test
	void parseHexColorRejectsNull() {
		assertNull(KubeUILayoutMath.parseHexColor(null));
	}

	@Test
	void formatHexColorIgnoresAlphaChannel() {
		assertEquals("#FF5555", KubeUILayoutMath.formatHexColor(0xFFFF5555));
	}

	@Test
	void formatHexColorPadsShortValues() {
		assertEquals("#0000FF", KubeUILayoutMath.formatHexColor(0x0000FF));
	}

	@Test
	void formatHexColorRoundTripsWithParse() {
		int original = 0x1A2B3C;
		String formatted = KubeUILayoutMath.formatHexColor(original);
		assertEquals(original, KubeUILayoutMath.parseHexColor(formatted));
	}
}
