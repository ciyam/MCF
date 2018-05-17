package settings;

public class Settings {

	public static Settings getInstance() {
		return new Settings();
	}

	public int getMaxBytePerFee() {
		return 1024;
	}

}
