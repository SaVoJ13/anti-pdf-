# Звірка декларацій Google Play з `main` (RC3), 2026-11-25

Роль: «Монетизація і Google Play» (автор). Звірено документи зі станом `main` `fb99f35`. Код і Play Console не змінювались. Статус: готово до QA (незалежної перевірки ще не було).

Склеєний маніфест зібрано командою `:app:processReleaseManifest` (JDK 21). Файл лежить у збірці (не в git): `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml` (далі «склеєний маніфест»).

## 1. Що змінилось у декларації

Нічого, що змінює відповіді у формі Data safety, політику приватності чи декларацію Advertising ID. Змінились лише тексти документів:
- `docs/store/DATA_SAFETY.md:14`: прибрано застарілу згадку `play-services-oss-licenses:17.1.0` (її видалено в D1). Тепер там описано власні екрани ліцензій.
- `docs/store/DATA_SAFETY.md:17`: додано підсумок склеєного маніфесту.
- `docs/store/DATA_SAFETY.md:67`: додано примітку про дозволи Privacy Sandbox від AdMob (з позначкою НЕ ПЕРЕВІРЕНО).
- `docs/THIRD_PARTY.md:23`: виправлено посилання на рядок `room-compiler` (було `:55`, стало `:112`).

## 2. Що треба Власнику в Play Console (лише перелік, я нічого не змінював)

1. Нових обов'язкових змін від D1, R1 і O-1 немає: залежності реклами й сканера не змінились, нових дозволів немає.
2. Раніше відомі відкриті пункти (не нові): чи змінено форму Data safety за списком із `DATA_SAFETY.md` розд. 5 (мета «Analytics» для Approximate location; відповідь про видалення даних «Ні»). Чи це вже зроблено в консолі, я не знаю: НЕ ПЕРЕВІРЕНО.
3. Рішення Власника: чи потрібні окремі відповіді в Play щодо `<queries>` з `com.android.vending` і `HsdpShimActivity` (приходять з `play-services-ads-api`/`hsdp`) та дозволів `ACCESS_ADSERVICES_*`. Я не знаю, що саме питає консоль: НЕ ПЕРЕВІРЕНО.
4. Перед релізом з бойовими AdMob ID (зараз у закритому тесті тестові) знову звірити Data safety і Advertising ID.

## 3. Підтвердження по пунктах

### 3.1 Залежності (збіг із `THIRD_PARTY.md`)
| Пункт | Факт у коді | Документи |
|---|---|---|
| AdMob 25.5.0, UMP 4.0.0 | `core-ads/build.gradle.kts:53-54` | збігається (`DATA_SAFETY.md:6,12`, `THIRD_PARTY.md`) |
| androidx.pdf 1.0.0-beta01, PdfBox-Android 2.0.27.0 | `core-pdf/build.gradle.kts:48-49` | збігається |
| Room 2.6.1 (runtime, ktx, compiler через `annotationProcessor`) | `app/build.gradle.kts:110-112` | збігається; рядок у `THIRD_PARTY.md` виправлено |
| ML Kit Document Scanner 16.0.0-beta1 | `app/build.gradle.kts:115` | збігається |
| Плагін `oss-licenses-plugin` 0.10.10 (лише збірка) | `build.gradle.kts:5`, `app/build.gradle.kts:6` | збігається |
| `play-services-oss-licenses` відсутня | тест `LicensesDialogHelperTest.kt:79`; у `app/build.gradle.kts` такої залежності немає | `DATA_SAFETY.md:14` виправлено |
| Власні екрани ліцензій | `LibraryLicensesActivity.kt:44,46` читає `R.raw.third_party_license_metadata` і `R.raw.third_party_licenses`; парсер `OssLicenseParser.kt`; обидві Activity в `app/src/main/AndroidManifest.xml` з `exported=false` | відповідає виправленому тексту |
| Аналітика / крашлог | У `app/build.gradle.kts`, `core-*/build.gradle.kts`, кореневому `build.gradle.kts` немає Firebase, Crashlytics, Analytics (пошук) | політика «власної аналітики немає» вірна. У склеєному маніфесті є транзитивні компоненти Google (`play-services-measurement-*`, firebase) від SDK Google; їх покриває рядок про транзитивні Google-бібліотеки в `THIRD_PARTY.md` |

