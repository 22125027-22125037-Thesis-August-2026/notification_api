# FCM Notifications in Notification Service

This service sends Firebase Cloud Messaging (FCM) push notifications to **registered device tokens** for selected event types.

## What gets pushed to the device?

Currently, the service sends FCM pushes for these inbound RabbitMQ events:

| Source event | Inbox type | Device push? | Notes |
|---|---|---:|---|
| `appointment.booked` | `BOOKING` | Yes | Also sends a booking confirmation email. |
| `streak.milestone` | `STREAK` | Yes | Push-only outbound channel. |
| `message.missed` | `CHAT` | Yes | Push-only outbound channel. |

### Not currently pushed

The inbox supports `REMINDER` and `INSIGHT`, but there is no consumer in this repo that publishes FCM pushes for those types yet.

## Device targeting

Pushes are sent to all active device tokens registered for the profile receiving the event. A profile can have multiple devices, so the same notification may be pushed to phone, tablet, and web tokens.

The tokens are stored in the `device_tokens` table and looked up by `profile_id` before fan-out.

## Push payload shape

Each push uses:

- `title`: short summary shown in the system notification UI
- `body`: message preview
- `data`: key/value metadata for deep-linking in the client

### Booking push

Title example:

- `Appointment confirmed`

Body example:

- `Your session with Dr. X is confirmed for ...`

Data keys:

- `type = appointment.booked`
- `profileId`
- `appointmentId`
- `therapistName`

### Streak push

Title example:

- `You hit a <milestone> streak!`

Body example:

- `Day 7. Keep going — your future self thanks you.`

Data keys:

- `type = streak.milestone`
- `profileId`
- `streakCount`
- `milestoneName`

### Missed message push

Title example:

- `New message from <sender>`

Body example:

- `Open the chat to read what you missed.`

Data keys:

- `type = message.missed`
- `profileId`
- `channelId`
- `senderName`

## Delivery behavior

- If no tokens are registered for the profile, the inbox row is still saved and push is skipped.
- If an FCM token is invalid or revoked, it is deregistered automatically when Firebase returns a dead-token error.
- If Firebase is disabled or credentials are missing, push dispatch is skipped with a warning log.

## Important note

FCM is used only for device delivery. The inbox row in PostgreSQL is still created for every processed event, so the app can show a history even if push delivery fails.
