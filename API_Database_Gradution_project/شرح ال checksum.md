# شرح نظام الـ Checksum والاستعادة في مشروع API_Database_Gradution_project

وثيقة تقنية توضح آلية حساب الـ Checksum، تخزين الكتب في الأرشيف، الحذف المنطقي (Soft Delete)، وآلية الاستعادة باستخدام Restore Token.

المشروع: API_Database_Gradution_project

## 1) مقدمة

تعتمد المنظومة على بصمات رقمية (Hashes) لضمان سلامة الملفات وتقليل التكرار وربط النسخ المؤرشفة بإمكانية الاستعادة. يتم استخدام SHA-256 لتوليد بصمتين مختلفتين: بصمة الملف (File Checksum) وبصمة المحتوى (Content Hash).

## 2) أهداف الوثيقة

- توضيح الفرق بين File Checksum و Content Hash ولماذا نحتاجهما معاً.
- تحديد أماكن حساب الـ hashes داخل المشروع (ملفات/دوال).
- شرح دور الجداول الأساسية في قاعدة البيانات وعلاقتها بسير العمل.
- توثيق مسار رفع الكتاب وحذفه واستعادته، مع نقاط الفشل المحتملة.

## 3) المصطلحات والتعريفات

- **Checksum/Hash**: قيمة ثابتة تمثل محتوى (ملف/نص) بشكل شبه فريد.
- **File Checksum**: SHA-256 على بايتات الملف الخام (binary). يُستخدم كمفتاح أرشفة واستعادة.
- **Content Hash**: SHA-256 على النص المستخرج بعد التنظيف/التطبيع. يُستخدم لتحديد تشابه المحتوى.
- **Canonical Path**: المسار القياسي الذي تُحفظ فيه النسخة الأساسية من الكتاب وفق checksum.
- **Archive**: مخزن مركزي للكتب يعتمد على `fileChecksum` بحيث يمكن مشاركته بين عدة مستخدمين.
- **Soft Delete**: حذف منطقي عبر تعيين `deletedAt` بدلاً من حذف السجل فعلياً.

## 4) أنواع الـ Hashes في المشروع ولماذا

### 4.1) File Checksum (SHA-256 على بايتات الملف)

يتم حسابه على ملف الكتاب كما هو (PDF/EPUB/DOCX/MOBI). أي تغيير ولو بسيط في الملف ينتج checksum مختلف. لهذا السبب يُستخدم كهوية نهائية للملف عند الأرشفة والاستعادة.

### 4.2) Content Hash (SHA-256 على النص بعد التطبيع)

يتم حسابه على النص المستخرج بعد التنظيف/التطبيع، خصوصاً لمعالجة اختلافات الكتابة العربية (مثل التشكيل وتطبيع بعض الحروف). الهدف هو اكتشاف تكرار المحتوى حتى لو اختلف الملف أو ترتيبه.

## 5) مكان حساب الـ Checksum داخل الكود

الملف المسؤول: `app/utils/text_utils.py`

### 5.1) دوال File checksum

```python
def compute_file_hash_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def compute_file_hash_stream(stream: BinaryIO, chunk_size: int = 1024 * 1024) -> str:
    h = hashlib.sha256()
    while True:
        chunk = stream.read(chunk_size)
        if not chunk:
            break
        h.update(chunk)
    return h.hexdigest()
```

ملاحظة: استخدام النسخة `stream` مفيد للملفات الكبيرة لتجنب تحميل الملف كله في الذاكرة.

### 5.2) دوال Content hash (مع التطبيع)

```python
def compute_text_hash(text: str, version: str = "v2") -> str:
    if version == "v1":
        normalized = _normalized_text_for_hash_v1(text)
    else:
        normalized = _normalized_text_for_hash_v2(text)
    if not normalized:
        normalized = text.strip()
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()

def compute_text_hashes(text: str) -> list[str]:
    v2 = compute_text_hash(text, version="v2")
    v1 = compute_text_hash(text, version="v1")
    if v2 == v1:
        return [v2]
    return [v2, v1]
```

الفكرة: دعم أكثر من إصدار تطبيع (v1/v2) لتقليل الأخطاء الناتجة عن اختلاف أساليب التنظيف عبر الزمن، مع الحفاظ على توافق خلفي عند وجود بيانات قديمة.

## 6) مخطط قاعدة البيانات (الجداول ذات العلاقة)

الملف: `app/models.py`

### 6.1) جدول PdfBook

يمثل نسخة كتاب تخص مستخدماً محدداً. أهم الحقول: `userId`، `fileChecksum`، `checksum` (contentHash)، `deletedAt` (للحذف المنطقي)، `audioId`.

### 6.2) جدول BookArchive

يمثل الأرشيف المركزي للملف وفق `fileChecksum`. يتيح مشاركة نسخة واحدة من الملف (والصوت إن توفر) بين عدة مستخدمين. `archivedAt` يحدد إن كانت النسخة مؤرشفة (منقولة لمسار الأرشيف).

### 6.3) جدول AudioBook

