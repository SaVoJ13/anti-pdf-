# Google Play Data Safety: відповіді для Anti PDF (чернетка)

> Чернетка для Власника, форму в Play Console заповнює Власник. Форму подано в Play Console 2026-11-18 (на розгляді Google). Відповідь про видалення даних у поданій формі поки «Так»; її буде змінено на «Ні» разом з RC2, після завершення поточної перевірки Google (зміну в консолі робить координатор з Власником; цей документ уже містить цільову відповідь «Ні»). Звірено з кодом 2026-11-16; звірку зі сторінками Google повторено **2026-11-18**.
>
> **Версії SDK і дата звірки (2026-11-18):**
> - AdMob: `play-services-ads:25.5.0` (з `core-ads/build.gradle.kts`, оновлено з 23.6.0 на гілці `fix/stage1-ads-16kb`). Сторінка Google https://developers.google.com/admob/android/privacy/play-data-disclosure описує саме Google Mobile Ads SDK **25.5.0** («latest»); отримано 2026-11-18; у підвалі сторінки зазначено «Last updated 2026-11-17 UTC.» (підтверджено незалежним QA у сирому HTML). **Повторну звірку для 25.5.0 виконано 2026-11-18** (розд. 5). Сторінка охоплює лише поточну версію, тож порівняння «23.6.0 проти 25.5.0» зі сторінки неможливе (НЕ ПЕРЕВІРЕНО); перевірено 25.5.0 проти цього документа.
> - ML Kit Document Scanner: `play-services-mlkit-document-scanner:16.0.0-beta1` (з `app/build.gradle.kts`). Сторінка Google https://developers.google.com/ml-kit/android-data-disclosure (редакція 14 травня 2025) охоплює лише останні версії SDK і Document Scanner окремо не згадує; віднесення сканера до функцій, що працюють через Google Play services (там ідентифікатори: пристрою та встановлення), є висновком, не прямим текстом сторінки.
> - Google UMP: `user-messaging-platform:4.0.0` (власних даних для Data safety не додає; окремої сторінки розкриття даних для UMP при звірці не знайдено: НЕ ПЕРЕВІРЕНО).

**Application:** Anti PDF
**Package:** `com.savoj.volumpdf.anti`
**Min / target SDK:** 28 / 36
**SDK з мережею:** Google AdMob (`com.google.android.gms:play-services-ads:25.5.0`), Google UMP (`com.google.android.ump:user-messaging-platform:4.0.0`), Google ML Kit Document Scanner (`com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1`, працює через Google Play services; збирає технічну діагностику; застосунок скани не передає).
**Інші SDK:** PdfBox-Android, androidx.pdf, Room (усе локально); `com.google.android.gms:play-services-oss-licenses:17.1.0` (лише показ ліцензій на екрані, мережевого збору даних не додає).
**Резервне копіювання:** `allowBackup="false"` (рішення Власника 2026-11-14); `dataExtractionRules` / `fullBackupContent` не задано, бо автокопіювання вимкнено.
**Дозволи у власному маніфесті:** лише `INTERNET`. Склеєний маніфест релізу перевірено, дивись `docs/project_log.md` (запис release-prep).

---

## 1. Overview

