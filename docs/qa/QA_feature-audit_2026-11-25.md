# QA: docs/feature-audit (FEATURE_AUDIT.md, FEATURE_MAP.md), 2026-11-25

Перевірено: `origin/docs/feature-audit`, HEAD `3d98e07`, проти `origin/main` = `b65a684`. Метод: лише читання коду й документів; Gradle, телефон, ADB, емулятор не використовувались. Текст автора не змінювався.

## Вердикт: **Not Verified** (документи; блокерів для коду немає)

Причина: хибне твердження про рекламу (банер є і в `ToolsActivity`) у трьох місцях, занижене описання розбіжності DATA_SAFETY (відповідь є в `DECISIONS.md:104`) і хибне «рішення не знайшов» щодо «Захистити паролем» (`DECISIONS.md:106`). Решта вибірки з 25 тверджень підтверджена. Після правок автора потрібен лише короткий повторний огляд цих місць.

## 3. Межі змін

- `git diff --name-only origin/main`: рівно 3 файли: `docs/FEATURE_AUDIT.md`, `docs/FEATURE_MAP.md`, `docs/project_log.md` (+333 рядки).
- Зміни в `app/`, `core-pdf/`, `core-ads/`, `AGENTS.md`, `CLAUDE.md`, `.agents/`, `docs/governance/`: 0 (порожній diff). Підтверджено.
- Рядків у карті 65 (8 «нема», решта є/частково): підраховано, збігається із заявою автора.

## 1. Перевірені твердження (25: підтверджено 19, не підтверджено 6)