يمثل ملفات الصوت الناتجة. يمكن ربطه بكتب متعددة عبر `audioId`.

### 6.4) جدول BookRestoreLog

يسجل عمليات الاستعادة: من استعاد ماذا ومتى، مع `deviceId` و `ipAddress` إن توفرت.

## 7) سير العمل الكامل (Upload → Delete → Restore)

### 7.1) رفع كتاب (POST /books)

الملف: `app/routes.py`

الخطوات المنطقية:

1) قراءة الملف وتحويله إلى bytes.  
2) حساب `fileChecksum` على البايتات.  
3) البحث عن سجل أرشيف سابق لنفس `fileChecksum`.  
4) إن كانت النسخة موجودة ومؤرشفة: إرجاع `restore_required` مع `restore_token`.  
5) إن لم تكن مؤرشفة: حفظ الملف في المسار القياسي، استخراج النص، ثم إنشاء `PdfBook` للمستخدم وتحديث/إنشاء `BookArchive`.

### 7.2) استخراج النص حسب نوع الملف

يتم استخراج النص حسب الامتداد مع فحوصات سلامة بسيطة (مثل signature للـ PDF). ثم يتم تنظيف النص (`clean_extracted_text`) قبل حساب `content hash`.

### 7.3) Restore Token (تأكيد الاستعادة)

يتم توليد Token موقّع بـ HMAC باستخدام `RESTORE_TOKEN_SECRET` ويحتوي: `file_checksum` و `device_id` و `exp` (انتهاء صلاحية). الهدف منع تزوير طلبات الاستعادة وتقييد التوكن بجهاز محدد وزمن محدد.

### 7.4) الحذف (Soft Delete) والأرشفة

الحذف لا يمسح الملف مباشرة. يتم تعيين `deletedAt` لنسخة المستخدم. إذا لم يعد هناك أي كتاب نشط يحمل نفس `fileChecksum`، تُوضع `archivedAt` في `BookArchive` ويتم نقل الملفات إلى مسار الأرشيف مع التحقق من سلامة checksum قبل النقل.

### 7.5) تأكيد الاستعادة (POST /books/restore/confirm)

عند التأكيد:

1) التحقق من التوكن (التوقيع + انتهاء الصلاحية).  
2) التأكد أن `device_id` داخل التوكن يطابق جهاز الطلب.  
3) التحقق من سلامة الملف المؤرشف بمقارنة `fileChecksum` قبل الإرجاع للمسار القياسي.  
4) إنشاء `PdfBook` جديد للمستخدم مع ربطه بنفس `audioId` إن كان موجوداً.  
5) تسجيل العملية في `BookRestoreLog`.  

### 7.6) سجل الاستعادات (GET /books/restore/logs)

يرجع قائمة بعناصر الاستعادة المرتبطة بالمستخدم (حسب `device_id`) مع معلومات موجزة: وقت الاستعادة، `file_checksum`، صيغة الملف، العنوان، توفر الصوت.

## 8) منطق CRUD المرتبط بالأرشفة والاستعادة

الملف: `app/crud.py`

### 8.1) create_book

ينشئ `PdfBook` للمستخدم أو يعيد الموجود إذا كان هناك كتاب بنفس `content hash` (مع دعم v1/v2). كما يقوم بتثبيت الحقول (`fileChecksum`/`fileFormat`/`filePath`) إن كانت ناقصة.

### 8.2) upsert_archive

ينشئ أو يحدث `BookArchive` وفق `fileChecksum`. الهدف توحيد نسخة الملف في مكان واحد وربطها بالصوت إن توفر.

### 8.3) mark_archive_archived_if_no_active_books

يجعل الأرشيف في حالة `archivedAt` فقط إذا لم يوجد أي `PdfBook` نشط يحمل نفس `fileChecksum`.

### 8.4) create_book_from_archive

ينشئ `PdfBook` جديد لمستخدم معين من بيانات `BookArchive` مع إعادة استخدام الصوت (`audioId`) إن كان موجوداً، ويتجنب إنشاء نسخة مكررة إذا كانت موجودة مسبقاً.

### 8.5) log_restore + get_restore_logs_by_user

تسجيل الاستعادات واسترجاعها بهدف التدقيق (Audit) وتتبع العمليات.

## 9) نقاط تحقق وجودة (Integrity & Edge Cases)

- التحقق من توقيع PDF (%PDF) لتقليل أخطاء قراءة ملفات ليست PDF.
- فشل استخراج النص يسبب HTTP 400 (ملف تالف/غير صالح).
- في الاستعادة: إذا checksum للملف المؤرشف لا يطابق المطلوب يتم إرجاع 409 (checksum mismatch).
- إذا `device_id` لا يطابق التوكن يتم إرجاع 403 (Forbidden).

## 10) خاتمة

يضمن هذا التصميم تقليل التكرار، وإتاحة الاستعادة الآمنة نسبياً للكتب المحذوفة من خلال ربط الأرشيف ببصمة الملف، مع فصل واضح بين هوية الملف (`fileChecksum`) وهوية المحتوى (`content hash`).
