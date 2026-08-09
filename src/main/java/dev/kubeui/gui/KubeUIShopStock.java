package dev.kubeui.gui;

/// One buyable/sellable line in a [KubeUIShop] vendor - `currentPrice` is the only mutable field
/// (everything else is the script-declared configuration from `KubeUIActions.defineShop(...)`),
/// updated periodically by [KubeUIShop#tickFluctuation] when `minPrice`/`maxPrice` are set -
/// "just a temporal rule" was the scope explicitly left open by the roadmap entry itself: a
/// bounded random walk each interval, not a full supply/demand simulation, which would need real
/// purchase-volume tracking this lot doesn't build).
final class KubeUIShopStock {
	final String id;
	final String itemId;
	final int itemCount;
	final String currency;
	final long basePrice;
	final boolean sellable;
	final double sellRate;
	final Long minPrice;
	final Long maxPrice;
	final int fluctuationIntervalTicks;
	int stock;

	volatile long currentPrice;

	KubeUIShopStock(String id, String itemId, int itemCount, String currency, long basePrice, boolean sellable, double sellRate, Long minPrice, Long maxPrice, int fluctuationIntervalTicks, int stock) {
		this.id = id;
		this.itemId = itemId;
		this.itemCount = itemCount;
		this.currency = currency;
		this.basePrice = basePrice;
		this.sellable = sellable;
		this.sellRate = sellRate;
		this.minPrice = minPrice;
		this.maxPrice = maxPrice;
		this.fluctuationIntervalTicks = fluctuationIntervalTicks;
		this.stock = stock;
		this.currentPrice = basePrice;
	}

	boolean fluctuates() {
		return minPrice != null && maxPrice != null && maxPrice > minPrice;
	}
}
