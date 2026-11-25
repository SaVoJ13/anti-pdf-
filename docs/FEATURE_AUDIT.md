# FEATURE_AUDIT: аудит функцій Anti PDF (Фаза 0)

> Автор: Агент (Feature Audit), гілка `docs/feature-audit`. Основа: `origin/main` = `b65a684` (2026-11-25). Версія в коді: `1.0.1` / код `2` (`app/build.gradle.kts:32-33`), minSdk 28, targetSdk 36.
> Метод: лише читання коду й ресурсів. Нічого не запускалось: ні Gradle, ні телефон, ні емулятор. Тому «працює» тут означає «реалізовано й підключено в коді», а не «перевірено на пристрої». Усе, що я не зміг підтвердити читанням, позначено **НЕ ПЕРЕВІРЕНО**.
> Скорочення: `A` = `app/src/main/java/com/savoj/volumpdf/`, `C` = `core-pdf/src/main/java/com/savoj/volumpdf/core/pdf/`, `AT` / `CT` = тести `app` / `core-pdf` (шляхи в `docs/FEATURE_MAP.md`). ID функцій збігаються з `FEATURE_MAP.md`.
> Статус: готово до QA (самоверифікації немає).

## Простими словами
- У карті функцій 65 рядків: 57 реалізовано (повністю або частково), 8 відсутні (§5).
- Усе, що видно в меню «⋮» читалки й бібліотеки, є в коді й має рядки у 5 мовах (uk/en/de/es/ru, по 360 рядків; паритет ключів контролює тест).
- Окремого екрана «Налаштування» немає: параметри розкидані по меню «⋮».
- Вкладки «Файли» немає. У бібліотеці один список «Останні»; «Обране», пошук і сортування працюють як фільтри цього списку.
- «Захистити паролем» (нове шифрування) і OCR відсутні. OCR за рішенням Власника лише після релізу.

---

## 0. Загальні факти

| Тема | Факт | Джерело |
|---|---|---|
| Екрани | `MainActivity` (бібліотека + інструменти), `ReaderActivity`, `ToolsActivity`, `PageOrganizerActivity`, `ScanDocumentActivity`, `OnboardingActivity`, `LibraryLicensesActivity`, `LibraryLicenseDetailActivity` | `app/src/main/AndroidManifest.xml:22-72` |
| Експортований вхід | Лише `MainActivity`: `MAIN/LAUNCHER` і `VIEW` для `application/pdf`, схеми `content` і `file`. `SEND` та `EDIT` у маніфесті немає | `AndroidManifest.xml:22-39` |
| Власні дозволи | Лише `INTERNET` (реклама). Файли тільки через SAF (`OpenDocument`, `CreateDocument`); `READ/WRITE_EXTERNAL_STORAGE` немає | `AndroidManifest.xml:4` |
| Камера | Сканування через Google Play Services Document Scanner; власного `CAMERA` у маніфесті немає | `app/build.gradle.kts:115`, `A ui/scanner/DocumentScannerLogic.kt:23-32` |
| Склеєний маніфест | `CURRENT_STATE.md` (запис 2026-11-24) згадує 9 дозволів у збірці `r1qa` (результат злиття SDK). Перелік цих 9 я не перевіряв: **НЕ ПЕРЕВІРЕНО** | `docs/governance/CURRENT_STATE.md` |
| Мови | `locales_config.xml`: en, uk, de, ru, es. У кожному з 5 файлів `values*/strings.xml` по 360 рядків; тест вимагає однакових ключів, непорожніх значень, однакових плейсхолдерів | `app/src/main/res/xml/locales_config.xml`, `AT ui/StringsParityTest.kt` |
| Зберігання | Room: «Останні документи» (`RecentDocument`: uri, назва, сторінки, розмір, час відкриття, `lastPageRead`, обране). Закладок, нотаток, бібліотеки підписів у БД немає | `A data/db/RecentDocument.java`, `AppDatabase.java` |
| Резервні копії | `allowBackup="false"` | `AndroidManifest.xml:8` |
| Рушії | Перегляд: `androidx.pdf:pdf-viewer-fragment:1.0.0-beta01` (`@ExperimentalPdfApi` лише в `PdfViewerBridge.kt`); операції й розмітка: PdfBox-Android 2.0.27.0; сканер: ML Kit Document Scanner 16.0.0-beta1 | `docs/governance/PROJECT_PROFILE.md` §5; `app/build.gradle.kts:115` |

---

## 1. Бібліотека (`MainActivity`)

Структура: `ViewPager2` із двох сторінок (Бібліотека, Інструменти) і нижня навігація з двох пунктів (`MainActivity.kt:249-293`, `res/menu/menu_main_bottom.xml`). Дії toolbar бібліотеки видно лише на вкладці 0 (`A ui/main/HomeLogic.kt:toolbarState`, `MainActivity.kt:403-418`).

