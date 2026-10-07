# تجهيز قاعدة بيانات Firebase للـ APIs

المشروع المرتبط بالواجهة: **insta-11358**. قاعدة البيانات التي يستخدمها Spring Boot هي **Cloud Firestore — (default)**.

الكود الذي تم توفيره هو إعداد Firebase Web App. `getAnalytics()` يشغل Analytics فقط؛ لا ينشئ قاعدة بيانات ولا يمنح السيرفر صلاحية إدارة Firestore. تم وضع إعداد الويب في `src/main/resources/static/assets/js/config/firebase-config.js`، ولم يتم تفعيل تتبع Analytics داخل لوحة الإدارة.

## ما تم تجهيزه

- تعريف كامل للـCollections والحقول وأنواعها في [schema.json](../src/main/resources/database/schema.json).
- APIs لقراءة تعريف البيانات، فحص حالة التهيئة، وإنشاء مستندات الإعداد الناقصة.
- قسم **Integration → Firestore database setup** في اللوحة.
- التهيئة داخل transaction واحدة، ويمكن تكرارها بدون تكرار البيانات أو إعادة ضبط الإعدادات.
- لا يتم إدخال مستخدمين أو أجهزة أو طلبات تجريبية في قاعدة البيانات الحقيقية.

## الجداول المطلوبة (Collections)

| Collection | Document ID | المحتوى | متى يتم إنشاؤه؟ |
|---|---|---|---|
| `users` | Firebase Auth UID | الاسم، البريد، اللغة، آخر نشاط، عدد الأجهزة | أول تسجيل جهاز عبر API |
| `installations` | معرّف تثبيت عشوائي ثابت | المستخدم، الجهاز، Android/iOS، الإصدار، build، FCM token | تسجيل التطبيق عبر API |
| `events` | SHA-256(uid:eventId) | الطلبات، المميزات المستخدمة، أكواد الأخطاء، المدة، رسالة المشكلة | أول حدث يرسله التطبيق |
| `notifications` | SHA-256(adminUid:idempotencyKey) | الرسالة، المستلمون، حالة الإرسال، المقبول والفاشل | أول طلب إرسال إشعار |
| `settings` | `application` | رسائل الصيانة والتحديث بالعربي والإنجليزي وسياسة الإصدارات | زر التهيئة أو حفظ الإعدادات |
| `audit` | ID تلقائي | من عدّل الإعدادات ومتى، وتفاصيل التغيير | التهيئة أو تعديل السياسة |
| `_meta` | `schema` | إصدار هيكل البيانات، أسماء الـCollections، وقت ومن نفّذ التهيئة | زر التهيئة |

Firestore لا يحتاج `CREATE TABLE`. الـCollection تظهر بعد إنشاء أول Document بداخلها. لذلك عدم ظهور `users` أو `events` قبل أول استخدام حقيقي أمر طبيعي؛ الـAPIs جاهزة لإنشائها. لا تضف Document وهمي اسمه `test` أو `schema` داخل مجموعات المستخدمين والأجهزة لأنه سيؤثر على العدادات.

الحسابات وكلمات المرور يديرها **Firebase Authentication**. لا يتم تخزين كلمات مرور في `users` أو إنشاء جدول كلمات مرور. لا نحتاج جدولًا منفصلًا لـonline users أو multi-device users: تُحسب من `lastSeen` و`deviceCount`. الإصدارات والصيانة داخل مستند إعداد واحد يستخدمه الـAPI بالفعل.

## 1. تفعيل Firestore في المشروع