| # | Твердження автора | Результат | Доказ |
|---|---|---|---|
| 1 | 11 `ToolType` | підтверджено | `ToolsAdapter.kt:12-24`: CREATE_BLANK ... COMPRESS, 11 значень |
| 2 | 3 групи: Створити / Сторінки / Оформити й оптимізувати | підтверджено | `ToolGroups.kt:7-23`, склад збігається |
| 3 | 5 мов, по 360 рядків | підтверджено | `grep -c '<string '`: values, uk, de, es, ru = 360 кожен; `locales_config.xml`: en, uk, de, ru, es |
| 4 | Вкладки «Файли» немає, лише 2 пункти | підтверджено | `menu_main_bottom.xml`: лише `nav_library`, `nav_tools` |
| 5 | У маніфесті лише MAIN і VIEW, SEND/EDIT немає; `INTERNET` єдиний власний дозвіл; `allowBackup=false` | підтверджено | `AndroidManifest.xml:4, 8, 27, 32-37` |
| 6 | Екрана «Налаштування» немає | підтверджено | 8 Activity в маніфесті (`:23-71`), жодної Settings |
| 7 | `StandardProtectionPolicy` лише для збереження наявного шифрування | підтверджено | `PdfIo.kt:12` (import), `:175 copyEncryption`, `:179` (`StandardProtectionPolicy(pw, pw, ...)`), `:190 keepEncryption`; інструмента шифрування в `ToolType` немає |
| 8 | Нічна тема є: DayNight + нічна тема читання + сепія | підтверджено | `values/themes.xml:3` (`Theme.Material3.DayNight`), є `values-night/themes.xml`; `ReadingTheme.kt:11-12` NIGHT, SEPIA; `ReaderActivity.kt:3182 showReadingThemeDialog` |
| 9 | Відкриття PDF із паролем у читалці/редагуванні (`ReaderActivity.kt:397-460`) | підтверджено | `:397 queryAccess`, `:415 obtainAccess`, `:434 askPassword`; `:2648` пароль копії |
| 10 | Блокування для зашифрованого/без прав: `PlaceMarkupLogic.kt:784-789` | підтверджено | `:784 enum Gate`, `:788-789 ENCRYPTED / NO_MARKUP_RIGHTS` |
| 11 | Інструменти пароль не передають, помилка → `error_invalid_password` | підтверджено (висновок із коду) | `ToolsViewModel.kt` без `password`; `ErrorMessages.kt:32, 61` |
| 12 | Interstitial після дії інструмента, кулдаун 5 хв | підтверджено | `ToolsActivity.kt:486`; `AdMobAdsManager.kt:168`; `:258 DEFAULT_COOLDOWN_MS = 5*60*1000L` |
| 13 | У читалці реклами немає | підтверджено | пошук `AdMobAdsManager`/`AdView`/`showActionInterstitial` у `ui/reader`: 0 збігів; `AdMobAdsManager` лише в `MainActivity` і `ToolsActivity` |
| 14 | **Банер лише в бібліотеці / «лише на головному екрані»** (AUDIT §7 P1, §9 №11; MAP P1 «Бібліотека») | **НЕ ПІДТВЕРДЖЕНО (хибно)** | `ToolsActivity.kt:147` `adsManager.bindAdaptiveBanner(this, binding.bannerContainer)`; `activity_tools.xml:444` `bannerContainer` («Banner Container at Bottom of ToolsActivity»); `docs/product/FEATURE_AUDIT_2026-11-23.md:84`: банер «у головному контейнері та `ToolsActivity`». Автор бачив `ToolsActivity.kt` у своєму grep, але приписав йому лише interstitial |
| 15 | Рішення Власника про OCR: `DECISIONS.md:105` | підтверджено | рядок 105 = «РІШЕННЯ ВЛАСНИКА: OCR і пошук у сканах — ПІСЛЯ релізу» |
| 16 | «Рішення Власника щодо "Захистити паролем": в DECISIONS.md не знайшов» (AUDIT §5.1) | **НЕ ПІДТВЕРДЖЕНО (занижено)** | `DECISIONS.md:106`: «РІШЕННЯ ВЛАСНИКА: інструмент "Захистити паролем" ... у беклозі, термін не визначено, чекає рішення Власника. Відкриття PDF із паролем уже працює» |
| 17 | Виділення/підкреслення/закреслення як анотація немає | підтверджено | пошук `Highlight`/`Underline`/`StrikeOut` у `core-pdf/src/main`: 0 збігів; `PlaceTool` (`PlaceMarkupLogic.kt:12`) = CHECK, CROSS, TEXT, INK, SIGNATURE, HAND, IMAGE |
| 18 | Сканер: `ScanDocumentActivity.kt:100 GmsDocumentScanning.getClient` | підтверджено | `:100` точно |
| 19 | Опорні рядки `ReaderActivity.kt` (157, 659, 1011, 1048, 1081, 1164, 2163, 2234, 2271, 2454, 2609, 2714, 3036, 3100-3144, 3182) | підтверджено | `setupViewerFragment:157`, `openSignatureDialog:659`, `enterPlaceMode:1011`, `showPlaceUi:1048`, `leavePlaceThen:1081`, `showTextInputDialog:1164`, `savePending:2163`, `saveIntoChosenCopy:2234`, `pickImageForReaderLauncher:2271`, `showMoveToPageDialog:2454`, `printCurrentDocument:2609`, `showReadOnlyOffer:2714`, `showCustomColorDialog:3036`, `action_print:3100`, `action_toggle_favorite:3113`, `action_share:3117`, `action_tools:3125`, `action_details:3129`, `action_organize_pages:3133`, `ACTION_SEND:3144`, `showReadingThemeDialog:3182` |
| 20 | Опорні рядки `MainActivity.kt` (208, 212, 380, 411, 429, 433, 448, 491) | підтверджено | `launchSafOpenDocument:208`, `createNewBlankDocumentAndOpen:212`, `onCreateOptionsMenu:380`, `isPrivacyOptionsRequired:411`, `action_filter_favorites:429`, `sort_by_date:433`, `action_clear_history:448`, `showClearHistoryConfirmationDialog:491`; VIEW/EDIT у `:188` |
| 21 | `ToolsActivity.kt:164` для `CreateDocument` | **НЕ ПІДТВЕРДЖЕНО (хибний рядок)** | `:82-83` реєстрація `CreateDocument("application/pdf")`, `:370` запуск; на `:164` початок `setupToolUi` |
| 22 | `PageOperationsEngine.kt:60-68` `password = null` за замовчуванням | **НЕ ПІДТВЕРДЖЕНО (хибний рядок)** | файл має 107 рядків; `password: String? = null` на `:20, 30, 39` |
| 23 | `MainActivity.kt:311` запуск сканера | **НЕ ПІДТВЕРДЖЕНО (зсув на 1)** | `:311` = `setSpeedDialExpanded`; `ScanDocumentActivity.start(this)` на `:312` |
| 24 | MAP R11: «див. AUDIT §3.2» | **НЕ ПІДТВЕРДЖЕНО (хибне посилання)** | У AUDIT підрозділу 3.2 немає; про пароль це §2.3 |
| 25 | `PdfIo.kt:51` (MAP R11) | підтверджено | `:51 fun loadDocument(context, uri, password)` |

## 2. Розбіжність DATA_SAFETY.md vs `play-services-oss-licenses`

