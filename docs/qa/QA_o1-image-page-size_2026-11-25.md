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

---

# Коло 3: O1-3 (альбомне зменшується до ширини 595 + поворот зображення), автор SHA `96adaba`

Злито `origin/codex/fix-o1-image-page-size` (96adaba) у `qa/o1-image-page-size` merge-комітом (без force); конфлікт лише в `docs/project_log.md` (записи збережено). Змінено: `ImagePageGeometry.kt`, `PageOperationsEngine.kt` (+`rotationsDegrees` з типовим значенням), `PdfBoxPageOperationsEngine.kt` (imagesToPdf), `ToolsViewModel.kt`, `ToolsActivity.kt`, `SelectedFileAdapter.kt`, `item_selected_file.xml`, тести `ImagePageGeometryTest` (21) і `SelectedFileItemRotationTest` (3, новий), `CURRENT_STATE.md`. Залежності, дозволи, версія, AGENTS/CLAUDE/конституція не змінені.

## Вердикт кола 3: QA Verified (код і автотести), з 1 низьким зауваженням щодо покриття тестами. Device gate НЕ пройдено.

## Геометрія (читанням і тестами)
- Правило: масштаб `min(1, 595/dw, 842/dh)`, де dw/dh - розміри після повороту. Видима ширина завжди <= 595, висота <= 842, малі не збільшуються, пропорції збережені. Альбом 4000x3000 -> 595x446.25 (тест). Орієнтація тепер не обмежується боксом 842x595: це відповідає рішенню Власника.
- Поворот: `normalizeRotation` приводить 90/180/270, від'ємні (-90 -> 270) і >360 за модулем; нестандартні (напр. 45) -> 0. Для 90/270 розміри міняються місцями до обчислення масштабу. MediaBox = розмір малювання до `/Rotate`, `page.rotation = rotation`. Зображення малюється `drawImage(0,0,w,h)` у MediaBox, а `/Rotate` (за годинниковою) обертає сторінку: напрямок збігається з кнопкою «за годинниковою». Пікселі не перекодовуються (XObject лишається 4000x3000, тест).
- Крайні випадки: 0/від'ємні розміри -> 1x1; 100x20000, 8000x6000, дуже широкі/вузькі, рівно A4, малі з поворотом - тести є; Int.MAX без переповнення (`toFloat`).
- `appendFiles` викликає `pageSizePt(w, h)` (rotation = 0 за замовчуванням): геометрія та сама, поворот там не підтримується (за задумом: кнопка видима лише в «Зображення у PDF»).

## Контракти й виклики
- `imagesToPdf(..., rotationsDegrees: List<Int> = emptyList())` - параметр з типовим значенням, наявні виклики (`ScanDocumentActivity` x2, тести `PdfBoxPageOperationsEngineTest`, `LargePageOpsTest`) компілюються без змін. Реалізація не повторює типове значення (коректно для override). 
- Довжина rotations != кількості зображень: `getOrElse(index) { 0 }` - коротший список дає 0 для решти (тест: 3 зображення, список із 2), довший - зайві ігноруються. Не ламається.

## UI
- `btnRotateFile`: 48dp x 48dp, `contentDescription=@string/action_rotate_clockwise`, `padding 12dp`, `selectableItemBackgroundBorderless`, видимий лише для зображень (`isImage`, лише екран «Зображення у PDF»; у інших інструментах елементи не-зображення, кнопка `GONE`).
- Рядок `action_rotate_clockwise` є в усіх 5 локалях (values, de, es, ru, uk, рядок 297), іконка `ic_rotate_right` існує.
- Стан: кут зберігається в `SelectedFileItem.rotationDeg` у `ViewModel` (переживає поворот екрана). 4 натиски -> 90, 180, 270, 0 (тест). Видалення: кут належить елементу, не індексу, тому «їде» разом з елементом. Перестановки елементів у списку немає (немає `ItemTouchHelper`), отже сценарій не існує. Додавання нових файлів не скидає кути (`_selectedFiles + files`). Індекс кліку береться з `bindingAdapterPosition` і перевіряється на `NO_POSITION`; `ivItemTypeIcon.rotation` виставляється при кожному bind (без «залишкового» повороту при перевикористанні холдера).
- Спостереження UX: поворот показується обертанням значка типу файлу, а не мініатюрою зображення - Власнику варто оцінити на телефоні, чи це зрозуміло.

