# اختبار APIs الكنترول بانيل

## 1. اختبارات آلية بدون Firebase

```bash
mvn test
```

اختبارات الخدمة الحالية تغطي سياسة الإصدارات، تنقية JSON، الإشعارات ونتائج FCM، بيانات الديمو، وحالة الاتصال. `ApiContractTest` يضيف اختبار مسارات HTTP وصلاحيات القراءة، ربط الحقول والـ aliases بالمستخدم المسجل، حفظ الإعدادات، تسجيل الأجهزة، heartbeat، logs، ورفض الطلبات الناقصة. الخدمات الخارجية في اختبارات HTTP مستبدلة بـ mocks؛ نجاحها لا يعني نجاح اتصال Firebase الحقيقي.

## 2. Postman

اعمل Import للملفين:

- `postman/AppControl.postman_collection.json`
- `postman/AppControl.local.postman_environment.json`

اختار Environment باسم AppControl - local test. الكولكشن يحتوي 29 request موزعة على جميع مسارات API الحالية مع حالات نجاح وفشل وroute aliases.

شغّل Spring Boot بالإعدادات المحلية الصحيحة:

```bash
mvn spring-boot:run
```

استخدم `baseUrl = http://localhost:8089` أو عنوان السيرفر الفعلي. رابط GitHub Pages للواجهة التجريبية فقط، ولا يشغّل هذه APIs.

### متغيرات البيئة

| المتغير | القيمة المطلوبة |
|---|---|
| baseUrl | عنوان Spring Boot بدون `/api` أو شرطة أخيرة |
| adminToken | Firebase ID token لحساب لديه custom claim: admin=true |
| userToken | Firebase ID token لحساب مستخدم عادي |
| userId / userId2 | UID فعليان مختلفان للمستخدمين المستهدفين |
| projectId | مشروع Firebase التجريبي المتصل به السيرفر |
| fcmToken | FCM registration token من موبايل تجريبي؛ ليس Firebase ID token |
| deviceId | معرّف تثبيت تجريبي؛ 8–128 حرفًا من حروف/أرقام/_/- |
| notificationId | ID إشعار موجود؛ يُحفظ تلقائيًا بعد طلب إرسال ناجح |
| cursor | اتركه فارغًا لأول صفحة، ثم استخدم nextCursor من النتيجة |
| allowWrites | false افتراضيًا؛ غيّره إلى true لتجربة عمليات الكتابة |
| allowBroadcast | false افتراضيًا؛ true يسمح بطلب الإرسال لكل المستخدمين أيضًا |

يمكن أخذ Firebase ID token من تطبيقك بعد تسجيل الدخول عبر Firebase Authentication باستدعاء `currentUser.getIdToken()` حسب SDK المستخدم. الأدمن يجب أن يحمل admin=true؛ جدّد التوكن بعد تعديل الـ claim. احتفظ بالتوكن في القيمة المحلية في Postman ولا ترفعه إلى GitHub. التوكن ليس API key ولا service account JSON.

### ترتيب التجربة

1. شغّل Public ثم Admin reads. طلب Connection يرجع حالة الاتصال حتى إن كان Firebase غير مهيأ؛ الطلبات التي تقرأ البيانات تحتاج اتصالًا فعليًا.
2. طلبا تفاصيل الإشعار والمستلمين يحتاجان notificationId موجودًا؛ يمكن تشغيلهما بعد إرسال إشعار تجريبي.
3. للتجربة على مشروع اختبار، فعّل allowWrites ثم شغّل تسجيل الجهاز، heartbeat وCapture request. شغّل Unregister بعد الانتهاء، لأنه يفصل الجهاز.
4. شغّل Get settings قبل Save current settings؛ الثاني يعيد حفظ نفس الإعدادات التي قرأتها. لا تشغّله لو لم تنجح القراءة.
5. جرّب Send selected users على حسابين تجريبيين. طلب Replay يستخدم نفس pushKey ونفس المحتوى للتحقق من عدم تكرار الإرسال. غيّر pushKey لكل إرسال جديد، ولا تغيّره عند اختبار إعادة المحاولة.
6. اختبر Negative cases: النتائج المتوقعة 401 بدون تسجيل دخول، 403 للمستخدم العادي على admin، و400 للبيانات أو الباراميترات الناقصة. اختبارات 400 التي تتطلب توكن تحتاج توكن صالحًا حتى تصل لمرحلة فحص الطلب.
7. إرسال All users يرسل فعلًا لجميع المستخدمين في المشروع عند تفعيل allowWrites وallowBroadcast. شغّله منفردًا في مشروع الاختبار فقط.
8. Cleanup يحذف سجلات الديمو المعلّمة التي تخص خدمة seed؛ لا يحذف تلقائيًا الحدث الذي أرسلته يدويًا. eventId ثابت لإعادة التجربة؛ غيّره لإنشاء حدث جديد.

اختبارات Postman تتحقق من HTTP status وJSON، وبعض أشكال الاستجابة، وتحفظ policyJson وnotificationId. لا تفترض أنها تختبر وصول الإشعار إلى شاشة الموبايل: قبول FCM لا يضمن عرض الإشعار على الجهاز؛ راقب الجهاز ونتائج recipients أيضًا.

تشغيل الكولكشن كله مع allowWrites=false يتخطى عمليات الكتابة عمدًا. ابدأ بالمجلدات المناسبة بدل اعتبار كل الطلبات مستقلة عن البيانات المطلوبة.
