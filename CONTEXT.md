# uMatter Notification Service — Context

## 1. Purpose

The Notification Service is a **stateful, event-driven background worker** for the
uMatter platform. It now owns one local domain — the **in-app notification inbox** —
which lives in its own PostgreSQL database. Its responsibilities are:

> 1. Consume domain events from RabbitMQ produced by other uMatter services
>    (Booking, Tracking, Social).
> 2. Persist every alert to the local `in_app_notifications` table so the
>    mobile/web client can render a read/unread history.
> 3. Dispatch the corresponding outbound communication via **SMTP email** or
>    **Firebase Cloud Messaging (FCM) push notifications**.
> 4. Expose a small REST API for the client to query and update inbox state.

### Architectural stance
- **Stateful, but narrowly:** the service owns exactly one table
  (`in_app_notifications`). It still does not own user accounts, devices,
  bookings, streaks, or chats — those remain in their respective domains.
- **Postgres + Flyway** for the inbox; **Redis** for idempotency dedup
  (see §3); RabbitMQ for inbound events.
- **Dual-action consumer:** after the Redis idempotency check, every consumer
  performs *both* `historyService.save(...)` (DB) *and* `dispatcher.send(...)`
  (Email or FCM) — see §4.
- Horizontally scalable: instances are still interchangeable; all per-message
  state lives in shared Postgres + Redis.

### What this service is *not*
- It is not the producer of the source-of-truth domain events.
- It does not maintain user preferences (those live in the owning domains).
- It does not call other uMatter services synchronously.

---

## 2. RabbitMQ Topology

All exchanges are **topic** exchanges. Each domain owns its own exchange; the
notification service binds queues it owns into those exchanges using stable
routing keys. Every queue has a paired DLX/DLQ for poison messages.

| Domain   | Exchange           | Routing Key           | Queue (owned by this service)             | DLX                | DLQ                                        |
|----------|--------------------|-----------------------|-------------------------------------------|--------------------|--------------------------------------------|
| Booking  | `booking.exchange` | `appointment.booked`  | `notification.booking.booked.q`           | `booking.dlx`      | `notification.booking.booked.dlq`          |
| Tracking | `tracking.exchange`| `streak.milestone`    | `notification.tracking.streak.q`          | `tracking.dlx`     | `notification.tracking.streak.dlq`         |
| Social   | `social.exchange`  | `message.missed`      | `notification.social.message-missed.q`    | `social.dlx`       | `notification.social.message-missed.dlq`   |

The queue declarations live in [RabbitConfig.java](src/main/java/com/umatter/notification/config/RabbitConfig.java);
their string values come from `notification.rabbit.*` in
[application.yml](src/main/resources/application.yml).

### Listener semantics
- **Acknowledgement mode**: `auto` — Spring acks on successful return, nacks on
  thrown exception.
- **No requeue on failure**: `defaultRequeueRejected=false`. A thrown exception
  routes the message straight to the queue's DLQ via its
  `x-dead-letter-exchange` argument, preventing poison-message loops.
- **Retry**: 3 attempts with exponential backoff (2s → 10s) inside the listener
  container before giving up and dead-lettering.
- **Concurrency**: 3–10 concurrent consumers per queue, prefetch 20.
- **Error handler**: [AmqpListenerErrorHandler.java](src/main/java/com/umatter/notification/exception/AmqpListenerErrorHandler.java)
  logs and rethrows as `AmqpRejectAndDontRequeueException`.

### Event payload contract

Producers carry **profile_id**, not device tokens. Token-to-device routing is
the Notification Service's responsibility (see §3a, `device_tokens`).

Every payload implements [`NotificationEnvelope`](src/main/java/com/umatter/notification/dto/NotificationEnvelope.java)
and MUST carry:

```json
{
  "messageId": "uuid-v4-or-equivalent-unique-id",
  "occurredAt": "2026-05-13T09:32:00Z",
  "... domain fields ..."
}
```

| Routing key          | DTO                                                                                                            | Inbox `type` | Outbound channel |
|----------------------|----------------------------------------------------------------------------------------------------------------|--------------|------------------|
| `appointment.booked` | [`BookingConfirmedEvent`](src/main/java/com/umatter/notification/dto/BookingConfirmedEvent.java)               | `BOOKING`    | Email (HTML)     |
| `streak.milestone`   | [`StreakMilestoneEvent`](src/main/java/com/umatter/notification/dto/StreakMilestoneEvent.java)                 | `STREAK`     | FCM push         |
| `message.missed`     | [`MessageMissedEvent`](src/main/java/com/umatter/notification/dto/MessageMissedEvent.java)                     | `CHAT`       | FCM push         |

