package com.uga.bnserver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppUtil {
	
	public static boolean checkPattern(String inputPattern, String data) {
		Pattern pattern = Pattern.compile(inputPattern);		
		Matcher matcher = pattern.matcher(data);
		return matcher.matches();
	}
}
