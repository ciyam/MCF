package globalization;

import java.nio.file.Paths;

public class ContextPaths {
	
	public static boolean isValidKey(String value) {
		return !value.contains("/");			
	}

	public static String combinePaths(String left, String right) {
		return Paths.get("/", left, right).normalize().toString();
	}
	
	public static String getParent(String path) {
		return combinePaths(path, "..");
	}
	
	public static boolean isRoot(String path) {
		return path.equals("/");
	}

}
