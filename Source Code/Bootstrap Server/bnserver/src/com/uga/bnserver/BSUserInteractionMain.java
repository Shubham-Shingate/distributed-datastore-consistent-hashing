package com.uga.bnserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class BSUserInteractionMain {
	
	public static Node prev;
	
	public static Node self;
	
	public static Node next;

	public static LinkedHashMap<Integer, String> dataChunk;
	
	public static Properties appProperties;
	
	public static Properties appData;
	
	static {
		prev = new Node();
		self = new Node();
		next = new Node();
		dataChunk = new LinkedHashMap<Integer, String>();
		appProperties = new Properties();
		appData = new Properties();
	}
	
	public static void main(String[] args) throws Exception {
	
		//On startup read both the config files
		if (args.length != 2) {
			System.err.println("Pass the config file name and data file name for bnserver execution as the command line argument only");
			return;
		}
		
		/** --------Load the properties file and data file into the application-------- */
		//File propertyFile = new File("bs-config.txt");
		//File dataFile = new File("data.txt");
		File propertyFile = new File(args[0]);
		File dataFile = new File(args[1]);
		if (!propertyFile.exists()) {
			System.err.println("The property file with the given name does not exists");
			return;
		}
		if (!dataFile.exists()) {
			System.err.println("The data file with the given name does not exists");
			return;
		}
		
		FileInputStream fis = new FileInputStream(propertyFile);
		FileInputStream fis2 = new FileInputStream(dataFile);
		try {
			appProperties.load(fis);
			appData.load(fis2);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				fis.close();
				fis2.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		// Set the self node details to the above Node objects
		self.setNodeId(appProperties.getProperty("bnserver.id"));
		self.setIpAddress(InetAddress.getLocalHost().getHostAddress());
		self.setPortNumber(appProperties.getProperty("bnserver.port"));
		
		//Initialize the keyspace(dataChunk) of bnserver
		for (int i = 1; i < 1024; i++) {
			dataChunk.put(i, null);
		}
		dataChunk.put(0, null);
		
		//Update the null initialized dataChunk with the data read from the datafile.txt
		Map<Object, Object> dataMap = new LinkedHashMap<Object, Object>(appData);
		for (Map.Entry<Object, Object> dataEntry : dataMap.entrySet()) {
			dataChunk.put(Integer.parseInt((String) dataEntry.getKey()), (String) dataEntry.getValue());
		}
		
		// Start the ConsistentHashingThread
		BSConsistentHashingThread chThread = new BSConsistentHashingThread();
		Thread t = new Thread(chThread);
		t.start();
		
		Scanner userInSc = null;
		try {
			//Define line & scanner to take user commands
			String line = "";
			userInSc = new Scanner(System.in);
			
			while (true) { // Wait for the user command
				System.out.print(AppConstants.BOOTSTRAP_SERVER);
				
				line = userInSc.nextLine(); // what we entered
				System.out.println("You entered " + line);
				String[] commandArr = line.split(" ", 3);

				switch (commandArr[0]) {
				
				case AppConstants.LOOKUP:
					if (AppUtil.checkPattern("^lookup [0-9]{1,4}[:.,-]?$", line)) {
						if ((Integer.parseInt(commandArr[1]) >= 0) && (Integer.parseInt(commandArr[1]) <= 1023)) {
							String lookupKey = commandArr[1];
							if (dataChunk.containsKey(Integer.parseInt(lookupKey))) {
								//The record for lookup is available with me(bnserver) only, hence fetch it and return back to the user
								System.out.println(lookupKey + " : " + dataChunk.get(Integer.parseInt(lookupKey)));
								System.out.println("Node Trace: " + Arrays.asList(self.getNodeId().split(",")));
							} else {
								//Record for lookup is not with bnserver, hence delegate to further nodes for lookup
								//Open socket to next name server and pass the lookup responsibility
								Socket chExplorerSocket = null;
								PrintWriter chExplorerSocketOutPw = null;
								Scanner chExplorerSocketInSc = null;
								try {
									chExplorerSocket = new Socket(next.getIpAddress(),
											Integer.parseInt(next.getPortNumber()));
									chExplorerSocketOutPw = new PrintWriter(chExplorerSocket.getOutputStream(), true);
									chExplorerSocketInSc = new Scanner(chExplorerSocket.getInputStream());

									//Send the command to next name server in the chain
									chExplorerSocketOutPw.println(commandArr[0]);

									//Send the lookup key to the next nameserver
									chExplorerSocketOutPw.println(lookupKey);

									//Print the response from the next node after exploring is done
									System.out.println(lookupKey + " : " + chExplorerSocketInSc.nextLine());
									System.out.println("Node Trace: " + Arrays.asList(
											(self.getNodeId() + "," + chExplorerSocketInSc.nextLine()).split(",")));

								} catch (Exception e) {
									e.printStackTrace();
								} finally {
									chExplorerSocketInSc.close();
									chExplorerSocketOutPw.close();
									chExplorerSocket.close();
								}
							}
						} else {
							System.out.println("Key not found");
						} 
					} else {
						System.out.println("Invalid Command....");
					}
					break;
					
				case AppConstants.INSERT:
					if (AppUtil.checkPattern("^insert [0-9]{1,4}[:.,-]? [a-zA-Z0-9_]*$", line)) {
						if ((Integer.parseInt(commandArr[1]) >= 0) && (Integer.parseInt(commandArr[1]) <= 1023)) {
							String insertKey = commandArr[1];
							String insertValue = commandArr[2];
							if (dataChunk.containsKey(Integer.parseInt(insertKey))) {
								//The key for insert is available with me(bnserver) only, hence update it and return back to the user
								dataChunk.put(Integer.parseInt(insertKey), insertValue);
								System.out.println(insertKey + " : " + dataChunk.get(Integer.parseInt(insertKey)));
								System.out.println("Node Trace: " + Arrays.asList(self.getNodeId().split(",")));
							} else {
								//Record for lookup is not with bnserver, hence delegate to further nodes for lookup
								//Open socket to next name server and pass the lookup responsibility
								Socket chExplorerSocket = null;
								PrintWriter chExplorerSocketOutPw = null;
								Scanner chExplorerSocketInSc = null;
								try {
									chExplorerSocket = new Socket(next.getIpAddress(),
											Integer.parseInt(next.getPortNumber()));
									chExplorerSocketOutPw = new PrintWriter(chExplorerSocket.getOutputStream(), true);
									chExplorerSocketInSc = new Scanner(chExplorerSocket.getInputStream());

									//Send the command to next name server in the chain
									chExplorerSocketOutPw.println(commandArr[0]);

									//Send the insert key to the next nameserver
									chExplorerSocketOutPw.println(insertKey);
									chExplorerSocketOutPw.println(insertValue);

									//Print the response from the next node after exploring is done
									System.out.println(insertKey + " : " + chExplorerSocketInSc.nextLine());
									System.out.println("Node Trace: " + Arrays.asList(
											(self.getNodeId() + "," + chExplorerSocketInSc.nextLine()).split(",")));

								} catch (Exception e) {
									e.printStackTrace();
								} finally {
									chExplorerSocketInSc.close();
									chExplorerSocketOutPw.close();
									chExplorerSocket.close();
								}
							}
						} else {
							System.out.println("Key not found");
						} 
					} else {
						System.out.println("Invalid Command....");
					}
					break;
				
				case AppConstants.DELETE:
					if (AppUtil.checkPattern("^delete [0-9]{1,4}[:.,-]?$", line)) {
						if ((Integer.parseInt(commandArr[1]) >= 0) && (Integer.parseInt(commandArr[1]) <= 1023)) {
							String deleteKey = commandArr[1];
							if (dataChunk.containsKey(Integer.parseInt(deleteKey))) {
								//The key for insert is available with me(bnserver) only, hence update it and return back to the user
								dataChunk.put(Integer.parseInt(deleteKey), null);
								System.out.println(deleteKey + " : " + dataChunk.get(Integer.parseInt(deleteKey)));
								System.out.println("Node Trace: " + Arrays.asList(self.getNodeId().split(",")));
							} else {
								//Record for lookup is not with bnserver, hence delegate to further nodes for lookup
								//Open socket to next name server and pass the lookup responsibility
								Socket chExplorerSocket = null;
								PrintWriter chExplorerSocketOutPw = null;
								Scanner chExplorerSocketInSc = null;
								try {
									chExplorerSocket = new Socket(next.getIpAddress(),
											Integer.parseInt(next.getPortNumber()));
									chExplorerSocketOutPw = new PrintWriter(chExplorerSocket.getOutputStream(), true);
									chExplorerSocketInSc = new Scanner(chExplorerSocket.getInputStream());

									//Send the command to next name server in the chain
									chExplorerSocketOutPw.println(commandArr[0]);

									//Send the insert key to the next nameserver
									chExplorerSocketOutPw.println(deleteKey);

									//Print the response from the next node after exploring is done
									System.out.println(deleteKey + " : " + chExplorerSocketInSc.nextLine());
									System.out.println("Node Trace: " + Arrays.asList(
											(self.getNodeId() + "," + chExplorerSocketInSc.nextLine()).split(",")));

								} catch (Exception e) {
									e.printStackTrace();
								} finally {
									chExplorerSocketInSc.close();
									chExplorerSocketOutPw.close();
									chExplorerSocket.close();
								}
							}
						} else {
							System.out.println("Key not found");
						} 
					} else {
						System.out.println("Invalid Command....");
					}
					break;

				default:
					System.out.println("Invalid Command.....");
					break;
				
				}
				
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} finally {
			userInSc.close();
		}
	}
	
}