## Мутації
- M1: ширина альбомного (`A4_SHORT_PT / dw` -> `A4_LONG_PT / dw`): 21 тест, 12 впали (геометричні й обидва рушійні). Червоне.
- M2: ігнорування кутів у рушії (`getOrElse` -> `0`): впав `engineImagesToPdfRotationAppliedWithoutPixelChange` (21 тест, 1 впав). Червоне.
- M3: `ToolsViewModel.executeImagesToPdf` не передає `rotations` у `imagesToPdf`: уся `app`-ітерація тестів ЗЕЛЕНА (BUILD SUCCESSFUL). Тобто передачу кутів від ViewModel до рушія тест не ловить. Усі мутації відновлено `git checkout`, дерево чисте.

## Числа з XML (JDK 21, `--rerun-tasks`, BUILD SUCCESSFUL)
- core-pdf: 17 файлів, 128 тестів, 0 failures, 0 errors, 1 skipped (`ImagePageGeometryTest`: 21, 0 помилок).
- app: 47 файлів, 438 тестів, 0 failures, 0 errors, 0 skipped (`SelectedFileItemRotationTest`: 3, 0 помилок).
- lint (`lint-results-debug.xml`, перегенеровано): 0 Error, 149 Warning.

## Дефекти кола 3
Критичних, високих, середніх немає.
- Низький L5 (прогалина покриття): немає тесту, що `ToolsViewModel.executeImagesToPdf` передає `rotationDeg` елементів у `imagesToPdf` (мутація M3 не ловиться), як і тесту `rotateFileAt` (лише для зображень) та видалення з поворотами. Рекомендація: тест ViewModel із підміненим рушієм.
- Низькі L1-L4 з кіл 1-2 лишаються без змін.

## НЕ ПЕРЕВІРЕНО (коло 3)
- Вигляд і поведінка на телефоні: кнопка повороту, видимий результат (альбом -> 595 завширшки, /Rotate у різних переглядачах), торк-зона 48dp, читалка поверненого PDF.
- Реальні фото з EXIF-орієнтацією разом із ручним поворотом (EXIF як і раніше не враховується).
- Додаткові перегляди /Rotate у сторонніх переглядачах; PNG/WebP.

---

# Коло 4: автоповорот за EXIF Orientation, автор SHA `4188580`

Злито `origin/codex/fix-o1-image-page-size` (4188580) у `qa/o1-image-page-size` merge-комітом (без force); конфлікт лише в `docs/project_log.md` (записи збережено). Нове: `ExifOrientation.kt` (`internal`), зміни в `PdfBoxPageOperationsEngine.kt` (imagesToPdf, appendFiles), тести `ExifOrientationTest` (5) і +4 тести в `ImagePageGeometryTest` (усього 25), документи. Публічні сигнатури, залежності, дозволи, AGENTS/CLAUDE/конституція не змінені.

## Вердикт кола 4: QA Verified (код, аналіз залежності й автотести). Device gate НЕ пройдено.

## Парсер `ExifOrientation` (читанням)
- JPEG (SOI FFD8), обхід сегментів до SOS/EOI; APP1 з заголовком `Exif\0\0`; TIFF `II`/`MM`, магічне 42, IFD0, тег 0x0112, тип SHORT (3), значення 1..8; інше -> 1.
- Безпека: усі читання через `u8` з перевіркою меж; `u16/u32` перевіряють `end`; `read()` обгортає все в `try/catch(Throwable)` -> 1. Нескінченних циклів немає: у `parse` індекс завжди росте (`i++` або `i += 2 + len`, `len >= 2`), цикл IFD обмежений `count` (<= 65535) і межами сегмента. Зміщення IFD перевіряється (`>= 8`, без переповнення Int). Обрізані/порожні/не-JPEG (PNG, GIF, випадкові байти) -> 1. Виняткiв назовні немає.
- Значення SHORT читається з перших двох байтів поля значення з урахуванням порядку байтів: коректно для II і MM.

## Мапінг
- 3 -> 180, 6 -> 90 CW, 8 -> 270; дзеркальні: 2 -> 0, 4 -> 180, 5 -> 270, 7 -> 90 (лише поворот, дзеркало ігнорується). Це узгоджено з exiftool («Mirror horizontal and rotate 270 CW» для 5, «...90 CW» для 7). Для дзеркальних значень результат буде віддзеркалений відносно істини (рідкісні випадки, напр. деякі селфі-камери або редактори); не краще й не гірше за інші прості реалізації, ризик низький і зафіксований (L6).
- Сума: `normalizeRotation(exifDeg + manualDeg)`, тобто EXIF + ручний за годинниковою; геометрія рахується за сумарним кутом (розміри міняються для 90/270), `/Rotate` = сума. Тест: EXIF 6 + ручний 90 -> 180, 595x446.25.

