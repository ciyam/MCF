package org.qora.asset;

import java.math.BigDecimal;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.block.BlockChain;
import org.qora.data.asset.OrderData;
import org.qora.data.asset.TradeData;
import org.qora.repository.AssetRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class Trade {

	// Properties
	private Repository repository;
	private TradeData tradeData;

	// Constructors

	public Trade(Repository repository, TradeData tradeData) {
		this.repository = repository;
		this.tradeData = tradeData;
	}

	// Processing

	public void process() throws DataException {
		AssetRepository assetRepository = this.repository.getAssetRepository();

		// Save trade into repository
		assetRepository.save(tradeData);

		// Update corresponding Orders on both sides of trade
		OrderData initiatingOrder = assetRepository.fromOrderId(this.tradeData.getInitiator());
		OrderData targetOrder = assetRepository.fromOrderId(this.tradeData.getTarget());

		// Under 'new' pricing scheme, "amount" and "fulfilled" are the same asset for both orders
		boolean isNewPricing = initiatingOrder.getTimestamp() > BlockChain.getInstance().getNewAssetPricingTimestamp();
		BigDecimal newPricingAmount = (initiatingOrder.getHaveAssetId() < initiatingOrder.getWantAssetId()) ? this.tradeData.getTargetAmount() : this.tradeData.getInitiatorAmount();

		initiatingOrder.setFulfilled(initiatingOrder.getFulfilled().add(isNewPricing ? newPricingAmount : tradeData.getInitiatorAmount()));
		initiatingOrder.setIsFulfilled(Order.isFulfilled(initiatingOrder));
		// Set isClosed to true if isFulfilled now true
		initiatingOrder.setIsClosed(initiatingOrder.getIsFulfilled());
		assetRepository.save(initiatingOrder);

		targetOrder.setFulfilled(targetOrder.getFulfilled().add(isNewPricing ? newPricingAmount : tradeData.getTargetAmount()));
		targetOrder.setIsFulfilled(Order.isFulfilled(targetOrder));
		// Set isClosed to true if isFulfilled now true
		targetOrder.setIsClosed(targetOrder.getIsFulfilled());
		assetRepository.save(targetOrder);

		// Actually transfer asset balances
		Account initiatingCreator = new PublicKeyAccount(this.repository, initiatingOrder.getCreatorPublicKey());
		initiatingCreator.setConfirmedBalance(initiatingOrder.getWantAssetId(),
				initiatingCreator.getConfirmedBalance(initiatingOrder.getWantAssetId()).add(tradeData.getTargetAmount()));

		Account targetCreator = new PublicKeyAccount(this.repository, targetOrder.getCreatorPublicKey());
		targetCreator.setConfirmedBalance(targetOrder.getWantAssetId(),
				targetCreator.getConfirmedBalance(targetOrder.getWantAssetId()).add(tradeData.getInitiatorAmount()));
	}

	public void orphan() throws DataException {
		AssetRepository assetRepository = this.repository.getAssetRepository();

		// Revert corresponding Orders on both sides of trade
		OrderData initiatingOrder = assetRepository.fromOrderId(this.tradeData.getInitiator());
		OrderData targetOrder = assetRepository.fromOrderId(this.tradeData.getTarget());

		// Under 'new' pricing scheme, "amount" and "fulfilled" are the same asset for both orders
		boolean isNewPricing = initiatingOrder.getTimestamp() > BlockChain.getInstance().getNewAssetPricingTimestamp();
		BigDecimal newPricingAmount = (initiatingOrder.getHaveAssetId() < initiatingOrder.getWantAssetId()) ? this.tradeData.getTargetAmount() : this.tradeData.getInitiatorAmount();

		initiatingOrder.setFulfilled(initiatingOrder.getFulfilled().subtract(isNewPricing ? newPricingAmount : tradeData.getInitiatorAmount()));
		initiatingOrder.setIsFulfilled(Order.isFulfilled(initiatingOrder));
		// Set isClosed to false if isFulfilled now false
		initiatingOrder.setIsClosed(initiatingOrder.getIsFulfilled());
		assetRepository.save(initiatingOrder);

		targetOrder.setFulfilled(targetOrder.getFulfilled().subtract(isNewPricing ? newPricingAmount : tradeData.getTargetAmount()));
		targetOrder.setIsFulfilled(Order.isFulfilled(targetOrder));
		// Set isClosed to false if isFulfilled now false
		targetOrder.setIsClosed(targetOrder.getIsFulfilled());
		assetRepository.save(targetOrder);

		// Reverse asset transfers
		Account initiatingCreator = new PublicKeyAccount(this.repository, initiatingOrder.getCreatorPublicKey());
		initiatingCreator.setConfirmedBalance(initiatingOrder.getWantAssetId(),
				initiatingCreator.getConfirmedBalance(initiatingOrder.getWantAssetId()).subtract(tradeData.getTargetAmount()));

		Account targetCreator = new PublicKeyAccount(this.repository, targetOrder.getCreatorPublicKey());
		targetCreator.setConfirmedBalance(targetOrder.getWantAssetId(),
				targetCreator.getConfirmedBalance(targetOrder.getWantAssetId()).subtract(tradeData.getInitiatorAmount()));

		// Remove trade from repository
		assetRepository.delete(tradeData);
	}

}
