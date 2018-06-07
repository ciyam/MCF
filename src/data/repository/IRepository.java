package data.repository;

import data.block.IBlockData;

public interface IRepository {
	// XXX use NoDataFoundException?
	IBlockData getBlockBySignature(byte[] signature) throws Exception;
	IBlockData getBlockByHeight(int height) throws Exception;
}
