package api.models;

import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import api.ApiError;
import api.ApiErrorFactory;
import data.block.BlockData;
import data.transaction.TransactionData;
import io.swagger.v3.oas.annotations.media.Schema;
import qora.block.Block;
import repository.DataException;
import repository.Repository;

@Schema(description = "Block with (optional) transactions")
@XmlAccessorType(XmlAccessType.FIELD)
public class BlockWithTransactions {

	@Schema(implementation = BlockData.class, name = "block", title = "block data")
	@XmlElement(name = "block")
	public BlockData blockData;

	public List<TransactionData> transactions;

	// For JAX-RS
	protected BlockWithTransactions() {
	}

	public BlockWithTransactions(Repository repository, BlockData blockData, boolean includeTransactions) throws DataException {
		if (blockData == null)
			throw ApiErrorFactory.getInstance().createError(ApiError.BLOCK_NO_EXISTS);

		this.blockData = blockData;

		if (includeTransactions) {
			Block block = new Block(repository, blockData);
			this.transactions = block.getTransactions().stream().map(transaction -> transaction.getTransactionData()).collect(Collectors.toList());
		}
	}

}