| ID | Функція | Де в інтерфейсі | Працює (за кодом) | Обмеження | Тести | Мови | Дозволи |
|---|---|---|---|---|---|---|---|
| L1 | Список «Останні»: мініатюра, назва, «Стор. X з N», розмір | Вкладка «Бібліотека» | Так: `RecentsFragment.kt`, `RecentsAdapter.kt:55-78`, `util/PdfThumbnailLoader.kt` | Поведінка для зашифрованих, бітих файлів і відкликаних URI: **НЕ ПЕРЕВІРЕНО** | `RecentDocumentTest`, `DocumentRepositoryTest` | 5/5 | SAF (persistable URI, `util/SafUtils.kt`) |
| L2 | Пошук за назвою | Лупа в toolbar (`menu_main.xml`, `always|collapseActionView`) | Так: `MainActivity.kt:380-400`. Дефект D2-1 (згортання пошуку) виправлено й закрито на пристрої (`CURRENT_STATE.md`, запис 2026-11-24 «фінал») | Лише за назвою; за вмістом PDF пошуку немає | `HomeOnboardingTest` (логіка); UI-пошук автотестом **НЕ ПЕРЕВІРЕНО** | 5/5 | Немає |
| L3 | Фільтр «Обране» | Toolbar → «Фільтр і сортування» (`action_filter_favorites`) | Так: `MainActivity.kt:429`, `MainViewModel.toggleFavoritesFilter` | Це фільтр списку, не вкладка | `DocumentRepositoryTest` | 5/5 | Немає |
| L4 | Сортування: дата, назва, розмір | Той самий пункт, радіо-група | Так: `MainActivity.kt:433-446`, `A data/model/SortOrder.kt` | Лише 3 варіанти | `DocumentRepositoryTest` | 5/5 | Немає |
| L5 | Обране (зірка) і дії картки: Поділитися, Відомості, Видалити з історії | Контекстне меню картки (`menu_document_item.xml`, `RecentsAdapter.kt:110`) | Пункти є в меню; обране також у «⋮» читалки (`ReaderActivity.kt:3113`) | Що саме робить «Видалити з історії» і чи не чіпає файл: **НЕ ПЕРЕВІРЕНО** читанням `RecentsAdapter` | `RecentDocumentTest` (обране) | 5/5 | Немає |
| L6 | «Очистити історію» з чекбоксом «залишити обране» | Toolbar «⋮» | Так: `MainActivity.kt:448, 491-494`; пункт неактивний, коли історії немає (`:410`) | Файли не видаляє (за назвою діалогу; код видалення не читав: **НЕ ПЕРЕВІРЕНО**) | `HomeOnboardingTest` | 5/5 | Немає |
| L7 | Порожні стани: порожньо / нічого не знайдено / немає обраного | Бібліотека | Так: `HomeLogic.kt:emptyState`, `emptyStateButtons` | — | `HomeOnboardingTest` | 5/5 | Немає |
| L8 | «Відкрити PDF» | FAB | Так: `MainActivity.kt:208` (`OpenDocument`, `application/pdf`) | Лише PDF | `SafUtilsTest` | 5/5 | SAF |
| L9 | «Створити PDF» (порожній) | FAB-розкриття | Так: `MainActivity.kt:212-221`, `A util/DocumentCreationHelper.kt` | Місце збереження: **НЕ ПЕРЕВІРЕНО** | Окремого тесту не знайшов: **НЕ ПЕРЕВІРЕНО** | 5/5 | Немає |
| L10 | «Сканувати» | FAB-розкриття | Так: `MainActivity.kt:311`, `ScanDocumentActivity.start` | Потрібні Google Play Services (§2.4) | `DocumentScannerLogicTest` | 5/5 | Через Play Services |
| L11 | Вкладки Бібліотека/Інструменти | Нижня навігація | Так | Лише 2 вкладки | `HomeOnboardingTest` | 5/5 | Немає |
| L12 | Вкладка «Файли» | — | **Нема** | — | — | — | — |
| L13 | Динамічні кольори (Material You) | Toolbar «⋮» (лише Android 12+) | Так: `MainActivity.kt:419-423, 462-468` (`recreate()` після перемикання) | На Android 9–11 пункт сховано | `AppearancePreferencesIsolatedProcessTest`, `ThemesDialogTest`, `AppearanceContrastTest` | 5/5 | Немає |
| L14 | Банер реклами | Низ бібліотеки (`bannerContainer`) | Так: `MainActivity.kt:161-167` після згоди UMP | Залежить від UMP (§7) | `AdMobAdsManagerTest` | n/a | `INTERNET` |
| L15 | Політика приватності, «Налаштування приватності» (UMP), «Ліцензії» | Toolbar «⋮» | Так: `MainActivity.kt:452-476`; «Налаштування приватності» видно лише коли UMP цього вимагає (`:411`) | URL з `BuildConfig.PRIVACY_POLICY_URL` | `PrivacyMenuTest`, `LicensesDialogHelperTest` | 5/5 | `INTERNET` |
| L16 | Онбординг (3 екрани, пропуск, лише 1-й запуск) | Перший запуск | Так: `A ui/onboarding/*`, `MainActivity.kt:102-108`; при відкритті з VIEW-інтенту не показується | — | `HomeOnboardingTest` | 5/5 | Немає |

## 2. Читалка (`ReaderActivity`)

### 2.1 Меню «⋮» (R1 злито в `main` PR #14, device gate R1–R11 ок, R12 TalkBack пропущено)

Видимість: `ReaderChromeLogic.menuState` (`A ui/reader/ReaderChromeLogic.kt`) і `onPrepareOptionsMenu` (`ReaderActivity.kt:2991-3012`). Якщо файл не відкрився (`openFailed`), усе приховано.

