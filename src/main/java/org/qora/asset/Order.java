package org.qora.asset;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.data.asset.AssetData;
import org.qora.data.asset.OrderData;
import org.qora.data.asset.TradeData;
import org.qora.repository.AssetRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.hash.HashCode;

public class Order {

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

	public BigDecimal calculateAmountGranularity(AssetData haveAssetData, AssetData wantAssetData, OrderData theirOrderData) {
		// 100 million to scale BigDecimal.setScale(8) fractional amounts into integers, essentially 1e8
		BigInteger multiplier = BigInteger.valueOf(100_000_000L);

		// Calculate the minimum increment at which I can buy using greatest-common-divisor
		BigInteger haveAmount = BigInteger.ONE.multiply(multiplier);
		BigInteger priceAmount = theirOrderData.getPrice().multiply(new BigDecimal(multiplier)).toBigInteger();
		BigInteger gcd = haveAmount.gcd(priceAmount);
		haveAmount = haveAmount.divide(gcd);
		priceAmount = priceAmount.divide(gcd);

		// Calculate GCD in combination with divisibility
		if (wantAssetData.getIsDivisible())
			haveAmount = haveAmount.multiply(multiplier);

		if (haveAssetData.getIsDivisible())
			priceAmount = priceAmount.multiply(multiplier);

		gcd = haveAmount.gcd(priceAmount);

		// Calculate the increment at which we have to buy
		BigDecimal increment = new BigDecimal(haveAmount.divide(gcd));
		if (wantAssetData.getIsDivisible())
			increment = increment.divide(new BigDecimal(multiplier));

		// Return
		return increment;
	}

	// Navigation

	public List<TradeData> getTrades() throws DataException {
		return this.repository.getAssetRepository().getOrdersTrades(this.orderData.getOrderId());
	}

	// Processing

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

		// Attempt to match orders
		LOGGER.debug("Processing our order " + HashCode.fromBytes(this.orderData.getOrderId()).toString());
		LOGGER.trace("We have: " + this.orderData.getAmount().toPlainString() + " " + haveAssetData.getName());
		LOGGER.trace("We want " + this.orderData.getPrice().toPlainString() + " " + wantAssetData.getName() + " per " + haveAssetData.getName());

		// Fetch corresponding open orders that might potentially match, hence reversed want/have assetId args.
		// Returned orders are sorted with lowest "price" first.
		List<OrderData> orders = assetRepository.getOpenOrders(wantAssetId, haveAssetId);
		LOGGER.trace("Open orders fetched from repository: " + orders.size());

		/*
		 * Our order example:
		 * 
		 * haveAssetId=[GOLD], amount=10,000, wantAssetId=0 (QORA), price=0.002
		 * 
		 * This translates to "we have 10,000 GOLD and want to buy QORA at a price of 0.002 QORA per GOLD"
		 * 
		 * So if our order matched, we'd end up with 10,000 * 0.002 = 20 QORA, essentially costing 1/0.002 = 500 GOLD each.
		 * 
		 * So 500 GOLD [each] is our "buyingPrice".
		 */
		BigDecimal ourPrice = this.orderData.getPrice();

		for (OrderData theirOrderData : orders) {
			LOGGER.trace("Considering order " + HashCode.fromBytes(theirOrderData.getOrderId()).toString());
			// Note swapped use of have/want asset data as this is from 'their' perspective.
			LOGGER.trace("They have: " + theirOrderData.getAmount().toPlainString() + " " + wantAssetData.getName());
			LOGGER.trace("They want " + theirOrderData.getPrice().toPlainString() + " " + haveAssetData.getName() + " per " + wantAssetData.getName());

			/*
			 * Potential matching order example:
			 * 
			 * haveAssetId=0 (QORA), amount=40, wantAssetId=[GOLD], price=486
			 * 
			 * This translates to "we have 40 QORA and want to buy GOLD at a price of 486 GOLD per QORA"
			 * 
			 * So if their order matched, they'd end up with 40 * 486 = 19,440 GOLD, essentially costing 1/486 = 0.00205761 QORA each.
			 * 
			 * So 0.00205761 QORA [each] is their "buyingPrice".
			 */

			// Round down otherwise their buyingPrice would be better than advertised and cause issues
			BigDecimal theirBuyingPrice = BigDecimal.ONE.setScale(8).divide(theirOrderData.getPrice(), RoundingMode.DOWN);
			LOGGER.trace("theirBuyingPrice: " + theirBuyingPrice.toPlainString() + " " + wantAssetData.getName() + " per " + haveAssetData.getName());

			// If their buyingPrice is less than what we're willing to pay then we're done as prices only get worse as we iterate through list of orders
			if (theirBuyingPrice.compareTo(ourPrice) < 0)
				break;

			// Calculate how many want-asset we could buy at their price
			BigDecimal ourAmountLeft = this.getAmountLeft().multiply(theirBuyingPrice).setScale(8, RoundingMode.DOWN);
			LOGGER.trace("ourAmountLeft (max we could buy at their price): " + ourAmountLeft.toPlainString() + " " + wantAssetData.getName());
			// How many want-asset is remaining available in this order
			BigDecimal theirAmountLeft = Order.getAmountLeft(theirOrderData);
			LOGGER.trace("theirAmountLeft (max amount remaining in order): " + theirAmountLeft.toPlainString() + " " + wantAssetData.getName());
			// So matchable want-asset amount is the minimum of above two values
			BigDecimal matchedAmount = ourAmountLeft.min(theirAmountLeft);
			LOGGER.trace("matchedAmount: " + matchedAmount.toPlainString() + " " + wantAssetData.getName());

			// If we can't buy anything then try another order
			if (matchedAmount.compareTo(BigDecimal.ZERO) <= 0)
				continue;

			// Calculate amount granularity based on both assets' divisibility
			BigDecimal increment = this.calculateAmountGranularity(haveAssetData, wantAssetData, theirOrderData);
			LOGGER.trace("increment (want-asset amount granularity): " + increment.toPlainString() + " " + wantAssetData.getName());
			matchedAmount = matchedAmount.subtract(matchedAmount.remainder(increment));
			LOGGER.trace("matchedAmount adjusted for granularity: " + matchedAmount.toPlainString() + " " + wantAssetData.getName());

			// If we can't buy anything then try another order
			if (matchedAmount.compareTo(BigDecimal.ZERO) <= 0)
				continue;

			// Trade can go ahead!

			// Calculate the total cost to us, in have-asset, based on their price
			BigDecimal tradePrice = matchedAmount.multiply(theirOrderData.getPrice()).setScale(8);
			LOGGER.trace("tradePrice ('want' trade agreed): " + tradePrice.toPlainString() + " " + haveAssetData.getName());

			// Construct trade
			TradeData tradeData = new TradeData(this.orderData.getOrderId(), theirOrderData.getOrderId(), matchedAmount, tradePrice,
					this.orderData.getTimestamp());
			// Process trade, updating corresponding orders in repository
			Trade trade = new Trade(this.repository, tradeData);
			trade.process();

			// Update our order in terms of fulfilment, etc. but do not save into repository as that's handled by Trade above
			this.orderData.setFulfilled(this.orderData.getFulfilled().add(tradePrice));
			LOGGER.trace("Updated our order's fulfilled amount to: " + this.orderData.getFulfilled().toPlainString() + " " + haveAssetData.getName());
			LOGGER.trace("Our order's amount remaining: " + this.getAmountLeft().toPlainString() + " " + haveAssetData.getName());

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
