# chat-service

Scalable chat app

Design a horizontal scalable chat system using Java, Spring, Maven, Redis, NoSQL DB and RabbitMQ
Chat system is a web app that should support both 1-1 as well as group chat.
Group chat has a total people limit of 100.
Online indicator support must be present and it should support only text messages.
Chat history must be stored forever
System should
  1. Receive messages from clients
  2. Find the right recipients for each message and relay the message to the recipients.
  3. If the recipient is not available, hold the message for the recipient on the server until he/she is online.
Clients use HTTP protocol for sending messages to the chat server.
Servers should use websocket connection to communicate with the client.
Chat servers should be stateless
Client initially sends WebSocket connection and it starts as a HTTP connection and can be upgraded to a WebSocket connection.
Signup, login, user profile could be minimal using only username, email and password. And they can use the traditional request/response method.
Chat system is broken into three categories: Stateless service, Stateful service and third-party integration.
Stateless services are Service discovery, Authentication service, Group management and user profile.
Stateful service is Chat service
Third party service is push notification which can be stubbed for Android FCM and iOS APN
Service discovery service gives the client a list of DNS hostnames of the chat servers that the client could connect to.
Chat servers facilitate message sending and receiving
Presence servers manage online/offline status
API servers handle things like login, user profile, registration etc
Notification servers send push notifications
Key-Value store is used to store chat history. When an offline user comes online. She will see all her previous chat history.
User profile, settings and user friend list, etc., are stored in PostgreSQL.
Key-value NoSQL store is used for chat history. Cassandra or HBase can be used whichever is simpler to integrate with spring MVC
Chat table for 1-1 can contain fields like message_id, message_from, message_to, content and created_at.
Group chat table consist of fields like channel_id, message_id, user_id, content and created_at. Composite primary key can be (channel_id, message_id)
Use a local sequence number generator for message_id since IDs are only unique within a group.
Service discovery can be done using a Zookeeper. Use minimal configuration so I can understand the basic concept.
1-1 chat flow
1. User A sends a chat message to chat server 1
2. chat server 1 obtains a message id from the ID generator
3. chat server 1 sent the message to message sync queue
4. Message is stored in key-value store
5. If user B is online message is forwarded to chat server 2 where user B is connected
6. If user b is offline a push notification is sent from PN servers
7. chat server 2 forwards the message to user B. There is a persistent websocket connection between user B and chat server 2.
 
