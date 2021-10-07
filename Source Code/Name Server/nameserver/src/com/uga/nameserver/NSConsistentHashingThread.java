package com.uga.nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;

public class NSConsistentHashingThread implements Runnable {
	
	/** Duties of this thread-
	 * 1. This thread will execute a while loop where each while iteration waits for socket incomming connection
	 * 2. 
	 */
	
	@Override
	public void run() {
		
		Thread.currentThread().setName("NS-Consistent-Hashing-Thread");
		while (true) {
			try (ServerSocket listener = new ServerSocket(Integer.parseInt(NSUserInteractionMain.self.getPortNumber()))) {
				/*
				 * Listen for a socket connection from either of the name servers
				 */
				Socket chSocket = listener.accept();
				
				OutputStream out = chSocket.getOutputStream();
	            InputStream in = chSocket.getInputStream();
	            PrintWriter chSocketOutPw = new PrintWriter(out,true);
	            Scanner chSocketInSc = new Scanner(in);
	            //Receive the command from other servers bootstrap server/name server 
	            String line = chSocketInSc.nextLine();
	            
				switch (line) {
				case AppConstants.ENTER:
					//Receive the ID of the new coming node (name server)
					String newNodeID = chSocketInSc.nextLine();
					//capture the IP address of the new coming node from the socket
					String newNodeIP = chSocketInSc.nextLine();
					//receive the port number of the new coming node 
					String newNodePort = chSocketInSc.nextLine();
					
					//Check if new node's responsibility belongs to this name server node
					if (NSUserInteractionMain.dataChunk.containsKey(Integer.parseInt(newNodeID))) { 
						//The new node is the self responsibility of this nameServer. Write self responsibility logic here#######@@@@@
						chSocketOutPw.println("No");
						
						//get the submap from the entire dataChunk that needs to be sent to the newly joined node 
						TreeMap<Integer, String> dataChunk = new TreeMap<Integer, String>(NSUserInteractionMain.dataChunk);
						SortedMap<Integer, String> transferDataChunk = dataChunk.subMap(dataChunk.firstKey(), Integer.parseInt(newNodeID)+1);
						
						//Update the nameServer's dataChunk as some data is distributed to the new node!
						for (Integer key: transferDataChunk.keySet()) {
							if (NSUserInteractionMain.dataChunk.containsKey(key)) {
								NSUserInteractionMain.dataChunk.remove(key);
							}
						}
						
						//Serialize the transferDataChunk from MAP to JSON String to send over socket
						String transferDataChunkJson = new ObjectMapper().writeValueAsString(transferDataChunk);
						
						Socket dataChunkSocket = null;
						PrintWriter dataChunkSocketOutPw = null;
						Scanner dataChunkSocketInSc = null;
						
						try {
							/**----- Open the socket to the newly joined node in order to send chunk of data which new node needs. -----*/
							dataChunkSocket = new Socket(newNodeIP, Integer.parseInt(newNodePort));
							dataChunkSocketOutPw = new PrintWriter(dataChunkSocket.getOutputStream(), true);
							dataChunkSocketInSc = new Scanner(dataChunkSocket.getInputStream());
							
							/*----- Send the dataChunk to the newly joined node -----*/
							//Sending the command
							dataChunkSocketOutPw.println(AppConstants.ADJ_KEYSPACE);
							//Sending the dataChunk
							dataChunkSocketOutPw.println(transferDataChunkJson);
							
							// Send the nameServer's prev node & its own self details to the new node.
							Node[] nodeArr = { NSUserInteractionMain.prev, NSUserInteractionMain.self };
							List<Node> nodes = Arrays.asList(nodeArr);
							for (Node node : nodes) {
								dataChunkSocketOutPw.println(node.getNodeId());
								dataChunkSocketOutPw.println(node.getIpAddress());
								dataChunkSocketOutPw.println(node.getPortNumber());
							}
							dataChunkSocketInSc.nextLine();
							
							//Now nameserver will update its prev pointer to point newly entered node
							NSUserInteractionMain.prev.setNodeId(newNodeID);
							NSUserInteractionMain.prev.setIpAddress(newNodeIP);
							NSUserInteractionMain.prev.setPortNumber(newNodePort);
							
							//Now inform the server(bootstrap/nameserver) which contacted this nameserver for node handling responsibility to update its pointers
							chSocketOutPw.println(newNodeID);
							chSocketOutPw.println(newNodeIP);
							chSocketOutPw.println(newNodePort);
							
							//Send the node ID for back tracing of visited nodes
							chSocketOutPw.println(NSUserInteractionMain.self.getNodeId());
							
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							dataChunkSocketOutPw.close();
							dataChunkSocketInSc.close();
							dataChunkSocket.close();
						}
							
					} else {
						//The new node is the responsibility of some successor node
						chSocketOutPw.println("Yes");
						//Open a new socket to this nameserver's successor and send the new coming node details to it.
						Socket chExplorerSocket = null;
						PrintWriter chExplorerSocketOutPw = null;
						Scanner chExplorerSocketInSc = null;
						try {
							//Open the socket to the successor of this nameserver to explore more
							chExplorerSocket = new Socket(NSUserInteractionMain.next.getIpAddress(), Integer.parseInt(NSUserInteractionMain.next.getPortNumber()));
							chExplorerSocketOutPw = new PrintWriter(chExplorerSocket.getOutputStream(), true);
							chExplorerSocketInSc = new Scanner(chExplorerSocket.getInputStream());
							
							/*---Send the new node details to the successor of this nameserver---*/
							chExplorerSocketOutPw.println(line);
							chExplorerSocketOutPw.println(newNodeID);
							chExplorerSocketOutPw.println(newNodeIP);
							chExplorerSocketOutPw.println(newNodePort);
							
							String exploreMore = chExplorerSocketInSc.nextLine();
							
							if (exploreMore.equals("Yes")) {
								// If's body is purposely kept blank because if the successor also sends the new coming node ahead in the chain of consistent hashing in that case,
								//this node's next pointer is not needed to be updated!!
							} else {
								//Set the name server next successor pointer to new node.							
								NSUserInteractionMain.next.setNodeId(chExplorerSocketInSc.nextLine());
								NSUserInteractionMain.next.setIpAddress(chExplorerSocketInSc.nextLine());
								NSUserInteractionMain.next.setPortNumber(chExplorerSocketInSc.nextLine());
							}
							
							String nodeEntryTrace = NSUserInteractionMain.self.getNodeId() +"," +chExplorerSocketInSc.nextLine();
							chSocketOutPw.println(nodeEntryTrace);
							
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							chExplorerSocketOutPw.close();
							chExplorerSocketInSc.close();
							chExplorerSocket.close();
						}	
					}	
					break;

				case AppConstants.EXIT:
					//Receive the mode of exit process
					String exitMode = chSocketInSc.nextLine();
					if (exitMode.equals("data+pointers")) {
						//Then we have to adjust the surrendered dataChunk from previous exiting node to this node's dataChunk
						String transferDataChunkJson = chSocketInSc.nextLine();
						
						// Set the received/surrendered dataChunk to this Node
						Map<?, ?> strMap = new ObjectMapper().readValue(transferDataChunkJson, LinkedHashMap.class);	
						for (Map.Entry<?, ?> strEntry : strMap.entrySet()) {
							NSUserInteractionMain.dataChunk.put(Integer.parseInt((String) strEntry.getKey()), (String) strEntry.getValue());
						}
						
						SortedMap<Integer, String> sortedDataChunk = new TreeMap<Integer, String> (NSUserInteractionMain.dataChunk);
						NSUserInteractionMain.dataChunk.clear();
						NSUserInteractionMain.dataChunk.putAll(sortedDataChunk);
						
						//Update the prev pointer of this node.
						NSUserInteractionMain.prev.setNodeId(chSocketInSc.nextLine());
						NSUserInteractionMain.prev.setIpAddress(chSocketInSc.nextLine());
						NSUserInteractionMain.prev.setPortNumber(chSocketInSc.nextLine());
						chSocketOutPw.println("done");
						
					} else {
						//Upate the next pointer of this node
						NSUserInteractionMain.next.setNodeId(chSocketInSc.nextLine());
						NSUserInteractionMain.next.setIpAddress(chSocketInSc.nextLine());
						NSUserInteractionMain.next.setPortNumber(chSocketInSc.nextLine());
						chSocketOutPw.println("done");
					}
					break;
					
				case AppConstants.INSERT:
					String insertKey = chSocketInSc.nextLine();
					String insertValue = chSocketInSc.nextLine();
					if (NSUserInteractionMain.dataChunk.containsKey(Integer.parseInt(insertKey))) {
						//The key for record insert is available with me(nameserver) only, hence update it and respond back to the prev node via already open socket
						NSUserInteractionMain.dataChunk.put(Integer.parseInt(insertKey), insertValue);
						
						//Respond back to the prev node
						chSocketOutPw.println(NSUserInteractionMain.dataChunk.get(Integer.parseInt(insertKey)));
						chSocketOutPw.println(NSUserInteractionMain.self.getNodeId());
					} else {
						//Record for lookup is not with this nameserver, hence delegate to further nodes for lookup
						//Open socket to next name server and pass the lookup responsibility
						Socket chExplorerSocket = null;
						PrintWriter chExplorerSocketOutPw = null;
						Scanner chExplorerSocketInSc = null;
						try {
							chExplorerSocket = new Socket(NSUserInteractionMain.next.getIpAddress(), Integer.parseInt(NSUserInteractionMain.next.getPortNumber()));
							chExplorerSocketOutPw = new PrintWriter(chExplorerSocket.getOutputStream(), true);
							chExplorerSocketInSc = new Scanner(chExplorerSocket.getInputStream());
							
							//Send the command to next name server in the chain
							chExplorerSocketOutPw.println(line);
							
							//Send the insert key and value to the next nameserver
							chExplorerSocketOutPw.println(insertKey);
							chExplorerSocketOutPw.println(insertValue);
							
							//Return the response from the next node after exploring is done
							chSocketOutPw.println(chExplorerSocketInSc.nextLine());
							chSocketOutPw.println(NSUserInteractionMain.self.getNodeId()+","+chExplorerSocketInSc.nextLine());
							
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							chExplorerSocketInSc.close();
							chExplorerSocketOutPw.close();
							chExplorerSocket.close();
						}
					}
					break;
					
				case AppConstants.LOOKUP:
					String lookupKey = chSocketInSc.nextLine();
					if (NSUserInteractionMain.dataChunk.containsKey(Integer.parseInt(lookupKey))) {
						//The record for lookup is available with me(nameserver) only, hence fetch it and return back to the prev node via already open socket
						chSocketOutPw.println(NSUserInteractionMain.dataChunk.get(Integer.parseInt(lookupKey)));
						chSocketOutPw.println(NSUserInteractionMain.self.getNodeId());
					} else {
						//Record for lookup is not with this nameserver, hence delegate to further nodes for lookup
						//Open socket to next name server and pass the lookup responsibility
						Socket chExplorerSocket = null;
						PrintWriter chExplorerSocketOutPw = null;
						Scanner chExplorerSocketInSc = null;
						try {
							chExplorerSocket = new Socket(NSUserInteractionMain.next.getIpAddress(), Integer.parseInt(NSUserInteractionMain.next.getPortNumber()));
							chExplorerSocketOutPw = new PrintWriter(chExplorerSocket.getOutputStream(), true);
							chExplorerSocketInSc = new Scanner(chExplorerSocket.getInputStream());
							
							//Send the command to next name server in the chain
							chExplorerSocketOutPw.println(line);
							
							//Send the lookup key to the next nameserver
							chExplorerSocketOutPw.println(lookupKey);
							
							//Return the response from the next node after exploring is done
							chSocketOutPw.println(chExplorerSocketInSc.nextLine());
							chSocketOutPw.println(NSUserInteractionMain.self.getNodeId()+","+chExplorerSocketInSc.nextLine());
							
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							chExplorerSocketInSc.close();
							chExplorerSocketOutPw.close();
							chExplorerSocket.close();
						}
					}
					break;
					
				case AppConstants.DELETE:
					String deleteKey = chSocketInSc.nextLine();
					
					if (NSUserInteractionMain.dataChunk.containsKey(Integer.parseInt(deleteKey))) {
						//The key for record insert is available with me(nameserver) only, hence update it and respond back to the prev node via already open socket
						NSUserInteractionMain.dataChunk.put(Integer.parseInt(deleteKey), null);
						
						//Respond back to the prev node
						chSocketOutPw.println(NSUserInteractionMain.dataChunk.get(Integer.parseInt(deleteKey)));
						chSocketOutPw.println(NSUserInteractionMain.self.getNodeId());
					} else {
						//Record for lookup is not with this nameserver, hence delegate to further nodes for lookup
						//Open socket to next name server and pass the lookup responsibility
						Socket chExplorerSocket = null;
						PrintWriter chExplorerSocketOutPw = null;
						Scanner chExplorerSocketInSc = null;
						try {
							chExplorerSocket = new Socket(NSUserInteractionMain.next.getIpAddress(), Integer.parseInt(NSUserInteractionMain.next.getPortNumber()));
							chExplorerSocketOutPw = new PrintWriter(chExplorerSocket.getOutputStream(), true);
							chExplorerSocketInSc = new Scanner(chExplorerSocket.getInputStream());
							
							//Send the command to next name server in the chain
							chExplorerSocketOutPw.println(line);
							
							//Send the insert key and value to the next nameserver
							chExplorerSocketOutPw.println(deleteKey);
							
							//Return the response from the next node after exploring is done
							chSocketOutPw.println(chExplorerSocketInSc.nextLine());
							chSocketOutPw.println(NSUserInteractionMain.self.getNodeId()+","+chExplorerSocketInSc.nextLine());
							
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							chExplorerSocketInSc.close();
							chExplorerSocketOutPw.close();
							chExplorerSocket.close();
						}
					}
					break;
					
				case AppConstants.ADJ_KEYSPACE:
					// Receive the dataChunk that belongs to this newly entered node
					String transferDataChunkJson = chSocketInSc.nextLine();
					
					// Set the dataChunk to this Node
					Map<?, ?> strMap = new ObjectMapper().readValue(transferDataChunkJson, LinkedHashMap.class);	
					for (Map.Entry<?, ?> strEntry : strMap.entrySet()) {
						NSUserInteractionMain.dataChunk.put(Integer.parseInt((String) strEntry.getKey()), (String) strEntry.getValue());
					}

					// Start pointing to the next and prev nodes as informed
					NSUserInteractionMain.prev.setNodeId(chSocketInSc.nextLine());
					NSUserInteractionMain.prev.setIpAddress(chSocketInSc.nextLine());
					NSUserInteractionMain.prev.setPortNumber(chSocketInSc.nextLine());
					
					NSUserInteractionMain.next.setNodeId(chSocketInSc.nextLine());
					NSUserInteractionMain.next.setIpAddress(chSocketInSc.nextLine());
					NSUserInteractionMain.next.setPortNumber(chSocketInSc.nextLine());
					chSocketOutPw.println("ok");
					break;
					
				case AppConstants.PTR_UPDATE_BN:
					//Set the details of the new node as the successor to this node.
					//Receive the ID of the new coming node (name server)
					String newNxtNodeID = chSocketInSc.nextLine();
					//capture the IP address of the new coming node from the socket
					String newNxtNodeIP = chSocketInSc.nextLine();
					//receive the port number of the new coming node 
					String newNxtNodePort = chSocketInSc.nextLine();
					
					//Update the next(successor) pointer of this name server to point the newly added node
					NSUserInteractionMain.next.setNodeId(newNxtNodeID);
					NSUserInteractionMain.next.setIpAddress(newNxtNodeIP);
					NSUserInteractionMain.next.setPortNumber(newNxtNodePort);
					
					break;

				default:
					break;
				}

				chSocketInSc.close();
				chSocketOutPw.close();
				chSocket.close();  
	            
			} catch (IOException e) {		
				e.printStackTrace();
			}
		
		}
		
	}

}
