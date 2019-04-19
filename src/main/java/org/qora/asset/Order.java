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
import org.qora.utils.Base58;

public class Order {

	private static final Logger LOGGER = LogManager.getLogger(Order.class);

	// Properties
	private Repository repository;
	private OrderData orderData;

	// Used quite a bit
	private final boolean isOurOrderNewPricing;
	private final long haveAssetId;
	private final long wantAssetId;

	/** Cache of price-pair units e.g. QORA/GOLD, but use getPricePair() instead! */
	private String cachedPricePair;

	/** Cache of have-asset data - but use getHaveAsset() instead! */
	AssetData cachedHaveAssetData;
	/** Cache of want-asset data - but use getWantAsset() instead! */
	AssetData cachedWantAssetData;

	// Constructors

	public Order(Repository repository, OrderData orderData) {
		this.repository = repository;
		this.orderData = orderData;

		this.isOurOrderNewPricing = this.orderData.getTimestamp() >= BlockChain.getInstance().getNewAssetPricingTimestamp();
		this.haveAssetId = this.orderData.getHaveAssetId();
		this.wantAssetId = this.orderData.getWantAssetId();
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
	 * Returns granularity/batch-size of matched-amount, given price, so that return-amount is valid size.
	 * <p>
	 * If matched-amount of matched-asset is traded when two orders match,
	 * then the corresponding return-amount of the other (return) asset needs to be either
	 * an integer, if return-asset is indivisible,
	 * or to the nearest 0.00000001 if return-asset is divisible.
	 * <p>
	 * @return granularity of matched-amount
	 */
	public static BigDecimal calculateAmountGranularity(boolean isAmountAssetDivisible, boolean isReturnAssetDivisible, BigDecimal price) {
		// Multiplier to scale BigDecimal fractional amounts into integer domain
		BigInteger multiplier = BigInteger.valueOf(1_0000_0000L);

		// Calculate the minimum increment for matched-amount using greatest-common-divisor
		BigInteger returnAmount = multiplier; // 1 unit (* multiplier)
		BigInteger matchedAmount = price.movePointRight(8).toBigInteger();

		BigInteger gcd = returnAmount.gcd(matchedAmount);
		returnAmount = returnAmount.divide(gcd);
		matchedAmount = matchedAmount.divide(gcd);

		// Calculate GCD in combination with divisibility
		if (isAmountAssetDivisible)
			returnAmount = returnAmount.multiply(multiplier);

		if (isReturnAssetDivisible)
			matchedAmount = matchedAmount.multiply(multiplier);

		gcd = returnAmount.gcd(matchedAmount);

		// Calculate the granularity at which we have to buy
		BigDecimal granularity = new BigDecimal(returnAmount.divide(gcd));
		if (isAmountAssetDivisible)
			granularity = granularity.movePointLeft(8);

		// Return
		return granularity;
	}

	/**
	 * Returns price-pair in string form.
	 * <p>
	 * e.g. <tt>"QORA/GOLD"</tt>
	 */
	public String getPricePair() throws DataException {
		if (cachedPricePair == null)
			calcPricePair();

		return cachedPricePair;
	}

	/** Calculate price pair. (e.g. QORA/GOLD)
	 * <p>
	 * Under 'new' pricing scheme, lowest-assetID asset is first,
	 * so if QORA has assetID 0 and GOLD has assetID 10, then
	 * the pricing pair is QORA/GOLD.
	 * <p>
	 * This means the "amount" fields are expressed in terms
	 * of the higher-assetID asset. (e.g. GOLD)
	 */
	private void calcPricePair() throws DataException {
		AssetData haveAssetData = getHaveAsset();
		AssetData wantAssetData = getWantAsset();

		if (isOurOrderNewPricing && haveAssetId > wantAssetId)
			cachedPricePair = wantAssetData.getName() + "/" + haveAssetData.getName();
		else
			cachedPricePair = haveAssetData.getName() + "/" + wantAssetData.getName();
	}

	/** Returns amount of have-asset to remove from order's creator's balance on placing this order. */
	private BigDecimal calcHaveAssetCommittment() {
		BigDecimal committedCost = this.orderData.getAmount();

		// If 'new' pricing and "amount" is in want-asset then we need to convert
		if (isOurOrderNewPricing && haveAssetId < wantAssetId)
			committedCost = committedCost.multiply(this.orderData.getPrice()).setScale(8, RoundingMode.HALF_UP);

		return committedCost;
	}

	/** Returns amount of remaining have-asset to refund to order's creator's balance on cancelling this order. */
	private BigDecimal calcHaveAssetRefund() {
		BigDecimal refund = getAmountLeft();

		// If 'new' pricing and "amount" is in want-asset then we need to convert
		if (isOurOrderNewPricing && haveAssetId < wantAssetId)
			refund = refund.multiply(this.orderData.getPrice()).setScale(8, RoundingMode.HALF_UP);

		return refund;
	}

	// Navigation

	public List<TradeData> getTrades() throws DataException {
		return this.repository.getAssetRepository().getOrdersTrades(this.orderData.getOrderId());
	}

	public AssetData getHaveAsset() throws DataException {
		if (cachedHaveAssetData == null)
			cachedHaveAssetData = this.repository.getAssetRepository().fromAssetId(haveAssetId);

		return cachedHaveAssetData;
	}

	public AssetData getWantAsset() throws DataException {
		if (cachedWantAssetData == null)
			cachedWantAssetData = this.repository.getAssetRepository().fromAssetId(wantAssetId);

		return cachedWantAssetData;
	}

	/**
	 * Returns AssetData for asset in effect for "amount" field.
	 * <p>
	 * For 'old' pricing, this is the have-asset.<br>
	 * For 'new' pricing, this is the asset with highest assetID.
	 */
	public AssetData getAmountAsset() throws DataException {
		if (isOurOrderNewPricing && wantAssetId > haveAssetId)
			return getWantAsset();
		else
			return getHaveAsset();
	}

	/**
	 * Returns AssetData for other (return) asset traded.
	 * <p>
	 * For 'old' pricing, this is the want-asset.<br>
	 * For 'new' pricing, this is the asset with lowest assetID.
	 */
	public AssetData getReturnAsset() throws DataException {
		if (isOurOrderNewPricing && haveAssetId < wantAssetId)
			return getHaveAsset();
		else
			return getWantAsset();
	}

	// Processing

	private void logOrder(String orderPrefix, boolean isOurOrder, OrderData orderData) throws DataException {
		// Avoid calculations if possible
		if (LOGGER.getLevel().isMoreSpecificThan(Level.DEBUG))
			return;

		final String weThey = isOurOrder ? "We" : "They";
		final String ourTheir = isOurOrder ? "Our" : "Their";

		// NOTE: the following values are specific to passed orderData, not the same as class instance values!

		final boolean isOrderNewAssetPricing = orderData.getTimestamp() >= BlockChain.getInstance().getNewAssetPricingTimestamp();

		final long haveAssetId = orderData.getHaveAssetId();
		final long wantAssetId = orderData.getWantAssetId();

		final AssetData haveAssetData = this.repository.getAssetRepository().fromAssetId(haveAssetId);
		final AssetData wantAssetData = this.repository.getAssetRepository().fromAssetId(wantAssetId);

		final long amountAssetId = (isOurOrderNewPricing && wantAssetId > haveAssetId) ? wantAssetId : haveAssetId;
		final long returnAssetId = (isOurOrderNewPricing && haveAssetId < wantAssetId) ? haveAssetId : wantAssetId;

		final AssetData amountAssetData = this.repository.getAssetRepository().fromAssetId(amountAssetId);
		final AssetData returnAssetData = this.repository.getAssetRepository().fromAssetId(returnAssetId);

		LOGGER.debug(String.format("%s %s", orderPrefix, Base58.encode(orderData.getOrderId())));

		LOGGER.trace(String.format("%s have %s, want %s. '%s' pricing scheme.", weThey, haveAssetData.getName(), wantAssetData.getName(), isOrderNewAssetPricing ? "new" : "old"));

		LOGGER.trace(String.format("%s amount: %s (ordered) - %s (fulfilled) = %s %s left", ourTheir,
				orderData.getAmount().stripTrailingZeros().toPlainString(),
				orderData.getFulfilled().stripTrailingZeros().toPlainString(),
				Order.getAmountLeft(orderData).stripTrailingZeros().toPlainString(),
				amountAssetData.getName()));

		BigDecimal maxReturnAmount = Order.getAmountLeft(orderData).multiply(orderData.getPrice()).setScale(8, RoundingMode.HALF_UP);

		LOGGER.trace(String.format("%s price: %s %s (%s %s tradable)", ourTheir,
				orderData.getPrice().toPlainString(), getPricePair(),
				maxReturnAmount.stripTrailingZeros().toPlainString(), returnAssetData.getName()));
	}

	public void process() throws DataException {
		AssetRepository assetRepository = this.repository.getAssetRepository();

		AssetData haveAssetData = getHaveAsset();
		AssetData wantAssetData = getWantAsset();

		/** The asset while working out amount that matches. */
		AssetData matchingAssetData = isOurOrderNewPricing ? getAmountAsset() : wantAssetData;
		/** The return asset traded if trade completes. */
		AssetData returnAssetData = isOurOrderNewPricing ? getReturnAsset() : haveAssetData;

		// Subtract have-asset from creator
		Account creator = new PublicKeyAccount(this.repository, this.orderData.getCreatorPublicKey());
		creator.setConfirmedBalance(haveAssetId, creator.getConfirmedBalance(haveAssetId).subtract(this.calcHaveAssetCommittment()));

		// Save this order into repository so it's available for matching, possibly by itself
		this.repository.getAssetRepository().save(this.orderData);

		logOrder("Processing our order", true, this.orderData);

		// Fetch corresponding open orders that might potentially match, hence reversed want/have assetIDs.
		// Returned orders are sorted with lowest "price" first.
		List<OrderData> orders = assetRepository.getOpenOrdersForTrading(wantAssetId, haveAssetId, isOurOrderNewPricing ? this.orderData.getPrice() : null);
		LOGGER.trace("Open orders fetched from repository: " + orders.size());

		if (orders.isEmpty())
			return;

		// Attempt to match orders

		/*
		 * Potential matching order example ("old"):
		 * 
		 * Our order:
		 * haveAssetId=[GOLD], wantAssetId=0 (QORA), amount=40 (GOLD), price=486 (QORA/GOLD)
		 * This translates to "we have 40 GOLD and want QORA at a price of 486 QORA per GOLD"
		 * If our order matched, we'd end up with 40 * 486 = 19,440 QORA.
		 * 
		 * Their order:
		 * haveAssetId=0 (QORA), wantAssetId=[GOLD], amount=20,000 (QORA), price=0.00205761 (GOLD/QORA)
		 * This translates to "they have 20,000 QORA and want GOLD at a price of 0.00205761 GOLD per QORA"
		 * 
		 * Their price, converted into 'our' units of QORA/GOLD, is: 1 / 0.00205761 = 486.00074844 QORA/GOLD.
		 * This is better than our requested 486 QORA/GOLD so this order matches.
		 * 
		 * Using their price, we end up with 40 * 486.00074844 = 19440.02993760 QORA. They end up with 40 GOLD.
		 * 
		 * If their order had 19,440 QORA left, only 19,440 * 0.00205761 = 39.99993840 GOLD would be traded.
		 */

		/*
		 * Potential matching order example ("new"):
		 * 
		 * Our order:
		 * haveAssetId=[GOLD], wantAssetId=0 (QORA), amount=40 (GOLD), price=486 (QORA/GOLD)
		 * This translates to "we have 40 GOLD and want QORA at a price of 486 QORA per GOLD"
		 * If our order matched, we'd end up with 19,440 QORA at a cost of 19,440 / 486 = 40 GOLD.
		 * 
		 * Their order:
		 * haveAssetId=0 (QORA), wantAssetId=[GOLD], amount=40 (GOLD), price=486.00074844 (QORA/GOLD)
		 * This translates to "they have QORA and want GOLD at a price of 486.00074844 QORA per GOLD"
		 * 
		 * Their price is better than our requested 486 QORA/GOLD so this order matches.
		 * 
		 * Using their price, we end up with 40 * 486.00074844 = 19440.02993760 QORA. They end up with 40 GOLD.
		 * 
		 * If their order only had 36 GOLD left, only 36 * 486.00074844 = 17496.02694384 QORA would be traded.
		 */

		BigDecimal ourPrice = this.orderData.getPrice();

		for (OrderData theirOrderData : orders) {
			logOrder("Considering order", false, theirOrderData);

			// Not used:
			// boolean isTheirOrderNewAssetPricing = theirOrderData.getTimestamp() >= BlockChain.getInstance().getNewAssetPricingTimestamp();

			// Determine their order price
			BigDecimal theirPrice;

			if (isOurOrderNewPricing) {
				// Pricing units are the same way round for both orders, so no conversion needed.
				// Orders under 'old' pricing have been converted during repository update.
				theirPrice = theirOrderData.getPrice();
				LOGGER.trace(String.format("Their price: %s %s", theirPrice.toPlainString(), getPricePair()));
			} else {
				// If our order is 'old' pricing then all other existing orders must be 'old' pricing too
				// Their order pricing will be inverted, so convert
				theirPrice = BigDecimal.ONE.setScale(8).divide(theirOrderData.getPrice(), RoundingMode.DOWN);
				LOGGER.trace(String.format("Their price: %s %s per %s", theirPrice.toPlainString(), wantAssetData.getName(), haveAssetData.getName()));
			}

			// If their price is worse than what we're willing to accept then we're done as prices only get worse as we iterate through list of orders
			if (isOurOrderNewPricing) {
				if (haveAssetId < wantAssetId && theirPrice.compareTo(ourPrice) > 0)
					break;
				if (haveAssetId > wantAssetId && theirPrice.compareTo(ourPrice) < 0)
					break;
			} else {
				// 'old' pricing scheme
				if (theirPrice.compareTo(ourPrice) < 0)
					break;
			}

			// Calculate how much we could buy at their price.
			BigDecimal ourMaxAmount;
			if (isOurOrderNewPricing)
				// In 'new' pricing scheme, "amount" is expressed in terms of asset with highest assetID
				ourMaxAmount = this.getAmountLeft();
			else
				// In 'old' pricing scheme, "amount" is expressed in terms of our want-asset.
				ourMaxAmount = this.getAmountLeft().multiply(theirPrice).setScale(8, RoundingMode.DOWN);
			LOGGER.trace("ourMaxAmount (max we could trade at their price): " + ourMaxAmount.stripTrailingZeros().toPlainString() + " " + matchingAssetData.getName());

			// How much is remaining available in their order.
			BigDecimal theirAmountLeft = Order.getAmountLeft(theirOrderData);
			LOGGER.trace("theirAmountLeft (max amount remaining in their order): " + theirAmountLeft.stripTrailingZeros().toPlainString() + " " + matchingAssetData.getName());

			// So matchable want-asset amount is the minimum of above two values
			BigDecimal matchedAmount = ourMaxAmount.min(theirAmountLeft);
			LOGGER.trace("matchedAmount: " + matchedAmount.stripTrailingZeros().toPlainString() + " " + matchingAssetData.getName());

			// If we can't buy anything then try another order
			if (matchedAmount.compareTo(BigDecimal.ZERO) <= 0)
				continue;

			// Calculate amount granularity, based on price and both assets' divisibility, so that return-amount traded is a valid value (integer or to 8 d.p.)
			BigDecimal granularity = calculateAmountGranularity(matchingAssetData.getIsDivisible(), returnAssetData.getIsDivisible(), theirOrderData.getPrice());
			LOGGER.trace("granularity (amount granularity): " + granularity.stripTrailingZeros().toPlainString() + " " + matchingAssetData.getName());

			// Reduce matched amount (if need be) to fit granularity
			matchedAmount = matchedAmount.subtract(matchedAmount.remainder(granularity));
			LOGGER.trace("matchedAmount adjusted for granularity: " + matchedAmount.stripTrailingZeros().toPlainString() + " " + matchingAssetData.getName());

			// If we can't buy anything then try another order
			if (matchedAmount.compareTo(BigDecimal.ZERO) <= 0)
				continue;

			// Safety check
			if (!matchingAssetData.getIsDivisible() && matchedAmount.stripTrailingZeros().scale() > 0) {
				Account participant = new PublicKeyAccount(this.repository, theirOrderData.getCreatorPublicKey());

				String message = String.format("Refusing to trade fractional %s [indivisible assetID %d] for %s",
						matchedAmount.toPlainString(), matchingAssetData.getAssetId(), participant.getAddress());
				LOGGER.error(message);
				throw new DataException(message);
			}

			// Trade can go ahead!

			// Calculate the total cost to us, in return-asset, based on their price
			BigDecimal returnAmountTraded = matchedAmount.multiply(theirOrderData.getPrice()).setScale(8, RoundingMode.DOWN);
			LOGGER.trace("returnAmountTraded: " + returnAmountTraded.stripTrailingZeros().toPlainString() + " " + returnAssetData.getName());

			// Safety check
			if (!returnAssetData.getIsDivisible() && returnAmountTraded.stripTrailingZeros().scale() > 0) {
				String message = String.format("Refusing to trade fractional %s [indivisible assetID %d] for %s",
						returnAmountTraded.toPlainString(), returnAssetData.getAssetId(), creator.getAddress());
				LOGGER.error(message);
				throw new DataException(message);
			}

			BigDecimal tradedWantAmount = (isOurOrderNewPricing && haveAssetId > wantAssetId) ? returnAmountTraded : matchedAmount;
			BigDecimal tradedHaveAmount = (isOurOrderNewPricing && haveAssetId > wantAssetId) ? matchedAmount : returnAmountTraded;

			// We also need to know how much have-asset to refund based on price improvement ('new' pricing only and only one direction applies)
			BigDecimal haveAssetRefund = isOurOrderNewPricing && haveAssetId < wantAssetId ? ourPrice.subtract(theirPrice).abs().multiply(matchedAmount).setScale(8, RoundingMode.DOWN) : BigDecimal.ZERO;

			LOGGER.trace(String.format("We traded %s %s (have-asset) for %s %s (want-asset), saving %s %s (have-asset)",
					tradedHaveAmount.toPlainString(), haveAssetData.getName(),
					tradedWantAmount.toPlainString(), wantAssetData.getName(),
					haveAssetRefund.toPlainString(), haveAssetData.getName()));

			// Construct trade
			TradeData tradeData = new TradeData(this.orderData.getOrderId(), theirOrderData.getOrderId(),
					tradedWantAmount, tradedHaveAmount, haveAssetRefund, this.orderData.getTimestamp());
			// Process trade, updating corresponding orders in repository
			Trade trade = new Trade(this.repository, tradeData);
			trade.process();

			// Update our order in terms of fulfilment, etc. but do not save into repository as that's handled by Trade above
			BigDecimal amountFulfilled = isOurOrderNewPricing ? matchedAmount : returnAmountTraded;
			this.orderData.setFulfilled(this.orderData.getFulfilled().add(amountFulfilled));
			LOGGER.trace("Updated our order's fulfilled amount to: " + this.orderData.getFulfilled().stripTrailingZeros().toPlainString() + " " + matchingAssetData.getName());
			LOGGER.trace("Our order's amount remaining: " + this.getAmountLeft().stripTrailingZeros().toPlainString() + " " + matchingAssetData.getName());

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
		Account creator = new PublicKeyAccount(this.repository, this.orderData.getCreatorPublicKey());
		creator.setConfirmedBalance(haveAssetId, creator.getConfirmedBalance(haveAssetId).add(this.calcHaveAssetCommittment()));
	}

	// This is called by CancelOrderTransaction so that an Order can no longer trade
	public void cancel() throws DataException {
		this.orderData.setIsClosed(true);
		this.repository.getAssetRepository().save(this.orderData);

		// Update creator's balance with unfulfilled amount
		Account creator = new PublicKeyAccount(this.repository, this.orderData.getCreatorPublicKey());
		creator.setConfirmedBalance(haveAssetId, creator.getConfirmedBalance(haveAssetId).add(calcHaveAssetRefund()));
	}

	// Opposite of cancel() above for use during orphaning
	public void reopen() throws DataException {
		// Update creator's balance with unfulfilled amount
		Account creator = new PublicKeyAccount(this.repository, this.orderData.getCreatorPublicKey());
		creator.setConfirmedBalance(haveAssetId, creator.getConfirmedBalance(haveAssetId).subtract(calcHaveAssetRefund()));

		this.orderData.setIsClosed(false);
		this.repository.getAssetRepository().save(this.orderData);
	}

}
