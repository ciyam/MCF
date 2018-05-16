package qora.account;

public final class GenesisAccount extends PublicKeyAccount {

	public GenesisAccount() {
		super(new byte[] { 1, 1, 1, 1, 1, 1, 1, 1 });
	}

}
