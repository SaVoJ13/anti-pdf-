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
