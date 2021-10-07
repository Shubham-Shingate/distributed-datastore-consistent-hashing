package com.uga.nameserver;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppUtil {
	
	// To get the response from the server
	public static String getPrintWriterResponse(String line, Scanner socketInSc) {
        while (true) {
            line = socketInSc.nextLine();
            if (line.equals("") || line.equals("goodbye")) {
                return line;
            }
            System.out.println("Coordinator response: " + line);
        }
    }
	
	public static String getPrintWriterResponseRegistration(String line, Scanner socketInSc) {
		String coordinatorRes = null;
		while (true) {
            line = socketInSc.nextLine();
            if (line.equals("") || line.equals("goodbye")) {
                return coordinatorRes;
            }
            coordinatorRes = line;
            System.out.println("Coordinator response: " + line);
        }
    }
	
	public static boolean checkPattern(String inputPattern, String data) {
		Pattern pattern = Pattern.compile(inputPattern);		
		Matcher matcher = pattern.matcher(data);
		return matcher.matches();
	}
}
