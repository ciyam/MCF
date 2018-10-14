package globalization;

import java.nio.file.Paths;

public class ContextPaths {
	
	public static boolean isValidKey(String value) {
		return !value.contains("/") && !ContextPaths.containsParentReference(value);			
	}

	public static boolean containsParentReference(String value) {
		for(String part : value.split("/")) {
			if(part.equalsIgnoreCase(".."))
				return true;
		}
		return false;
	}
	
	public static String combinePaths(String left, String right) {
		left = (left != null) ? left : "";
		right = (right != null) ? right : "";
		return Paths.get("/", left, right).normalize().toString();
	}
	
	public static String getParent(String path) {
		return combinePaths(path, "..");
	}
	
	public static boolean isRoot(String path) {
		return path.equals("/");
	}

}
