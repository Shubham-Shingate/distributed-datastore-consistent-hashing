package com.uga.nameserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.Scanner;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NSUserInteractionMain {
	
	public static Node prev;
	
	public static Node self;
	
	public static Node next;
	
	public static Node bootstrapNode;
	
	public static LinkedHashMap<Integer, String> dataChunk;
	
	public static Properties appProperties;

	static {
		prev = new Node();
		self = new Node();
		next = new Node();
		bootstrapNode = new Node();
		dataChunk = new LinkedHashMap<Integer, String>();
		appProperties = new Properties();
	}
	
	public static void main(String[] args) throws IOException {
	
		//On startup read the config
		if (args.length != 1) {
			System.err.println("Pass the config file name for nameserver execution as the command line argument only");
			return;
		}
		
		/** --------Load the properties file into the application-------- */
		//File propertyFile = new File("ns-config.txt");
		File propertyFile = new File(args[0]);
		if (!propertyFile.exists()) {
			System.err.println("The property file with the given name does not exists");
			return;
		}
		FileInputStream fis = new FileInputStream(propertyFile);
		try {
			appProperties.load(fis);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		//Set the self node details to the above Node objects
		self.setNodeId(appProperties.getProperty("nameserver.id"));
		self.setIpAddress(InetAddress.getLocalHost().getHostAddress());
		self.setPortNumber(appProperties.getProperty("nameserver.port"));
		
		//Set the connection details of the bnserver/bootstrap server
		bootstrapNode.setIpAddress(appProperties.getProperty("bnserver.ip"));
		bootstrapNode.setPortNumber(appProperties.getProperty("bnserver.port"));
		
		//Start the ConsistentHashingThread
		NSConsistentHashingThread chThread = new NSConsistentHashingThread();
		Thread t = new Thread(chThread);
		t.start();    
        
		Scanner userInSc = null;
		try {
			//Define line & scanner to take user commands
			String line = "";
			userInSc = new Scanner(System.in);
			while (true) { // Wait for the user command
				System.out.print(AppConstants.NAME_SERVER);
				line = userInSc.nextLine(); // what we entered
				System.out.println("You entered " + line);

				switch (line) {
				case AppConstants.ENTER:
					Socket bnSocket = null;
					PrintWriter bnSocketOutPw = null;
					Scanner bnSocketInSc = null;
					try {
						bnSocket = new Socket(bootstrapNode.getIpAddress(),
								Integer.parseInt(bootstrapNode.getPortNumber()));
						bnSocketOutPw = new PrintWriter(bnSocket.getOutputStream(), true);
						bnSocketInSc = new Scanner(bnSocket.getInputStream());

						//Send the command to bnserver
						bnSocketOutPw.println(line);

						//Send this new coming node's details to the bnserver
						bnSocketOutPw.println(self.getNodeId());
						bnSocketOutPw.println(self.getIpAddress());
						bnSocketOutPw.println(self.getPortNumber());

						//Get the tracing of the ID's of the server's that were traversed while installing this node.
						String[] trace = bnSocketInSc.nextLine().split(",");

						//Print the bootstrap server response
						System.out.println("Key Space: " + NSUserInteractionMain.dataChunk.keySet().toArray()[0] + "-"
								+ NSUserInteractionMain.dataChunk.keySet().toArray()[NSUserInteractionMain.dataChunk.size()
										- 1]);
						System.out.println("Predecessor: " + prev.getNodeId());
						System.out.println("Successor: " + next.getNodeId());
						System.out.println("Node Trace: " + Arrays.asList(trace));
						System.out.println("Succesful Entry");

					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						bnSocketInSc.close();
						bnSocketOutPw.close();
						bnSocket.close();
					}
					break;

				case AppConstants.EXIT:
					//Begin the gracefull exit procedure by connecting successor and surrender this node's dataChunk to successor
					Socket exitSocket = null;
					PrintWriter exitSocketOutPw = null;
					Scanner exitSocketInSc = null;
					String exitResponse1 = null;
					String exitResponse2 = null;
					//Connect to successor and send dataChunk+pointers
					try {
						exitSocket = new Socket(next.getIpAddress(), Integer.parseInt(next.getPortNumber()));
						exitSocketOutPw = new PrintWriter(exitSocket.getOutputStream(), true);
						exitSocketInSc = new Scanner(exitSocket.getInputStream());

						// Send the command to successor node
						exitSocketOutPw.println(line);
						//Send the behaviour of successor's exit process
						exitSocketOutPw.println("data+pointers");

						//Send (surrender) the dataChunk to the successor node
						String transferDataChunkJson = new ObjectMapper().writeValueAsString(dataChunk);
						exitSocketOutPw.println(transferDataChunkJson);

						//Send the pointers of this node's prev node to the successor
						exitSocketOutPw.println(prev.getNodeId());
						exitSocketOutPw.println(prev.getIpAddress());
						exitSocketOutPw.println(prev.getPortNumber());

						exitResponse1 = exitSocketInSc.nextLine();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						exitSocketInSc.close();
						exitSocketOutPw.close();
						exitSocket.close();
					}

					//Connect to predecessor and send pointers
					try {
						exitSocket = new Socket(prev.getIpAddress(), Integer.parseInt(prev.getPortNumber()));
						exitSocketOutPw = new PrintWriter(exitSocket.getOutputStream(), true);
						exitSocketInSc = new Scanner(exitSocket.getInputStream());

						// Send the command to successor node
						exitSocketOutPw.println(line);
						//Send the behaviour of predecesspor's exit process
						exitSocketOutPw.println("pointers");

						//Send the pointers of this node's next node to the predecessor
						exitSocketOutPw.println(next.getNodeId());
						exitSocketOutPw.println(next.getIpAddress());
						exitSocketOutPw.println(next.getPortNumber());

						exitResponse2 = exitSocketInSc.nextLine();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						exitSocketInSc.close();
						exitSocketOutPw.close();
						exitSocket.close();
					}

					//Now refresh the pointers and dataChunk of this exiting node
					prev.setNodeId(null);
					prev.setIpAddress(null);
					prev.setPortNumber(null);

					next.setNodeId(null);
					next.setIpAddress(null);
					next.setPortNumber(null);

					dataChunk.clear();

					if (exitResponse1.equals("done") && exitResponse2.equals("done")) {
						System.out.println("Exit sucessful");
					}
					break;

				default:
					System.out.println("Invalid Command.....");
					break;

				}
			} 
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			userInSc.close();
		}

	}
	
}
