# Chat Service — Quick Start Guide

## Prerequisites
- Java 21
- Maven 3.9+
- Docker & Docker Compose

---

## 1. Start infrastructure

```bash
docker-compose up -d
```

This starts:
| Service | Port | Notes |
|---|---|---|
| PostgreSQL | 5432 | DB: `chatdb`, user: `chatuser`, pass: `chatpass` |
| Redis | 6379 | Hot cache + presence |
| RabbitMQ | 5672 / 15672 | 15672 = Management UI (guest/guest) |
| Zookeeper | 2181 | Service discovery |

---

## 2. Build & Run

```bash
mvn clean package -DskipTests
java -jar target/chat-service-0.0.1-SNAPSHOT.jar
```

Or using Maven directly:
```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## 3. API Reference

### Auth (public — no token needed)

| Method | URL | Body |
|---|---|---|
| POST | `/api/auth/signup` | `{"username":"alice","email":"a@b.com","password":"secret123"}` |
| POST | `/api/auth/login`  | `{"username":"alice","password":"secret123"}` |

Both return: `{"token":"<JWT>","userId":1,"username":"alice"}`

### Users (Bearer token required)

| Method | URL | Description |
|---|---|---|
| GET | `/api/users/me` | Own profile |
| GET | `/api/users/{id}` | Any user profile |

### Groups

| Method | URL | Body / Notes |
|---|---|---|
| POST | `/api/groups` | `{"name":"My Group"}` — creates group, caller becomes owner |
| GET | `/api/groups` | List groups you belong to |
| GET | `/api/groups/{id}` | Group details |
| POST | `/api/groups/{id}/members` | `{"userId":2}` — owner only, max 100 members |
| DELETE | `/api/groups/{id}/members/{userId}` | Remove member — owner only |

### Chat history (REST — load on reconnect)

| Method | URL |
|---|---|
| GET | `/api/chat/history/direct/{otherUserId}` |
| GET | `/api/chat/history/group/{groupId}` |
| GET | `/api/chat/presence/{userId}` |

### Service discovery

```
GET /api/discovery/servers
→ {"servers":["localhost:8080"]}
```

---

## 4. WebSocket / STOMP Connection

```javascript
// 1. Get a token via POST /api/auth/login
// 2. Connect (token passed as query param)
const socket = new SockJS('http://localhost:8080/ws?token=<JWT>');
const client = Stomp.over(socket);

client.connect({}, () => {
  // Subscribe to your 1-1 inbox
  client.subscribe('/user/queue/messages', (frame) => {
    const msg = JSON.parse(frame.body);
    console.log('Received:', msg);
  });

  // Subscribe to a group channel
  client.subscribe('/topic/group/42', (frame) => { ... });

  // Send a direct message
  client.send('/app/chat.sendDirect', {}, JSON.stringify({
    type: 'DIRECT',
    targetId: 2,          // recipient userId
    content: 'Hello!'
  }));

  // Send a group message
  client.send('/app/chat.sendGroup', {}, JSON.stringify({
    type: 'GROUP',
    targetId: 42,         // groupId
    content: 'Hello group!'
  }));

  // Heartbeat (call every 5 s)
  setInterval(() => client.send('/app/chat.heartbeat', {}, ''), 5000);
});
```

---

## 5. Architecture Notes (Interview Points)

### Why WebSocket?
Persistent bi-directional connection — server can push messages without polling.
HTTP is used only for stateless API calls (auth, profile, group management).

### Why RabbitMQ?
Decouples the sending server from the receiving server. The message is enqueued and any available server can deliver it. Supports offline buffering naturally.

### Why Redis for presence?
TTL keys are O(1) check and auto-expire — no cleanup job needed.
A missing key = offline. Heartbeat = key refresh.

### Why Zookeeper?
Ephemeral nodes automatically disappear when a server crashes.
Client asks Zookeeper for live servers → gets a DNS hostname → opens WebSocket.

### Local sequence number for group message IDs
Per README: IDs only need to be unique *within* a group.
`AtomicLong` per groupId avoids a distributed ID generation round-trip.
In production this counter would live in Redis (`INCR group:seq:{groupId}`).

### How does offline delivery work?
1. Message arrives → persisted to PostgreSQL (forever).
2. Push notification sent (FCM/APN stub).
3. On reconnect → client calls `GET /api/chat/history/direct/{userId}` to hydrate.

### Group fan-out model
Each member has their own durable inbox queue.
This is the "per-recipient inbox" model — scales to 100 members per README spec.

---

## 6. Running a Second Instance (horizontal scaling demo)

```bash
java -jar target/chat-service-0.0.1-SNAPSHOT.jar \
  --server.port=8081 \
  --chat.server.instance-id=chat-server-2
```

Both instances register in Zookeeper. `GET /api/discovery/servers` returns both addresses.
