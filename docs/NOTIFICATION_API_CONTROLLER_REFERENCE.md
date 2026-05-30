# uMatter Notification API — Frontend Reference

A single-document reference for the mobile and web clients consuming the
Notification Service. If you're implementing the inbox screen, the
notification badge, or wiring up FCM device registration, everything you need
is here.

> Companion docs (for backend engineers, not frontend):
> - [../CONTEXT.md](../CONTEXT.md) — service architecture
> - Producer event contracts (RabbitMQ payloads) — not relevant to the frontend;
>   you only call HTTP endpoints.

---

## 1. Base URL & environments

| Environment | Base URL                                           |
|-------------|----------------------------------------------------|
| Local       | `http://localhost:8084` *(host port may vary, see `SERVER_PORT` in `.env`)* |
| Staging     | _TBD when deployed_                                |
| Production  | _TBD when deployed_                                |

All endpoints below are relative to the base URL.

All requests and responses use **`Content-Type: application/json; charset=utf-8`**.
The service is fully UTF-8 native — Vietnamese diacritics (`ậ`, `ấ`, `ề`, etc.)
round-trip correctly without any encoding flags.

---

## 2. Authentication

**Current state (May 2026):** all API endpoints are protected by Spring Security.
Every request (except explicitly public endpoints like `/actuator/**` and `/error`) must carry:
```
Authorization: Bearer <JWT>
```
issued by the uMatter Auth service. Authorization rule:
> The JWT's `profileId` claim must match the resource owner **OR** the JWT must carry `role: ADMIN`.

For `POST /api/v1/devices`, `profileId` is already read from the JWT claim and
is not accepted in the request body.

---

## 3. Endpoints — overview

| # | Method | Path                                              | Purpose                              |
|---|--------|---------------------------------------------------|--------------------------------------|
| 1 | `GET`  | `/api/v1/notifications/{profileId}`               | List inbox (paginated, newest first) |
| 2 | `PUT`  | `/api/v1/notifications/{notificationId}/read`     | Mark one notification as read        |
| 3 | `PUT`  | `/api/v1/notifications/{profileId}/read-all`      | Mark every notification as read      |
| 4 | `POST` | `/api/v1/devices`                                 | Register / refresh an FCM token      |
| 5 | `DELETE` | `/api/v1/devices/{deviceToken}`                 | Deregister an FCM token              |
| — | `GET`  | `/actuator/health`                                | Service liveness probe               |

---

## 4. Endpoint reference

### 4.1 — `GET /api/v1/notifications/{profileId}`

List the user's notification inbox, **newest first**, paginated.

**Path params**

| Name        | Type    | Notes              |
|-------------|---------|--------------------|
| `profileId` | UUID    | The user's profile |

**Query params**

| Name   | Type | Default | Range  | Notes                              |
|--------|------|---------|--------|------------------------------------|
| `page` | int  | `0`     | `≥ 0`  | Zero-based page index              |
| `size` | int  | `20`    | `1..100` | Capped at 100 server-side          |

**Response — `200 OK`**

```json
{
  "content": [
    {
      "notificationId": "1c68d9ce-a9ad-45b3-9e30-a7e77718071e",
      "profileId":       "e1d0add5-b9c8-57b5-36e6-059991832f17",
      "title":           "Tin nhắn mới từ Dave",
      "message":         "52 Tin nhắn chưa đọc",
      "type":            "CHAT",
      "read":            false,
      "createdAt":       "2026-05-17T05:44:45.324017Z"
    }
    // ... up to `size` items
  ],
  "totalElements": 8,
  "totalPages":    1,
  "number":        0,
  "size":          20,
  "first":         true,
  "last":          true,
  "numberOfElements": 8,
  "empty":         false
  // … plus Spring Data Page metadata (sort, pageable, etc.)
}
```

The fields you actually need on the frontend are highlighted in bold:
**`content[]`**, **`totalElements`**, **`number`** (current page), **`last`**
(no more pages?). Everything else is Spring Data boilerplate you can ignore.

