# Anti PDF — аудит фактичних екранів і функцій

> Дата зрізу: 2026-11-23. Джерело правди: `app/src/main` і ресурси застосунку. Це read-only аудит коду; збірка, емулятори й пристрій у межах аудиту не запускалися. Такі пункти прямо позначені **НЕ ПЕРЕВІРЕНО**.

## 1. Навіщо цей аудит

Попередні дизайн-документи описують різні історичні стани. Цей документ фіксує те, що реально є в коді перед редизайном, щоб новий UI не загубив функції й не відновив уже виправлені проблеми.

## 2. Карта екранів

| Екран | Фактичні можливості | Основні власники в коді |
|---|---|---|
| Онбординг | 3 сторінки: читання; редагування/інструменти; локальність файлів + AdMob/приватність; пропуск; зовнішній `ACTION_VIEW` з PDF не блокується | `ui/onboarding/OnboardingActivity.kt`, `OnboardingLogic.kt` |
| Головний контейнер | ViewPager2; Бібліотека/Інструменти; app bar; banner; FAB-кластер | `ui/main/MainActivity.kt`, `res/layout/activity_main.xml` |
| Бібліотека | продовжити читання, недавні, пошук, обране, сортування, очистити історію, Open/Create/Scan | `RecentsFragment.kt`, `RecentsAdapter.kt`, `MainViewModel.kt`, `HomeLogic.kt` |
| Інструменти | 11 карток, див. повний список нижче | `ToolsFragment.kt`, `ToolsAdapter.kt` |
| Операція | picker, параметри, CreateDocument, прогрес, результат, можливий interstitial після успіху | `ToolsActivity.kt`, `ToolsViewModel.kt` |
| Сканер | Google document scanner; fallback до зображень; відкриття результату | `ScanDocumentActivity.kt`, `DocumentScannerLogic.kt` |
| Організатор | мініатюри, drag-and-drop, rotate, delete+Undo, reset, save copy | `PageOrganizerActivity.kt`, `PageOrganizerViewModel.kt`, `PageOrganizerAdapter.kt` |
| Читач | читання, progress restore, HUD, пошук, тема сторінок, favorite, share, tools, details | `ReaderActivity.kt`, `PdfViewerBridge.kt`, `ReaderChromeLogic.kt` |
| Редактор | text, ink, ✓/✗, signature, image, select/move/resize, undo/redo, save | `ReaderActivity.kt`, `ReaderViewModel.kt`, `MarkupOverlayView.kt`, `PlaceBar*`, `PlaceMarkupLogic.kt` |
| Форми | виявлення, текст/checkbox/choice, навігація, save разом із розміткою | `FillFormBottomSheetDialogFragment.kt`, `FormFieldsAdapter.kt`, `FormEditing.kt` |
| Підпис | малювання підпису на custom canvas і вставка як розмітки | `SignatureBottomSheetDialogFragment.kt`, `SignaturePadView.kt` |
| Ліцензії | власний вступний діалог і зовнішні Google OSS menu/detail screens | `ui/common/LicensesDialogHelper.kt` |

## 3. Фактичні входи й переходи

- Launcher → `MainActivity`; перший звичайний запуск → `OnboardingActivity` → назад у Main.
- Зовнішній `ACTION_VIEW` з PDF (саме він оголошений у manifest) обходить онбординг і відкриває `ReaderActivity`. `MainActivity` також захисно обробляє `ACTION_EDIT`, але intent-filter для зовнішнього `ACTION_EDIT` не оголошений.
- Бібліотека → системний SAF picker → Читач.
- Create blank → локальний односторінковий PDF → Читач.
- Scan → Сканер → Читач.
- Каталог інструментів: Create blank → Читач; Scan → Сканер; Organize → picker → Організатор; інші вісім → `ToolsActivity`.
- Читач → Reader tools sheet / Форми / Організатор / Системний друк або Share.

## 4. Повний фактичний каталог інструментів

### Вкладка Інструменти — 11 типів

1. `CREATE_BLANK` — створити порожній PDF;
2. `SCAN_DOCUMENT` — сканувати;
3. `ORGANIZE_PAGES` — організувати сторінки;
4. `MERGE` — об’єднати;
5. `SPLIT` — витягти/розділити;
6. `ROTATE` — повернути;
7. `DELETE_PAGES` — видалити сторінки;
8. `IMAGES_TO_PDF` — зображення у PDF;
9. `WATERMARK` — водяний знак;
10. `PAGE_NUMBERS` — номери сторінок;
11. `COMPRESS` — стиснути.

Джерело: `ui/tools/ToolsAdapter.kt`.

### Шторка читача — 15 дій

Додати сторінку, додати зображення, вставити файл, організувати, повернути, видалити сторінки, розділити, стиснути, водяний знак, номери сторінок, об’єднати, сканувати, друк, поділитися, відомості.

Джерело: `ReaderToolsBottomSheetDialogFragment.kt`.

## 5. Поточна модель читача

### Читання

`VolumPdfViewerFragment` / `PdfViewerBridge`, відновлення останньої сторінки, HUD (номер сторінки, масштаб 100/150/200%, системний пошук), FAB **Редагувати**, меню Day/Night/Sepia, favorite, share, tools, organize, details, forms за наявності, save copy для read-only.

