package qora.assets;

import data.assets.OrderData;
import data.assets.TradeData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import repository.AssetRepository;
import repository.DataException;
import repository.Repository;

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
		initiatingOrder.setFulfilled(initiatingOrder.getFulfilled().add(tradeData.getPrice()));
		initiatingOrder.setIsFulfilled(Order.isFulfilled(initiatingOrder));
		assetRepository.save(initiatingOrder);

		OrderData targetOrder = assetRepository.fromOrderId(this.tradeData.getTarget());
		targetOrder.setFulfilled(targetOrder.getFulfilled().add(tradeData.getAmount()));
		targetOrder.setIsFulfilled(Order.isFulfilled(targetOrder));
		assetRepository.save(targetOrder);

		// Actually transfer asset balances
		Account initiatingCreator = new PublicKeyAccount(this.repository, initiatingOrder.getCreatorPublicKey());
		initiatingCreator.setConfirmedBalance(initiatingOrder.getWantAssetId(),
				initiatingCreator.getConfirmedBalance(initiatingOrder.getWantAssetId()).add(tradeData.getAmount()));

		Account targetCreator = new PublicKeyAccount(this.repository, targetOrder.getCreatorPublicKey());
		targetCreator.setConfirmedBalance(targetOrder.getWantAssetId(),
				targetCreator.getConfirmedBalance(targetOrder.getWantAssetId()).add(tradeData.getPrice()));
	}

	public void orphan() throws DataException {
		AssetRepository assetRepository = this.repository.getAssetRepository();

		// Revert corresponding Orders on both sides of trade
		OrderData initiatingOrder = assetRepository.fromOrderId(this.tradeData.getInitiator());
		initiatingOrder.setFulfilled(initiatingOrder.getFulfilled().subtract(tradeData.getPrice()));
		initiatingOrder.setIsFulfilled(Order.isFulfilled(initiatingOrder));
		assetRepository.save(initiatingOrder);

		OrderData targetOrder = assetRepository.fromOrderId(this.tradeData.getTarget());
		targetOrder.setFulfilled(targetOrder.getFulfilled().subtract(tradeData.getAmount()));
		targetOrder.setIsFulfilled(Order.isFulfilled(targetOrder));
		assetRepository.save(targetOrder);

		// Reverse asset transfers
		Account initiatingCreator = new PublicKeyAccount(this.repository, initiatingOrder.getCreatorPublicKey());
		initiatingCreator.setConfirmedBalance(initiatingOrder.getWantAssetId(),
				initiatingCreator.getConfirmedBalance(initiatingOrder.getWantAssetId()).subtract(tradeData.getAmount()));

		Account targetCreator = new PublicKeyAccount(this.repository, targetOrder.getCreatorPublicKey());
		targetCreator.setConfirmedBalance(targetOrder.getWantAssetId(),
				targetCreator.getConfirmedBalance(targetOrder.getWantAssetId()).subtract(tradeData.getPrice()));

		// Remove trade from repository
		assetRepository.delete(tradeData);
	}

}