| Пункт | ID | Умова | Код дії | Тест |
|---|---|---|---|---|
| Обране / Прибрати | L5 | Відкритий документ | `ReaderActivity.kt:3113` | `RecentDocumentTest` |
| Поділитися | R7 | Так | `:3117, 3144` (`ACTION_SEND`, `application/pdf`, `FLAG_GRANT_READ_URI_PERMISSION`) | **НЕ ПЕРЕВІРЕНО** |
| Друкувати | R6 | Так, також у режимі «Редагувати» | `:3100, 2609`, `A util/PdfPrintDocumentAdapter.kt` | `ReaderChromeLogicTest` (видимість) |
| Зберегти копію | R8 | Так, також у режимі «Редагувати» | `:3096, 567, 2633` | `ReaderChromeLogicTest` |
| Сторінки й файл (шторка) | R10 | Так | `:3125, 2568` | **НЕ ПЕРЕВІРЕНО** |
| Упорядкувати сторінки | T4 | Так | `:3133` → `PageOrganizerActivity.start` | `PageOrganizerLogicTest` |
| Відомості | R9 | Так | `:3129, 3152` | **НЕ ПЕРЕВІРЕНО** |
| Тема читання | R5 | Завжди | `:3121, 3182` | `ReadingThemeLogicTest` |
| Пошук | R2 | Лише в режимі «Редагувати» (у читанні лупа в пігулці) | `:3109, 280` | `TextSearchUiLogicTest` |
| Заповнити форму | R12 | Лише коли є поля форми (`formFieldCount > 0`) | `:3092, 382` | `FormEditingTest`, `FormFieldsUiLogicTest` |
| Зберегти / Готово / «+» | E1, E9 | Лише в режимі «Редагувати» | `:3079-3091` | `PlaceBarLogicTest`, `ReaderChromeLogicTest` |

### 2.2 Функції читання

| ID | Функція | Де в інтерфейсі | Працює | Обмеження | Тести | Мови | Дозволи |
|---|---|---|---|---|---|---|---|
| R1 | Перегляд PDF (прокрутка, зум, вибір тексту) | Основна область, `androidx.pdf` | Так: `ReaderActivity.kt:157 setupViewerFragment`, `PdfViewerBridge.kt` | Бібліотека в бета-версії (`1.0.0-beta01`). Вибір/копіювання тексту реалізує сама бібліотека: **НЕ ПЕРЕВІРЕНО** | `ViewportRestorePlannerTest`, `ConfigRecreateLogicTest`, `PendingLoadGuardTest` | 5/5 | SAF |
| R2 | Пошук тексту | Лупа в пігулці сторінки (`btnHudSearch`); панель із `androidx.pdf` | Так: `ReaderActivity.kt:268-290`, `PdfViewerBridge.kt:54` (`isTextSearchActive`) | Лише текстовий шар; у сканах без тексту нічого не знайде (OCR нема, §5.2). Мова тексту самої панелі пошуку: **НЕ ПЕРЕВІРЕНО** | `TextSearchUiLogicTest` | 5/5 (наші рядки) | Немає |
| R3 | Перехід на сторінку | Тап по «N / M» | `ReaderActivity.kt:317`; не діє, якщо ≤1 сторінки (`:318`) | — | **НЕ ПЕРЕВІРЕНО** | 5/5 | Немає |
| R4 | Відновлення останньої сторінки | Автоматично | `ReaderActivity.kt:213-222`, `ReadingProgressLogic.targetRestorePage` | `lastPageRead` у БД | `ReadingProgressLogicTest` | 5/5 | Немає |
| R5 | Тема читання: День, Ніч, Сепія | «⋮» → «Тема читання» (діалог із вибором) | Так: `ReaderActivity.kt:3182-3213`; колірний фільтр шару переглядача (`ReadingThemeLogic.createPaint`), вибір у `ReadingPreferences` | Змінює вигляд шару перегляду, не вміст PDF; поведінка на зображеннях: **НЕ ПЕРЕВІРЕНО** | `ReadingThemeLogicTest` | 5/5 | Немає |
| R6 | Друк | «⋮» і шторка | Так: `ReaderActivity.kt:2609` (`PrintManager` + `PdfPrintDocumentAdapter`) | Помилка показується як `err_print_failed` | **НЕ ПЕРЕВІРЕНО** | 5/5 | Немає |
| R7 | Поділитися | «⋮», шторка, меню картки | Так: `ReaderActivity.kt:3144` | — | **НЕ ПЕРЕВІРЕНО** | 5/5 | Немає |
| R8 | Зберегти копію | «⋮» | Так: `ReaderActivity.kt:567, 2633-2712`. З R1: за незбережених змін діалог «Зберегти зміни / Копіювати як є / Скасувати» | Для зашифрованого документа копія зберігається з тим самим паролем (`:2648`, підказка `place_copy_saved_password_hint`) | `ReaderChromeLogicTest` | 5/5 | SAF `CreateDocument` |
| R9 | Відомості: назва, сторінок, розмір, остання сторінка, дата, URI | «⋮» | Так: `ReaderActivity.kt:3152-3179` | Показує сирий URI | **НЕ ПЕРЕВІРЕНО** | 5/5 | Немає |
| R10 | Шторка «Сторінки й файл» | «⋮» | Так: `ReaderToolsBottomSheetDialogFragment.kt`, 15 дій (`ReaderToolAction`): додати сторінку, додати зображення, вставити файл, впорядкувати, повернути, видалити, розділити, стиснути, водяний знак, нумерація, об'єднати, сканувати, друк, поділитися, відомості | Частина дій відкриває `ToolsActivity` (`ReaderActivity.kt:2576-2599`) | **НЕ ПЕРЕВІРЕНО** | 5/5 | SAF |
| R11 | Пароль на вхід | Діалог пароля | Частково, див. §2.3 | | `EditAccessTest`; `CT large/LargeEncryptedTest` | 5/5 | Немає |
| R12 | Заповнення форм AcroForm | «⋮» → «Заповнити форму» | Так: `ReaderActivity.kt:382`, `FillFormBottomSheetDialogFragment`, `FormFieldsAdapter`, `FormEditing.kt`; рушій `C PdfBoxFormEngine`; поля `FormField.Text/CheckBox/Choice` (`C model/FormField.kt:12, 21, 30`) | Інші типи полів (радіо, підписні): **НЕ ПЕРЕВІРЕНО** | `FormEditingTest`, `FormFieldsUiLogicTest`; `CT PdfBoxFormEngineTest`, `PdfBoxFormMultilineTest` | 5/5 | Немає |
| R13 | Пігулка сторінки/зуму і FAB «Редагувати» | Низ читалки | Так: `ReaderActivity.kt:291-315 applyReaderChromeVisibility`, `updateReaderHud` | Ховаються під час пошуку й у режимі «Редагувати» | `ReaderChromeLogicTest` | 5/5 | Немає |