### Помилка

Вже існує окремий стан із **Спробувати ще**, **Вибрати інший файл**, **Закрити**; HUD і редагування ховаються.

### Редагування

Верхня панель: Save за наявності змін, Done, Add. Нижня графітова панель: Text, Pen, Delete, Select, Action (✓/✗/signature/image), Undo, Redo, Options. Є картка параметрів, selection pill, текстовий редактор над IME, signature/form sheets, password і unsaved dialogs.

Не можна змінювати без окремого контрактного рішення: `MarkupEngine`, `MarkupLayout`, `DocumentEditability`, `checkEditability`, `PdfPermissionException`, `PendingStore`.

## 6. Фактичні позитивні основи для редизайну

- Material 3, light/dark і optional dynamic colors уже є.
- `minSdk=28`, `targetSdk=36`, `resizeableActivity=true`.
- `WindowInsetsHelper` уміє об’єднувати system bars, display cutout та IME.
- `AdaptiveGrid`, `AdaptiveLayoutLogic`, `MaxWidthLinearLayout` уже дають адаптивні колонки й максимальну ширину 840 dp.
- Основні XML-кнопки здебільшого мають 48 dp.
- Статична індиго/графітова палітра має JVM-перевірку контрасту.
- Banner живе у головному контейнері та `ToolsActivity`; `AdMobAdsManager` робить контейнер `GONE` до завантаження й при помилці/неможливості запиту.
- Reader має polite live-region для сторінки й кілька корисних accessibility descriptions.

## 7. UX-борг, підтверджений кодом

### P0

1. **L7 — OSS licenses.** Зовнішні `OssLicensesMenuActivity`/detail заходять під status bar на targetSdk 36; дефект уже підтверджено на Galaxy S24 Ultra. Debug показує placeholder замість реального release-списку. Надійний напрям — власний AppCompat/Material список і деталі з наявних OSS-даних.
2. **Недоступний markup canvas.** `MarkupOverlayView` є одним custom View без virtual accessibility nodes. Об’єкти, selection, drag, resize й pinch недоступні TalkBack як окремі дії.
3. **Цілі менші 48 dp.** `SwatchView.compactMode` створює вузол близько 30 dp; `btnTextBoxResize` має 28×28 dp. Поточний `TouchTargetsTest` не бачить усі програмні controls.
4. **Неконтекстний toolbar.** На вкладці Інструменти лишаються бібліотечні Search/Filter/Sort/Clear History, бо підготовка меню не враховує поточну сторінку.

### P1

- 11 карток інструментів і 15 reader actions — плоскі списки без групування.
- Share, Details та Organize дублюються в reader overflow і в sheet.
- `ToolsActivity` відразу кидає нового користувача в системний picker без короткого пояснення.
- Немає clean/immersive reading; HUD не ховається автоматично.
- Форми заховані в overflow; немає контекстного CTA «Заповнити форму».
- Delete дублюється як постійний слот і як selection-pill action; Options дублює повторний тап активного tool.
- `ReaderActivity.kt` має понад 3200 рядків і концентрує багато станів — великий візуальний diff має високий регресійний ризик.
- Організатор покладається на drag-and-drop без видимого handle або доступних команд «вище/нижче».
- Явний traversal/focus restore для reader overlays, speed-dial та кількох модальних шарів не підтверджено.
- Signature canvas має лише touch drawing; доступна альтернатива не визначена.

### НЕ ПЕРЕВІРЕНО

- повний TalkBack happy path, Switch Access і hardware keyboard;
- font scale 150%/200% (власник візуально перевіряв збільшений шрифт, але точне значення не зафіксовано);
- Android 9–11;
- physical tablet, Fold, DeX, split-screen;
- gesture navigation + IME для всіх sheets;
- UMP accessibility;
- Android 17 як окрема повна матриця;
- release-екран реального списку OSS після редизайну.

## 8. Суперечності історичних UI-документів

- `UI_REDESIGN_SPEC.md` має статус Phase 0, хоча описаний редактор значною мірою реалізований.
- Старої великої світлої `placePanelCard` у поточному UI немає; її замінили графітові toolbar/options.
- У read mode немає окремих top icons Theme/Add; Theme у overflow, Add тільки в edit mode.
- `UI_REVIEW_2026-11-16.md` описує ранній стан із трьома FAB та дубльованими controls; пізніші примітки частково суперечать раннім.
- Твердження про відсутність Material You, онбордингу, haptics, resizeable layout та adaptive columns застаріли.
- Мов не чотири, а п’ять: `en`, `uk`, `de`, `ru`, `es`.
- У вкладці не «12+», а 11 tool cards; у reader sheet — 15 actions.
- Рекомендація створити error screen уже виконана.

Історичні документи корисні як хронологія, але джерелом для нової реалізації є цей аудит, `ANTI_PDF_REDESIGN_V2.md` і актуальний код.

## 9. Висновок

Найбільший ефект дасть не чергова зміна палітри, а контекстний toolbar, групування дій, clean reading, помітні форми, зменшення дублювання й доступна модель editor objects. Перший технічний зріз має закрити L7 та базові accessibility targets, а не починати з декоративних анімацій.