1. افتح https://console.firebase.google.com/project/insta-11358/firestore
2. إذا لم تكن القاعدة موجودة: **Create database → Cloud Firestore Standard / Native mode → (default)**، واختر المنطقة المناسبة وProduction mode.
3. إذا كانت القاعدة موجودة بالفعل، استخدمها ولا تنشئ بديلًا أو تحذف بياناتها. تأكد أن أسماء الـCollections أعلاه ليست مستخدمة لنظام آخر بهيكل مختلف قبل التهيئة.
4. فعّل Authentication بطريقة Email/Password وأنشئ حساب الأدمن، واضبط صلاحية `admin=true` كما في [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

ملف `firestore.rules` الحالي يمنع وصول العملاء المباشر لأن القراءة والكتابة تمر عبر Spring Boot. في مشروع يحتوي أنظمة أخرى، راجع وادمج القواعد بدل استبدال قواعده كاملة. القواعد لا تعطي صلاحيات Admin SDK؛ حساب خدمة السيرفر يستخدم IAM.

## 2. تشغيل Spring Boot بصلاحيات Firebase

إعداد الويب الذي تم توفيره لا يكفي لهذه الخطوة. استخدم Application Default Credentials / Workload Identity على السيرفر، أو ملف Service Account محليًا خارج الريبو.

مثال PowerShell:

```powershell
$env:FIREBASE_ENABLED = 'true'
$env:FIREBASE_PROJECT_ID = 'insta-11358'
$env:GOOGLE_APPLICATION_CREDENTIALS = 'C:\secure\firebase-service-account.json'
mvn spring-boot:run
```

لا ترسل مفتاح Service Account في المحادثة، ولا تضعه في GitHub أو `assets/js/config/firebase-config.js`. اتركه في جهازك أو إعدادات السيرفر فقط. المفتاح العام الموجود في Web Config ليس بديلًا له.

## 3. تهيئة المستندات

افتح `http://localhost:8080`، واضغط **Connect Firebase** وسجّل بحساب الأدمن. ثم:

**Integration → Initialize database → Initialize missing configuration**.

سيتم إنشاء المستندات الناقصة فقط:

- `settings/application`: صيانة معطلة، رسائل عربية وإنجليزية، build مبدئي 1، وروابط عامة للمتاجر.
- `_meta/schema`: تعريف إصدار التهيئة وأسماء الـCollections.
- `audit/{id}`: تسجيل عملية التهيئة إذا حدث تغيير بالفعل.

إذا كانت إعدادات الإصدار موجودة، لن تتغير. إذا كانت التهيئة مكتملة، يعيد الطلب `changed:false` ولا ينشئ audit إضافيًا. إصدار schema مختلف يتطلب migration مدروسة ولا يتم استبداله.

يمكن تشغيل نفس العملية من Swagger أو HTTP:

```http
POST /api/admin/database/initialize
Authorization: Bearer <FIREBASE_ADMIN_ID_TOKEN>
```

APIs الإعداد:

| Method | Path | الاستخدام |
|---|---|---|
| GET | `/api/admin/database/schema` | تعريف جميع الـCollections والحقول |
| GET | `/api/admin/database` | المشروع، حالة التهيئة، والـCollections التي تحتوي سجلات |
| POST | `/api/admin/database/initialize` | تهيئة آمنة للمستندات الناقصة |

كلها للأدمن فقط. حالة `initialized` تعني وجود مستند إعدادات وmetadata بإصدار متوافق؛ لا تعني اكتمال إعداد APNs أو استلام Push على جهاز حقيقي.

## 4. إدخال البيانات الحقيقية

بعد التهيئة:

1. أدخل روابط تطبيقك الفعلية في App Store وGoogle Play وأرقام الـBuild في **Version control**.
2. اربط الموبايل بـFirebase Authentication وFCM، ثم استدعِ `POST /api/mobile/devices` بعد تسجيل الدخول لتظهر `users` و`installations`.
3. أرسل heartbeat أثناء نشاط التطبيق، وأرسل الأحداث إلى `POST /api/mobile/events` لتظهر الطلبات والمشكلات.
4. جرّب إشعارًا لمستخدم تجريبي فعلي من **Push notifications** لتظهر `notifications`.

أمثلة الطلبات: [API.md](API.md). الـAPI يحدد UID من Firebase ID token؛ لا يقبل UID يختاره المستخدم لتسجيل أحداث أو أجهزة لشخص آخر.

## أنواع البيانات والفهارس

- `createdAt` و`lastSeen` أرقام صحيحة بالـmilliseconds من السيرفر، وليست حقول Firestore Timestamp؛ الحفاظ على النوع مهم لاستعلامات online.
- `minimumBuild` و`latestBuild` و`deviceCount` و`status` أرقام، وليس نصوصًا.
- `maintenance` Boolean، و`userIds` array، و`android`/`ios` maps.
- الاستعلامات الحالية تعتمد على الفهارس الافتراضية أحادية الحقل وعلى ترتيب document ID؛ لم نضف أو نحذف composite indexes من مشروعك.
- ملف schema توثيق للهيكل وليس SQL migration ولا نظام تحقق آلي داخل Firestore. التحقق من المدخلات يتم في Spring Boot DTOs. لا تعدّل المستندات يدويًا بأنواع مختلفة.

## حالة التنفيذ

الكود وتهيئة الهيكل جاهزان. لم يتم الاتصال بقاعدة المشروع الحقيقية أو إدخال بيانات فيها باستخدام Web Config وحده. تنفيذ التهيئة الفعلي يحتاج تفعيل القاعدة وصلاحيات السيرفر وتسجيل دخول الأدمن بالخطوات أعلاه.

مراجع Firebase:

- https://firebase.google.com/docs/firestore/data-model
- https://firebase.google.com/docs/projects/api-keys
- https://firebase.google.com/docs/admin/setup
