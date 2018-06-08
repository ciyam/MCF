package repository;

import data.block.BlockData;

public interface BlockRepository {
	BlockData fromSignature(byte[] signature) throws DataException;
	BlockData fromHeight(int height) throws DataException;
}