### 2.3 Пароль на вхід (відкриття PDF із паролем): що саме є
1. **Перегляд зашифрованого PDF** виконує `androidx.pdf`. У `PdfViewerBridge.kt` обробки пароля немає (`grep`). Як бібліотека питає пароль і чи локалізує діалог: **НЕ ПЕРЕВІРЕНО**.
2. **Режим «Редагувати» і форми для зашифрованого документа.** Власний діалог пароля: `ReaderActivity.kt:397-460` (`obtainAccess`, `askPassword`), макет `DialogDocumentPasswordBinding`, рядки `password_title`, `password_wrong`, `password_continue`. Пароль живе лише у `ReaderViewModel.documentPassword`, на диск не пишеться (коментар `:433`). Стан: `EditAccessModel.resolve` (`A ui/reader/EditAccess.kt`). Права автора: `PlaceMarkupLogic.kt:784-789` (`Gate.ENCRYPTED`, `NO_MARKUP_RIGHTS`).
3. **Збереження** зберігає шифрування: `PdfIo.copyEncryption` (`C PdfIo.kt:175-182`) і `keepEncryption` (`:190`); ключ 40/128/256, AES для ≥128.
4. **Інструменти** (`ToolsActivity`) для зашифрованих PDF: `ToolsViewModel` не передає пароль (`password = null` за замовчуванням у `PageOperationsEngine.kt:60-68`; виклики `ToolsViewModel.kt:99-118`). Помилка пароля зіставляється з `ErrorMessages.Kind.PASSWORD` → `error_invalid_password` (`A util/ErrorMessages.kt:32, 61`). Тобто інструменти не питають пароль, а показують помилку (висновок із коду, не перевірено на пристрої). Для інструментів «відкриття із паролем» неповне.

Тести: `EditAccessTest`, `PendingLoadGuardTest`; `CT large/LargeEncryptedTest`, `PdfIoErrorMappingTest`.

### 2.4 Сканер (бібліотека, інструменти, читалка)
- `ScanDocumentActivity.kt:100` `GmsDocumentScanning.getClient(options)`; `DocumentScannerLogic.buildScannerOptions`: імпорт із галереї дозволено, до 100 сторінок, формати PDF і JPEG, режим FULL.
- Результат копіюється у внутрішнє сховище `filesDir/scanned/Scan_<дата>.pdf` (`persistScannedPdf`).
- Обмеження: потрібні Google Play Services і завантажуваний модуль сканера; поведінка без них: **НЕ ПЕРЕВІРЕНО**. ML Kit передає дані Google (`docs/store/DATA_SAFETY.md`).
- Тест: `DocumentScannerLogicTest`.

## 3. Редагування (режим «Редагувати»)

Вхід: FAB `fabReaderEdit`, `ReaderActivity.kt:1011 enterPlaceMode`, `1048 showPlaceUi`; вихід «Готово» `leavePlaceThen :1081`. Нижня панель: `btnSlotText`, `btnSlotPen`, `btnSlotSelect`, `btnSlotAction`, `btnSlotDelete`, `btnSlotOptions`, `btnPlaceUndo`, `btnPlaceRedo` (`res/layout/activity_reader.xml`). Інструменти: `PlaceTool { CHECK, CROSS, TEXT, INK, SIGNATURE, HAND, IMAGE }` (`PlaceMarkupLogic.kt:12`).

