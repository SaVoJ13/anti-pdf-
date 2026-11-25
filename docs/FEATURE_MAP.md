# FEATURE_MAP: функція → екран → код → статус → тест

> Anti PDF. Знімок за `origin/main` = `b65a684` (2026-11-25). Лише читання коду; на пристрої нічого не запускалось. Деталі, мови, дозволи й обмеження: `docs/FEATURE_AUDIT.md` (ID збігаються).
> Статус: **є** / **частково** / **нема**. «Тест» = файли тестів, що називаються за функцією; чи повно вони покривають поведінку, НЕ ПЕРЕВІРЕНО (тести не запускались).
> Відповідність ID: L13-L16 в AUDIT = S3, P1, S1/P3, S6 тут. Додаткові рядки: R13 (пігулка і FAB), E14 (режим «Рука»).
> Скорочення шляхів: `A` = `app/src/main/java/com/savoj/volumpdf/`, `C` = `core-pdf/src/main/java/com/savoj/volumpdf/core/pdf/`, `AT` = `app/src/test/java/com/savoj/volumpdf/`, `CT` = `core-pdf/src/test/java/com/savoj/volumpdf/core/pdf/`.

| ID | Функція | Екран / точка входу | Код | Статус | Тест |
|---|---|---|---|---|---|
| L1 | Список «Останні» (мініатюра, назва, сторінка, розмір) | Вкладка «Бібліотека» | `A ui/main/RecentsFragment.kt`, `RecentsAdapter.kt`, `util/PdfThumbnailLoader.kt` | є | `AT data/db/RecentDocumentTest.kt`, `data/repository/DocumentRepositoryTest.kt` |
| L2 | Пошук у бібліотеці | Toolbar «лупа» (`menu_main.xml:action_search`) | `A ui/main/MainActivity.kt:380-400`, `MainViewModel.kt` | є | `AT ui/HomeOnboardingTest.kt` (логіка стану); UI-пошук НЕ ПЕРЕВІРЕНО |
| L3 | Фільтр «Обране» | Toolbar «Фільтр і сортування» | `MainActivity.kt:429`, `MainViewModel.toggleFavoritesFilter` | є | `AT data/repository/DocumentRepositoryTest.kt` |
| L4 | Сортування (дата / назва / розмір) | Той самий пункт | `MainActivity.kt:433-446`, `A data/model/SortOrder.kt` | є | `DocumentRepositoryTest.kt` |
| L5 | Обране (зірка) на картці й у читалці | Меню картки; «⋮» читалки | `res/menu/menu_document_item.xml`, `ReaderActivity.kt:3113` | є | `RecentDocumentTest.kt` |
| L6 | Очистити історію (з опцією «лишити обране») | Toolbar «⋮» | `MainActivity.kt:448, 491` | є | `AT ui/HomeOnboardingTest.kt` (`clearHistoryStateChanged`) |
| L7 | Порожні стани (порожньо / нічого не знайдено / немає обраного) | Бібліотека | `A ui/main/HomeLogic.kt:emptyState` | є | `AT ui/HomeOnboardingTest.kt` |
| L8 | Відкрити PDF (SAF) | FAB «Відкрити PDF» | `MainActivity.kt:208, 295-300` | є | `AT util/SafUtilsTest.kt` |
| L9 | Створити порожній PDF | FAB-розкриття; Інструменти | `MainActivity.kt:212`, `A util/DocumentCreationHelper.kt` | є | НЕ ПЕРЕВІРЕНО (окремого тесту не знайдено) |
| L10 | Сканувати (з FAB) | FAB-розкриття | `MainActivity.kt:311`, `A ui/scanner/ScanDocumentActivity.kt` | є | `AT ui/scanner/DocumentScannerLogicTest.kt` |
| L11 | Вкладки «Бібліотека» / «Інструменти» | Нижня навігація | `MainActivity.kt:249-293`, `res/menu/menu_main_bottom.xml` | є (лише 2 вкладки) | `AT ui/HomeOnboardingTest.kt` |
| L12 | Вкладка/розділ «Файли» (огляд сховища) | — | — | нема | — |
| O1 | Відкриття PDF з інших застосунків (VIEW) | Системний вибір «Відкрити в…» | `AndroidManifest.xml:31-38`, `MainActivity.kt:184-197` | є | `AT ui/HomeOnboardingTest.kt` (`isDocumentIntent`) |
| O2 | Прийом `SEND` / «Поділитися в Anti PDF» | — | — | нема (у маніфесті лише MAIN і VIEW) | — |
| R1 | Читання/перегляд PDF (androidx.pdf) | `ReaderActivity` | `A ui/reader/ReaderActivity.kt:157`, `PdfViewerBridge.kt` | є | `AT ui/reader/ViewportRestorePlannerTest.kt`, `ConfigRecreateLogicTest.kt` |
| R2 | Пошук тексту в документі | Лупа в пігулці; «⋮» → Пошук у режимі «Редагувати» | `ReaderActivity.kt:268-290`, `PdfViewerBridge.kt:54` | є (UI самого пошуку з androidx.pdf) | `AT ui/reader/TextSearchUiLogicTest.kt` |
| R3 | Перехід на сторінку | Пігулка «N / M» | `ReaderActivity.kt:317` | є | НЕ ПЕРЕВІРЕНО (тесту діалогу не знайдено) |
| R4 | Відновлення останньої сторінки | Автоматично при відкритті | `ReaderActivity.kt:213`, `A ui/reader/ReadingProgressLogic.kt` | є | `ReadingProgressLogicTest.kt` |
| R5 | Тема читання (День / Ніч / Сепія) | «⋮» → Тема читання | `ReaderActivity.kt:3121, 3182, 3205`, `ReadingTheme.kt`, `ReadingThemeLogic.kt` | є | `AT ui/reader/ReadingThemeLogicTest.kt` |
| R6 | Друк | «⋮» → Друкувати; шторка | `ReaderActivity.kt:2609`, `A util/PdfPrintDocumentAdapter.kt` | є | НЕ ПЕРЕВІРЕНО (тесту адаптера не знайдено) |
| R7 | Поділитися (`ACTION_SEND`) | «⋮» → Поділитися | `ReaderActivity.kt:3117, 3144` | є | НЕ ПЕРЕВІРЕНО |
| R8 | Зберегти копію | «⋮» → Зберегти копію | `ReaderActivity.kt:567, 2633`, `ReaderChromeLogic.kt` | є | `AT ui/reader/ReaderChromeLogicTest.kt` |
| R9 | Відомості про документ | «⋮» → Відомості | `ReaderActivity.kt:3152` | є | НЕ ПЕРЕВІРЕНО |
| R10 | Шторка «Сторінки й файл» (15 дій) | «⋮» → Сторінки й файл | `A ui/reader/ReaderToolsBottomSheetDialogFragment.kt`, `ReaderActivity.kt:2568` | є | НЕ ПЕРЕВІРЕНО (UI) |
| R11 | Відкриття PDF із паролем | Діалог пароля | `ReaderActivity.kt:397-460`, `EditAccess.kt`, `C PdfIo.kt:51` | частково (див. AUDIT §3.2) | `AT ui/reader/EditAccessTest.kt`, `CT large/LargeEncryptedTest.kt` |
| R12 | Заповнення форм AcroForm | «⋮» → Заповнити форму (якщо поля є) | `ReaderActivity.kt:382`, `FillFormBottomSheetDialogFragment.kt`, `C PdfBoxFormEngine.kt` | є (Text, CheckBox, Choice) | `AT ui/reader/FormEditingTest.kt`, `FormFieldsUiLogicTest.kt`; `CT PdfBoxFormEngineTest.kt`, `PdfBoxFormMultilineTest.kt` |
| E1 | Режим «Редагувати» (вхід/вихід, Готово) | FAB «Редагувати» | `ReaderActivity.kt:1011, 1048, 1081` | є | `AT ui/reader/PlaceBarLogicTest.kt`, `PlaceEditorTest.kt` |
| E2 | Текстовий напис (розмір, колір, фон, поворот) | Панель «Текст» | `ReaderActivity.kt:1164, 1318`, `PlaceMarkupLogic.kt` | є | `PlaceTextHardBreakTest.kt`, `PlaceTextPaginationTest.kt`, `PlaceMarkupLogicTest.kt`; `CT PdfBoxMarkupEngineTest.kt` |
| E3 | ✓ / ✗ | Панель «Дія» | `PlaceMarkupLogic.kt:12` (`CHECK`, `CROSS`) | є | `PlaceMarkupLogicTest.kt` |
| E4 | Малювання (ручка / маркер / хайлайтер) | Панель «Перо» | `ReaderActivity.kt:717`, `PlaceMarkupLogic.kt:22`, `MarkupOverlayView.kt` | є (хайлайтер = напівпрозорий штрих, не анотація тексту) | `InkSizingTest.kt`, `InkSignatureEditorTest.kt` |
| E5 | Підпис (намалювати й поставити) | Панель «Дія» → Підпис | `ReaderActivity.kt:659`, `SignatureBottomSheetDialogFragment.kt`, `SignaturePadView.kt` | є (зображення підпису, не цифровий підпис) | `InkSignatureEditorTest.kt` |
| E6 | Додати зображення на сторінку | «+» у режимі «Редагувати»; шторка | `ReaderActivity.kt:2271, 3016` | частково (відкрите питання O-1: завеликі зображення) | `PlaceEditorTest.kt`, `PageSpaceMathTest.kt` |
| E7 | Виділення об'єкта, переміщення, розмір, поворот, «по розміру сторінки», «на іншу сторінку», видалення | Панель виділення | `ReaderActivity.kt:2454`, `PlaceEditor.kt` | є | `PlaceEditorTest.kt`, `SelectionPillTest.kt`, `PageSpaceMathTest.kt` |
| E8 | Скасувати / повторити | Панель режиму | `res/layout/activity_reader.xml:btnPlaceUndo/btnPlaceRedo`, `PlaceEditor.kt` | є | `PlaceEditorTest.kt` |
| E9 | Збереження змін у файл / копію; діалог незбережених змін | «Зберегти»; «Назад» | `ReaderActivity.kt:1104, 2163, 2234` | є | `PendingStoreTest.kt`, `StructuralEditKeepsPendingTest.kt`; `CT PdfBoxMarkupEngineTest.kt` |
| E10 | Права автора / зашифрований документ: блокування змін | Режим «Редагувати» | `PlaceMarkupLogic.kt:784-789`, `EditAccess.kt` | є | `EditAccessTest.kt`, `PendingLoadGuardTest.kt` |
| E14 | Режим «Рука» (прокрутка без постановки об'єктів) | Панель режиму | `ReaderActivity.kt:1138, 1159`, `PlaceMarkupLogic.kt:12` | є | `PlaceMarkupLogicTest.kt` |
| R13 | Пігулка сторінки/зуму й FAB «Редагувати» | Низ читалки | `ReaderActivity.kt:291-315` | є | `ReaderChromeLogicTest.kt` |
| E11 | Виділення/підкреслення/закреслення тексту PDF як анотація | — | — | нема | — |
| E12 | Нотатки-коментарі (sticky note) | — | — | нема | — |
| E13 | Закладки | — | — | нема (Room зберігає лише «Останні») | — |
| T1 | Створити порожній PDF | Інструменти → «Створити» | `ToolType.CREATE_BLANK`, `ToolsActivity.kt:107-119`, `C PdfBoxPageOperationsEngine.createBlankPdf` | є | `CT PdfBoxPageOperationsEngineTest.kt` |
| T2 | Сканувати документ | Інструменти → «Створити» | `ToolType.SCAN_DOCUMENT`, `ScanDocumentActivity.kt:100`, `DocumentScannerLogic.kt` | є | `DocumentScannerLogicTest.kt` |
| T3 | Зображення в PDF | Інструменти → «Створити» | `ToolType.IMAGES_TO_PDF`, `ToolsViewModel.executeImagesToPdf`, `C imagesToPdf` | є | `CT PdfBoxPageOperationsEngineTest.kt`, `large/LargePageOpsTest.kt` |
| T4 | Упорядкувати сторінки (візуально) | Інструменти → «Сторінки»; «⋮» читалки | `ToolType.ORGANIZE_PAGES`, `A ui/organizer/*`, `C organizePages` | є | `AT ui/organizer/PageOrganizerLogicTest.kt`; `CT PdfBoxOrganizePagesTest.kt` |
| T5 | Об'єднати PDF | Інструменти → «Сторінки» | `ToolType.MERGE`, `ToolsViewModel.executeMerge`, `C mergePdfs` | є | `CT PdfBoxPageOperationsEngineTest.kt` |
| T6 | Розділити (витягти сторінки) | Інструменти → «Сторінки» | `ToolType.SPLIT`, `executeSplit`, `C extractPages` | є | `AT util/PageRangeParserTest.kt`; `CT PdfBoxPageOperationsEngineTest.kt` |
| T7 | Повернути сторінки | Інструменти → «Сторінки» | `ToolType.ROTATE`, `ToolsActivity.kt:388-395`, `C rotatePages` | є (90/180/270) | `CT PdfBoxPageOperationsEngineTest.kt` |
| T8 | Видалити сторінки | Інструменти → «Сторінки» | `ToolType.DELETE_PAGES`, `C deletePages` | є | `CT PdfBoxPageOperationsEngineTest.kt` |
| T9 | Водяний знак (текст) | Інструменти → «Оформити й оптимізувати» | `ToolType.WATERMARK`, `C addWatermark` | є (лише текст) | `CT PdfBoxWatermarkAndPageNumbersTest.kt` |
| T10 | Нумерація сторінок | Той самий розділ | `ToolType.PAGE_NUMBERS`, `ToolsActivity.kt:408-414`, `C addPageNumbers` | є | `CT PdfBoxWatermarkAndPageNumbersTest.kt` |
| T11 | Стиснення (Low/Medium/High) | Той самий розділ | `C model/CompressionLevel.kt`, `ToolType.COMPRESS`, `C compressPdf` | є | `CT PdfBoxCompressTest.kt` |
| T12 | Додати сторінку / файл у відкритий документ | «+» / шторка читалки | `ReaderActivity.kt:2534, 2492`, `C appendBlankPages`, `appendFiles` | є | `CT PdfBoxPageOpsMemoryAndSecurityTest.kt` (частково, НЕ ПЕРЕВІРЕНО зміст) |
| T13 | «Захистити паролем» (нове шифрування) | — | — | нема | — |
| T14 | OCR / пошук у сканах | — | — | нема (рішення Власника: після релізу) | — |
| P1 | Реклама: банер | Бібліотека (`bannerContainer`) | `MainActivity.kt:161-167`, `core-ads/.../AdMobAdsManager.kt` | є | `core-ads/src/test/.../AdMobAdsManagerTest.kt`, `AdIdsConfigTest.kt` |
| P2 | Реклама: interstitial після завершеної дії інструмента (cooldown 5 хв) | Після операції в інструментах | `ToolsActivity.kt:485`, `AdMobAdsManager.kt:168` | є | `AdMobAdsManagerTest.kt` |
| P3 | Згода UMP + «Налаштування конфіденційності» | Перший запуск; «⋮» бібліотеки | `AdMobAdsManager.requestConsentAndInit`, `MainActivity.kt:411, 456` | є | `AdMobAdsManagerTest.kt`, `AT ui/PrivacyMenuTest.kt` |
| S1 | Політика приватності (посилання) | «⋮» бібліотеки | `MainActivity.kt:452, 478` | є | `AT ui/PrivacyMenuTest.kt` |
| S2 | Ліцензії відкритого ПЗ: текст + «Бібліотеки» | «⋮» → Ліцензії | `A ui/common/LicensesDialogHelper.kt`, `LibraryLicensesActivity.kt`, `LibraryLicenseDetailActivity.kt`, `OssLicenseParser.kt` | є | `AT ui/common/LicensesDialogHelperTest.kt`, `OssLicenseParserTest.kt` |
| S3 | Динамічні кольори (Material You) | «⋮» бібліотеки (Android 12+) | `MainActivity.kt:419-423, 462`, `A ui/common/AppearancePreferences.kt` | є | `AT ui/common/AppearancePreferencesIsolatedProcessTest.kt`, `ui/ThemesDialogTest.kt`, `AppearanceContrastTest.kt` |
| S4 | Тема застосунку Світла/Темна (за системою) | Системні налаштування | `res/values/themes.xml:3`, `res/values-night/themes.xml` | є (власного перемикача нема) | `AT ui/AppearanceContrastTest.kt` |
| S5 | Мови uk/en/de/es/ru | Системні налаштування мови застосунку | `res/xml/locales_config.xml`, `res/values*/strings.xml` | є | `AT ui/StringsParityTest.kt` |
| S6 | Онбординг (3 екрани) | Перший запуск | `A ui/onboarding/*` | є | `AT ui/HomeOnboardingTest.kt` |
| S7 | Окремий екран «Налаштування» | — | — | нема (параметри в меню «⋮») | — |
