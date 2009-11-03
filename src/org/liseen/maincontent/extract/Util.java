package org.liseen.maincontent.extract;

public class Util {
	
	public static boolean enableDebug = false;
	public static void logErr(String msg) {
		if (enableDebug)
			System.err.println(msg);
	}
	
	public static void debug(String msg) {
		if (enableDebug)
			System.err.println(msg);
	}
}