| ID | Функція | Працює | Обмеження | Тести |
|---|---|---|---|---|
| E1 | Режим «Редагувати», вихід, діалог «Незбережені зміни» | Так: `ReaderActivity.kt:1081-1160` | Зміни тримаються в пам'яті/`PendingStore` до «Зберегти» (рішення 2026-11-17, `DECISIONS.md:87`) | `PlaceBarLogicTest`, `PlaceEditorTest`, `PendingStoreTest` |
| E2 | Текстовий напис: введення, розмір, колір (палітра + власний RGB `showCustomColorDialog :3036`), фон, поворот, довгі тексти на кілька сторінок | Так: `showTextInputDialog :1164`, `showTextDialog :1318` | Лише символи, які підтримує шрифт (`TextVerdict.Unsupported`); шрифт Roboto Regular (`THIRD_PARTY.md`); інші писемності: **НЕ ПЕРЕВІРЕНО** | `PlaceMarkupLogicTest`, `PlaceTextHardBreakTest`, `PlaceTextPaginationTest`; `CT PdfBoxMarkupEngineTest` |
| E3 | ✓ і ✗ (перемикання виду) | Так: `ReaderActivity.kt:974` | — | `PlaceMarkupLogicTest` |
| E4 | Малювання: ручка, маркер, хайлайтер | Так: `InkPreset` (`PlaceMarkupLogic.kt:22`), `MarkupOverlayView` | Хайлайтер це напівпрозорий штрих, **не** виділення тексту PDF | `InkSizingTest`, `InkSignatureEditorTest` |
| E5 | Підпис: малювання в шторці й розміщення | Так: `openSignatureDialog :659`, `SignatureBottomSheetDialogFragment`, `SignaturePadView` | Зображення підпису, не цифровий підпис; збереженої бібліотеки підписів у коді не знайдено (шторка лише повертає `Bitmap` через `onSignatureConfirmed`) | `InkSignatureEditorTest` |
| E6 | Додати зображення на сторінку | Так: `pickImageForReaderLauncher :2271`, «+» і шторка | Відкрите спостереження **O-1**: зображення/скани надто великі щодо порожньої сторінки; на Play-копії **НЕ ПЕРЕВІРЕНО** (`CURRENT_STATE.md`, аналіз у `project_log.md`) | `PlaceEditorTest`, `PageSpaceMathTest` |
| E7 | Виділення об'єкта: перемістити, розмір, поворот, «по розміру сторінки», «на іншу сторінку» (`showMoveToPageDialog :2454`), «Редагувати», видалити | Так: `btnSel*`, `btnSlot*` | — | `PlaceEditorTest`, `SelectionPillTest`, `PageSpaceMathTest` |
| E8 | Скасувати / повторити | Так: `btnPlaceUndo/Redo`, `PlaceEditor` | — | `PlaceEditorTest` |
| E9 | Зберегти: у файл або нову копію | Так: `savePending :2163`, `saveIntoChosenCopy :2234`, `showReadOnlyOffer :2714` | Якщо документ лише для читання, пропонується копія; повідомлення `SaveNotices` | `StructuralEditKeepsPendingTest`, `PendingStoreTest`, `Qa3fAppFinalTest`; `CT Qa3fPlaceFinalTest` |
| E10 | Блокування змін для зашифрованого документа або без прав автора | Так: `PlaceMarkupLogic.kt:784-789`, `EditAccess.kt` | — | `EditAccessTest`, `PendingLoadGuardTest` |
| E11 | Виділення/підкреслення/закреслення тексту як анотація | **Нема.** У `core-pdf` лише `FreeTextData` і власна розмітка; анотацій Highlight/Underline/StrikeOut не створюється (`grep`) | `docs/product/COMPETITIVE_AND_INNOVATION_2026-11-16.md` теж фіксує «у коді їх немає» | — |
| E12 | Нотатки-коментарі | **Нема** | — | — |
| E13 | Закладки | **Нема** (Room зберігає лише «Останні») | — | — |
| E14 | Режим «Рука» (прокрутка без постановки об'єктів) | Так: `PlaceTool.HAND`, `ReaderActivity.kt:1138, 1159` | — | `PlaceMarkupLogicTest` |

Мови E1–E14: рядки в усіх 5 `values*/strings.xml` (префікси `place_*`, `password_*`, `form_*`); окремо кожен рядок не перевіряв, є лише загальний тест паритету ключів. Дозволи: SAF.

## 4. Інструменти («Інструменти» і `ToolsActivity`)

`ToolType` (11): `CREATE_BLANK`, `SCAN_DOCUMENT`, `ORGANIZE_PAGES`, `MERGE`, `SPLIT`, `ROTATE`, `DELETE_PAGES`, `IMAGES_TO_PDF`, `WATERMARK`, `PAGE_NUMBERS`, `COMPRESS` (`A ui/tools/ToolsAdapter.kt:12-24`). Групи D3 (`A ui/tools/ToolGroups.kt:7-23`): **Створити** (порожній, Сканувати, Зображення в PDF), **Сторінки** (Упорядкувати, Об'єднати, Розділити, Повернути, Видалити), **Оформити й оптимізувати** (Водяний знак, Нумерація, Стиснення). Тест групування: `AT ui/tools/ToolGroupingLogicTest`.

Потік: вибір файлів → перевірка введення → `CreateDocument` (`ToolsActivity.kt:164`) → `ToolsViewModel.execute*` → `PageOperationsEngine` (PdfBox) → interstitial з кулдауном 5 хв і діалог результату (`ToolsActivity.kt:485-486`). Вихід завжди в новий файл за вибором користувача; захист від збігу вхідного й вихідного файлу: `CT PdfIoSameTargetTest` (висновок щодо UI: **НЕ ПЕРЕВІРЕНО**).

| ID | Інструмент | Налаштування | Обмеження | Тести |
|---|---|---|---|---|
| T1 | Створити порожній PDF | Без налаштувань; створює й відкриває (`ToolsActivity.kt:107-119`) | — | `CT PdfBoxPageOperationsEngineTest` |
| T2 | Сканувати | Запускає `ScanDocumentActivity` і закриває екран | Google Play Services | `DocumentScannerLogicTest` |
| T3 | Зображення → PDF | `OpenMultipleDocuments` (`image/jpeg`, `image/png`, `image/*`), список файлів (`SelectedFileAdapter`) | Завеликі зображення: див. O-1; великі файли: `CT large/LargePageOpsTest` | `CT PdfBoxPageOperationsEngineTest`, `LargePageOpsTest` |
| T4 | Упорядкувати сторінки | Окремий екран `PageOrganizerActivity`: переставлення, поворот, видалення, «Скинути» | Один файл | `PageOrganizerLogicTest`; `CT PdfBoxOrganizePagesTest` |
| T5 | Об'єднати | Мінімум 2 файли (`error_select_min_two_files`, `ToolsActivity.kt:278`) | — | `CT PdfBoxPageOperationsEngineTest` |
| T6 | Розділити (витягти сторінки) | Діапазон, наприклад «1-3,5» (`PageRangeParser`), обов'язковий | — | `AT PageRangeParserTest` |
| T7 | Повернути | 90/180/270 (`rbRotate*`, `:388-395`), діапазон необов'язковий | — | `CT PdfBoxPageOperationsEngineTest` |
| T8 | Видалити сторінки | Діапазон обов'язковий | Видалення всіх сторінок: **НЕ ПЕРЕВІРЕНО** | `CT PdfBoxPageOperationsEngineTest` |
| T9 | Водяний знак | Текст обов'язковий, діапазон необов'язковий | Лише текст, без зображень | `CT PdfBoxWatermarkAndPageNumbersTest` |
| T10 | Нумерація | Формат (`rgPageNumbersFormat`, `:408-413`), діапазон | — | `CT PdfBoxWatermarkAndPageNumbersTest` |
| T11 | Стиснення | LOW (1800 px, JPEG 0.85), MEDIUM (1400, 0.70), HIGH (1000, 0.50) (`C model/CompressionLevel.kt`) | Ефект на текстових PDF: **НЕ ПЕРЕВІРЕНО** | `CT PdfBoxCompressTest` |
| T12 | Додати сторінку/файл у відкритий документ (з читалки) | `ReaderActivity.kt:2534, 2492`; `C appendBlankPages`, `appendFiles` | — | `CT PdfBoxPageOpsMemoryAndSecurityTest` (зміст **НЕ ПЕРЕВІРЕНО**) |

Для всіх: мови 5/5 (`tool_*_title/_desc`, `filename_*`, `error_*`); дозволи SAF; реклама: interstitial (§7); пароль: §2.3 п. 4.

## 5. Нема, але очікують користувачі

### 5.1 «Захистити паролем» (нове шифрування PDF)
- **Стан: нема** (T13). У `ToolType` й `ToolGroups.kt` такого інструмента немає. `StandardProtectionPolicy` використовується лише для **збереження вже наявного** шифрування (`C PdfIo.kt:12, 175-182, 190`), а не для встановлення пароля користувачем.
- Відкриття PDF із паролем уже є (§2.3): діалог пароля, збереження копії з тим самим паролем (`ReaderActivity.kt:2648`).
- Відкриті питання: поведінка інструментів на зашифрованих файлах (§2.3 п. 4); чи потрібне «зняти пароль». Рішення Власника щодо «Захистити паролем»: в `DECISIONS.md` не знайшов, **НЕ ПЕРЕВІРЕНО**.

### 5.2 OCR і пошук у сканах
- **Стан: нема** (T14). Розпізнавання тексту в коді немає. Скан із ML Kit Document Scanner це картинки в PDF; пошук R2 їх не бачить (`OWNER_INBOX.md`, §17).
- **Рішення Власника (2026-11-24, `docs/governance/DECISIONS.md:105`): OCR і пошук у сканах лише ПІСЛЯ релізу (production).** Перед початком обов'язково: розмір застосунку, правило Google про 16 KB для `.so`, вплив на Data safety і `THIRD_PARTY.md`; ролі «PDF-ядро» й «Монетизація і Google Play», окремий QA.
- З документів (цитую, у цій фазі не перевіряв): `COMPETITIVE_AND_INNOVATION_2026-11-16.md` §5.2: ML Kit Text Recognition v2 має обмеження за писемностями (за записом, кирилиці серед скриптів немає; точне формулювання **НЕ ПЕРЕВІРЕНО**); Tesseract додає нативні бібліотеки (ризик для 16 KB); `androidx.pdf` не віддає текст сторінки через API, потрібен `PDFTextStripper` з PdfBox.
- Чотири перевірки Власника, усі **НЕ ПЕРЕВІРЕНО** (у цій фазі нічого не будується):
  1. Розмір APK/AAB: поточний не вимірювався.
  2. 16 KB page size: наявність і вирівнювання `.so`. Раніше AdMob/UMP піднято до 25.5.0/4.0.0 саме через 16 KB (`DECISIONS.md:97`); стан `.so` PdfBox/ML Kit Scanner: **НЕ ПЕРЕВІРЕНО**.
  3. Data safety: `docs/store/DATA_SAFETY.md` уже розкриває ML Kit Document Scanner; для OCR потрібне оновлення залежно від моделі (вбудована чи через Play Services).
  4. `docs/THIRD_PARTY.md`: новий рядок із ліцензією з першоджерела.

### 5.3 Інше відсутнє
| Функція | Підтвердження |
|---|---|
| Вкладка «Файли» | `menu_main_bottom.xml`: лише `nav_library`, `nav_tools` |
| «Поділитися в Anti PDF» (`SEND`) | `AndroidManifest.xml`: фільтри лише `MAIN`, `VIEW` |
| Екран «Налаштування» | Немає Activity; лише меню «⋮» (`menu_main.xml`) |
| Виділення/підкреслення тексту, нотатки, закладки | E11-E13 |
| Експорт сторінок у зображення, цифровий підпис, зняття пароля | Немає у `ToolType` і `ReaderToolAction` |
| Власний перемикач теми застосунку (Світла/Темна/Системна) | Тема `Theme.Material3.DayNight` (`res/values/themes.xml:3`) слідує за системою; у меню лише динамічні кольори |

## 6. Ліцензії, Third-party

| ID | Функція | Код | Працює | Обмеження | Тести |
|---|---|---|---|---|---|
| S2a | Діалог «Ліцензії» з повними текстами (Apache-2.0, SIL OFL 1.1 тощо) | `A ui/common/LicensesDialogHelper.kt:39-72`; тексти з `assets` у фоновому потоці | Так | Якщо читання не вдалось: `licenses_load_failed` | `LicensesDialogHelperTest` |
| S2b | Екран «Бібліотеки»: список і деталі | `LibraryLicensesActivity`, `LibraryLicenseDetailActivity`, `OssLicenseParser`; кнопка в діалозі (`LicensesDialogHelper.kt:74`) | Так. Дані з плагіна `com.google.android.gms.oss-licenses-plugin` (`app/build.gradle.kts:6`); D1 закрито на `d2qa` (debug) | Release-збірка екранів на пристрої: **НЕ ПЕРЕВІРЕНО** (`CURRENT_STATE.md`, D1 device gate) | `OssLicenseParserTest` |
| S2c | Реєстр залежностей | `docs/THIRD_PARTY.md` | Документ, не функція | Звірку з `build.gradle.kts` у цій фазі не повторював | — |

Розбіжність для QA: `docs/store/DATA_SAFETY.md` згадує `play-services-oss-licenses:17.1.0`, а в `app/build.gradle.kts` у блоці `dependencies` (рядки 99-117) такого `implementation` я не бачу, є лише плагін (`:6`). Де підключена залежність: **НЕ ПЕРЕВІРЕНО**.

## 7. Реклама (AdMob + UMP), теми, мови

| ID | Функція | Код | Працює | Обмеження | Тести |
|---|---|---|---|---|---|
| P1 | Адаптивний банер | `core-ads/.../AdMobAdsManager.kt`, `MainActivity.kt:161-167` | Так, лише на головному екрані. У читалці реклами немає: `ReaderActivity.kt` не звертається до `AdMobAdsManager` (`grep`: лише `MainActivity.kt` і `ToolsActivity.kt`) | Банер після завершення UMP | `AdMobAdsManagerTest`, `AdIdsConfigTest` |
| P2 | Interstitial після завершеної дії інструмента, кулдаун 5 хв | `AdMobAdsManager.kt:168 showActionInterstitial`; виклик `ToolsActivity.kt:485` | Так | Реальні ID лише в release (`admob.properties`); debug тестові (`VerifyAdmobManifestTask` у `app/build.gradle.kts`) | `AdMobAdsManagerTest` |
| P3 | Згода UMP, «Налаштування приватності» | `AdMobAdsManager.requestConsentAndInit`, `isPrivacyOptionsRequired`, `showPrivacyOptionsForm`; `MainActivity.kt:411, 456` | Так | Пункт видно лише коли UMP вимагає | `AdMobAdsManagerTest`, `PrivacyMenuTest` |
| S4 | Тема застосунку Світла/Темна за системою | `res/values/themes.xml:3`, `res/values-night/themes.xml` | Так | Власного перемикача нема | `AppearanceContrastTest` |
| S3 | Динамічні кольори (Android 12+) | L13 | Так | | `ThemesDialogTest` |
| S5 | Мови | `locales_config.xml`, `res/values-*` | Так (5/5, по 360 рядків) | Якість перекладу не оцінювалась | `StringsParityTest` |

Дозволи реклами: `INTERNET` у власному маніфесті, решту додають SDK при злитті (**НЕ ПЕРЕВІРЕНО**, §0). Data safety: `docs/store/DATA_SAFETY.md` (форму подано 2026-11-18, на розгляді Google за записом документа).

## 8. Відкриття з інших застосунків

| ID | Сценарій | Код | Працює | Обмеження |
|---|---|---|---|---|
| O1 | «Відкрити в Anti PDF» з менеджера файлів, пошти, браузера | `AndroidManifest.xml:31-38`, `MainActivity.kt:178-197` (`onNewIntent` і `handleIntent` для `ACTION_VIEW` та `ACTION_EDIT` з `data`; `onDocumentSelected` додає в «Останні» й відкриває `ReaderActivity`) | Так за кодом | `ACTION_EDIT` обробляється в коді, але фільтра `EDIT` у маніфесті немає, тож зовнішні програми його до нас не спрямують |
| O1b | Онбординг при відкритті з інтенту | `OnboardingLogic.shouldShow(..., isDocumentIntent)` | Так: з інтенту онбординг пропускається | Тест `HomeOnboardingTest` |
| O1c | Збереження прав на URI | `SafUtils.takePersistableUriPermissionSafe` (`ToolsActivity.kt:70`) | Частково: чи відкриється зовнішній `content://` з «Останніх» після перезапуску, **НЕ ПЕРЕВІРЕНО** | — |
| O1d | `file://` | Схема `file` є в маніфесті; на Android 7+ поведінка: **НЕ ПЕРЕВІРЕНО** | | |
| O2 | `SEND` / «Поділитися в Anti PDF» | Фільтра немає | Нема | |

---

## 9. Суперечності з ChatGPT-картою «Повна карта функціональності»

**Застереження.** Текст цієї карти мені не передали, у репозиторії я її не знайшов (`grep` по `docs/`: файлу з такою назвою немає). Тому нижче не порівняння рядок за рядком, а типові твердження таких карт і факт за кодом. Порівняння з реальним текстом карти: **НЕ ПЕРЕВІРЕНО**. Приклад від Власника («нічна тема вже є») підтверджено.

| # | Типове твердження карти | Факт за кодом | Висновок |
|---|---|---|---|
| 1 | «Нічної теми немає» | Є. (а) Темна тема застосунку за системою: `res/values-night/themes.xml`, `Theme.Material3.DayNight`. (б) Нічна тема читання: `ReadingTheme.NIGHT`, `ReaderActivity.kt:3182-3213`, `ReadingThemeLogic`. (в) Є ще «Сепія» | Карта хибна |
| 2 | «Відкриття PDF із паролем немає» | Є для читання/редагування: `ReaderActivity.kt:397-460`; шифрування зберігається при збереженні `PdfIo.kt:175-190`. Для інструментів неповно (§2.3 п. 4) | Карта хибна або неточна |
| 3 | «Захистити паролем (шифрування)» як наявне | Немає (§5.1) | Карта хибна, якщо вона каже «є» |
| 4 | «OCR / пошук у сканах» | Немає; за рішенням Власника лише після релізу (§5.2) | Підтверджує «нема» |
| 5 | Вкладка «Файли», окремий розділ «Обране» | Є 2 вкладки; «Обране» це фільтр у меню (§1) | Розбіжність |
| 6 | Виділення/підкреслення тексту, нотатки, закладки | Нема (E11-E13); є лише малювання хайлайтером як штриха | Розбіжність, якщо карта обіцяє |
| 7 | «Налаштування» як екран | Екрана немає, лише пункти меню «⋮» | Розбіжність |
| 8 | «Цифровий підпис» | Лише підпис-зображення від руки (E5), не криптографічний | Уточнити |
| 9 | «Редагування наявного тексту PDF» | Немає: застосунок додає нові об'єкти (текст, ✓/✗, штрих, підпис, зображення) і заповнює форми; наявний текст не змінює | Розбіжність, якщо карта обіцяє |
| 10 | «11 інструментів» | Збіг: 11 `ToolType`. У шторці читалки 15 дій (додатково сторінка, зображення, вставка файлу, друк, поширення, відомості) | Збіг |
| 11 | «Реклама при відкритті файлу» | Немає: банер лише в бібліотеці, interstitial лише після дії інструмента | Розбіжність, якщо карта каже інакше |
| 12 | «Мови uk/en/de/es/ru» | Збіг (5/5, по 360 рядків) | Збіг |
| 13 | «Хмара, облікові записи, синхронізація» | Немає; `allowBackup=false`; файли лише SAF | Розбіжність, якщо карта обіцяє |
| 14 | «Експорт у зображення/Word» | Інструмента немає | Розбіжність, якщо карта обіцяє |

## 10. Що НЕ ПЕРЕВІРЕНО (зведення)
- Усе поведінкове на пристрої (запуски заборонені); TalkBack; Android 9–11; планшет/широкі екрани.
- Діалог пароля самого `androidx.pdf` (чи питає, чи локалізований).
- Поведінка інструментів на зашифрованих PDF (за кодом: помилка, не запит пароля).
- Повнота покриття тестами: перелічено файли за назвою, вміст не читався.
- Склеєний маніфест (9 дозволів із SDK); де підключено `play-services-oss-licenses`.
- Розмір APK/AAB; `.so` і 16 KB; Data safety для OCR.
- Текст ChatGPT-карти «Повна карта функціональності» (розділ 9 без нього).
- «Видалити з історії», «Очистити історію» (що саме видаляється), місце створеного порожнього PDF, `file://`, відкликані URI в «Останніх».
- Release-екрани ліцензій на пристрої; O-1 на Play-копії.
