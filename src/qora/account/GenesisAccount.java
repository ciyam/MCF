package qora.account;

import com.google.common.primitives.Bytes;

public final class GenesisAccount extends PublicKeyAccount {

	public GenesisAccount() {
		super(Bytes.ensureCapacity(new byte[] { 1, 1, 1, 1, 1, 1, 1, 1 }, 32, 0));
	}

}
