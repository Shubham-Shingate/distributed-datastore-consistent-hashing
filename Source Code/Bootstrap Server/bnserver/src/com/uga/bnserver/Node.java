package com.uga.bnserver;

public class Node {
	
	private String nodeId;
	
	private String ipAddress;

	private String portNumber;

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getPortNumber() {
		return portNumber;
	}

	public void setPortNumber(String portNumber) {
		this.portNumber = portNumber;
	}

	public Node(String nodeId, String ipAddress, String portNumber) {
		this.nodeId = nodeId;
		this.ipAddress = ipAddress;
		this.portNumber = portNumber;
	}

	public Node() {
		
	}

	@Override
	public String toString() {
		return "Node [nodeId=" + nodeId + ", ipAddress=" + ipAddress + ", portNumber=" + portNumber + "]";
	}

}
