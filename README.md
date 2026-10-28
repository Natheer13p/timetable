# 📦 ملفات الرفع إلى GitHub

هذه الملفات هي التي تُرفع إلى **جذر المستودع** `Natheer13p/timetable` (فرع `main`).

## 1) ارفع هذه الملفات إلى جذر المستودع

```
index.html
manifest.webmanifest
sw.js
icon-192.png
icon-512.png
apple-touch-icon.png
school.xml
school-version.json
version.json
apk/Timetable.apk
```

> الطريقة الأسهل: افتح المستودع على GitHub → **Add file → Upload files** → اسحب الملفات → Commit.
> أنشئ مجلد `apk` أولاً ثم ارفع داخله `Timetable.apk`.

## 2) فعّل صفحة الويب (GitHub Pages)

المستودع حالياً **بدون Pages**. فعّلها مرة واحدة:

`Settings → Pages → Source: Deploy from a branch → Branch: main → Folder: / (root) → Save`

بعد دقيقة يصبح الرابط:

**https://natheer13p.github.io/timetable/**

وهذا هو الرابط الذي يفتحه أصحاب الآيفون ويضيفونه للشاشة الرئيسية.

## 3) التحديث التلقائي (يعمل تلقائياً بعد الرفع)

التطبيق يقرأ هذه الملفات من رابط ثابت مدمج في الكود:

```
https://raw.githubusercontent.com/Natheer13p/timetable/main/version.json
https://raw.githubusercontent.com/Natheer13p/timetable/main/school-version.json
https://raw.githubusercontent.com/Natheer13p/timetable/main/school.xml
```

### عند إصدار نسخة جديدة من التطبيق
1. ارفع `versionCode` في `app/build.gradle` (مثلاً 5).
2. ابنِ نسخة release.
3. استبدل `apk/Timetable.apk` بالملف الجديد.
4. عدّل `version.json`:
   ```json
   { "versionCode": 5, "versionName": "3.2", "url": "https://github.com/Natheer13p/timetable/raw/main/apk/Timetable.apk", "notes": "..." }
   ```
5. Commit → كل مستخدم يفتح التطبيق يظهر له: **«يتوفر تحديث — اضغط للتحديث»**.

### عند تغيّر جدول المدرسة
1. استبدل `school.xml` بملف aSc الجديد.
2. ارفع رقم `version` في `school-version.json` (1 → 2 …).
3. Commit → البيانات تتحدث تلقائياً عند فتح التطبيق، **ويبقى اسم كل مدرّس مثبتاً** (المطابقة بالاسم).

## 4) إشعارات الآيفون (OneSignal)

1. أنشئ تطبيقاً مجانياً في https://onesignal.com (نوع: Web).
2. انسخ **App ID** والصقه في `index.html`:
   ```js
   var ONESIGNAL_APP_ID = "ضع-المعرّف-هنا";
   ```
3. أضف نطاق الموقع في إعدادات OneSignal: `natheer13p.github.io`.
4. على الآيفون (iOS 16.4+): افتح الرابط بـ **Safari** → مشاركة → **إضافة إلى الشاشة الرئيسية** → افتح التطبيق → اسمح بالإشعارات.

⚠️ حدود آبل: الإشعار يصل والتطبيق مسكّر، لكن **النطق الصوتي لا يعمل** والتطبيق مسكّر (قيد من iOS). النطق يعمل والتطبيق مفتوح.

## 5) مفتاح التوقيع (مهم جداً)

- الملف: `keystore/release.jks`
- الكلمة: `ObaidiTimetable2026Key`
- البصمة SHA-256: `25:A8:BD:18:1C:E6:02:45:33:2B:46:07:8A:22:0F:64:E0:75:EC:CE:77:11:1A:1E:1E:D8:04:35:1D:78:BE:05`

**لا تُنشئ مفتاحاً جديداً ولا تفقد هذا الملف** — أي تغيير يعني أن التحديثات لن تُثبَّت فوق القديم.

> أول انتقال من نسخة debug (v3) إلى هذه النسخة الموقّعة يحتاج **حذف التطبيق القديم مرة واحدة** ثم تثبيت الجديد.

## 6) ملاحظة عن المستودع الحالي

الملفات الموجودة حالياً في المستودع (`app/` و `index.html` القديم 9KB) هي **نسخة قديمة**. لتحديث الكود المصدري لاحقاً استبدل مجلد `app/` بمجلد المشروع الجديد (بدون مجلد `keystore`).
