package org.qora.asset;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.block.BlockChain;
import org.qora.data.asset.AssetData;
import org.qora.data.asset.OrderData;
import org.qora.data.asset.TradeData;
import org.qora.repository.AssetRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.hash.HashCode;

public class Order {

	/** BigDecimal scale for representing unit price in asset orders. */
	public static final int BD_PRICE_SCALE = 38;
	/** BigDecimal scale for representing unit price in asset orders in storage context. */
	public static final int BD_PRICE_STORAGE_SCALE = BD_PRICE_SCALE + 10;

	private static final Logger LOGGER = LogManager.getLogger(Order.class);

	// Properties
	private Repository repository;
	private OrderData orderData;

	// Constructors

	public Order(Repository repository, OrderData orderData) {
		this.repository = repository;
		this.orderData = orderData;
	}

	// Getters/Setters

	public OrderData getOrderData() {
		return this.orderData;
	}

	// More information

	public static BigDecimal getAmountLeft(OrderData orderData) {
		return orderData.getAmount().subtract(orderData.getFulfilled());
	}

	public BigDecimal getAmountLeft() {
		return Order.getAmountLeft(this.orderData);
	}

	public static boolean isFulfilled(OrderData orderData) {
		return orderData.getFulfilled().compareTo(orderData.getAmount()) == 0;
	}

	public boolean isFulfilled() {
		return Order.isFulfilled(this.orderData);
	}

	/**
	 * Returns want-asset granularity/unit-size given price.
	 * <p>
	 * @param theirPrice 
	 * @return unit price of want asset
	 */
	public static BigDecimal calculateAmountGranularity(AssetData haveAssetData, AssetData wantAssetData, OrderData theirOrderData) {
		// Multiplier to scale BigDecimal fractional amounts into integer domain
		BigInteger multiplier = BigInteger.valueOf(1_0000_0000L);

		// Calculate the minimum increment at which I can buy using greatest-common-divisor
		BigInteger haveAmount;
		BigInteger wantAmount;
		if (theirOrderData.getTimestamp() >= BlockChain.getInstance().getNewAssetPricingTimestamp()) {
			// "new" pricing scheme
			haveAmount = theirOrderData.getAmount().movePointRight(8).toBigInteger();
			wantAmount = theirOrderData.getWantAmount().movePointRight(8).toBigInteger();
		} else {
			// legacy "old" behaviour
			haveAmount = multiplier; // 1 unit (* multiplier)
			wantAmount = theirOrderData.getUnitPrice().movePointRight(8).toBigInteger();
		}

		BigInteger gcd = haveAmount.gcd(wantAmount);
		haveAmount = haveAmount.divide(gcd);
		wantAmount = wantAmount.divide(gcd);

		// Calculate GCD in combination with divisibility
		if (wantAssetData.getIsDivisible())
			haveAmount = haveAmount.multiply(multiplier);

		if (haveAssetData.getIsDivisible())
			wantAmount = wantAmount.multiply(multiplier);

		gcd = haveAmount.gcd(wantAmount);

		// Calculate the granularity at which we have to buy
		BigDecimal granularity = new BigDecimal(haveAmount.divide(gcd));
		if (wantAssetData.getIsDivisible())
			granularity = granularity.movePointLeft(8);

		// Return
		return granularity;
	}

	// Navigation

	public List<TradeData> getTrades() throws DataException {
		return this.repository.getAssetRepository().getOrdersTrades(this.orderData.getOrderId());
	}

	// Processing

	private void logOrder(String orderPrefix, boolean isMatchingNotInitial, OrderData orderData) throws DataException {
		// Avoid calculations if possible
		if (LOGGER.getLevel().isMoreSpecificThan(Level.DEBUG))
			return;

		final String weThey = isMatchingNotInitial ? "They" : "We";

		AssetData haveAssetData = this.repository.getAssetRepository().fromAssetId(orderData.getHaveAssetId());
		AssetData wantAssetData = this.repository.getAssetRepository().fromAssetId(orderData.getWantAssetId());

		LOGGER.debug(String.format("%s %s", orderPrefix, HashCode.fromBytes(orderData.getOrderId()).toString()));

		LOGGER.trace(String.format("%s have: %s %s", weThey, orderData.getAmount().stripTrailingZeros().toPlainString(), haveAssetData.getName()));

		LOGGER.trace(String.format("%s want at least %s %s per %s (minimum %s %s total)", weThey,
				orderData.getUnitPrice().toPlainString(), wantAssetData.getName(), haveAssetData.getName(),
				orderData.getWantAmount().stripTrailingZeros().toPlainString(), wantAssetData.getName()));
	}

