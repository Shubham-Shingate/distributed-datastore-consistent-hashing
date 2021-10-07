# Consistent Hashing-based Naming Service

> Distributed Computing Systems (Spring 2021 CSCI 6780) - Project 4

> Team Members: Shubham Shingate, Vipul Shinde

> Technologies Used: Java 1.8

> Problem Statement: To implement a Consistent Hashing-based Naming Service


## Consistent Hashing-based Naming Service Model 
In this project, we have implemented a system for consistent hashing (CH)-based flat naming system. Consistent Hashing provides a lookup service for key-value pairs. The CH system stores (key, value) pairs on a distributed set of servers.
These servers collaboratively provide lookup service along with insertion and deletion. In this project, we have implemented the CH name servers and a boot-strap CH server, which is a special CH server with certain extra functionalities.

![Architecture](Persistent_and_Asynchronous_Multicast_System_Architecture.png)

## Bootstrap Name Server:
As mentioned above the bootstrap name server is a permanent name server. Its startup signals
the creation of the CH system and its close-down brings the entire system down. It is assumed
that the Bootstrap name server is the first node in the system. In other words, at startup it is
responsible for the entire [0, 1023] range.

The Bootstrap name server (bnserver) takes a two command line parameter – the name of
the bootstrap name server configuration file (bs-config.txt) and the name of sample data file (data.txt). 

Upon startup the Bootstrap name server will manage the entire [0, 1023] range until other
servers join the system. The Bootstrap name server will also provide a user interaction thread.
The user interaction thread will support the following commands.

- ### Lookup key (lookup [key]): 
  Retrieves the value corresponding to the given key (if the key is in the system). If the given key is not in the system, “Key not found” should be printed.
  In addition to the value, this commands should also printout the sequence of server IDs that were contacted
  and the ID of the server from which the final response was obtained.
  
- ### Insert key value (insert [key] [value]): 
  Should insert the key value pair into the system. The command should print out the ID of the server into which the key value pair was inserted and the sequence of server IDS that were contacted.
  
- ### Delete (delete [key]):
  Participant indicates to the coordinator that it is temporarily going offline. The coordinator will have to send it messages sent during disconnection (subject to temporal constraint). Thread-B will relinquish the port and may become dormant or die. 

- ### Reconnect (reconnect [portnumber]):
  Participant indicates to the coordinator that it is online and it will specify the IP address and port number where its thread-B will receive multicast messages (thread-B has to be operational before sending the message to the coordinator). 

- ### Multicast Send (msend [message]):
  Multicast [message] to all current members. Note that [message] is an alpha-numeric string (e.g., UGACSRocks). The participant sends the message to the coordinator and unblocks after an acknowledgement is received. 

## Coordinator Details 
As mentioned before, the coordinator manages the multicast group, handles all communication, and stores messages for persistence. For simplicity, it is assumed that there is only one multicast group and all registered participants belong to the same group. The coordinator program will have one command-line parameter, namely, the configuration file name. The configuration file will have the following format. The first line indicates the port number where the coordinator will wait for incoming messages from participants. The second line indicates the persistence time threshold (td) in seconds. When the coordinator starts up, the multicast group is assumed to be empty. Participants join the group through register messages. When the coordinator gets a message from a participant, it first parses the message, acknowledges the receipt of the message, closes the connection, and then performs the requested action. For example, if it receives a “register” message, it will add the participant to the member list. If it receives a multicast message, it will send the message to the members that are currently online. If any members are offline, it will store the message for providing persistence. 


## Execution Instructions-

1. The "participant.jar" executable creates a message-log.txt file adjacent to it to show all the multicast group communication.
2. Make sure that participant.jar and its config file stay together in same folder.
3. Make sure that coordinator.jar and its config file stay together in same folder.
4. Please do not change config file's format (i.e key=value pairs). However, the sequence of config properties can be interchanged.
   For reference you can check the config files provided in submission zip folder.
5. Command for coordinator execution
     java -jar coordinator.jar <Config File Name>

   Command for participant execution
     java -jar participant.jar <Config File Name>

