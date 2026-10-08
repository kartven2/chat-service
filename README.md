# chat-service

> Horizontally scalable, real-time chat system built with Java 21, Spring Boot 3, RabbitMQ, Redis, PostgreSQL and Zookeeper.

---

## Table of Contents

1. [Requirements](#1-requirements)
2. [Architecture Overview](#2-architecture-overview)
3. [System Categories](#3-system-categories)
4. [Component Details](#4-component-details)
5. [Data Storage Design](#5-data-storage-design)
6. [Message Flows](#6-message-flows)
7. [Presence & Heartbeat](#7-presence--heartbeat)
8. [Service Discovery](#8-service-discovery)
9. [Security & Auth](#9-security--auth)
10. [Project Structure](#10-project-structure)
11. [API Reference](#11-api-reference)
12. [WebSocket / STOMP Reference](#12-websocket--stomp-reference)
13. [How to Run](#13-how-to-run)
14. [Scaling to Multiple Instances](#14-scaling-to-multiple-instances)
15. [Design Decisions & Interview Notes](#15-design-decisions--interview-notes)

---

## 1. Requirements

| Requirement | Implementation |
|---|---|
| 1-1 and group chat (max 100 members) | `ChatService`, `GroupService` |
| Online indicator | Redis TTL heartbeat via `PresenceService` |
| Text messages only | `content: String` field in message entities |
| Chat history stored forever | PostgreSQL (`direct_messages`, `group_messages` tables) |
| Receive, route and relay messages | RabbitMQ exchanges + `ChatService` listeners |
| Hold messages for offline users | Push notification stub; history loaded on reconnect |
| HTTP for sending, WebSocket for server→client | STOMP over WebSocket (SockJS fallback) |
| Stateless chat servers | JWT auth, session state in Redis |
| Signup / login / profile via HTTP request-response | REST API in `AuthController`, `UserController` |
| Push notifications (FCM / APN) | Stubbed in `NotificationService` |

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENT (Browser / Mobile)                   │
│                                                                     │
│  HTTP (REST)                     WebSocket (STOMP over SockJS)      │
│  POST /api/auth/login            ws://host/ws?token=<JWT>           │
│  GET  /api/groups                /app/chat.sendDirect               │
│  GET  /api/chat/history/...      /app/chat.sendGroup                │
└────────────┬─────────────────────────────┬──────────────────────────┘
             │                             │
             ▼                             ▼
┌────────────────────┐         ┌───────────────────────┐
│   API Server       │         │    Chat Server         │
│  (Stateless)       │         │    (Stateful)          │
│                    │         │                        │
│  AuthController    │         │  ChatController        │
│  UserController    │         │  WebSocketEventListener│
│  GroupController   │         │  PresenceService       │
│  DiscoveryController         │  ChatService           │
└─────────┬──────────┘         └──────────┬─────────────┘
          │                               │
          │         ┌─────────────────────┤
          │         │                     │
          ▼         ▼                     ▼
   ┌─────────────┐  ┌──────────────┐  ┌──────────────┐
   │  PostgreSQL  │  │   RabbitMQ   │  │    Redis     │
   │             │  │              │  │              │
   │  users      │  │ chat.direct  │  │ presence:*   │
   │  groups     │  │ chat.group   │  │ msg:direct:* │
   │  group_mem  │  │ (exchanges)  │  │ (hot cache)  │
   │  direct_msg │  └──────────────┘  └──────────────┘
   │  group_msg  │
   └─────────────┘
          │
          ▼
   ┌─────────────┐      ┌──────────────────────┐
   │  Zookeeper  │      │  NotificationService │
   │             │      │  (FCM / APN stub)    │
   │  /servers/  │      └──────────────────────┘
   │  chat-srv-1 │
   │  chat-srv-2 │
   └─────────────┘
```

---

## 3. System Categories

The chat system is divided into three categories, exactly as described in the design spec:

### Stateless Services
These are horizontally scalable and share no per-request state between instances.

| Service | Class | Responsibility |
|---|---|---|
| Auth service | `AuthService`, `AuthController` | Signup, login, JWT issuance |
| User profile | `UserController` | Read-only profile endpoints |
| Group management | `GroupService`, `GroupController` | Create groups, add/remove members |
| Service discovery | `ServiceDiscoveryService`, `DiscoveryController` | List available chat servers via Zookeeper |

### Stateful Service
| Service | Class | Responsibility |
|---|---|---|
| Chat server | `ChatService`, `ChatController` | WebSocket connections, message send/receive, RabbitMQ routing |

### Third-Party Integration (Stubbed)
| Service | Class | Responsibility |
|---|---|---|
| Push notifications | `NotificationService` | Simulates FCM (Android) and APN (iOS) calls; logs intent |

---

## 4. Component Details

### `ChatController`
- Spring `@Controller` with `@MessageMapping` methods (STOMP)
- Receives inbound WebSocket frames from connected clients
- Delegates to `ChatService` for routing and persistence
- Reads `userId` and `username` from WebSocket session attributes (set by `JwtHandshakeInterceptor` at handshake time)

**STOMP destinations handled:**

| Destination | Action |
|---|---|
| `/app/chat.sendDirect` | Send a 1-1 direct message |
| `/app/chat.sendGroup` | Send a group message |
| `/app/chat.heartbeat` | Refresh online presence TTL |
| `/app/chat.disconnect` | Explicit offline notification |

---

### `ChatService`
Core stateful service. Implements the full message pipeline:
- Persists every message to PostgreSQL (chat history forever)
- Pushes to Redis hot-cache (ring buffer, last 500 messages per conversation)
- Publishes to RabbitMQ for routing to the correct chat server
- Consumes from RabbitMQ and delivers via WebSocket or push notification
- Maintains per-group `AtomicLong` local sequence number generators (message IDs unique within a group)

---

### `PresenceService`
- Uses Redis TTL keys: `presence:{userId}` → value is the server instance ID
- `markOnline(userId)` — sets the key with TTL = 10 s (also called on WebSocket connect)
- `markOffline(userId)` — deletes the key (called on disconnect)
- `recordHeartbeat(userId)` — refreshes the TTL (called every ~5 s from client)
- `isOnline(userId)` — O(1) check: does the key exist?

---

### `GroupService`
- Creates and manages group channels (stored in PostgreSQL)
- Enforces the 100-member maximum at the service layer
- Only the group owner can add or remove members
- `getMemberIds()` returns the full member set for fan-out routing

---

### `ServiceDiscoveryService`
- Uses Apache Curator (Zookeeper client) with minimal configuration
- Registers each chat server as an **ephemeral znode** under `/servers/{instanceId}`
- Ephemeral nodes disappear automatically when the server session ends (crash detection)
- Clients call `GET /api/discovery/servers` → get a list of live chat server addresses → connect to any

---

### `NotificationService`
Stubbed implementation that logs what would be sent to FCM/APN:
```
[PUSH NOTIFICATION STUB] → userId=42 | from='alice' | preview='Hello!'
```
To integrate for real: add device token lookup + HTTP POST to FCM/APNs endpoints.

---

### `JwtHandshakeInterceptor`
- Runs before every WebSocket upgrade request
- Accepts JWT via query param (`?token=...`) or `Authorization: Bearer ...` header
- On success: injects `userId` and `username` into the WebSocket session attributes map
- On failure: rejects the handshake (HTTP 403)

---

### `WebSocketEventListener`
- Listens to Spring's `SessionConnectedEvent` and `SessionDisconnectEvent`
- Calls `PresenceService.markOnline()` / `markOffline()` automatically
- Handles unexpected disconnects (no need for the client to send `/app/chat.disconnect`)

---

## 5. Data Storage Design

### PostgreSQL (Relational — users, groups, persistent history)

**`users` table**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | Auto-generated |
| `username` | VARCHAR(64) UNIQUE | |
| `email` | VARCHAR(128) UNIQUE | |
| `password` | TEXT | BCrypt hashed |
| `created_at` | TIMESTAMPTZ | Set on insert |

**`groups` table**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `name` | VARCHAR(128) | |
| `owner_id` | BIGINT FK → users | |
| `created_at` | TIMESTAMPTZ | |

**`group_members` table** (collection table)
| Column | Type |
|---|---|
| `group_id` | BIGINT FK → groups |
| `user_id` | BIGINT FK → users |

**`direct_messages` table**
| Column | Type | Notes |
|---|---|---|
| `message_id` | BIGSERIAL PK | |
| `message_from` | BIGINT | Sender userId |
| `message_to` | BIGINT | Recipient userId |
| `content` | TEXT | |
| `created_at` | TIMESTAMPTZ | |

**`group_messages` table**
| Column | Type | Notes |
|---|---|---|
| `channel_id` | BIGINT (PK part 1) | Group ID |
| `message_id` | BIGINT (PK part 2) | Local sequence per group |
| `user_id` | BIGINT | Sender |
| `content` | TEXT | |
| `created_at` | TIMESTAMPTZ | |

> Composite PK `(channel_id, message_id)` — message IDs are only unique within a group, not globally.

---

### Redis (Key-Value — presence + hot cache)

| Key pattern | Value | TTL | Purpose |
|---|---|---|---|
| `presence:{userId}` | server instance ID | 10 s | Online indicator — auto-expires on missed heartbeat |
| `msg:direct:{lo}:{hi}` | JSON list (ChatMessageDTO) | none | Hot cache of last 500 messages between two users |

---

### RabbitMQ (Message Broker — routing between chat servers)

| Exchange | Type | Routing key | Purpose |
|---|---|---|---|
| `chat.direct` | Direct | `user.{userId}` | Route 1-1 messages to the recipient's queue |
| `chat.group` | Topic | `group.{groupId}.{memberId}` | Fan-out group messages to per-member inbox queues |
| `chat.dlx` | Direct | — | Dead-letter exchange for undeliverable messages |

**Queues declared dynamically by `ChatService`:**
- `direct.user.{userId}` — per-user 1-1 inbox
- `group.{groupId}.member.{memberId}` — per-member group inbox

---

### Zookeeper (Coordination — service discovery)

```
/ (namespace: chat-service)
└── /servers/
    ├── chat-server-1  →  "localhost:8080"   (EPHEMERAL)
    └── chat-server-2  →  "host2:8080"       (EPHEMERAL)
```

Ephemeral nodes: automatically deleted when the server process dies or loses its Zookeeper session. This gives the client an always-accurate list of live servers.

---

## 6. Message Flows

### 1-1 Direct Message Flow

```
User A (connected to Chat Server 1)
  │
  │  STOMP /app/chat.sendDirect  {targetId: B, content: "Hello"}
  ▼
ChatController.sendDirect()
  │
  ▼
ChatService.sendDirectMessage()
  ├── 1. Persist → PostgreSQL direct_messages
  ├── 2. Cache   → Redis  msg:direct:{minId}:{maxId}
  └── 3. Publish → RabbitMQ chat.direct exchange, routing key "user.{B}"
                        │
                        ▼
              Queue: direct.user.{B}
                        │
                        ▼
              ChatService.deliverDirectMessage()
                  ├── User B ONLINE?
                  │     YES → SimpMessagingTemplate.convertAndSendToUser(B, "/queue/messages", dto)
                  │              │
                  │              ▼
                  │           WebSocket push to User B
                  │
                  └── User B OFFLINE?
                        YES → NotificationService.sendPushNotification(B, ...)
                              [stub — logs FCM/APN intent]
                              [User B sees full history on next login via GET /api/chat/history/direct/{A}]
```

---

### Group Message Flow

```
User A (member of Group G, connected to Chat Server 1)
  │
  │  STOMP /app/chat.sendGroup  {targetId: G, content: "Hello group!"}
  ▼
ChatController.sendGroup()
  │
  ▼
ChatService.sendGroupMessage()
  ├── 1. Generate message ID  → AtomicLong[G].incrementAndGet()
  ├── 2. Persist              → PostgreSQL group_messages (channelId=G, messageId=N)
  └── 3. Fan-out              → for each memberId in group.memberIds:
                                    publish to chat.group exchange
                                    routing key = "group.{G}.{memberId}"
                                         │
                                    Queue: group.{G}.member.{memberId}
                                         │
                                    ChatService.deliverGroupMessage()
                                         ├── member ONLINE  → WebSocket push
                                         └── member OFFLINE → push notification stub
```

---

## 7. Presence & Heartbeat

The system uses a **heartbeat + Redis TTL** mechanism (as specified in the design):

```
Client                                    Server (Redis)
  │                                           │
  │──── STOMP /app/chat.heartbeat ──────────► │
  │                                           │  SET presence:{userId} "chat-server-1" EX 10
  │                                           │
  │  (repeat every 5 seconds)                 │
  │──── STOMP /app/chat.heartbeat ──────────► │  TTL refreshed to 10 s
  │                                           │
  │  (client disappears / network drop)       │
  │                                           │  Key expires after 10 s → user is OFFLINE
```

| Configuration key | Default | Purpose |
|---|---|---|
| `chat.presence.heartbeat-ttl-seconds` | `10` | Redis key TTL |
| `chat.presence.heartbeat-interval-ms` | `5000` | Expected client heartbeat interval |

---

## 8. Service Discovery

```
On startup:
  Chat Server 1 ──► Zookeeper: CREATE EPHEMERAL /servers/chat-server-1 = "localhost:8080"

Client wants to connect:
  Client ──► GET /api/discovery/servers
           ◄── {"servers": ["localhost:8080", "host2:8080"]}
  Client picks any server and opens WebSocket

On crash:
  Chat Server 1 dies → Zookeeper session expires → ephemeral node deleted automatically
  Next client discovery call won't see that server
```

---

## 9. Security & Auth

- **Signup / Login** — traditional HTTP request/response, returns a signed JWT
- **JWT** — contains `sub` (userId) and `username` claim; signed with HMAC-SHA256
- **HTTP requests** — `JwtAuthFilter` validates `Authorization: Bearer <token>` on every request; populates `SecurityContext` with `ChatUserPrincipal`
- **WebSocket connections** — `JwtHandshakeInterceptor` validates JWT at upgrade time; injects `userId`/`username` into the STOMP session attributes
- **Session management** — `STATELESS` — no HTTP session is ever created

---

## 10. Project Structure

```
src/main/java/com/example/chat/
│
├── ChatServiceApplication.java          ← Entry point (@EnableScheduling)
│
├── config/
│   ├── AppConfig.java                   ← RabbitAdmin, queue name beans, DaoAuthProvider
│   ├── RabbitMQConfig.java              ← Exchange declarations, JSON message converter
│   ├── RedisConfig.java                 ← RedisTemplate with JSON serialiser
│   ├── SecurityConfig.java              ← Stateless JWT security filter chain
│   ├── WebSocketConfig.java             ← STOMP endpoints, broker prefixes, SockJS
│   ├── WebSocketEventListener.java      ← Connect/disconnect → presence sync
│   └── ZookeeperConfig.java             ← Curator client with namespace
│
├── controller/
│   ├── AuthController.java              ← POST /api/auth/signup|login
│   ├── ChatController.java              ← @MessageMapping WebSocket handlers
│   ├── ChatHistoryController.java       ← GET /api/chat/history/*, /presence/*
│   ├── DiscoveryController.java         ← GET /api/discovery/servers
│   ├── GlobalExceptionHandler.java      ← @RestControllerAdvice error mapping
│   ├── GroupController.java             ← CRUD /api/groups
│   └── UserController.java              ← GET /api/users/me, /api/users/{id}
│
├── domain/
│   ├── DirectMessage.java               ← 1-1 message entity (PostgreSQL)
│   ├── Group.java                       ← Group channel entity (PostgreSQL)
│   ├── GroupMessage.java                ← Group message entity, composite PK
│   ├── GroupMessageId.java              ← Composite PK class (channelId + messageId)
│   └── User.java                        ← User entity (PostgreSQL)
│
├── dto/
│   ├── AuthDTO.java                     ← SignupRequest, LoginRequest, AuthResponse
│   ├── ChatMessageDTO.java              ← Wire format: WebSocket + RabbitMQ payload
│   └── GroupDTO.java                    ← Group create/response DTOs
│
├── repository/
│   ├── DirectMessageRepository.java     ← findConversation(userA, userB)
│   ├── GroupMessageRepository.java      ← findByChannelIdOrderByCreatedAtAsc
│   ├── GroupRepository.java             ← findGroupsByMemberId
│   └── UserRepository.java             ← findByUsername, existsByEmail
│
├── security/
│   ├── ChatUserPrincipal.java           ← Lightweight principal (userId + username)
│   ├── JwtAuthFilter.java               ← HTTP request JWT validation filter
│   ├── JwtHandshakeInterceptor.java     ← WebSocket upgrade JWT validation
│   └── JwtUtil.java                     ← Token generation and parsing (jjwt)
│
└── service/
    ├── AuthService.java                 ← Signup, login, BCrypt + JWT
    ├── ChatService.java                 ← Core: send/deliver, RabbitMQ, Redis, DB ★
    ├── ChatUserDetailsService.java      ← Spring Security UserDetailsService
    ├── GroupService.java                ← Group CRUD, 100-member cap
    ├── NotificationService.java         ← FCM/APN stub
    ├── PresenceService.java             ← Redis TTL heartbeat mechanism
    └── ServiceDiscoveryService.java     ← Zookeeper ephemeral node registration
```

---

## 11. API Reference

All endpoints except `/api/auth/**` and `/ws/**` require `Authorization: Bearer <JWT>`.

### Authentication

| Method | Path | Body | Response |
|---|---|---|---|
| `POST` | `/api/auth/signup` | `{"username","email","password"}` | `{"token","userId","username"}` |
| `POST` | `/api/auth/login` | `{"username","password"}` | `{"token","userId","username"}` |

### Users

| Method | Path | Response |
|---|---|---|
| `GET` | `/api/users/me` | Own profile |
| `GET` | `/api/users/{id}` | Any user's public profile |

### Groups

| Method | Path | Body | Notes |
|---|---|---|---|
| `POST` | `/api/groups` | `{"name":"..."}` | Creates group; caller becomes owner |
| `GET` | `/api/groups` | — | List groups you belong to |
| `GET` | `/api/groups/{id}` | — | Group details + member count |
| `POST` | `/api/groups/{id}/members` | `{"userId":2}` | Owner only; max 100 members |
| `DELETE` | `/api/groups/{id}/members/{userId}` | — | Owner only |

### Chat History & Presence

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/chat/history/direct/{otherUserId}` | Full 1-1 conversation history |
| `GET` | `/api/chat/history/group/{groupId}` | Full group message history |
| `GET` | `/api/chat/presence/{userId}` | `{"userId":1,"online":true}` |

### Service Discovery

| Method | Path | Response |
|---|---|---|
| `GET` | `/api/discovery/servers` | `{"servers":["localhost:8080"]}` |

---

## 12. WebSocket / STOMP Reference

### Connection

```
// Token via query param (recommended for browser SockJS)
const socket = new SockJS('http://localhost:8080/ws?token=<JWT>');
const client = Stomp.over(socket);

client.connect({}, onConnected, onError);
```

### Subscriptions (server → client)

| Destination | When used |
|---|---|
| `/user/queue/messages` | Receive 1-1 messages and group inbox messages |
| `/topic/group/{groupId}` | Receive broadcast group messages (alternative) |

### Sending (client → server)

```javascript
// Send a 1-1 direct message
client.send('/app/chat.sendDirect', {}, JSON.stringify({
  type: 'DIRECT',
  targetId: 7,           // recipient userId
  content: 'Hello!'
}));

// Send a group message
client.send('/app/chat.sendGroup', {}, JSON.stringify({
  type: 'GROUP',
  targetId: 3,           // groupId
  content: 'Hello group!'
}));

// Heartbeat — call every 5 s to stay online
setInterval(() => client.send('/app/chat.heartbeat', {}, '{}'), 5000);

// Explicit disconnect (optional — the server also listens for WebSocket close events)
client.send('/app/chat.disconnect', {}, '{}');
```

### `ChatMessageDTO` structure

```json
{
  "type": "DIRECT | GROUP | HEARTBEAT | ACK",
  "messageId": 42,
  "senderId": 1,
  "senderUsername": "alice",
  "targetId": 7,
  "content": "Hello!",
  "timestamp": "2026-10-07T19:00:00Z",
  "handledBy": "chat-server-1"
}
```

---

## 13. How to Run

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### Step 1 — Start infrastructure

```bash
docker-compose up -d
```

| Service | Port | Notes |
|---|---|---|
| PostgreSQL | `5432` | DB: `chatdb` / user: `chatuser` / pass: `chatpass` |
| Redis | `6379` | Presence + hot cache |
| RabbitMQ | `5672` | AMQP; Management UI at `http://localhost:15672` (guest/guest) |
| Zookeeper | `2181` | Service discovery |

### Step 2 — Build

```bash
mvn clean package -DskipTests
```

### Step 3 — Run

```bash
java -jar target/chat-service-0.0.1-SNAPSHOT.jar
```

Or with Maven dev mode:

```bash
mvn spring-boot:run
```

The service starts at **`http://localhost:8080`**.

### Step 4 — Quick smoke test

```bash
# Signup
curl -s -X POST http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123"}' | jq

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123"}' | jq -r .token)

# Get own profile
curl -s http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN" | jq

# Service discovery
curl -s http://localhost:8080/api/discovery/servers | jq
```

---

## 14. Scaling to Multiple Instances

Each chat server instance must have a unique `instance-id`. Run additional instances on different ports:

```bash
# Instance 1 (default — already running)
java -jar target/chat-service-0.0.1-SNAPSHOT.jar \
  --server.port=8080 \
  --chat.server.instance-id=chat-server-1

# Instance 2
java -jar target/chat-service-0.0.1-SNAPSHOT.jar \
  --server.port=8081 \
  --chat.server.instance-id=chat-server-2
```

Both instances register themselves in Zookeeper. The discovery endpoint returns both:
```json
{ "servers": ["localhost:8080", "localhost:8081"] }
```

Clients connect to any available server. Since all servers share the same Redis and RabbitMQ, messages flow correctly across instances.

---

## 15. Design Decisions & Interview Notes

### Why WebSocket instead of HTTP long-polling?
WebSocket provides a persistent, bi-directional, low-latency channel. The server can push messages to the client the instant they arrive without the client needing to poll. HTTP is still used for stateless API calls (auth, profile, history).

### Why RabbitMQ as the message bus?
- **Decouples senders from receivers.** Chat Server 1 publishes; Chat Server 2 (wherever the recipient is connected) consumes.
- **Natural offline buffer.** Durable queues hold messages until the consuming server is ready.
- **Fan-out for groups.** Topic exchange with per-member routing keys implements the inbox model: each member has their own queue, avoiding head-of-line blocking.

### Why Redis for presence and not the database?
- TTL-based expiry is O(1) and zero-maintenance — no cleanup job needed.
- A `SET key value EX 10` is much cheaper than an UPDATE + scheduled scan.
- Key missing = offline. Key present = online. Heartbeat = TTL refresh.

### Why Zookeeper for service discovery?
- Ephemeral znodes automatically disappear when a server crashes or loses its network — this is crash detection for free.
- Clients get a consistent view of live servers without polling a database.
- Curator (Apache Curator Framework) provides retries and connection management with minimal boilerplate.

### Why a local `AtomicLong` for group message IDs?
Per the design spec: *"IDs are only unique within a group."* A local counter avoids a distributed ID generation round-trip on every message. In production, this counter would be moved to Redis (`INCR group:seq:{groupId}`) to survive server restarts.

### Why does the group fan-out publish one message per member?
This is the **per-recipient inbox model**: each member has a dedicated durable queue. It prevents any single slow consumer from blocking others and naturally supports offline delivery — messages wait in the queue until the consumer's server comes online.

### How does offline delivery work end-to-end?
1. Message arrives → persisted to PostgreSQL immediately (history stored forever).
2. `isOnline(recipientId)` check fails → `NotificationService.sendPushNotification()` is called (stub in dev).
3. On reconnect, the client calls `GET /api/chat/history/direct/{senderId}` to hydrate the conversation from PostgreSQL.

### Chat servers are stateless — what does that mean?
No user session, auth state, or subscription list is stored in the chat server's memory beyond the lifecycle of the WebSocket connection. Authentication is validated per-request from the JWT. Presence state lives in Redis, not in the server. This means any server can handle any client — load balancers can route freely.
