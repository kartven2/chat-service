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

