package qora.assets;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

import data.assets.AssetData;
import data.assets.OrderData;
import data.assets.TradeData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import repository.AssetRepository;
import repository.DataException;
import repository.Repository;

public class Order {

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

		// Fetch corresponding open orders that might potentially match, hence reversed want/have assetId args.
		// Returned orders are sorted with lowest "price" first.
		List<OrderData> orders = assetRepository.getOpenOrders(wantAssetId, haveAssetId);

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

			// If their buyingPrice is less than what we're willing to pay then we're done as prices only get worse as we iterate through list of orders
			if (theirBuyingPrice.compareTo(ourPrice) < 0)
				break;

			// Calculate how many want-asset we could buy at their price
			BigDecimal ourAmountLeft = this.getAmountLeft().multiply(theirBuyingPrice).setScale(8, RoundingMode.DOWN);
			// How many want-asset is left available in this order
			BigDecimal theirAmountLeft = Order.getAmountLeft(theirOrderData);
			// So matchable want-asset amount is the minimum of above two values
			BigDecimal matchedAmount = ourAmountLeft.min(theirAmountLeft);

			// If we can't buy anything then we're done
			if (matchedAmount.compareTo(BigDecimal.ZERO) <= 0)
				break;

			// Calculate amount granularity based on both assets' divisibility
			BigDecimal increment = this.calculateAmountGranularity(haveAssetData, wantAssetData, theirOrderData);
			matchedAmount = matchedAmount.subtract(matchedAmount.remainder(increment));

			// If we can't buy anything then we're done
			if (matchedAmount.compareTo(BigDecimal.ZERO) <= 0)
				break;

			// Trade can go ahead!

			// Calculate the total cost to us based on their price
			BigDecimal tradePrice = matchedAmount.multiply(theirOrderData.getPrice()).setScale(8);

			// Construct trade
			TradeData tradeData = new TradeData(this.orderData.getOrderId(), theirOrderData.getOrderId(), matchedAmount, tradePrice,
					this.orderData.getTimestamp());
			// Process trade, updating corresponding orders in repository
			Trade trade = new Trade(this.repository, tradeData);
			trade.process();

			// Update our order in terms of fulfilment, etc. but do not save into repository as that's handled by Trade above
			this.orderData.setFulfilled(this.orderData.getFulfilled().add(matchedAmount));

			// Continue on to process other open orders in case we still have amount left to match
		}
	}

	public void orphan() throws DataException {
		// Orphan trades that occurred as a result of this order
		for (TradeData tradeData : getTrades()) {
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
