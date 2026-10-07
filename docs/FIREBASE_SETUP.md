# Firebase setup

## 1. Create or select your project

1. Enable Firebase Authentication → Email/Password.
2. Create a Cloud Firestore database in Native mode and select its permanent region deliberately.
3. Enable Cloud Messaging and configure the Android app / Apple APNs credentials in Firebase.
4. Register a Firebase web app for the panel. Copy its **public** web configuration into `src/main/resources/static/config.js` (apiKey, authDomain, projectId, appId). This is not a service-account key.
5. Add the panel's domain to Authentication's authorized domains.
6. Apply `firestore.rules` using your Firebase console or CLI. These rules deny direct client access; mobile and panel clients use Spring Boot. **If reusing an existing database, review and merge rules first: replacing existing rules could break other apps.** The Admin SDK is authorized by server IAM, not these rules.

## 2. Server credentials

Use Application Default Credentials / Workload Identity in hosted environments where possible. For local work, keep a service-account JSON file **outside this repository** and export:

```sh
export FIREBASE_ENABLED=true
export FIREBASE_PROJECT_ID='your-project-id'
export GOOGLE_APPLICATION_CREDENTIALS='/secure/location/service-account.json'
mvn spring-boot:run
```

PowerShell:

```powershell
$env:FIREBASE_ENABLED = 'true'
$env:FIREBASE_PROJECT_ID = 'your-project-id'
$env:GOOGLE_APPLICATION_CREDENTIALS = 'C:\secure\service-account.json'
mvn spring-boot:run
```

Do not put private keys in config.js, GitHub, localStorage, or browser requests. `.env.example` documents variables; Spring does not automatically read a `.env` file. Grant the runtime service account only the Firestore, Firebase Auth and FCM permissions it needs. Auth token verification checks revocation; the service account also needs access to retrieve Firebase Auth users.

## 3. Grant administrator access

Create an admin user in Firebase Authentication. From a **trusted** Java Admin SDK environment initialized with your server credentials, run this once using its UID:

```java
var auth = com.google.firebase.auth.FirebaseAuth.getInstance();
var user = auth.getUser("THE_ADMIN_FIREBASE_UID");
var claims = new java.util.HashMap<String, Object>(user.getCustomClaims());
claims.put("admin", true);
auth.setCustomUserClaims(user.getUid(), claims);
```

Do not expose this operation as a public endpoint. Sign out/in after granting the claim. Use **Connect Firebase** in the panel and enter the account email/password. The browser uses in-memory authentication persistence and returns to demo mode after reload; tokens are refreshed by the Firebase SDK during the session.

## 4. Deploy

```sh
mvn clean verify
java -jar target/appcontrol-panel-1.0.0.jar
```

Or build `docker build -t appcontrol .` and run with the environment variables and a read-only credentials mount. Prefer workload identity instead of files in production. The same server serves HTML and `/api`, so no permissive CORS is required. Place it behind an HTTPS reverse proxy/load balancer. `PORT` defaults to 8080. Limit request bodies to 32 KB and apply route-specific rate limits at your gateway (especially login, heartbeat, event ingestion and notification send).

## 5. Verify live behavior

- Sign in as an admin; the badge must change to **Live Firebase**.
- Register a real mobile installation and verify it appears under Users & devices.
- Send heartbeat; online count should reflect the last five minutes.
- Register another installation for the same UID; device count should increase, not user count.
- Send an event and inspect the user-specific log.
- Send one push to a test device and verify receipt on the device. FCM acceptance alone is not delivery confirmation.
- Check Android and iOS policy with builds below minimum, between minimum/latest, and equal to latest; test `en` and `ar-KW`.
- Enable maintenance and verify it overrides the update action. Disable it afterward.

## Official references

- https://firebase.google.com/docs/admin/setup
- https://firebase.google.com/docs/auth/admin/custom-claims
- https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
- https://docs.spring.io/spring-boot/3.5/system-requirements.html