| Питання | Відповідь | Пояснення |
|---|---|---|
| Збирає або передає застосунок обов'язкові типи даних? | **Так** | Збір виконують два компоненти Google: рекламний SDK (AdMob) і ML Kit (сканер, технічна діагностика). Власного збору даних застосунок не робить. |
| Усі дані шифруються під час передачі? | **Так** | Мережевий обмін AdMob і ML Kit із серверами Google шифрується (TLS/HTTPS). |
| Чи можна запросити видалення даних? | **Ні** | За правилами Google (https://support.google.com/googleplay/android-developer/answer/10787469) відповідь «Так» дозволена, лише якщо розробник дає спосіб запросити видалення зібраних даних або сам видаляє чи анонімізує їх протягом 90 днів. Власних даних користувача (серверів, акаунтів) застосунок не збирає. Дані, які збирають AdMob і ML Kit, зберігає Google; розробник не може їх видалити чи анонімізувати, і способу подати такий запит через застосунок немає. Скидання рекламного ID не видаляє вже зібраних даних. Контакт: appvsdeveloper@gmail.com. |
|  | довідка | Це не відповідь на питання форми, а довідка для користувача: рекламний ID можна скинути або видалити в налаштуваннях Android; локальні дані застосунку (список «Останні», налаштування) видаляються очищенням даних застосунку в налаштуваннях Android. |
| Чи є політика приватності (URL)? | **Так** | https://savoj13.github.io/anti-pdf-/privacy/ (GitHub Pages з `docs/privacy/`; прописано в `BuildConfig.PRIVACY_POLICY_URL`). Сторінка працює з 2026-11-18. |
| Чи дотримано Families / дитячі правила? | Не застосовується | Цільова аудиторія: 13+ (13-15, 16-17, 18+), групи «до 13» не вибираються; застосунок не спрямований на дітей до 13. Реклама обмежена `maxAdContentRating = PG`. Аналіз і ризики: `docs/release/AGE_13PLUS_ANALYSIS_2026-11-17.md`. |

---

## 2. Типи даних

### 2.1 Файли й документи, фото
- **Не збираються і не передаються.** PDF, зображення та результати сканера обробляються локально. Сканер (Google ML Kit Document Scanner) працює через Google Play services. Застосунок не завантажує скани й не передає їх на власні сервери. Прямого підтвердження «обробка лише на пристрої» від Google немає: НЕ ПЕРЕВІРЕНО. Технічну діагностику сканера збирає ML Kit (дивись 2.3 і 2.5); самі скани й документи не надсилаються.

### 2.2 Location
- **Approximate location:** збирається AdMob (з IP). Передається. **Обов'язково (Required)**. Цілі: Advertising or marketing; Analytics; Fraud prevention, security and compliance. (Analytics додано 2026-11-18: сторінка Google для 25.5.0 називає цілі «advertising, analytics, fraud prevention» для всієї групи типів, включно з IP-адресою.)

### 2.3 Device or other IDs
- **Device or other IDs** (рекламний ID, app set ID, ідентифікатори пристрою/додатка SDK): збирається AdMob. Передається. **Обов'язково (Required)**. Цілі: Advertising or marketing; Fraud prevention, security and compliance; Analytics.
- **Device or other IDs** (ідентифікатор пристрою та ідентифікатори встановлення): збирає ML Kit (сканер). Збирається, **третім особам не передається**. **Обов'язково (Required)**. Шифрується під час передачі (HTTPS). Ціль: Analytics. Джерело: https://developers.google.com/ml-kit/android-data-disclosure

### 2.4 App activity
- **App interactions** (покази й кліки реклами): збирає AdMob. Передається. **Обов'язково (Required)**. Цілі: Advertising or marketing; Analytics; Fraud prevention, security and compliance.

### 2.5 App info and performance
- **Diagnostics** рекламного SDK (продуктивність і діагностика SDK): збирає AdMob. Передається. **Обов'язково (Required)**. Цілі: Analytics; Fraud prevention, security and compliance. Окремо **Crash logs не збираються** (відповідає вже заповненому в Play Console: лише Diagnostics).
- **Diagnostics** і **Other app performance data** (затримка, розмір вхідних/вихідних даних, версія функції, тип події, коди помилок, налаштування API, інформація про пристрій і застосунок): збирає ML Kit (сканер). Збирається, **третім особам не передається**. **Обов'язково (Required)**. Шифрується під час передачі (HTTPS). Ціль: Analytics. Джерело: https://developers.google.com/ml-kit/android-data-disclosure

### 2.6 Принцип «Обов'язково»
У Data safety «Optional» означає, що КОЖЕН користувач може відмовитися від збору. Форма UMP показується лише в ЄЕЗ, Великій Британії та Швейцарії; в інших регіонах збір автоматичний, а ML Kit відмовитися не дає. Тому всі типи даних AdMob і ML Kit позначені як Required.

### 2.7 Усе інше
Не збирається: Personal info (ім'я, email, телефон, адреса, User IDs), Financial info, Health and fitness, Messages, Photos and videos, Audio, Calendar, Contacts, Web browsing, Files and docs.

---

## 3. Обробка згоди
Google UMP показує форму в ЄЕЗ/Великій Британії/Швейцарії. Пункт меню «Налаштування конфіденційності» (UMP privacy options) показується, коли Google цього вимагає. Пункт «Політика приватності» відкриває URL політики.

## 4. Декларація Advertising ID (App content)
Реліз тягне дозвіл `com.google.android.gms.permission.AD_ID` через SDK реклами (підтвердження зі склеєного маніфесту в журналі). У Play Console: App content / Advertising ID: так, застосунок використовує рекламний ID для реклами.

## 5. Повторна звірка після оновлення SDK
- **Виконано 2026-11-18 для AdMob 25.5.0** (джерело: https://developers.google.com/admob/android/privacy/play-data-disclosure, отримано 2026-11-18). Сторінка перелічує: IP-адреса (може слугувати для оцінки загального місцезнаходження), взаємодія з продуктом (запуск, тапи, перегляди відео), діагностика (час запуску, hang rate, енергоспоживання), ідентифікатори пристрою й облікових записів (рекламний ID Android, app set ID, за наявності інші ідентифікатори входу на пристрої). Цілі названо для групи загалом: реклама, аналітика, запобігання шахрайству. Шифрування: TLS. Рекламний ID: збір необов'язковий, розробник може заборонити через маніфест.
- **Збіг із документом:** типи (approx location з IP; Device or other IDs; App interactions; Diagnostics), Required, шифрування у дорозі, «Crash logs не збираються»: збігаються.
- **Розбіжність 1 (виправлено в документі):** Approximate location мала цілі Advertising + Fraud prevention; сторінка називає ще Analytics. Додано Analytics (п. 2.2).
- **Розбіжність 2 (рішення координатора, не змінено):** сторінка не розрізняє «collected» і «shared»; заголовок говорить «collects and shares». Позначення «Передається» (shared) для AdMob лишено як було.
- **Що змінити у формі Play Console (робить координатор із власником; форму я не чіпав):** у типі «Approximate location» додати мету «Analytics» до вже вибраних «Advertising or marketing» і «Fraud prevention, security and compliance». Інших змін типів/цілей для AdMob 25.5.0 не виявлено.
- **НЕ ПЕРЕВІРЕНО:** різниця між 23.6.0 і 25.5.0 (сторінка для старої версії недоступна); чи вимагають правила Google «Analytics» для approx location окремо від тексту групи.

## 6. Що вирішує Власник
- Остаточні відповіді у формі Data safety (після звірки з актуальною сторінкою Google).
- Підтвердити, що сторінка політики відкривається за URL (сторінка працює з 2026-11-18); контактна адреса: appvsdeveloper@gmail.com (публічна, рішення 2026-11-17).