	public void process() throws DataException {
		AssetRepository assetRepository = this.repository.getAssetRepository();

		long haveAssetId = this.orderData.getHaveAssetId();
		AssetData haveAssetData = assetRepository.fromAssetId(haveAssetId);
		long wantAssetId = this.orderData.getWantAssetId();
		AssetData wantAssetData = assetRepository.fromAssetId(wantAssetId);

		// Subtract asset from creator
		Account creator = new PublicKeyAccount(this.repository, this.orderData.getCreatorPublicKey());
		creator.setConfirmedBalance(haveAssetId, creator.getConfirmedBalance(haveAssetId).subtract(this.orderData.getAmount()));

		// Save this order into repository so it's available for matching, possibly by itself
		this.repository.getAssetRepository().save(this.orderData);

		/*
		 * Our order example ("old"):
		 * 
		 * haveAssetId=[GOLD], amount=10,000, wantAssetId=[QORA], price=0.002
		 * 
		 * This translates to "we have 10,000 GOLD and want to buy QORA at a price of 0.002 QORA per GOLD"
		 * 
		 * So if our order matched, we'd end up with 10,000 * 0.002 = 20 QORA, essentially costing 1/0.002 = 500 GOLD each.
		 * 
		 * So 500 GOLD [each] is our (selling) unit price and want-amount is 20 QORA.
		 * 
		 * Another example (showing representation error and hence move to "new" pricing):
		 * haveAssetId=[QORA], amount=24, wantAssetId=[GOLD], price=0.08333333
		 * unit price: 12.00000048 GOLD, want-amount: 1.9999992 GOLD
		 */

		/*
		 * Our order example ("new"):
		 * 
		 * haveAssetId=[GOLD], amount=10,000, wantAssetId=0 (QORA), want-amount=20
		 * 
		 * This translates to "we have 10,000 GOLD and want to buy 20 QORA"
		 * 
		 * So if our order matched, we'd end up with 20 QORA, essentially costing 10,000 / 20 = 500 GOLD each.
		 * 
		 * So 500 GOLD [each] is our (selling) unit price and want-amount is 20 QORA.
		 * 
		 * Another example:
		 * haveAssetId=[QORA], amount=24, wantAssetId=[GOLD], want-amount=2
		 * unit price: 12.00000000 GOLD, want-amount: 2.00000000 GOLD
		 */
		logOrder("Processing our order", false, this.orderData);

		// Fetch corresponding open orders that might potentially match, hence reversed want/have assetId args.
		// Returned orders are sorted with lowest "price" first.
		List<OrderData> orders = assetRepository.getOpenOrders(wantAssetId, haveAssetId);
		LOGGER.trace("Open orders fetched from repository: " + orders.size());

		if (orders.isEmpty())
			return;

		// Attempt to match orders

		BigDecimal ourUnitPrice = this.orderData.getUnitPrice();
		LOGGER.trace(String.format("Our minimum price: %s %s per %s", ourUnitPrice.toPlainString(), wantAssetData.getName(), haveAssetData.getName()));

		for (OrderData theirOrderData : orders) {
			logOrder("Considering order", true, theirOrderData);

			/*
			 * Potential matching order example ("old"):
			 * 
			 * haveAssetId=0 (QORA), amount=40, wantAssetId=[GOLD], price=486
			 * 
			 * This translates to "we have 40 QORA and want to buy GOLD at a price of 486 GOLD per QORA"
			 * 
			 * So if their order matched, they'd end up with 40 * 486 = 19,440 GOLD, essentially costing 1/486 = 0.00205761 QORA each.
			 * 
			 * So 0.00205761 QORA [each] is their unit price and maximum amount is 19,440 GOLD.
			 */

			/*
			 * Potential matching order example ("new"):
			 * 
			 * haveAssetId=0 (QORA), amount=40, wantAssetId=[GOLD], price=19,440
			 * 
			 * This translates to "we have 40 QORA and want to buy 19,440 GOLD"
			 * 
			 * So if their order matched, they'd end up with 19,440 GOLD, essentially costing 40 / 19,440 = 0.00205761 QORA each.
			 * 
			 * So 0.00205761 QORA [each] is their unit price and maximum amount is 19,440 GOLD.
			 */

			boolean isTheirOrderNewAssetPricing = theirOrderData.getTimestamp() >= BlockChain.getInstance().getNewAssetPricingTimestamp();

			BigDecimal theirBuyingPrice = BigDecimal.ONE.setScale(isTheirOrderNewAssetPricing ? Order.BD_PRICE_STORAGE_SCALE : 8).divide(theirOrderData.getUnitPrice(), RoundingMode.DOWN);
			LOGGER.trace(String.format("Their price: %s %s per %s", theirBuyingPrice.toPlainString(), wantAssetData.getName(), haveAssetData.getName()));

			// If their buyingPrice is less than what we're willing to accept then we're done as prices only get worse as we iterate through list of orders
			if (theirBuyingPrice.compareTo(ourUnitPrice) < 0)
				break;

			// Calculate how many want-asset we could buy at their price
			BigDecimal ourMaxWantAmount = this.getAmountLeft().multiply(theirBuyingPrice).setScale(8, RoundingMode.DOWN);
			LOGGER.trace("ourMaxWantAmount (max we could buy at their price): " + ourMaxWantAmount.stripTrailingZeros().toPlainString() + " " + wantAssetData.getName());

			if (isTheirOrderNewAssetPricing) {
				ourMaxWantAmount = ourMaxWantAmount.max(this.getAmountLeft().divide(theirOrderData.getUnitPrice(), RoundingMode.DOWN).setScale(8, RoundingMode.DOWN));
				LOGGER.trace("ourMaxWantAmount (max we could buy at their price) using inverted calculation: " + ourMaxWantAmount.stripTrailingZeros().toPlainString() + " " + wantAssetData.getName());
			}

			// How many want-asset is remaining available in their order. (have-asset amount from their perspective).
			BigDecimal theirWantAmountLeft = Order.getAmountLeft(theirOrderData);
			LOGGER.trace("theirWantAmountLeft (max amount remaining in their order): " + theirWantAmountLeft.stripTrailingZeros().toPlainString() + " " + wantAssetData.getName());

			// So matchable want-asset amount is the minimum of above two values
			BigDecimal matchedWantAmount = ourMaxWantAmount.min(theirWantAmountLeft);
			LOGGER.trace("matchedWantAmount: " + matchedWantAmount.stripTrailingZeros().toPlainString() + " " + wantAssetData.getName());

			// If we can't buy anything then try another order
			if (matchedWantAmount.compareTo(BigDecimal.ZERO) <= 0)
				continue;

			// Calculate want-amount granularity, based on price and both assets' divisibility, so that have-amount traded is a valid amount (integer or to 8 d.p.)
			BigDecimal wantGranularity = calculateAmountGranularity(haveAssetData, wantAssetData, theirOrderData);
			LOGGER.trace("wantGranularity (want-asset amount granularity): " + wantGranularity.stripTrailingZeros().toPlainString() + " " + wantAssetData.getName());

			// Reduce matched amount (if need be) to fit granularity
			matchedWantAmount = matchedWantAmount.subtract(matchedWantAmount.remainder(wantGranularity));
			LOGGER.trace("matchedWantAmount adjusted for granularity: " + matchedWantAmount.stripTrailingZeros().toPlainString() + " " + wantAssetData.getName());

			// If we can't buy anything then try another order
			if (matchedWantAmount.compareTo(BigDecimal.ZERO) <= 0)
				continue;

			// Safety checks
			if (matchedWantAmount.compareTo(Order.getAmountLeft(theirOrderData)) > 0) {
				Account participant = new PublicKeyAccount(this.repository, theirOrderData.getCreatorPublicKey());

				String message = String.format("Refusing to trade more %s then requested %s [assetId %d] for %s",
						matchedWantAmount.toPlainString(), Order.getAmountLeft(theirOrderData).toPlainString(), wantAssetId, participant.getAddress());
				LOGGER.error(message);
				throw new DataException(message);
			}

			if (!wantAssetData.getIsDivisible() && matchedWantAmount.stripTrailingZeros().scale() > 0) {
				Account participant = new PublicKeyAccount(this.repository, theirOrderData.getCreatorPublicKey());

				String message = String.format("Refusing to trade fractional %s [indivisible assetId %d] for %s",
						matchedWantAmount.toPlainString(), wantAssetId, participant.getAddress());
				LOGGER.error(message);
				throw new DataException(message);
			}

			// Trade can go ahead!

			// Calculate the total cost to us, in have-asset, based on their price
			BigDecimal haveAmountTraded;

			if (isTheirOrderNewAssetPricing) {
				BigDecimal theirTruncatedPrice = theirBuyingPrice.setScale(Order.BD_PRICE_SCALE, RoundingMode.DOWN);
				BigDecimal ourTruncatedPrice = ourUnitPrice.setScale(Order.BD_PRICE_SCALE, RoundingMode.DOWN);

				// Safety check
				if (theirTruncatedPrice.compareTo(ourTruncatedPrice) < 0) {
					String message = String.format("Refusing to trade at worse price %s than our minimum of %s",
							theirTruncatedPrice.toPlainString(), ourTruncatedPrice.toPlainString(), creator.getAddress());
					LOGGER.error(message);
					throw new DataException(message);
				}

				haveAmountTraded = matchedWantAmount.divide(theirTruncatedPrice, RoundingMode.DOWN).setScale(8, RoundingMode.DOWN);
			} else {
				haveAmountTraded = matchedWantAmount.multiply(theirOrderData.getUnitPrice()).setScale(8, RoundingMode.DOWN);
			}
			LOGGER.trace("haveAmountTraded: " + haveAmountTraded.stripTrailingZeros().toPlainString() + " " + haveAssetData.getName());

			// Safety checks
			if (haveAmountTraded.compareTo(this.getAmountLeft()) > 0) {
				String message = String.format("Refusing to trade more %s then requested %s [assetId %d] for %s",
						haveAmountTraded.toPlainString(), this.getAmountLeft().toPlainString(), haveAssetId, creator.getAddress());
				LOGGER.error(message);
				throw new DataException(message);
			}

			if (!haveAssetData.getIsDivisible() && haveAmountTraded.stripTrailingZeros().scale() > 0) {
				String message = String.format("Refusing to trade fractional %s [indivisible assetId %d] for %s",
						haveAmountTraded.toPlainString(), haveAssetId, creator.getAddress());
				LOGGER.error(message);
				throw new DataException(message);
			}

			// Construct trade
			TradeData tradeData = new TradeData(this.orderData.getOrderId(), theirOrderData.getOrderId(), matchedWantAmount, haveAmountTraded,
					this.orderData.getTimestamp());
			// Process trade, updating corresponding orders in repository
			Trade trade = new Trade(this.repository, tradeData);
			trade.process();

			// Update our order in terms of fulfilment, etc. but do not save into repository as that's handled by Trade above
			this.orderData.setFulfilled(this.orderData.getFulfilled().add(haveAmountTraded));
			LOGGER.trace("Updated our order's fulfilled amount to: " + this.orderData.getFulfilled().stripTrailingZeros().toPlainString() + " " + haveAssetData.getName());
			LOGGER.trace("Our order's amount remaining: " + this.getAmountLeft().stripTrailingZeros().toPlainString() + " " + haveAssetData.getName());

			// Continue on to process other open orders if we still have amount left to match
			if (this.getAmountLeft().compareTo(BigDecimal.ZERO) <= 0)
				break;
		}
	}

	public void orphan() throws DataException {
		// Orphan trades that occurred as a result of this order
		for (TradeData tradeData : getTrades())
			if (Arrays.equals(this.orderData.getOrderId(), tradeData.getInitiator())) {
				Trade trade = new Trade(this.repository, tradeData);
				trade.orphan();
			}

		// Delete this order from repository
		this.repository.getAssetRepository().delete(this.orderData.getOrderId());

		// Return asset to creator
		long haveAssetId = this.orderData.getHaveAssetId();
		Account creator = new PublicKeyAccount(this.repository, this.orderData.getCreatorPublicKey());
		creator.setConfirmedBalance(haveAssetId, creator.getConfirmedBalance(haveAssetId).add(this.orderData.getAmount()));
	}

	// This is called by CancelOrderTransaction so that an Order can no longer trade
	public void cancel() throws DataException {
		this.orderData.setIsClosed(true);
		this.repository.getAssetRepository().save(this.orderData);
	}

	// Opposite of cancel() above for use during orphaning
	public void reopen() throws DataException {
		this.orderData.setIsClosed(false);
		this.repository.getAssetRepository().save(this.orderData);
	}

}
