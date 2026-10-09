# REST API and mobile integration

Base URL: `https://YOUR_HOST/api`. Interactive schema: `/swagger-ui.html`; OpenAPI JSON: `/v3/api-docs`.

All API routes except `GET /mobile/config` and `GET /connection` require `Authorization: Bearer <Firebase ID token>`. `/admin/**` additionally requires the boolean `admin=true` claim. A user cannot choose another UID for device registration, heartbeat or event ingestion: ownership comes from the verified token.

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
| POST | `/admin/notifications` | Personalized array of 1–100 IDs or all-users ID 0; requires `Idempotency-Key` |
| GET | `/admin/notifications/{id}` | Request summary plus first page of recipients / FCM results |
| GET | `/admin/notifications/{id}/recipients?cursor=` | Remaining recipients, 50 per page |
| GET | `/admin/settings` | Current version and maintenance policy |
| PUT | `/admin/settings` | Replace complete validated policy; writes an audit record |

List responses: `{ "items": [...], "nextCursor": "" }`. Pass non-empty nextCursor to retrieve the next page. Paging order is document ID; the UI sorts loaded events/history by timestamp. Search/status filters operate on loaded records; use the server-side UID filter for a user's logs. This avoids silently claiming that a first-page search covers the entire database.

### Push requests, recipients and FCM results

`POST /api/admin/notifications` (alias `/api/admin/notifications/requests`) requires an admin token and `Idempotency-Key`. Send this body to **all registered users**, including users beyond the first UI page:

```json
[{"id":0,"title":"test","message":"test"}]
```

Send different content to selected users:

```json
[{"id":"FIREBASE_UID_1","title":"First title","message":"First message"},
 {"id":"FIREBASE_UID_2","title":"Second title","message":"Second message"}]
```

Numeric IDs such as 1 and 2 are accepted and converted to strings; they only work if the corresponding user document IDs are literally `1` and `2`. Use the actual Firebase UID in this project. ID 0 is reserved for all users and cannot be mixed with other entries. Duplicate IDs are rejected. Maximum 100 selected entries; the broadcast walks the full users collection in pages of 100. The existing `{userIds,title,body}` payload remains accepted.

The response contains `id`, the normalized `requests`, `audience`, `recipientCount`, `accepted`, `failed`, `unknown`, `status`, and `recipients: {items,nextCursor}`. Each recipient includes its UID, title, message, counts and `fcmResults` with `deviceId`, status, and FCM `messageId` or `errorCode`. It never exposes tokens. History is stored in `notifications/{requestId}` with recipient details in its `recipients` subcollection.

FCM calls contain at most 500 messages each. Acceptance is not delivery confirmation. Recipients with no registered token return `no_devices`; missing users return `user_not_found`. In demo mode the status is `simulated`, never an invented FCM success.

The same idempotency key and payload returns the stored request without resending. A different payload with the same key is rejected. Indeterminate provider errors or interruption leave `unknown` or `processing`; inspect saved recipient results before deciding on another send. Broadcast runs synchronously in this version; large campaigns need a durable worker and queue before production-scale use. User membership can change during pagination; this is not a frozen audience snapshot.

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

## Request Input.json and Output.json

Include `input` and `output` in `POST /api/mobile/events`:

```json
{"eventId":"request-uuid-1234","feature":"Order details screen","method":"GET","path":"/orders/{id}","status":500,"durationMs":2400,"message":"Screen failed to open after API timeout","input":{"params":{"id":"sample-order"},"body":null},"output":{"status":500,"body":{"error":{"code":"TIMEOUT","message":"Order service did not respond"}}}}
```

The server derives `uid` from the caller's Firebase token. Both payloads can be JSON objects, arrays, primitives or null; each is limited to 16 KB and depth 12. Known password, token, authorization, cookie, key and card credential fields are recursively redacted. The mobile app must sanitize other personal data before sending. Firestore stores serialized `inputJson` and `outputJson` to preserve nested JSON arrays; the admin API returns parsed `input` and `output`. Existing logs without payloads show "No payload was captured". The UI displays and downloads Input.json and Output.json inside Requests & logs. Overview no longer fetches or displays request rows.

## Register device under mobile settings

`POST /api/mobile/settings/register-device` is an alias of `/api/mobile/devices`, using the same authenticated ownership and update behavior:

```json
{"id":"persistent-device-uuid","pushNotificationToken":"FCM_TOKEN","deviceVersion":"2.4.1","platform":"android","model":"Samsung Galaxy S24","build":241,"language":"ar"}
```

Here `id` is the installation ID, not the user ID. The original field names `installationId`, `fcmToken` and `version` remain supported. `platform`, `build`, `model` and `language` are still required. An empty/null token removes push registration. Device version and integer build describe the installed app release.

## Enable or disable individual releases

Each platform in the settings may now contain:

```json
{"minimumBuild":240,"latestBuild":241,"version":"2.4.1","storeUrl":"https://play.google.com/store/apps?id=YOUR_PACKAGE","releases":[{"build":240,"version":"2.4.0","enabled":false},{"build":241,"version":"2.4.1","enabled":true}]}
```

The mobile config response adds `versionEnabled` and `allowAccess`. An explicitly disabled build receives `action: version_disabled`, `allowAccess: false`, `updateRequired: true`, the localized update message and store URL. Maintenance has first priority. Enabling a build does not override the minimum supported build; unlisted builds follow the existing minimum/latest policy. Select an enabled latest build before disabling the old latest, to avoid redirecting users to a disabled target. Up to 100 unique rules per platform; omitted release lists remain compatible with old settings. The mobile app must enforce the action once connected in phase two.
