# QA: O-1 розмір сторінки із зображення (2026-11-25)

Гілка автора: `codex/fix-o1-image-page-size`, SHA `e31d11b`. QA-гілка: `qa/o1-image-page-size`. QA незалежний (не автор). Телефон/ADB/emulator не використовувались.

## Вердикт: QA Verified (код і автотести). Device gate не пройдено (НЕ ПЕРЕВІРЕНО на телефоні).

## 1. Обсяг diff проти origin/main
Змінено рівно 5 файлів: `core-pdf/.../ImagePageGeometry.kt` (новий), `PdfBoxPageOperationsEngine.kt` (2 місця), `ImagePageGeometryTest.kt` (новий), `docs/governance/CURRENT_STATE.md`, `docs/project_log.md`. Залежності, дозволи, версія, AGENTS.md, CLAUDE.md, `.agents`, конституція НЕ змінені (за `git diff --name-only`). Публічні сигнатури не змінені (`ImagePageGeometry` - `internal`).

## 2. Логіка (читанням коду)
- Орієнтація: `w > h` - альбом (бокс 842x595), інакше портрет (595x842); квадрат - портрет, 595x595 за коротким боком. Коректно.
- Масштаб `min(1, boxW/w, boxH/h)`: лише зменшення, пропорції збережені (один множник на обидві сторони). Менше/рівно A4 лишається 1 px = 1 pt (контракт 16x16 збережено). Рівно 595x842 не масштабується.
- Крайні випадки: 0/від'ємні -> `coerceAtLeast(1)` (1x1, без ділення на 0); Int.MAX - `toFloat()` без переповнення, масштаб малий додатний; 100x20000 -> ~4.2x842, пропорція збережена.
- Піксельні дані не перекодовуються: `PDImageXObject.createFromByteArray` не змінено, змінюються лише MediaBox і розміри в `drawImage`. Тест перевіряє, що XObject лишається 1500x2000.
- Обидва місця (`imagesToPdf`, `appendFiles`) викликають `ImagePageGeometry.pageSizePt` і малюють `drawImage(img, 0f, 0f, w, h)` з тими ж w/h, що й MediaBox: на всю сторінку без зсувів і обрізання.
- Інші споживачі: `ScanDocumentActivity` і `ToolsViewModel` викликають `imagesToPdf` (отримують нову геометрію, сигнатура та сама). `ReaderActivity.placeImageOnPage` не використовує движок, працює від розміру наявної сторінки (`pageSizePt` моста) - не зачеплено.

## 3. Мутаційна перевірка
- Мутація A: в `imagesToPdf` замінено виклик на `Pair(imgWidth.toFloat(), imgHeight.toFloat())` (старе 1 px = 1 pt): впав `engineImagesToPdfScalesPagesAndKeepsPixels` (12 тестів, 1 впав).
- Мутація B: те саме в `appendFiles`: впав `engineAppendFilesScalesImagePage` (12 тестів, 1 впав).
- Файл після кожної мутації відновлено `git checkout`; робоче дерево чисте. Тести ловлять регресію в обох місцях.

## 4. Прогін (JDK 21, `:core-pdf:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`): BUILD SUCCESSFUL
Числа пораховано з XML:
- core-pdf: 17 файлів XML, 119 тестів, 0 failures, 0 errors, 1 skipped (з них `ImagePageGeometryTest`: 12, 0 failures).
- app: 46 файлів XML, 431 тест, 0 failures, 0 errors, 0 skipped.
- lint (`lint-results-debug.xml`): 0 Error, 149 Warning (з XML; порівняння з базою main не робилось).

## Дефекти
- Критичні / Високі / Середні: немає.
- Низька (спостереження, не блокує, поза обсягом): (a) екстремально вузькі зображення (наприклад 1x100000) дають сторінку менше 1 pt шириною; деякі переглядачі можуть її не показати. Не відтворювалось. (b) Орієнтація визначається за сирими пікселями; EXIF-поворот не враховується (було й раніше, не регресія).
- Інформаційно: у тесті один тест називається `portraitBetweenShortAndLongNotRotated`, але перевіряє альбом 700x600 (назва ввідна в оману, на логіку не впливає).

## НЕ ПЕРЕВІРЕНО
- Вигляд на телефоні (фото камери, скан, мала іконка, альбомне фото; Play-копія проти нової). Device gate лишається за Власником.
- Реальні PNG/WebP (тести використовують лише JPEG з підміненим SOF).
- Android 9-11; поведінка переглядачів на дуже малих сторінках.
- Lint-базу main не порівнювали.

---

# Коло 2: O1-2 (скан ML Kit обходив imagesToPdf), автор SHA `8f80dee`