Факти за кодом:
- `app/build.gradle.kts:6` і `build.gradle.kts:5` містять лише **плагін** `com.google.android.gms.oss-licenses-plugin` (0.10.10). Рядка `implementation("...play-services-oss-licenses...")` немає. `gradle/libs.versions.toml` у проєкті **відсутній** (у `gradle/` лише `wrapper`), версійного каталогу немає.
- Залежність **навмисно видалена** в D1: `DECISIONS.md:104` (2026-11-23) і `THIRD_PARTY.md:45` («видалено з runtime у D1 2026-11-23»). Тест `LicensesDialogHelperTest.kt:79` стверджує, що `build.gradle.kts` не містить `play-services-oss-licenses:`.
- `docs/store/DATA_SAFETY.md:14` досі називає `play-services-oss-licenses:17.1.0` як SDK, тобто DATA_SAFETY **застарів** (згадано бібліотеку, якої вже немає в APK).

Оцінка опису автора (AUDIT §6, §10): розбіжність помічена правильно, але подана як «де підключена залежність: НЕ ПЕРЕВІРЕНО». Це занижено: відповідь однозначна й лежить у `DECISIONS.md:104`, `THIRD_PARTY.md:45`, `LicensesDialogHelperTest.kt:79` (залежності немає; застарів `DATA_SAFETY.md:14`). Рекомендація (не виправляю): вказати причину й передати ролі «Монетизація і Google Play» оновити `DATA_SAFETY.md:14`. Чи вплине це на декларацію Play Console, я не оцінював: НЕ ПЕРЕВІРЕНО (імовірно ні: бібліотека лише показувала ліцензії).

## Дефекти за серйозністю

Критичних і високих немає.

**Середні**
- D-1. Хибно: «банер лише в бібліотеці / лише на головному екрані» (AUDIT §7 P1, §9 №11; MAP P1 «Екран: Бібліотека»). Насправді банер є й у `ToolsActivity` (`ToolsActivity.kt:147`, `activity_tools.xml:444`). Це важливо для реклами й Data safety. Висновок «у читалці реклами немає» вірний.
- D-2. Розбіжність DATA_SAFETY vs oss-licenses описана як «НЕ ПЕРЕВІРЕНО», хоча відповідь є в `DECISIONS.md:104`, `THIRD_PARTY.md:45`, `LicensesDialogHelperTest.kt:79`. Реальний дефект `DATA_SAFETY.md:14` (застарілий) не названо.
- D-3. AUDIT §5.1 (і §10): «рішення Власника щодо "Захистити паролем" в DECISIONS.md не знайшов» помилково: є `DECISIONS.md:106` (беклог, термін не визначено).

**Низькі**
- D-4. Хибні рядки: `ToolsActivity.kt:164` (має бути `:82-83, 370`), `PageOperationsEngine.kt:60-68` (має бути `:20, 30, 39`), `MainActivity.kt:311` (має бути `:312`).
- D-5. MAP R11 посилається на «AUDIT §3.2», такого розділу немає (§2.3).
- D-6. Не згадано існуючий `docs/product/FEATURE_AUDIT_2026-11-23.md` (схожа назва; ризик плутанини). Наскільки вони суперечать одне одному, не перевірено (читав лише рядок 84).
- D-7. Шапка AUDIT каже «ID збігаються з FEATURE_MAP», а нотатка MAP «L13-L16 в AUDIT = S3, P1, S1/P3, S6 тут» означає, що не збігаються; це плутає.

Назва продукту скрізь «Anti PDF» (пошук «Volum» у двох файлах: збігів, окрім імен пакетів/тем, немає). Зайвих функцій «є», де їх нема, не знайдено; занижень «нема/не знайшов» там, де є, знайдено 1 (D-3), завищень «лише» 1 (D-1).

## Що НЕ ПЕРЕВІРЕНО мною

- Усе поведінкове на пристрої (заборонено), TalkBack, Android 9-11.
- Вміст тестів і їхнє покриття; повний перелік тестових файлів з MAP/AUDIT не звіряв із файловою системою.
- Решту ~40 номерів рядків і прив'язок: `ReaderActivity.kt:2991-3012, 3079-3092, 2534, 2492, 2568-2599, 280, 317, 382`, `RecentsAdapter.kt:55-110`, `HomeLogic`, `ReaderChromeLogic`, `EditAccess.kt`, `ToolsActivity.kt:107-119, 278, 388-413`, `LicensesDialogHelper.kt:39-74` тощо.
- Заяви про шторку (15 дій `ReaderToolAction`), «Видалити з історії», типи полів форм, значення `CompressionLevel`, 9 дозволів склеєного маніфесту.
- Цитати з `DECISIONS.md:87, 97`, `CURRENT_STATE.md`, `OWNER_INBOX.md`, `COMPETITIVE_AND_INNOVATION` §5.2.
- Розділ 9 (карта ChatGPT): перевірено лише відповідність фактам коду, самої карти я не бачив.