> **Required envelope addition:** `BookingConfirmedEvent` now also requires
> `profileId` (UUID) so the inbox row can be attributed to a user. Producers
> on the Booking side must include it.

---

## 3. Redis Idempotency Mechanism

### Why
RabbitMQ provides **at-least-once** delivery. A network blip, a consumer crash
mid-ack, or a producer retry can cause the same logical event to be redelivered.
Sending the same booking confirmation email twice — or the same push twice in
five seconds — is a visible failure mode for users.

### How
Before any consumer dispatches a notification, it calls
[`IdempotencyService.tryAcquire(scope, messageId)`](src/main/java/com/umatter/notification/service/idempotency/IdempotencyService.java).
That method runs a single atomic Redis operation:

```
SET notif:idemp:<scope>:<messageId>  "1"  NX  EX  86400
```

(via `StringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24))`).

- **First-seen path**: Redis returns `true` → key created with a 24-hour TTL →
  consumer proceeds to dispatch.
- **Duplicate path**: Redis returns `false` (key already exists) → consumer
  silently acks and skips.

### Key layout
```
notif:idemp:<routing-key>:<messageId>
```
The routing key is included as a **scope** so the same `messageId` never
collides across domains (e.g. a booking event and a tracking event that happen
to share an id will not deduplicate each other).

### TTL choice
`24h` is the default (`notification.idempotency.ttl-hours` in `application.yml`).
It is long enough to absorb realistic redelivery storms after broker outages and
short enough that the keyspace stays bounded. Override via env if needed.

### Failure semantics
If a `messageId` is missing from the payload the service logs a warning and
processes the event once (fail-open) — the producer is the bug, and dropping
the event would be worse than a possible duplicate. Redis failures bubble up as
[`IdempotencyException`](src/main/java/com/umatter/notification/exception/IdempotencyException.java)
and route the message to DLQ.

---

## 3a. In-App Notification Inbox (PostgreSQL)

The service owns a dedicated PostgreSQL database (`notification`) on host port
**5435** (chosen to avoid colliding with the Auth and Booking DBs in the wider
uMatter platform). The schema is managed by **Flyway**:

- [`V1__init_notification_schema.sql`](src/main/resources/db/migration/V1__init_notification_schema.sql) — `in_app_notifications` table
- [`V2__add_types_and_seed_data.sql`](src/main/resources/db/migration/V2__add_types_and_seed_data.sql) — adds `REMINDER`/`INSIGHT` to the type CHECK + seeds Figma demo rows
- [`V3__create_device_tokens_table.sql`](src/main/resources/db/migration/V3__create_device_tokens_table.sql) — `device_tokens` table

The service owns **exactly two tables**: `in_app_notifications` (the inbox)
and `device_tokens` (FCM routing registry). It does not own user accounts,
bookings, streaks, or chats — those still live in their producing domains.

### Table: `in_app_notifications`

| Column            | Type           | Notes                                                |
|-------------------|----------------|------------------------------------------------------|
| `notification_id` | `UUID PK`      | generated by the service                             |
| `profile_id`      | `UUID`         | the user receiving the alert                         |
| `title`           | `VARCHAR(255)` | short heading shown in the inbox                     |
| `message`         | `TEXT`         | body of the alert                                    |
| `type`            | `VARCHAR(32)`  | one of `BOOKING`, `STREAK`, `CHAT`, `REMINDER`, `INSIGHT` (CHECK constraint) |
| `is_read`         | `BOOLEAN`      | default `FALSE`                                       |
| `created_at`      | `TIMESTAMPTZ`  | default `NOW()`                                       |

Indexes:
- `idx_in_app_notifications_profile_id` — single-column on profile_id
- `idx_in_app_notifications_created_at` — descending, for global recency
- `idx_in_app_notifications_profile_created` — composite (profile_id, created_at DESC)
  to serve the inbox query directly

JPA model:
[`InAppNotification`](src/main/java/com/umatter/notification/persistence/InAppNotification.java)
+ [`InAppNotificationRepository`](src/main/java/com/umatter/notification/persistence/InAppNotificationRepository.java).
Business operations are wrapped by
[`NotificationHistoryService`](src/main/java/com/umatter/notification/service/NotificationHistoryService.java).

### Table: `device_tokens`

