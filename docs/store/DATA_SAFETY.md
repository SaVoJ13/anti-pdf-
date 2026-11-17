# Google Play Data Safety: відповіді для Anti PDF (чернетка)

> Чернетка для Власника, форму в Play Console заповнює Власник. Нічого не подано. Звірено з кодом 2026-11-16. Список даних AdMob може змінитися: перед поданням звір з актуальною сторінкою Google "Google Mobile Ads SDK data disclosure" (https://developers.google.com/admob/android/privacy/play-data-disclosure) для версії SDK, що йде в релізі (зараз `play-services-ads:23.6.0`; на сторінці Google згадано 25.5.0, тобто можливі відмінності).

**Application:** Anti PDF
**Package:** `com.savoj.volumpdf.anti`
**Min / target SDK:** 28 / 36
**SDK з мережею:** Google AdMob (`com.google.android.gms:play-services-ads:23.6.0`), Google UMP (`com.google.android.ump:user-messaging-platform:3.1.0`).
**Інші SDK:** Google ML Kit Document Scanner (`play-services-mlkit-document-scanner:16.0.0-beta1`, через Google Play services), PdfBox-Android, androidx.pdf, Room (усе локально).
**Резервне копіювання:** `allowBackup="false"` (рішення Власника 2026-11-14); `dataExtractionRules` / `fullBackupContent` не задано, бо автокопіювання вимкнено.
**Дозволи у власному маніфесті:** лише `INTERNET`. Склеєний маніфест релізу перевірено, дивись `docs/project_log.md` (запис release-prep).

---

## 1. Overview

| Питання | Відповідь | Пояснення |
|---|---|---|
| Збирає або передає застосунок обов'язкові типи даних? | **Так** | Збір виконує рекламний SDK (AdMob). Власного збору даних застосунок не робить. |
| Усі дані шифруються під час передачі? | **Так** | Мережевий обмін SDK із серверами Google йде через HTTPS. |
| Чи можна запросити видалення даних? | **Так (через налаштування Android / Google)** | Користувач скидає/видаляє рекламний ID у налаштуваннях Android; локальні дані видаляються очищенням даних застосунку. Власних серверних даних користувача немає. Контакт: appvsdeveloper@gmail.com. |
| Чи є політика приватності (URL)? | **Так** | https://savoj13.github.io/anti-pdf-/privacy/ (GitHub Pages з `docs/privacy/`; прописано в `BuildConfig.PRIVACY_POLICY_URL`). Сторінка стане доступною після ввімкнення Pages Керівником. |
| Чи дотримано Families / дитячі правила? | Не застосовується | Цільова аудиторія: 13+ (13-15, 16-17, 18+), групи «до 13» не вибираються; застосунок не спрямований на дітей до 13. Реклама обмежена `maxAdContentRating = PG`. Аналіз і ризики: `docs/release/AGE_13PLUS_ANALYSIS_2026-11-17.md`. |

---

## 2. Типи даних

### 2.1 Файли й документи, фото
- **Не збираються і не передаються.** PDF, зображення та результати сканера обробляються локально. Сканер (Google ML Kit Document Scanner) працює на пристрої через Google Play services; застосунок не отримує доступу до мережі для скану й не завантажує скани.

### 2.2 Location
- **Approximate location:** збирається AdMob (з IP). Передається. Необов'язково (залежить від згоди UMP). Цілі: Advertising or marketing; Fraud prevention, security and compliance.

### 2.3 Device or other IDs
- **Device or other IDs** (рекламний ID, ідентифікатори пристрою/додатка SDK): збирається AdMob. Передається. Цілі: Advertising or marketing; Fraud prevention, security and compliance; Analytics.

### 2.4 App activity
- **App interactions** (покази й кліки реклами): збирає AdMob. Передається. Цілі: Advertising or marketing; Analytics; Fraud prevention, security and compliance.

### 2.5 App info and performance
- **Crash logs** і **Diagnostics** рекламного SDK: збирає AdMob. Передається. Цілі: Analytics; Fraud prevention, security and compliance.

### 2.6 Усе інше
Не збирається: Personal info (ім'я, email, телефон, адреса, User IDs), Financial info, Health and fitness, Messages, Photos and videos, Audio, Calendar, Contacts, Web browsing, Files and docs.

---

## 3. Обробка згоди
Google UMP показує форму в ЄЕЗ/Великій Британії/Швейцарії. Пункт меню «Налаштування конфіденційності» (UMP privacy options) показується, коли Google цього вимагає. Пункт «Політика приватності» відкриває URL політики.

## 4. Декларація Advertising ID (App content)
Реліз тягне дозвіл `com.google.android.gms.permission.AD_ID` через SDK реклами (підтвердження зі склеєного маніфесту в журналі). У Play Console: App content / Advertising ID: так, застосунок використовує рекламний ID для реклами.

## 5. Що вирішує Власник
- Остаточні відповіді у формі Data safety (після звірки з актуальною сторінкою Google).
- Підтвердити, що сторінка політики відкривається за URL після ввімкнення GitHub Pages; контактна адреса: appvsdeveloper@gmail.com (публічна, рішення 2026-11-17).