### 3.2 Дозволи (склеєний маніфест)
Усього 9 `uses-permission`: `INTERNET` (рядок 11, власний; `app/src/main/AndroidManifest.xml:4`), `ACCESS_NETWORK_STATE` (51), `com.google.android.gms.permission.AD_ID` (52), `ACCESS_ADSERVICES_AD_ID` (53), `ACCESS_ADSERVICES_ATTRIBUTION` (54), `ACCESS_ADSERVICES_TOPICS` (55), `WAKE_LOCK` (56), `FOREGROUND_SERVICE` (57, від `androidx.work:work-runtime`), власний підписний `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (63).
- Заява «у власному маніфесті лише `INTERNET`» (`DATA_SAFETY.md:16`, `PRIVACY_POLICY.md` розд. 4) вірна.
- Заява про `AD_ID` (`DATA_SAFETY.md` розд. 4) вірна. Число 9 збігається з `CURRENT_STATE.md` (запис 2026-11-18: «9 → 9»).
- Дозволів на камеру і сховище немає: сканер працює через Google Play services, файли через системний вибір.

### 3.3 `uses-feature` портрет
`app/src/main/AndroidManifest.xml:7-9`: `android.hardware.screen.portrait`, `required="false"`; у склеєному маніфесті збережено (рядок 14). На Data safety і політику не впливає; це лише сумісність пристроїв у Play. Примітка: Activity сканера ML Kit у склеєному маніфесті має `screenOrientation=portrait` (рядки 185-187); вона належить SDK.

### 3.4 Збір даних
- Збирають лише Google AdMob і ML Kit (діагностика). Власних серверів, акаунтів, аналітики немає: підтверджено залежностями (3.1).
- Локальні дані: таблиця Room `recent_documents` з полями `uri`, `title`, `pageCount`, `fileSizeBytes`, `lastOpenedTimestamp`, `lastPageRead`, `isFavorite` (`app/src/main/java/com/savoj/volumpdf/data/db/RecentDocument.java:8-19`); збігається з переліком у `PRIVACY_POLICY.md` розд. 2. Каталоги `documents`, `scanned`, `pending` у `filesDir` існують (`DocumentCreationHelper.kt:35`, `DocumentScannerLogic.kt:59`, `ReaderViewModel.kt:207`).
- Реклама: банер у `MainActivity.kt:165` і `ToolsActivity.kt:147`; повноекранна після дії в `ToolsActivity.kt:487`; інтервал 5 хв (`AdMobAdsManager.kt:258`). Відповідає політиці. У `ReaderActivity` виклику реклами не знайдено.
- Вік: `maxAdContentRating = PG` (`AdMobAdsManager.kt:267`), child-directed не ставиться (`:274`); збігається з документами.
- `allowBackup="false"`: `app/src/main/AndroidManifest.xml:13`; збігається.

### 3.5 16 KB
За `docs/release/ELF_16KB_CHECK_2026-11-18.md:6`: у release AAB і APK немає `.so`. Нових нативних залежностей після того в `build.gradle.kts` не додано (залежності вище збігаються з тими, що були). Повторну перевірку AAB на `fb99f35` не робив: НЕ ПЕРЕВІРЕНО (потрібна перед відправкою нової збірки). Перед OCR це обов'язково (рішення Власника 2026-11-24).

### 3.6 Нічна тема та мови
- Нічна тема: каталог `app/src/main/res/values-night`; у `STORE_LISTING.md:38,66` згадано нічну тему; суперечності немає.
- Мови: `locales_config.xml` (en, uk, de, ru, es; підключено в `AndroidManifest.xml:17`); каталоги `values-uk`, `values-de`, `values-es`, `values-ru` існують; опис у `STORE_LISTING.md` є для en/uk/de/ru/es. Збігається.

## 4. Сумнівне, не змінено
- `STORE_LISTING.md:37-38`: «Text selection ... (Android 12+)» і «highlights». У `FEATURE_AUDIT.md:118` зазначено, що хайлайтер це напівпрозорий штрих, а не виділення тексту PDF; виділення тексту дає системний переглядач `androidx.pdf`. Це вже в `STORE_LISTING.md` розд. 4 як рішення Власника; формулювання не міняв. НЕ ПЕРЕВІРЕНО на пристрої.
- Сторінка політики на GitHub Pages (`docs/privacy/index.html`, «Last updated 2026-11-18»): не змінювалась, відповідає `PRIVACY_POLICY.md`; чи відкривається вона зараз за URL, я не перевіряв.

## 5. НЕ ПЕРЕВІРЕНО
Усе в Play Console (поточні відповіді форм); повторна збірка AAB і 16 KB на `fb99f35`; потреба декларацій для `<queries>`, `HsdpShimActivity`, `ACCESS_ADSERVICES_*`; поведінка на пристрої; незалежний QA.