The mobile app registers its FCM token here on login (and on token-refresh).
The consumers look the registered tokens up by `profile_id` when fanning out a
push — producers (Booking/Tracking/Social) never need to know the token.

| Column          | Type           | Notes                                                |
|-----------------|----------------|------------------------------------------------------|
| `device_token`  | `TEXT PK`      | FCM token; globally unique (Firebase guarantee)      |
| `profile_id`    | `UUID`         | the user this device belongs to                      |
| `platform`      | `VARCHAR(16)`  | `ANDROID`, `IOS`, or `WEB` (CHECK constraint)        |
| `created_at`    | `TIMESTAMPTZ`  | when the token was first registered                  |
| `last_seen_at`  | `TIMESTAMPTZ`  | refreshed on every re-register                       |

Index: `idx_device_tokens_profile_id` on `profile_id`.

A single profile can have multiple rows (phone + tablet + web). Re-registering
the same token under a different profile is a simple UPDATE — that handles the
"sign out → different user signs in on the same device" case.

JPA model:
[`DeviceToken`](src/main/java/com/umatter/notification/persistence/DeviceToken.java)
+ [`DeviceTokenRepository`](src/main/java/com/umatter/notification/persistence/DeviceTokenRepository.java).
Business operations are wrapped by
[`DeviceTokenService`](src/main/java/com/umatter/notification/service/DeviceTokenService.java).

### Consumer flow

Every consumer follows the same shape:

```
1. idempotency.tryAcquire(scope, messageId)         # Redis; short-circuit on duplicate
2. historyService.save(profileId, type, ...)        # Postgres; commits inbox row
3. dispatch:
     - booking  → emailDispatcher.send(...)         # SMTP
     - tracking → for each registered token: push   # FCM fan-out
     - social   → for each registered token: push   # FCM fan-out
```

For the push consumers, step 3 looks up `device_tokens` by `profile_id` and
iterates — one dead/invalid token logs a warning but **does not fail** the
event, since the inbox row is the authoritative record. A profile with zero
registered devices logs an info line and proceeds (the user will still see
the row when they open the app).

Failure semantics:
- Step 2 throws → message goes to DLQ; no email/push sent.
- Step 3 (whole batch fails for booking, or unhandled error for push) →
  DB row already committed; message goes to DLQ.

---

## 3b. REST API

Base path: `/api/v1/notifications` —
[`NotificationController`](src/main/java/com/umatter/notification/api/NotificationController.java).
All endpoints return JSON.

| Method | Path                                  | Purpose                                              |
|--------|---------------------------------------|------------------------------------------------------|
| GET    | `/api/v1/notifications/{profileId}`   | Paginated inbox for the user, newest first          |
| PUT    | `/api/v1/notifications/{notificationId}/read` | Mark one notification as read              |
| PUT    | `/api/v1/notifications/{profileId}/read-all`  | Mark every notification for that user read |
| POST   | `/api/v1/devices`                     | Register/refresh an FCM device token (idempotent)   |
| DELETE | `/api/v1/devices/{deviceToken}`       | Deregister a device (logout, token invalidation)    |

### `GET /api/v1/notifications/{profileId}`

Query params:
- `page` (default `0`)
- `size` (default `20`, capped at `100`)