---

# Коло 2 (повторний огляд), SHA автора `d92ee40`

Метод: `git merge origin/docs/feature-audit` у `qa/feature-audit` (без force); перегляд `git diff 3d98e07 d92ee40`; перевірка кожного виправлення читанням коду.

## Вердикт кола 2: **QA Verified** (з одним дрібним зауваженням D-8)

## Межі змін
- `git diff --name-only origin/main origin/docs/feature-audit`: лише `docs/FEATURE_AUDIT.md`, `docs/FEATURE_MAP.md`, `docs/project_log.md`.
- Diff для `app/`, `core-pdf/`, `core-ads/`, `AGENTS.md`, `CLAUDE.md`, `.agents/`, `docs/governance/`: порожній. Код і governance не чіпались.

## Перевірка виправлень D-1…D-7

| Дефект | Статус | Доказ |
|---|---|---|
| D-1 банер лише в бібліотеці | виправлено, вірно | AUDIT P1 і §9 №11, MAP P1 тепер: головний екран + `ToolsActivity.kt:147`, `activity_tools.xml:444`. Перечитано: `ToolsActivity.kt:147 bindAdaptiveBanner`, `activity_tools.xml:444 bannerContainer`, у `ui/reader` реклами немає |
| D-2 oss-licenses | виправлено, вірно | AUDIT §6 і §10: залежність видалена в D1, `DECISIONS.md:104`, `THIRD_PARTY.md:45`, `LicensesDialogHelperTest.kt:79`; плагін лишився `app/build.gradle.kts:6`; застарів `DATA_SAFETY.md:14`. Усе звірено в колі 1 |
| D-3 «Захистити паролем» | виправлено, вірно | AUDIT §5.1 цитує `DECISIONS.md:106`; рядок 106 існує зі змістом «беклог, термін не визначено» |
| D-4 рядки | виправлено, вірно | `ToolsActivity.kt:82-83`/`:370` (CreateDocument), `PageOperationsEngine.kt:20, 30, 39`, `MainActivity.kt:312` (AUDIT L10 і MAP L10), усе збігається з кодом |
| D-5 «AUDIT §3.2» | виправлено | MAP R11 тепер «AUDIT §2.3»; розділ існує |
| D-6 дубль-документ | виправлено | у шапках AUDIT і MAP є попередження про `docs/product/FEATURE_AUDIT_2026-11-23.md`; узгодженість двох чесно позначена НЕ ПЕРЕВІРЕНО |
| D-7 ID | виправлено | шапки AUDIT і MAP тепер явно перелічують винятки (L13-L16 ↔ S3, P1, S1/P3, S6; R13, E14) |

## 5 нових випадкових тверджень (не перевірялись у колі 1)

| # | Твердження | Результат | Доказ |
|---|---|---|---|
| 26 | Стиснення: LOW 1800/0.85, MEDIUM 1400/0.70, HIGH 1000/0.50 | підтверджено | `CompressionLevel.kt:7-9` |
| 27 | Сортування лише 3 варіанти | підтверджено | `SortOrder.kt`: DATE_DESC, NAME_ASC, SIZE_DESC |
| 28 | Шторка читалки має 15 дій | підтверджено | `ReaderToolsBottomSheetDialogFragment.kt:16-32`: 15 значень `ReaderToolAction` |
| 29 | Скан копіюється в `filesDir/scanned/` (`persistScannedPdf`) | підтверджено | `DocumentScannerLogic.kt:39-43`, `ScanDocumentActivity.kt:159` |
| 30 | Поля форм: `FormField.Text/CheckBox/Choice` (`FormField.kt:12, 21, 30`) | підтверджено | рядки точні |
| 31 | `error_select_min_two_files` на `ToolsActivity.kt:278` (інструмент «Об'єднати») | підтверджено частково | рядок насправді `:280` (зсув на 2) |

Підсумок кола 2: з 6 нових вибіркових тверджень 5 підтверджено, 1 з неточним рядком.

## Нові дефекти
- D-8 (низький). `ToolsActivity.kt:278` у AUDIT §4 (T5) і, ймовірно, поряд: фактичний рядок `:280`. Не блокує.

## Що НЕ ПЕРЕВІРЕНО (кола 2)
Те саме, що в колі 1 (пристрій, TalkBack, Android 9-11, вміст тестів, більшість рядків-посилань, склеєний маніфест, розділ 9 без самої карти ChatGPT). Рядки `ToolsActivity.kt:388-413` (повернення/нумерація) окремо не звіряв.
