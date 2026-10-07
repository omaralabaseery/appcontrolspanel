# REST API and mobile integration

Base URL: `https://YOUR_HOST/api`. Interactive schema: `/swagger-ui.html`; OpenAPI JSON: `/v3/api-docs`.

All routes except `GET /mobile/config` require `Authorization: Bearer <Firebase ID token>`. `/admin/**` additionally requires the boolean `admin=true` claim. A user cannot choose another UID for device registration, heartbeat or event ingestion: ownership comes from the verified token.

## Admin APIs

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/database/schema` | Collection definitions and field types |
| GET | `/admin/database` | Bootstrap status and populated collections |
| POST | `/admin/database/initialize` | Create missing settings/metadata and audit; preserve existing data |
| GET | `/admin/stats` | `users`, `online`, `installations`, `multiDevice`, `requests`, `errors`, `updatedAt` |
| GET | `/admin/users?cursor=` | Users, 50 per page |
| GET | `/admin/users/{uid}` | User and linked devices; no FCM tokens returned |
| GET | `/admin/events?uid=&cursor=` | All events or an exact UID |
| GET | `/admin/notifications?cursor=` | Notification history |
| POST | `/admin/notifications` | Send to 1–100 user IDs; requires `Idempotency-Key` header |
| GET | `/admin/settings` | Current version and maintenance policy |
| PUT | `/admin/settings` | Replace complete validated policy; writes an audit record |

List responses: `{ "items": [...], "nextCursor": "" }`. Pass non-empty nextCursor to retrieve the next page. Paging order is document ID; the UI sorts loaded events/history by timestamp. Search/status filters operate on loaded records; use the server-side UID filter for a user's logs. This avoids silently claiming that a first-page search covers the entire database.

### Push request

```http
POST /api/admin/notifications
Authorization: Bearer <ADMIN_ID_TOKEN>
Idempotency-Key: 863f145e-41a8-4cda-b4ea-04f3d77dccfa
Content-Type: application/json

{"userIds":["firebase-uid-1","firebase-uid-2"],"title":"Order update","body":"Your order is on its way."}
```

The server gathers registered tokens for those users, deduplicates them, and sends batches of up to 500. Status values: `processing`, `accepted`, `partial`, `no_devices`, `unknown`. `accepted` means FCM accepted messages, not confirmed display or delivery. Reusing an idempotency key for the same payload returns its existing result without sending again. Reusing it for different content is rejected. If network/provider failure leaves `unknown` or `processing`, inspect before retrying; use the same key to read its status. A new key may duplicate already delivered messages. For large campaigns add a durable background job/queue before increasing limits. This endpoint can be called by other authorized application features via a trusted admin integration identity; never give an ordinary mobile client an admin claim.

### Settings request

```json
{
  "maintenance": false,
  "maintenanceEn": "We are performing scheduled maintenance. Please try again shortly.",
  "maintenanceAr": "التطبيق تحت الصيانة. يرجى المحاولة مرة أخرى بعد قليل.",
  "updateEn": "Please update your app to continue.",
  "updateAr": "يرجى تحديث التطبيق للمتابعة.",
  "android": {"minimumBuild":240,"latestBuild":241,"version":"2.4.1","storeUrl":"https://play.google.com/store/apps?id=YOUR_PACKAGE"},
  "ios": {"minimumBuild":240,"latestBuild":241,"version":"2.4.1","storeUrl":"https://apps.apple.com/app/idYOUR_APP_ID"}
}
```

Use monotonically increasing **integer build numbers** on each platform. A version label such as `2.4.1` is display-only. Minimum must not exceed latest. The initial fallback policy uses build 1 and generic official store landing URLs; replace these with your actual app links before live mobile use.

## Mobile APIs

### Register/update an installation

Call after authenticated login, when the FCM token changes, when language changes and after upgrading. Persist a random installation UUID locally; do not regenerate it on each request and do not use invasive hardware identifiers.

```http
POST /api/mobile/devices
Authorization: Bearer <USER_ID_TOKEN>
Content-Type: application/json

{"installationId":"persisted-uuid-1234","platform":"android","model":"Samsung Galaxy S24","build":241,"version":"2.4.1","language":"ar","fcmToken":"CURRENT_FCM_TOKEN"}
```

No `uid` field is accepted. `language` is `ar` or `en`. `fcmToken` may be null/empty when permission is denied; it clears the stored token. A device can belong to one user at a time; registration transfers ownership and updates counts transactionally. A maximum of 20 linked installations per user is enforced. On logout, call `DELETE /mobile/devices/{installationId}` **before** signing out; this clears ownership/token but retains the installation record for counting.

### Presence

```http
POST /api/mobile/heartbeat
Authorization: Bearer <USER_ID_TOKEN>
Content-Type: application/json

{"installationId":"persisted-uuid-1234"}
```

Send every 60 seconds while foreground/active; stop in the background. Online means lastSeen within five minutes, not a persistent websocket connection. Registered devices must belong to the caller.

### Request and feature telemetry

```http
POST /api/mobile/events
Authorization: Bearer <USER_ID_TOKEN>
Content-Type: application/json

{"eventId":"persisted-event-uuid","feature":"Checkout","method":"POST","path":"/orders/{id}","status":500,"durationMs":2400,"message":"Payment gateway timeout; code PAY_TIMEOUT"}
```

Use a stable eventId across retries for deduplication. The server supplies UID and timestamp. Methods: GET, POST, PUT, PATCH, DELETE, LOCAL. Use LOCAL with a 2xx status for non-HTTP feature activity. Paths allow only sanitized route templates (no query strings). Never send request/response bodies, tokens, passwords, payment data, personal addresses, or sensitive stack traces in `message`. Client telemetry is untrusted diagnostic input, not an authoritative billing/security log.

### Version / maintenance lookup

```http
GET /api/mobile/config?platform=android&build=239&language=ar-KW
```

```json
{"action":"force_update","message":"يرجى تحديث التطبيق للمتابعة.","language":"ar","updateRequired":true,"updateAvailable":true,"storeUrl":"https://play.google.com/store/apps?id=YOUR_PACKAGE","latestBuild":241,"latestVersion":"2.4.1"}
```

| Action | App behavior |
|---|---|
| `maintenance` | Show localized blocking maintenance screen, offer retry; do not redirect to store |
| `force_update` | Show non-dismissible update dialog and open the returned store URL on Update |
| `optional_update` | Show update prompt with Update / Later |
| `allow` | Continue normally |

Call at app launch, after sign-in, and when resuming. Match Arabic locale prefixes (`ar`, `ar-KW`, `ar-EG`); all other locales fall back to English. Open the store link using Android/iOS URL launching APIs (Flutter: `url_launcher` with external application mode). On return from the store, re-read the installed build and policy. The website/API cannot redirect an already installed mobile app by itself: the mobile client must implement this behavior.

Handle 503/network errors with an explicit retry screen or a product-approved cache policy; never treat an API failure as a successful `allow` response. Maintenance here is a client policy; enforce maintenance server-side in your business APIs as well if transactions must be blocked.

## Error behavior

- 400: invalid fields/parameters.
- 401: missing/invalid/revoked Firebase token.
- 403: authenticated user lacks the admin claim.
- 503: Firebase not configured or unavailable configuration state.
- 500: unexpected provider/database failure; inspect server logs.

Use exponential backoff for safe reads/heartbeat; preserve event IDs and push idempotency keys when retrying writes. Server exceptions inside Firestore transactions may surface as a generic 500; inspect operational logs rather than blindly changing request IDs.