## Ключовий ризик: подвійний поворот
- У `pdfbox-android 2.0.27.0` (sources.jar з кешу Gradle) у пакеті `pdmodel/graphics/image` слів `exif`/`orientation` немає взагалі. `PDImageXObject.createFromByteArray` для JPEG викликає `JPEGFactory.createFromByteArray`, який бере розміри через `BitmapFactory.Options.inJustDecodeBounds` (сирі розміри, EXIF не застосовується) і вбудовує оригінальний DCT-потік без декодування й перекодування. Пікселі ніде не повертаються бібліотекою, тож автоповорот лише через `/Rotate` не дає подвійного повороту на рівні бібліотеки. Сирі розміри збігаються з тими, що використовує геометрія.
- Вбудований JPEG зберігає EXIF-сегмент, але PDF-рушії (PDFium, pdf.js, MuPDF) для DCTDecode не застосовують EXIF-орієнтацію: це НЕ ПЕРЕВІРЕНО на пристрої (див. нижче), але за специфікацією PDF очікувано.

## Скани ML Kit
- Сторінки сканера йдуть через `imagesToPdf` (коло 2), тож EXIF-орієнтація застосується й до них, якщо JPEG-сторінки містять тег ≠ 1. Документація ML Kit Document Scanner не описує EXIF-тег у результатах; сканер сам обрізає й вирівнює сторінку та віддає вже оброблені пікселі, тож очікувано тег відсутній або дорівнює 1 (подвійного повороту не буде). Це НЕ ПЕРЕВІРЕНО на пристрої: якщо на телефоні скан виявиться повернутим на 90, причина в EXIF-тегу сторінок ML Kit (виправлення: не застосовувати EXIF для сканів; тут лише спостереження).
- `appendFiles`: EXIF застосовується (тест `appendFilesAppliesExifRotation`: /Rotate 90); ручного повороту там немає, вірно.
- PNG/WebP: `read()` повертає 1 для не-JPEG (перевіряється SOI), тож PNG не змінюється; WebP не підтримується `createFromByteArray` (було й раніше).

## Мутації
- Парсер (тег 0x0112 -> 0x0113): 30 тестів, 5 впало (`allEightValuesBothEndian`, `truncatedDataNeverThrows`, три рушійні). Червоне.
- Мапінг (`6, 7 -> 90` -> `270`): 4 впали (`rotationMapping` і три рушійні). Червоне.
- Сума (`exifDeg + manualDeg` -> `manualDeg`): 2 впали (`exifOrientation6MakesLandscapeFilePortraitPage`, `exifPlusManualRotationAdds`). Червоне.
- Файли відновлено `git checkout`, дерево чисте.

## Закриття L5 з кола 3 (лише описом)
L5: немає тесту, що `ToolsViewModel.executeImagesToPdf` передає кути елементів у `imagesToPdf` (мутація «не передавати» лишалась зеленою), немає тестів `rotateFileAt` і видалення. У колі 4 нових тестів ViewModel немає, отже L5 ВІДКРИТИЙ як низька прогалина покриття (не блокує). Додатковий ефект: сума EXIF + ручний у `ToolsViewModel` не залежить від ViewModel (EXIF читається в рушії), тож ризик зведений до передачі ручних кутів. Рекомендація автору: тест ViewModel з підробленим `PageOperationsEngine`, що перевіряє `rotationsDegrees` у виклику.

## Числа з XML (JDK 21, `--rerun-tasks`, BUILD SUCCESSFUL)
- core-pdf: 18 файлів, 137 тестів, 0 failures, 0 errors, 1 skipped (`ExifOrientationTest`: 5; `ImagePageGeometryTest`: 25, усі без помилок).
- app: 47 файлів, 438 тестів, 0 failures, 0 errors, 0 skipped.
- lint (`lint-results-debug.xml`, перегенеровано): 0 Error, 149 Warning.

## Дефекти кола 4
Критичних, високих, середніх немає.
- L6 (низький): дзеркальні Orientation 2/4/5/7 лише повертаються, дзеркало не відновлюється (картинка лишається віддзеркаленою); рідкісно.
- L5 (низький, відкритий): прогалина покриття передачі кутів ViewModel -> рушій.
- Низькі L1-L4 з кіл 1-2 без змін. L4 (EXIF не враховується) з кола 1 тепер закрито для JPEG.

## НЕ ПЕРЕВІРЕНО (коло 4)
- Телефон: вертикальне фото з камери (Orientation 6/8) у «Зображення у PDF»: чи сторінка портретна й зображення пряме; різні переглядачі (читалка, PdfRenderer, сторонні).
- Чи справді PDF-переглядачі ігнорують EXIF у вбудованому JPEG (за специфікацією так, на пристрої не перевірялось).
- JPEG-сторінки ML Kit: чи є в них EXIF-тег ≠ 1.
- Реальні JPEG від камер різних виробників (тести - синтетичний APP1); EXIF у PNG (eXIf) і WebP не обробляється.