Злито `origin/codex/fix-o1-image-page-size` (8f80dee) у `qa/o1-image-page-size` merge-комітом (без force); єдиний конфлікт - `docs/project_log.md` (обидва записи збережено). Зміни автора в колі 2: `DocumentScannerLogic.kt` (+`chooseScanSource`, `ScanSource`), `ScanDocumentActivity.kt`, `DocumentScannerLogicTest.kt` (+4 тести), `CURRENT_STATE.md`, `project_log.md`. core-pdf, залежності, дозволи, AGENTS/CLAUDE/конституція не чіпались.

## Вердикт кола 2: QA Verified (код і автотести). Device gate (O1-2 на телефоні) НЕ пройдено.

## Перевірено читанням
- `chooseScanSource`: є JPEG-сторінки -> `Images(pages, pdfUri як запасний)`; немає сторінок, є PDF -> `MlKitPdf`; нічого -> null (тост помилки й `finish()`). Коректно, покрито 4 тестами.
- `handleScanSuccess`: URI сторінок і PDF зчитуються синхронно з результату одразу в обробнику, до `launch` і до закриття сканера. Права: `takePersistableUriPermissionSafe` для кожної сторінки (для content://; помилки проковтуються), читання йде в межах тимчасового дозволу активності - прийнятно.
- Побудова: `imagesToPdf` (IO) пише у `filesDir/scanned/<ім'я>`; запис у core-pdf через `saveSafely` (temp + rename), тож часткового цільового файлу при збої не лишається. При `Error` (також якщо виняток) - `targetFile.delete()`, потім запасний ML Kit PDF (`persistScannedPdf` у той самий файл); якщо запасного немає - виняток -> тост `err_save_scan` і `finish()`.
- `pageCount`: після успіху `result.pagesCount`, у запасному шляху `pages.size` від ML Kit (або 1). `recordDocumentOpened` викликається один раз, після побудови, з тим самим `persistentUri`. `openScannedDocument` один раз, далі `finish()`: подвійного відкриття нема. Витоків ресурсів не знайдено (потоки в core-pdf під `use`).
- `ReaderActivity` ~2284-2310 (скан для вставки на сторінку, `pageLimit=1`): бере `pages.first().imageUri` (запасно pdf) і малює через `placeImageOnPage` у межах уже наявної сторінки; PDF-сторінка із зображенням розміру не створюється. Змін справді не потребує.
- Інші шляхи: пошук `PDPage(`/`PDRectangle(`/`createFromByteArray`/`JPEGFactory`/`LosslessFactory`/`GmsDocumentScanning`/`pdf?.uri`: сторінка з розміром зображення створюється лише в двох місцях core-pdf (обидва через `ImagePageGeometry`). `JPEGFactory` у `PdfBoxPageOperationsEngine` (стиснення) і підпис/штамп - не створюють сторінок. Єдиний споживач PDF сканера, що обходив геометрію, був `ScanDocumentActivity` - виправлено; решта шляхів (`ToolsViewModel`, запасний вибір фото) вже йшли через `imagesToPdf`.

## Мутація
У `chooseScanSource` умову `pageImageUris.isNotEmpty()` змінено на `... && pdfUri == null` (повернення до обходу, коли є і сторінки, і PDF): впав `chooseScanSource_pagesPresent_usesImagesWithPdfFallback` (7 тестів, 1 впав). Файл відновлено `git checkout`, дерево чисте.

## Числа з XML (JDK 21, BUILD SUCCESSFUL)
- core-pdf: 17 файлів, 119 тестів, 0 failures, 0 errors, 1 skipped.
- app: 46 файлів, 435 тестів, 0 failures, 0 errors, 0 skipped (`DocumentScannerLogicTest`: 7, 0 помилок).
- lint (перегенеровано `--rerun-tasks`, `lint-results-debug.xml`): 0 Error, 149 Warning.

## Дефекти кола 2
Критичних, високих, середніх немає. Низькі (не блокують):
- L1: помилка скасування корутини (повертання/закриття екрана під час побудови) у внутрішньому `catch (e: Exception)` перетворюється на `Error` і веде на запасний шлях; далі `withContext` знову кидає скасування, зовнішній `catch` показує тост. Практично лише зайва робота/тост при закритті екрана.
- L2: у запасному шляху `persistScannedPdf` пише у файл напряму; при збої копіювання може лишитись частковий файл (поведінка існувала раніше).
- L3: `pageCount` у запасному шляху береться з `pages.size`; якщо частина сторінок без `imageUri` (`mapNotNull`), кількість могла б розійтись - малоймовірно.
- L4 (з кола 1, лишається): ReaderActivity для `pdf.uri` без сторінок декодує PDF як bitmap (не відтворюється при формат JPEG+PDF).

## НЕ ПЕРЕВІРЕНО (коло 2)
- Реальне «Сканувати» на телефоні (1 і багато сторінок, порожня A4 проти скану), права на content:// URI ML Kit, поведінка запасного шляху.
- Пам'ять/швидкість для ~100 сторінок великих JPEG (`imagesToPdf` ловить Throwable і піде на запасний PDF, але не вимірювалось).
- Ручне скасування й поворот екрана під час обробки.