Returns a Spring `Page<InAppNotificationDto>`:
```json
{
  "content": [
    {
      "notificationId": "...",
      "profileId": "...",
      "title": "Appointment confirmed",
      "message": "Your session with Dr. X is confirmed for ...",
      "type": "BOOKING",
      "read": false,
      "createdAt": "2026-05-13T09:32:00Z"
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

### `PUT /api/v1/notifications/{notificationId}/read`
- `200 OK` with `{ "notificationId": "...", "read": true }` on success.
- `404 Not Found` if the id does not exist (no rows updated).

### `PUT /api/v1/notifications/{profileId}/read-all`
- `200 OK` with `{ "profileId": "...", "updatedCount": N }`.

### `POST /api/v1/devices`
Request body:
```json
{
  "profileId": "e1d0add5-b9c8-57b5-36e6-059991832f17",
  "deviceToken": "fcm-token-from-the-mobile-app",
  "platform": "ANDROID"
}
```
- `201 Created` with `{ "profileId": "...", "platform": "...", "lastSeenAt": "..." }`.
- Idempotent: calling again with the same `deviceToken` re-binds the profile
  (handles "different user signs in on this device") and refreshes
  `lastSeenAt`.

### `DELETE /api/v1/devices/{deviceToken}`
- `204 No Content` on successful removal.
- `404 Not Found` if the token wasn't registered.

---

## 4. Firebase Admin SDK — Local Setup

The push dispatcher uses `firebase-admin` and authenticates with a
**service-account JSON** issued from the Firebase console.

### Step-by-step

1. In the Firebase console → **Project settings → Service accounts → Generate
   new private key**. A JSON file is downloaded.
2. Save it as `secrets/firebase-credentials.json` at the repo root (the
   `secrets/` folder is gitignored — never commit this file).
3. Configure the env var. Two equivalent options:

   **Option A — local (running on host)**
   ```bash
   export FIREBASE_CREDENTIALS_PATH="$(pwd)/secrets/firebase-credentials.json"
   export FIREBASE_ENABLED=true
   ```

   **Option B — docker-compose**
   The `notification-service` container mounts `./secrets` read-only at
   `/app/secrets`. Set in your `.env`:
   ```
   FIREBASE_CREDENTIALS_PATH=/app/secrets/firebase-credentials.json
   FIREBASE_ENABLED=true
   ```

4. Boot the service. Look for `Firebase Admin SDK initialized.` in logs.

### Running without Firebase (local dev)
Set `FIREBASE_ENABLED=false` (or leave `FIREBASE_CREDENTIALS_PATH` blank). The
service starts normally; push dispatches are logged and skipped instead of
failing. Email dispatch still works.

---

## 5. Environment Variables

All env vars are documented in [.env.example](.env.example). Copy that file to
`.env` and fill in real values. Categories:

- **RabbitMQ** — host, port, credentials, vhost
- **Redis** — host, port, optional password
- **PostgreSQL (inbox)** — `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (default
  `jdbc:postgresql://localhost:5435/notification`; in docker-compose the URL
  is rewritten to `jdbc:postgresql://notification-postgres:5432/notification`)
- **SMTP** — host, port, username, app password
- **Mail identity** — `MAIL_FROM`, `MAIL_FROM_NAME`
- **Firebase** — `FIREBASE_ENABLED`, `FIREBASE_CREDENTIALS_PATH`

---

## 6. Graceful Shutdown

Configured in `application.yml`:
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

On `SIGTERM`:
1. The Spring `WebServerGracefulShutdown` stops accepting new HTTP requests on
   the actuator port.
2. The Rabbit listener containers stop receiving new deliveries.
3. In-flight messages get up to 30s to finish dispatch and ack.
4. Connections to RabbitMQ and Redis are closed cleanly.

In the container, [`tini`](https://github.com/krallin/tini) is the PID-1 so
`SIGTERM` is forwarded correctly to the JVM (this is what makes
`docker stop` and Kubernetes pod termination behave well).

---

## 7. Local Development

### Bring up RabbitMQ + Redis + Postgres only
```bash
docker compose up rabbitmq redis notification-postgres
```
Postgres is reachable from the host at `localhost:5435` (db/user/password all
`notification` by default).
RabbitMQ management UI: http://localhost:15672 (guest/guest by default).

### Bring up everything (service included)
```bash
cp .env.example .env
# edit .env, then:
docker compose up --build
```

### Run the service from the IDE against compose infra
Run `rabbitmq`, `redis`, and `notification-postgres` services from compose,
then start
[`NotificationServiceApplication`](src/main/java/com/umatter/notification/NotificationServiceApplication.java)
from your IDE with the env vars from `.env` exported. Flyway will run V1
automatically on startup against the local Postgres.

### Gradle wrapper
The wrapper jar is not committed (binary). Bootstrap once with:
```bash
gradle wrapper --gradle-version 8.10.2
```
Then `./gradlew bootRun` / `./gradlew bootJar` as usual.

---

## 8. Package Layout

```
com.umatter.notification
├── NotificationServiceApplication
├── api/           # @RestController — inbox query + mark-read endpoints
├── config/        # Rabbit, Redis, Firebase, Mail — all @ConfigurationProperties-driven
├── consumer/      # @RabbitListener entry points (one per routing key)
├── dto/           # Inbound event payloads + outbound REST DTOs
├── persistence/   # JPA entity, enum, repository — owns `in_app_notifications`
├── service/       # Email + Push dispatchers, NotificationHistoryService
│   └── idempotency/  # Redis-backed dedup
└── exception/     # Custom exceptions + AMQP error handler
```

The layering rule is one-way:
`api/consumer → service → persistence → (external systems)`.
Consumers and the REST controller never call each other; services never call
consumers or the controller.
