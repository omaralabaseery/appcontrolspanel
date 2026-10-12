# Data model and operational notes

All server timestamps are epoch milliseconds. Firebase stores a single application's workspace per project.

| Collection | Document key | Main fields |
|---|---|---|
| users | Firebase Auth UID | name, email (from Auth), language, deviceCount, lastSeen, createdAt |
| installations | persistent installation ID | uid, model, platform, build, version, language, fcmToken, createdAt, lastSeen |
| events | SHA-256(uid:eventId) | uid, eventId, feature, method, path, status, durationMs, message, createdAt |
| notifications | SHA-256(actor:idempotencyKey) | actor, userIds, title, body, fingerprint, status, accepted, failed, createdAt |
| settings | application | maintenance, localized messages, Android/iOS policy |
| appEvents | auto ID | titleEn/Ar, messageEn/Ar, startAt, endAt, enabled, iconKey, accentColor, bannerUrl, createdAt, updatedAt |
| audit | auto ID | actor, action, createdAt, policy snapshot or createdDocuments |
| _meta | schema | schemaVersion, collectionNames, createdAt, createdBy |

## Metric definitions

- **Registered users:** users registered through `/mobile/devices`; not all historical Firebase Auth accounts.
- **Online users:** distinct user documents with lastSeen > now − 5 minutes.
- **Installations:** unique installation IDs ever registered, including unlinked devices. Reinstall/reset can generate a new ID. Not verified store-download count; actual store metrics need a separate store reporting integration.
- **Multi-device users:** user documents with deviceCount > 1; these are linked devices, not necessarily simultaneous active sessions.
- **Requests/errors:** mobile events recorded; error = status >= 400.

Device registration/ownership transfer/count changes run in Firestore transactions. Logging uses a deterministic event key, so retries do not inflate request counts. Unlinking retains installation history but removes the token. Tokens are removed from device API responses. Stale/invalid FCM tokens are currently reported as failed deliveries, not automatically deleted; refresh registration on token changes and add a cleanup job appropriate to your app lifecycle.

## Scale and deployment boundaries

- Lists are paged in document-ID order (50 rows). UI filtering applies to loaded rows. No unbounded collection scan is used for dashboard stats; they use count aggregations.
- Notifications are synchronous and limited to 100 users / 20 linked devices per user. Add a queue, worker checkpoints and cancellation controls for large campaigns. A provider interruption may leave delivery uncertain, deliberately preventing automatic duplicate retry with the same key.
- Apply rate limits, maximum request body size, TLS and monitoring at your gateway. Add Firebase App Check verification for mobile ingestion if your threat model requires it. Authentication alone does not prevent a valid user generating excessive telemetry/installations.
- Add Firestore retention/TTL policies for telemetry and audit according to your requirements. Removing installation or event records changes cumulative metrics and event retry deduplication; archive counters first if lifetime totals are needed. These policies are not silently enabled.
- User deletion and historical data export/erasure workflows are not included. Coordinate Firebase Auth deletion with your application data lifecycle.
- Policy writes include an audit snapshot but currently use last-write-wins semantics. Coordinate administrators; add ETag/version preconditions if concurrent policy editing is required.
- Deny-all client rules are provided for a dedicated database. Admin SDK access bypasses rules: secure server credentials and IAM.
- API DTO validation limits strings and sanitized path characters. Free-form diagnostic messages must be scrubbed in the mobile app. The panel escapes rendered dynamic content.
- The server has no demo bypass. Browser sample mode is separate, labelled and cannot send to Firebase.
- The frontend's Firebase SDK is pinned and loaded only when signing in; the demo has no third-party runtime dependency.

## Database initialization

See [DATABASE_SETUP_AR.md](DATABASE_SETUP_AR.md). The canonical field manifest is `src/main/resources/database/schema.json`. The admin initializer creates only missing settings/metadata, plus an audit record when a change occurs. It never creates fake users, installations, events or notifications. Repeated initialization is a no-op and preserves existing policy.
