# نشر نسخة المراجعة

الـ workflow `Deploy control panel review` ينشر واجهة HTML/CSS/JavaScript من `main` تلقائيًا عند تغيير ملفات الواجهة أو إعداد النشر. يمكن تشغيله يدويًا أيضًا.

## التفعيل مرة واحدة

1. افتح Settings → Pages في الريبو.
2. من Build and deployment اختر Source: GitHub Actions.
3. افتح Actions → Deploy control panel review → Run workflow واختر main.
4. بعد نجاح النشر، استخدم الرابط الظاهر في خطوة Deploy أو صفحة Settings → Pages.

الرابط المتوقع بدون custom domain:
https://omaralabaseery.github.io/appcontrolspanel/

الريبو Private: GitHub Pages للريبو الخاص يحتاج خطة تدعم ذلك مثل GitHub Pro. لا تغيّر خصوصية الريبو تلقائيًا؛ لو الخطة لا تدعمه، استخدم استضافة أخرى أو خطة مناسبة. نسخة Pages قد تكون متاحة للجميع حتى لو الريبو Private.

## ما الذي يعمل؟

- صفحات الكنترول بانيل، بيانات تجريبية، تفاصيل Input.json وOutput.json.
- تجربة إعدادات الفيرجن والصيانة والإشعارات داخل المتصفح.
- التغييرات محفوظة محليًا لكل متصفح؛ الإشعارات محاكاة فقط.
- لا يوجد تسجيل دخول حقيقي أو اتصال Firebase في نسخة المراجعة.

النشر ينسخ فقط `src/main/resources/static` ويستبدل إعداد Firebase في النسخة المنشورة بإعداد Demo. لا يرفع كود السيرفر أو ملفات service account أو بيانات Firestore. إعداد النسخة المحلية التي تعمل مع Spring Boot لا يتغير.

GitHub Pages لا يستضيف Java Spring Boot. تجربة الربط الحقيقي تحتاج استضافة الباك إند بشكل مستقل.

لإنشاء نسخة المراجعة محليًا:

```bash
python3 scripts/build-review.py
python3 -m http.server 8080 --directory _site
```

افتح http://localhost:8080. لبدء التجربة من جديد امسح بيانات الموقع من المتصفح.
