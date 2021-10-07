package com.uga.bnserver;

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

public class BSConsistentHashingThread implements Runnable {

	@Override
	public void run() {
		
		/** Duties of this thread-
		 * 1. This thread will execute a while loop where each while iteration waits for socket incomming connection
		 * 2. 
		 */
		
		Thread.currentThread().setName("BS-Consistent-Hashing-Thread");
		while (true) {
			try (ServerSocket listener = new ServerSocket(Integer.parseInt(BSUserInteractionMain.self.getPortNumber()))) {
				/*
				 * Listen for a socket connection from either of the name servers
				 */
				Socket chSocket = listener.accept();
				
				OutputStream out = chSocket.getOutputStream();
	            InputStream in = chSocket.getInputStream();
	            PrintWriter chSocketOutPw = new PrintWriter(out,true);
	            Scanner chSocketInSc = new Scanner(in);
	            String line = chSocketInSc.nextLine();
	            
				switch (line) {
				case AppConstants.ENTER:
					//Receive the ID of the new coming node (name server)
					String newNodeID = chSocketInSc.nextLine();
					//capture the IP address of the new coming node from the socket
					String newNodeIP = chSocketInSc.nextLine();
					//receive the port number of the new coming node 
					String newNodePort = chSocketInSc.nextLine();
					
					if(BSUserInteractionMain.dataChunk.containsKey(Integer.parseInt(newNodeID))) {
						//The new node is the self responsibility of bnserver(bootstrap node). Write self responsibility logic here#######@@@@@
						//get the submap from the entire dataChunk that needs to be sent to the newly joined node 
						TreeMap<Integer, String> dataChunk = new TreeMap<Integer, String>(BSUserInteractionMain.dataChunk);
						SortedMap<Integer, String> transferDataChunk = dataChunk.subMap((Integer) dataChunk.keySet().toArray()[1], Integer.parseInt(newNodeID)+1);
						
						//Update the bnserver's (bootstrap node's) dataChunk as some data is distributed to the new node!
						for (Integer key: transferDataChunk.keySet()) {
							if (BSUserInteractionMain.dataChunk.containsKey(key)) {
								BSUserInteractionMain.dataChunk.remove(key);
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
							
							/*----- Update the pointers of the new node accordingly -----*/
							if (BSUserInteractionMain.prev.getNodeId() != null && !BSUserInteractionMain.prev.getNodeId().equals("0")) { // If the bnserver(bootstrap node) has a predecessor(previous node) already

								// Send the bnserver's prev node & its own self details to the new node.
								Node[] nodeArr = { BSUserInteractionMain.prev, BSUserInteractionMain.self };
								List<Node> nodes = Arrays.asList(nodeArr);
								for (Node node : nodes) {
									dataChunkSocketOutPw.println(node.getNodeId());
									dataChunkSocketOutPw.println(node.getIpAddress());
									dataChunkSocketOutPw.println(node.getPortNumber());
								}

								// Open the socket to the prev node and update its sucessor pointer to point the newly entered node.
								Socket prevSocket = null;
								PrintWriter prevSocketOutPw = null;
								Scanner prevSocketInSc = null;
								try {
									prevSocket = new Socket(BSUserInteractionMain.prev.getIpAddress(), Integer.parseInt(BSUserInteractionMain.prev.getPortNumber()));
									prevSocketOutPw = new PrintWriter(prevSocket.getOutputStream(), true);
									prevSocketInSc = new Scanner(prevSocket.getInputStream());
									
									//Send the details of newly entered node to the prev node of bnserver (bootstrap server) so that prev node can point to its new successor
									prevSocketOutPw.println(AppConstants.PTR_UPDATE_BN);
									prevSocketOutPw.println(newNodeID);
									prevSocketOutPw.println(newNodeIP);
									prevSocketOutPw.println(newNodePort);
									
									// After receiving the done from the prev node, the bnserver will update its prev pointer to point new node
									BSUserInteractionMain.prev.setNodeId(newNodeID);
									BSUserInteractionMain.prev.setIpAddress(newNodeIP);
									BSUserInteractionMain.prev.setPortNumber(newNodePort);
									
								} catch (Exception e) {
									e.printStackTrace();
								} finally {
									prevSocketOutPw.close();
									prevSocketInSc.close();
									prevSocket.close();
								}	
								
							} else { // If the bnserver(bootstrap node) does not have any predecessor(previous node)
								//Send the bnserver's details only, but twice to the new node.
								Node[] nodeArr = {BSUserInteractionMain.self, BSUserInteractionMain.self};
								List<Node> nodes = Arrays.asList(nodeArr);
								for (Node node: nodes) {
									dataChunkSocketOutPw.println(node.getNodeId());
									dataChunkSocketOutPw.println(node.getIpAddress());
									dataChunkSocketOutPw.println(node.getPortNumber());
								}
								//update bnserver's own prev and next pointer to point the newly entered node
								BSUserInteractionMain.prev.setNodeId(newNodeID);
								BSUserInteractionMain.prev.setIpAddress(newNodeIP);
								BSUserInteractionMain.prev.setPortNumber(newNodePort);
								
								BSUserInteractionMain.next.setNodeId(newNodeID);
								BSUserInteractionMain.next.setIpAddress(newNodeIP);
								BSUserInteractionMain.next.setPortNumber(newNodePort);
							}
							dataChunkSocketInSc.nextLine();
							chSocketOutPw.println(BSUserInteractionMain.self.getNodeId());
							
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							dataChunkSocketOutPw.close();
							dataChunkSocketInSc.close();
							dataChunkSocket.close();
						}	
						
					} else {
						//The new node is the responsibility of some successor node
						//Open a new socket to bnserver's successor and send the new coming node details to it.
						Socket chExplorerSocket = null;
						PrintWriter chExplorerSocketOutPw = null;
						Scanner chExplorerSocketInSc = null;
						try {
							//Open the socket to the successor of this bnserver to explore more
							chExplorerSocket = new Socket(BSUserInteractionMain.next.getIpAddress(), Integer.parseInt(BSUserInteractionMain.next.getPortNumber()));
							chExplorerSocketOutPw = new PrintWriter(chExplorerSocket.getOutputStream(), true);
							chExplorerSocketInSc = new Scanner(chExplorerSocket.getInputStream());
							
							/*---Send the new node details to the successor of bnserver(bootstrap node)---*/
							chExplorerSocketOutPw.println(line);
							chExplorerSocketOutPw.println(newNodeID);
							chExplorerSocketOutPw.println(newNodeIP);
							chExplorerSocketOutPw.println(newNodePort);
							
							String exploreMore = chExplorerSocketInSc.nextLine();
							
							if (exploreMore.equals("Yes")) {
								// If's body is purposely kept blank because if the successor also sends the new coming node ahead in the chain of consistent hashing in that case,
								//this node's next pointer is not needed to be updated!!
							} else {
								//Set the bootstrap server next successor pointer to new node.							
								BSUserInteractionMain.next.setNodeId(chExplorerSocketInSc.nextLine());
								BSUserInteractionMain.next.setIpAddress(chExplorerSocketInSc.nextLine());
								BSUserInteractionMain.next.setPortNumber(chExplorerSocketInSc.nextLine());
							}
							
							String nodeEntryTrace = BSUserInteractionMain.self.getNodeId() +"," +chExplorerSocketInSc.nextLine();
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
							BSUserInteractionMain.dataChunk.put(Integer.parseInt((String) strEntry.getKey()), (String) strEntry.getValue());
						}
						
						SortedMap<Integer, String> sortedDataChunk = new TreeMap<Integer, String> (BSUserInteractionMain.dataChunk);
						
						Map.Entry<Integer, String> firstEntry = ((TreeMap<Integer, String>) sortedDataChunk).firstEntry();
						sortedDataChunk.remove(firstEntry.getKey());
						
						BSUserInteractionMain.dataChunk.clear();
						BSUserInteractionMain.dataChunk.putAll(sortedDataChunk);
						BSUserInteractionMain.dataChunk.put(firstEntry.getKey(), firstEntry.getValue());
						
						//Update the prev pointer of this node.
						BSUserInteractionMain.prev.setNodeId(chSocketInSc.nextLine());
						BSUserInteractionMain.prev.setIpAddress(chSocketInSc.nextLine());
						BSUserInteractionMain.prev.setPortNumber(chSocketInSc.nextLine());
						chSocketOutPw.println("done");
					} else {
						//Upate the next pointer of this node
						BSUserInteractionMain.next.setNodeId(chSocketInSc.nextLine());
						BSUserInteractionMain.next.setIpAddress(chSocketInSc.nextLine());
						BSUserInteractionMain.next.setPortNumber(chSocketInSc.nextLine());	
						chSocketOutPw.println("done");
					}
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
