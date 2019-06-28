package api.models;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import data.block.BlockData;
import data.transaction.TransactionData;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Block info, maybe including transactions")
// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class BlockWithTransactions {

	@Schema(implementation = BlockData.class, name = "block", title = "block data")
	@XmlElement(name = "block")
	public BlockData blockData;

	public List<TransactionData> transactions;

	// For JAX-RS
	protected BlockWithTransactions() {
	}

	public BlockWithTransactions(BlockData blockData, List<TransactionData> transactions) {
		this.blockData = blockData;
		this.transactions = transactions;
	}

}