> ⚠️ The exact JSON shape of the Spring `Page` envelope is not formally stable
> across Spring Data versions. We will likely switch to a custom DTO before
> production (you'll see fields like `pageable`, `sort.unsorted` disappear).
> Read **`content`**, **`totalElements`**, **`number`**, **`last`** and ignore the rest.

**Field reference (each item in `content[]`)**

| Field             | Type             | Notes |
|-------------------|------------------|-------|
| `notificationId`  | UUID             | Use this for `mark-read` calls |
| `profileId`       | UUID             | Always the path's profileId |
| `title`           | string (≤ 255)   | Display heading |
| `message`         | string (TEXT)    | Display body |
| `type`            | enum             | One of `BOOKING`, `STREAK`, `CHAT`, `REMINDER`, `INSIGHT` — drive icon/colour from this |
| `read`            | boolean          | `false` = render bold / show dot; `true` = render greyed |
| `createdAt`       | ISO-8601 UTC     | Always UTC; convert to local timezone client-side |

**Example**
```bash
curl "http://localhost:8084/api/v1/notifications/e1d0add5-b9c8-57b5-36e6-059991832f17?page=0&size=20" \
  -H "Authorization: Bearer <JWT>"
```

```typescript
// fetch
const res = await fetch(
  `${BASE_URL}/api/v1/notifications/${profileId}?page=0&size=20`,
  {
    headers: { Authorization: `Bearer ${token}` },
  }
);
const { content, totalElements, last } = await res.json();
```

---

### 4.2 — `PUT /api/v1/notifications/{notificationId}/read`

Mark a single notification as read. Idempotent — calling it twice on the same
already-read row still returns `200 OK`.

**Path params**

| Name             | Type | Notes |
|------------------|------|-------|
| `notificationId` | UUID | From the `notificationId` field of an inbox item |

**Response — `200 OK`**
```json
{
  "notificationId": "1c68d9ce-a9ad-45b3-9e30-a7e77718071e",
  "read": true
}
```

**Errors**

| Status | Meaning |
|--------|---------|
| `404 Not Found` | No notification with that id exists (or it was deleted) |

**Example**
```bash
curl -X PUT "http://localhost:8084/api/v1/notifications/1c68d9ce-a9ad-45b3-9e30-a7e77718071e/read" \
  -H "Authorization: Bearer <JWT>"
```

---

### 4.3 — `PUT /api/v1/notifications/{profileId}/read-all`

Mark every unread notification for the given profile as read. Useful for the
"mark all read" button in the inbox header.

**Path params**

| Name        | Type | Notes |
|-------------|------|-------|
| `profileId` | UUID | The user |

**Response — `200 OK`**
```json
{
  "profileId":    "e1d0add5-b9c8-57b5-36e6-059991832f17",
  "updatedCount": 5
}
```
`updatedCount` is the number of rows that flipped from `read=false` to
`read=true` — useful for updating the badge count without a re-fetch.

**Example**
```bash
curl -X PUT "http://localhost:8084/api/v1/notifications/e1d0add5-b9c8-57b5-36e6-059991832f17/read-all" \
  -H "Authorization: Bearer <JWT>"
```

---

### 4.4 — `POST /api/v1/devices`

Register an FCM device token (or refresh an existing one). **Idempotent** —
re-posting the same `deviceToken` updates `lastSeenAt` and re-binds the
profile in place.

**Request body**

```json
{
  "deviceToken": "<the long string from Firebase messaging().getToken()>",
  "platform":    "ANDROID"
}
```

**Body field reference**

| Field         | Type   | Required | Notes |
|---------------|--------|----------|-------|
| `deviceToken` | string | yes      | From the Firebase SDK on the mobile device |
| `platform`    | enum   | yes      | One of `ANDROID`, `IOS`, `WEB` |

**Response — `201 Created`**
```json
{
  "profileId":  "e1d0add5-b9c8-57b5-36e6-059991832f17",
  "platform":   "ANDROID",
  "lastSeenAt": "2026-05-17T12:34:56.789Z"
}
```

**Errors**

| Status | Meaning |
|--------|---------|
| `400 Bad Request` | Missing/blank `deviceToken` or invalid `platform` |
| `401 Unauthorized` | Missing/invalid Bearer token |
| `403 Forbidden` | Token is valid but missing required claims (for example `profileId`) |

**When to call this from the mobile app**

1. **On login** (after you have the JWT):
   - Request FCM permission (iOS: native prompt; Android 13+: native prompt)
   - If granted → `messaging().getToken()` → POST here
2. **On Firebase's token-refresh callback** (Firebase rotates tokens periodically):
   - POST the new token (same endpoint)
3. **On app foreground after long inactivity** (optional but recommended):
   - POST again to refresh `lastSeenAt` — this protects the row from being garbage-collected by a future stale-token cleanup job

**Example**
```bash
curl -X POST "http://localhost:8084/api/v1/devices" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceToken": "dGhpcy1pcy1mYWtlLWZjbS10b2tlbg",
    "platform": "ANDROID"
  }'
```

---

### 4.5 — `DELETE /api/v1/devices/{deviceToken}`

Deregister an FCM token. Call this on logout.

**Path params**

| Name          | Type   | Notes |
|---------------|--------|-------|
| `deviceToken` | string | URL-encoded if it contains special chars (FCM tokens usually don't, but be safe) |

**Response — `204 No Content`**

(Empty body)

**Errors**

| Status | Meaning |
|--------|---------|
| `404 Not Found` | Token wasn't registered |

**Example**
```bash
curl -X DELETE "http://localhost:8084/api/v1/devices/dGhpcy1pcy1mYWtlLWZjbS10b2tlbg" \
  -H "Authorization: Bearer <JWT>"
```

---

### 4.6 — `GET /actuator/health` *(diagnostics, not for end-user features)*

Returns the service's liveness + dependency health. Useful for showing a
"backend connected ✅" indicator in dev builds.

**Response — `200 OK`** when everything is healthy:
```json
{
  "status": "UP",
  "components": {
    "db":     { "status": "UP" },
    "redis":  { "status": "UP" },
    "rabbit": { "status": "UP" }
  }
}
```

Returns `503 Service Unavailable` (still with a JSON body) when any dependency is down.

---

## 5. Recommended client lifecycle

### 5.1 — App boot / login

```
1. Authenticate user      → get JWT (includes `profileId` claim)
2. Fetch inbox            → GET /api/v1/notifications/{profileId}?page=0&size=20
3. Ask FCM permission     → Firebase SDK
4. If granted:
     a. messaging().getToken()
  b. POST /api/v1/devices  { deviceToken, platform }
5. Subscribe to Firebase onTokenRefresh → re-POST the new token
6. Subscribe to Firebase onMessage      → update local state / show in-app banner
```

### 5.2 — Inbox screen open / pull-to-refresh

```
GET /api/v1/notifications/{profileId}?page=0&size=20
```
Render `content[]`. Show unread count via `content.filter(n => !n.read).length`
or your own server-driven badge count.

### 5.3 — User taps a notification

```
1. Navigate to the deep link based on `type`:
     CHAT      → open the relevant channel (channelId is in the FCM data payload)
     BOOKING   → open the appointment screen
     STREAK    → open the streak/journal screen
     REMINDER  → open the mood-log entry screen
     INSIGHT   → open the insights screen
2. PUT /api/v1/notifications/{notificationId}/read
3. Update local UI state so the badge decrements without a re-fetch
```

### 5.4 — User taps "mark all read"

```
PUT /api/v1/notifications/{profileId}/read-all
→ update all local items to read=true; zero the badge
```

### 5.5 — Logout

```
1. DELETE /api/v1/devices/{currentDeviceToken}
2. Clear local auth state and inbox cache
```

If you skip step 1, the user keeps receiving pushes on this device after logout — bad UX and a privacy risk.

---

## 6. Notification type → UI mapping (suggested)

These are the only five values the `type` field will ever return. Pick a stable
icon + colour scheme per type so users learn to recognise them at a glance.

| Type       | Source event                       | Suggested icon  | Suggested colour | Suggested deep-link target |
|------------|------------------------------------|-----------------|------------------|----------------------------|
| `BOOKING`  | A therapy session was confirmed    | calendar-check  | blue             | Appointment detail screen  |
| `STREAK`   | The user hit a streak milestone    | flame / trophy  | orange           | Streak / journal screen    |
| `CHAT`     | Missed message while user offline  | chat-bubble     | green            | Chat / channel screen      |
| `REMINDER` | Hasn't logged mood in N days       | bell            | yellow           | Mood-log entry screen      |
| `INSIGHT`  | Tracked metric crossed a threshold | chart-up        | purple           | Insights / analytics screen |

The backend does not currently include a deep-link URL in the response — derive
the target from `type` on the frontend. If we ever need per-row routing, we'll
add a `link` field to the response (additive, non-breaking).

---

## 7. Common pitfalls & FAQs

**Q: Why is `messageId` in the producer payload but not in the GET response?**
A: `messageId` is the *event* idempotency key (used internally by the service
to dedupe RabbitMQ redeliveries). It is not stored on the inbox row. The
client-facing identifier is `notificationId`, which is generated by the
service when the row is created. They are different concepts; do not confuse them.

**Q: Timestamps are showing as UTC instead of local time.**
A: That's the contract — `createdAt` is always UTC ISO-8601 (`...Z`). Convert
on the client:
```typescript
new Date(item.createdAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
```

**Q: Vietnamese text shows as `???` or `Tin nh?n m?i`.**
A: Your HTTP client is decoding as Latin-1. Force UTF-8:
- `fetch` — works by default if the response has `Content-Type: ...; charset=utf-8` (we send this).
- `axios` — defaults to UTF-8 for JSON; check you're not setting `responseType: 'arraybuffer'` accidentally.
- iOS native `URLSession` — works by default.

**Q: I got a `404` from mark-read but I just fetched the inbox.**
A: Race condition is possible (admin deleted the row, or your inbox cache is
stale). Refetch and reconcile. The `404` is benign — treat it the same as a
successful read on the client.

**Q: How do I get an unread count without fetching all pages?**
A: Today, fetch page 0 with `size=100` and filter client-side. If unread
counts get big enough that this is wasteful, we can add `GET /api/v1/notifications/{profileId}/unread-count` — open an issue if you hit this.

**Q: Do I need to register the device token on every app launch?**
A: No — once is enough. But it's safe to re-POST on every login (the endpoint
is idempotent and refreshing `lastSeenAt` is a feature, not a bug). The
**critical** times to POST are: first launch after install, after login as a
different user, and on every Firebase token-refresh callback.

**Q: The server returned a `400` from `POST /api/v1/devices` but my JSON looks fine.**
A: Most common cause: `platform` value isn't one of `ANDROID | IOS | WEB` (case-sensitive, uppercase). Also check `deviceToken` is non-empty.

---

## 8. Error response shape

Validation errors (`400`) follow Spring's default shape:

```json
{
  "timestamp": "2026-05-17T12:34:56.789+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/v1/devices"
}
```
(Field-level details may or may not be present depending on the failure.
Don't depend on the exact shape; just surface `error` to the user or your own
generic "something went wrong" message.)

Server errors (`5xx`) follow the same shape. Treat anything `>= 500` as
"backend is having a moment; retry with backoff."

---

## 9. Changelog

| Date       | Change                                                                 |
|------------|------------------------------------------------------------------------|
| 2026-05-17 | Added `POST /api/v1/devices` and `DELETE /api/v1/devices/{token}`. Removed `deviceToken` from producer-side event payloads (frontend-irrelevant, listed for completeness). |
| 2026-05-13 | Added `REMINDER` and `INSIGHT` to the `type` enum.                     |
| 2026-05-13 | Initial release: inbox endpoints (`GET`, `PUT read`, `PUT read-all`).  |
