# Журнал проєкту (нові записи зверху)

## 2026-11-03 — PDF-ядро (Реалізація AnnotationEngine: малювання штрихів, вставка підпису та додавання тексту)
**Що зроблено:**
- Розширено модуль `:core-pdf` реалізацією `AnnotationEngine` згідно з CONTRACTS.md v0.2.
- Додано моделі даних:
  - `com.savoj.volumpdf.core.pdf.model.InkStroke` (points: List<PointF>, color: Int, strokeWidth: Float).
  - `com.savoj.volumpdf.core.pdf.model.FreeTextData` (text: String, x: Float, y: Float, fontSize: Float, color: Int).
  - Забезпечено типізовані typealias у `com.pdfreader.core.pdf.model` та `com.pdfreader.core.pdf` для зворотної сумісності.
- Реалізовано інтерфейс `AnnotationEngine` та рушій `PdfBoxAnnotationEngine` на базі `com.tom-roush:pdfbox-android:2.0.27.0`:
  - `addInkAnnotations`: створення анотацій `PDAnnotationMarkup` з типом `PDAnnotationMarkup.SUB_TYPE_INK` (/Ink за стандартом ISO 32000-1), конвертація координат з Android Canvas у PDF координатну систему (`pdfY = pageHeightPt - touchY`), нормалізація кольору RGB у `PDColor`, обчислення bounding box `PDRectangle`, встановлення `PDBorderStyleDictionary` та генерація `AppearanceStream` (`constructAppearances`).
  - `addSignatureStamp`: надійна конвертація бітмапа підпису у `PDImageXObject` через `LosslessFactory.createFromImage` (збереження прозорості та альфа-каналу) з fallback на `JPEGFactory`, малювання у `PDPageContentStream` в режимі `APPEND` на цільових координатах `targetRectPt` з інверсією осі Y (`pdfY = pageHeightPt - targetRectPt.bottom`).
  - `addFreeText`: нанесення текстового блоку в `PDPageContentStream` в режимі `APPEND` за допомогою `PDType1Font.HELVETICA` із захисним санітайзингом символів поза WinAnsi кодуванням.
  - Безпечне збереження результатів у `outputUri` (SAF чи file) через проміжний файл для захисту від блокування дескрипторів та пошкодження документів при перезаписі (in-place save).
- Написано повний набір модульних unit-тестів у `core-pdf/src/test/java/com/savoj/volumpdf/core/pdf/PdfBoxAnnotationEngineTest.kt`:
  - `testAddInkAnnotation`: перевірка створення PDF, нанесення штриха `InkStroke`, наявності анотації типу `/Ink` у списку `page.annotations`, коректності координат та конвертації осі Y.
  - `testAddSignatureStamp`: перевірка нанесення бітмапа підпису та валідності створеного PDF.
  - `testAddFreeText`: перевірка нанесення тексту та його вилучення через `PDFTextStripper`.
  - `testInvalidPageIndex`: перевірка валідації діапазону сторінок.
- Успішно верифіковано: прогнано `./gradlew :core-pdf:test :core-pdf:assembleRelease` з JDK 21 (100% тестів пройдено, AAR зібрано).
**Що змінено:**
- Модуль `core-pdf/`:
  - `core-pdf/src/main/java/com/savoj/volumpdf/core/pdf/model/InkStroke.kt`
  - `core-pdf/src/main/java/com/savoj/volumpdf/core/pdf/model/FreeTextData.kt`
  - `core-pdf/src/main/java/com/savoj/volumpdf/core/pdf/AnnotationEngine.kt`
  - `core-pdf/src/main/java/com/savoj/volumpdf/core/pdf/PdfBoxAnnotationEngine.kt`
  - `core-pdf/src/main/java/com/pdfreader/core/pdf/model/Compatibility.kt`
  - `core-pdf/src/main/java/com/pdfreader/core/pdf/Compatibility.kt`
  - `core-pdf/src/test/java/com/savoj/volumpdf/core/pdf/PdfBoxAnnotationEngineTest.kt`
- `.gitignore` (додано `.kotlin/`)
- `docs/project_log.md`
**Рішення, які ухвалено:**
- Використано пряму генерацію векторних `/Ink` анотацій через `PDAnnotationMarkup` з побудовою нормального `AppearanceStream` (`constructAppearances`), що забезпечує коректне відображення малюнків як у системному `androidx.pdf` / `PdfRenderer`, так і у сторонніх програмах (Adobe Acrobat, Google Drive PDF тощо).
- Для вставки підпису використано `LosslessFactory.createFromImage`, що гарантує збереження прозорого фону (альфа-каналу) рукописного підпису без артефактів стиснення JPEG.
- Збереження змінених файлів реалізовано через тимчасовий буфер із наступним атомарним переносом у вихідний URI, що повністю виключає пошкодження оригінального файлу при аварійних збоях чи перезаписі того самого файлу.
**Що передати далі:**
- Керівнику проєкту: `AnnotationEngine` готовий, контракт `CONTRACTS.md` v0.2 повністю імплементовано.
- Агенту Android/UI: можна інтегрувати інструменти малювання (Ink), додавання підпису та тексту у відповідні UI-екрани.
**Відкриті питання:**
- Немає. Усі тести проходять успішно (100% зелених).

## 2026-11-03 — Монетизація і Google Play (Створення модуля core-ads, інтеграція в app та підготовка документів для Google Play)
**Що зроблено:**
- Створено Android Library модуль `:core-ads` (`namespace = "com.savoj.volumpdf.core.ads"`, `compileSdk = 36`, `compileSdkExtension = 19`, `minSdk = 28`).
- Підключено модуль `:core-ads` у `settings.gradle.kts` та `app/build.gradle.kts`.
- Реалізовано контракт `AdsManager` та `ConsentStatus` (за `CONTRACTS.md`) у `AdMobAdsManager`:
  - `requestConsentAndInit`: показ форми згоди Google UMP (User Messaging Platform) згідно з GDPR / ePrivacy Directive перед ініціалізацією реклами та завантаженням SDK.
  - `bindAdaptiveBanner`: створення адаптивного AdView, автоматичне згортання (`View.GONE`) контейнера реклами при відсутності інтернету або помилці завантаження без порожніх білих дірок.
  - `showActionInterstitial`: безпечний показ повноекранної реклами з обов'язковим інтервалом кулдауну не частіше 1 разу на 5 хвилин. При активному кулдауні або відсутності мережі дія користувача продовжується негайно без жодних блокувань.
  - Використано виключно офіційні тестові ідентифікатори Google AdMob (`TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"`, `TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"`, `TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"`). Жодних бойових ключів у репозиторії.
- Інтегровано рекламу в застосунок `app`:
  - Додано метатег `com.google.android.gms.ads.APPLICATION_ID` та дозвіл `android.permission.INTERNET` в `AndroidManifest.xml`.
  - Зареєстровано `ToolsActivity` в `AndroidManifest.xml`.
  - У `MainActivity`: виклик `requestConsentAndInit` при старті, динамічний адаптивний банер у нижній частині екрана зі збереженням доступності FAB (автоматичне коригування відступу FAB при появі банера).
  - У `ToolsActivity`: адаптивний банер над кнопкою дій та виклик `showActionInterstitial` після успішного збереження файлу перед показом діалогу результату.
- Підготовлено документацію для публікації в Google Play у каталозі `docs/store/`:
  - `PRIVACY_POLICY.md` — вичерпна політика конфіденційності (наголос на локальній обробці SAF, нульовій передачі документів у мережу, відсутності акаунтів та аналітики, передачі даних в AdMob лише за згодою UMP).
  - `DATA_SAFETY.md` — точні відповіді для анкети Google Play Data Safety з обґрунтуванням для всіх категорій даних (файли й документи — Not Collected; приблизне розташування, діагностика та рекламні ID — збираються стороннім SDK Google AdMob за згодою).
  - `STORE_LISTING.md` — маркетингова картка застосунку (назва 25 символів, короткий опис до 80 символів та повний опис до 4000 символів 4 мовами: uk, en, de, ru).
- Написано JVM unit-тести для `core-ads` (`AdMobAdsManagerTest`: перевірка тестових ID, станів згоди, негайного спрацьовування кулдауну та відсутності затримок при порожньому кеші).
- Успішно зібрано й верифіковано: `./gradlew :app:assembleDebug :core-ads:assembleRelease :core-ads:test :app:testDebugUnitTest`.
**Що змінено:**
- Модуль `core-ads/` (build.gradle.kts, AndroidManifest.xml, AdMobAdsManager.kt, AdsManager.kt, ConsentStatus.kt, тести)
- Модуль `app/` (build.gradle.kts, AndroidManifest.xml, MainActivity.kt, ToolsActivity.kt, activity_main.xml, activity_tools.xml)
- `settings.gradle.kts`
- Каталог `docs/store/` (PRIVACY_POLICY.md, DATA_SAFETY.md, STORE_LISTING.md)
- `docs/project_log.md`
**Рішення, які ухвалено:**
- Реалізовано єдиний синглтон `AdMobAdsManager.getInstance(context)`, що гарантує спільний таймер кулдауну (5 хвилин) між усіма активностями та екранами застосунку.
- Для адаптивного банера в `MainActivity` реалізовано `addOnLayoutChangeListener`, який автоматично коригує `bottomMargin` для ExtendedFAB при зміні видимості рекламного контейнера, що повністю виключає перекриття елементів керування банером.
- Контейнери реклами ініціалізуються як `View.GONE` і стають видимими лише після події `onAdLoaded()`. При помилці мережі контейнер очищується і знову стає `View.GONE`.
**Що передати далі:**
- Керівнику проєкту та QA: модуль `core-ads` готовий, реклама та згода інтегровані в `app`, документація для Play Console у `docs/store/` готова до аудиту.
**Відкриті питання:**
- Реєстрація бойового акаунту Google Play Console ($25) та налаштування рекламних блоків у Google AdMob консолі чекають на рішення та дії власника перед релізом у закрите тестування.

## 2026-11-03 — Android і UI (Створення модуля app, Room бази даних, Material 3 інтерфейсу та інструментів)
**Що зроблено:**
- Створено чистовий Android Application модуль `:app` (`namespace = "com.savoj.volumpdf"`, `applicationId = "com.savoj.volumpdf"`, `minSdk = 28`, `compileSdk = 36`, `compileSdkExtension = 19`, `targetSdk = 36`, `versionCode = 1`, `versionName = "1.0.0"`).
- Підключено модуль `:app` у `settings.gradle.kts` та `build.gradle.kts`. Використано лише затверджені в `THIRD_PARTY.md` залежності.
- Створено базу даних Room (`AppDatabase`) та таблицю `recent_documents` (`RecentDocument`: `uri`, `title`, `pageCount`, `fileSizeBytes`, `lastOpenedTimestamp`, `lastPageRead`, `isFavorite`) із `RecentDocumentDao` та репозиторієм `DocumentRepository`:
  - Повна реактивність через `Flow<List<RecentDocument>>` за допомогою Room InvalidationTracker.
  - Підтримка пошуку за назвою, фільтрації обраних та сортування (за часом відкриття, назвою та розміром).
  - Методи збереження, оновлення прогресу читання, перемикання статусу обраного та видалення.
- Реалізовано повний користувацький інтерфейс Material 3 (теми Day/Night, динамічні кольори, векторні ресурси, розмітка без фіксованих розмірів):
  - **`MainActivity`:** TabLayout + ViewPager2 («Останні документи» та «Інструменти»), ExtendedFAB «Відкрити PDF», SearchView для швидкого пошуку, меню сортування та фільтрації обраного.
  - **Безпечний доступ до файлів:** виключно через SAF (`OpenDocument` для читання, `OpenMultipleDocuments` для злиття/зображень, `CreateDocument` для експорту результатів). Жодного використання `MANAGE_EXTERNAL_STORAGE`. Автоматичне взяття постійних дозволів через `takePersistableUriPermission`.
  - **Обробка системного наміру:** підтримка `ACTION_VIEW` та `ACTION_EDIT` для `application/pdf` з автоматичною реєстрацією в базі та відкриттям читача.
  - **`ReaderActivity`:** інтеграція `VolumPdfViewerFragment` (обгортка над `androidx.pdf.viewer.fragment.PdfViewerFragment`), передача `documentUri = uri`, системний жест зуму та навігація, автоматичне збереження прогресу читання та дати останнього відкриття в Room, збереження та відновлення стану при повороті екрана та перестворенні процесу, діалог відомостей про документ та системний шаринг через `ACTION_SEND`.
  - **`ToolsActivity`:** повноцінний інтерфейс для 5 інструментів сторінок (Злиття, Розділення/вилучення, Поворот на 90°/180°/270°, Видалення сторінок, Зображення в PDF) з підтримкою парсингу діапазонів (напр. "1-3, 5, 8"), валідацією, показом прогресу виконання корутин та збереженням у вихідний SAF-файл з можливістю миттєвого відкриття створеного документа в читачі.
- Реалізовано 100% локалізацію в `strings.xml` для 4 мов: `values` (en), `values-uk` (uk), `values-ru` (ru), `values-de` (de).
- Написано повний набір JVM unit-тестів:
  - `PageRangeParserTest` (9 тестів: валідація діапазонів, одинарні сторінки, інверсії, дублікати, перевірка меж, некоректні формати).
  - `SafUtilsTest` (6 тестів: форматування розмірів файлів, таймстемпів).
  - `RecentDocumentTest` (тести моделі та копіювання).
  - `DocumentRepositoryTest` (6 тестів: сортування за датою/назвою/розміром, перемикання обраного, видалення, очищення).
- Успішно зібрано й верифіковано: `./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:testReleaseUnitTest` (усі тести пройшли успішно, згенеровано `app-debug.apk` та `app-release-unsigned.apk`).
**Що змінено:**
- Модуль `app/` (код, ресурси, маніфест, тести, build.gradle.kts)
- `settings.gradle.kts`
- `build.gradle.kts`
- `docs/project_log.md`
**Рішення, які ухвалено:**
- Організовано Room сутності та DAO через Java + Java Annotation Processor (`annotationProcessor("androidx.room:room-compiler:2.6.1")`), що повністю усуває конфлікт несумісності версій Kotlin Metadata (2.2.0 vs 2.0.0 у застарілому KAPT/Room 2.6.1) та забезпечує максимальну швидкість компіляції без потреби зниження версії Kotlin у проєкті.
- Реактивний потік `Flow<List<RecentDocument>>` побудовано за допомогою `callbackFlow` поверх Room `InvalidationTracker`, що забезпечує автоматичне оновлення UI при будь-яких змінах у базі даних.
- `VolumPdfViewerFragment` розширює `PdfViewerFragment` з `core-pdf` і надає зворотні виклики завантаження сторінок та помилок для синхронізації з ViewModel та базою даних.
**Що передати далі:**
- Керівнику проєкту: модуль `app` готовий, збірки успішні, unit-тести проходять. Модуль готовий до підключення `core-ads` для монетизації (UMP + AdMob банери та інтерстишели за контрактом `core-ads ↔ app`).
**Відкриті питання:**
- Немає. Усі вимоги завдання та MASTER_SPEC виконано.


## 2026-11-03 — PDF-ядро (Створення модуля core-pdf та реалізація PageOperationsEngine)
**Що зроблено:**
- Створено кореневу збірку проєкту з Gradle wrapper 8.14.3, AGP 8.13.0, Kotlin 2.2.20 та Android Library модуль `:core-pdf` (`namespace = "com.savoj.volumpdf.core.pdf"`, `compileSdk = 36`, `compileSdkExtension = 19`, `minSdk = 28`).
- Реалізовано моделі даних за CONTRACTS.md: `PdfDocumentMetadata`, `PdfLoadResult`, `PdfOperationResult` у пакеті `com.savoj.volumpdf.core.pdf.model`.
- Реалізовано інтерфейс `PageOperationsEngine` та рушій `PdfBoxPageOperationsEngine` на базі `com.tom-roush:pdfbox-android:2.0.27.0` у пакеті `com.savoj.volumpdf.core.pdf`:
  - `mergePdfs`: послідовне злиття кількох PDF через `PDFMergerUtility` з емісією прогресу `Flow<PdfOperationResult>`.
  - `extractPages`: експорт обраних сторінок у новий документ (`targetDoc.importPage(page)`).
  - `rotatePages`: поворот сторінок на кут, кратний 90° (0..360° нормалізація).
  - `deletePages`: видалення сторінок у порядку спадання індексів (запобігає зміщенню індексів) з перевіркою мінімуму в 1 сторінку.
  - `imagesToPdf`: конвертація JPEG/PNG/зображень у PDF з надійним визначенням розмірів (через маркери SOF / IHDR для незалежності від середовища).
  - Забезпечено коректну ініціалізацію `PDFBoxResourceLoader.init(context)`, безпечне закриття дескрипторів та потоків через `.use {}` / `try ... finally`, валідацію вхідних аргументів та перехоплення помилок.
- Надано типізовані аліаси для зворотної сумісності у пакеті `com.pdfreader.core.pdf`.
- Написано повний набір JVM unit-тестів у `PdfBoxPageOperationsEngineTest` (6 тестів: злиття, вилучення, поворот, видалення, створення PDF із зображення, валідація помилок).
- Успішно зібрано й перевірено: `./gradlew :core-pdf:test :core-pdf:assembleRelease` (усі 6 тестів зелені, release AAR зібрано).
**Що змінено:**
- Модуль `core-pdf/`
- Кореневі файли збірки (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`)
- `docs/project_log.md`
**Рішення, які ухвалено:**
- Встановлено `compileSdkExtension = 19` для сумісності з `androidx.pdf:pdf-viewer-fragment:1.0.0-beta01` (вимога SDK Extension >= 19).
- Організовано обробку потоків на `Dispatchers.IO` з коректним прокиданням `CancellationException`.
- Реалізовано вбудований парсер заголовків зображень (SOF0/SOF2 для JPEG та IHDR для PNG) для надійного вилучення розмірів без залежності від platform `BitmapFactory` у тестовому оточенні.
**Пропозиція до THIRD_PARTY.md (для затвердження Керівником):**
- Залежності збірки та тестів:
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1` (Apache-2.0)
  - `junit:junit:4.13.2` (EPL-1.0, test only)
  - `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1` (Apache-2.0, test only)
  - `org.mockito:mockito-core:5.15.2` (MIT, test only)
**Що передати далі:**
- Керівнику проєкту: оновити пакетні шляхи в CONTRACTS.md з `com.pdfreader...` на `com.savoj.volumpdf...` та внести за потреби тестові залежності в THIRD_PARTY.md.
- Агенту UI/Android: модуль `core-pdf` готовий до підключення в застосунок `app` (інтерфейс `PageOperationsEngine` та AAR готові).
**Відкриті питання:**
- Немає. Усі тести проходять успішно.

## 2026-11-03 — Керівник проєкту (Затвердження назви продукту Volum PDF)
**Що зроблено:**
- Власник затвердив назву продукту: **Volum PDF** (назва в магазині: **Volum PDF: Reader & Tools**).
- Зафіксовано постійний `applicationId`: `com.savoj.volumpdf`.
- Оновлено `docs/MASTER_SPEC.md`: питання назви закрито (R6).
**Що змінено:** `docs/MASTER_SPEC.md`, `docs/project_log.md`.
**Рішення, які ухвалено:** Усі параметри MVP та брендингу повністю заморожені. Переходимо до Фази 1 (реалізація модулів).


## 2026-11-03 — Керівник проєкту (Затвердження архітектури MVP та контрактів v0.1)
**Що зроблено:**
- Власник затвердив рішення R2 (гібридний рушій `androidx.pdf` + `PDFBox-Android`) та R3 (`minSdk 28`).
- Оновлено `docs/MASTER_SPEC.md`: деталізовано технічні рішення та затверджено точний обсяг функцій MVP (модуль перегляду, останні документи/закладки, 5 інструментів сторінок, реклама за суворими політиками Google Play).
- Оновлено `docs/THIRD_PARTY.md`: `androidx.pdf:pdf-viewer-fragment:1.0.0-beta01` та `com.tom-roush:pdfbox-android:2.0.27.0` переведено в статус постійно дозволених для проєкту. Зареєстровано Google AdMob.
- Розроблено та затверджено `docs/CONTRACTS.md` (версія v0.1): описано моделі та інтерфейси для `core-pdf` (маніпуляції зі сторінками) та `core-ads` (стан згоди UMP, банери, інтерстишели з таймером охолодження).
- Оновлено `.gitignore` для безпечного ігнорування експериментального каталогу `spike/` в гілці `main`.
**Що змінено:** `docs/MASTER_SPEC.md`, `docs/THIRD_PARTY.md`, `docs/CONTRACTS.md`, `.gitignore`, `docs/project_log.md`.
**Рішення, які ухвалено:** Перехід до розробки MVP на модульній архітектурі.
**Що передати далі:** Власнику — затвердження 3–5 варіантів назви проєкту; початок реалізації каркаса проєкту та модуля `core-pdf` відповідними агентами.


## 2026-11-03 — Керівник проєкту (звірка першоджерел, частина 2; рішення власника)
**Рішення власника:** R1 особистий акаунт; R5 закритий код (репозиторій тимчасово публічний); R8 мови uk, en, ru, de; R7 «не діти» прийнято разом із R8 (підтвердити). Записано в MASTER_SPEC.
**ПЕРЕВІРЕНО за першоджерелом (Play Help, 2026-11-03):**
- Реєстраційний збір: US$25 одноразово (answer 6112435). Передоплачені картки не приймаються.
- Нові застосунки й оновлення з 31.08.2026 мають таргетити Android 16 (API 36) (answer 11926878).
- PDFium (pdfium.googlesource.com, LICENSE): текст схожий на BSD-подібну (дозвільну) ліцензію («Redistribution and use… with or without modification»), не Apache, як писав звіт. Збереження повідомлення про авторство обовʼязкове. Повний текст і сторонні частини (third_party) ще не прочитані.
**НЕ ПЕРЕВІРЕНО досі:** дата 16 KB, DSA, Data safety/AdMob, ціни комерційних SDK, пошук/виділення в androidx.pdf на minSdk 28.
**Що змінено:** MASTER_SPEC (рішення), цей запис.

## 2026-11-03 — Керівник проєкту (звірка першоджерел, частина 1)
**Що зроблено:** прочитано першоджерела (raw LICENSE, GitHub API, Maven Central, Google Play Help, developer.android.com), 2026-11-03.
**ПЕРЕВІРЕНО за першоджерелом:**
- MuPDF (ArtifexSoftware/mupdf, COPYING) = GNU AGPL v3 → несумісна із закритим кодом без комерційної ліцензії.
- bblanchon/pdfium-binaries: LICENSE = MIT (обгортка збірки); ліцензію самого PDFium і сторонніх частин усередині збірки ще НЕ читано. Остання збірка chromium/8125 від 2026-10-26.
- PdfBox-Android (TomRoush): Apache-2.0 (GitHub API; файли LICENSE.txt і NOTICE.txt є). УВАГА: останній реліз 2.0.27.0 від 2023-01-02, останній коміт 2024-03-18 → практично не підтримується.
- Google Play Help (answer 14151465): особисті акаунти, створені після 13.11.2023: закритий тест ≥12 тестувальників, безперервно ≥14 днів, потім заявка на production.
- developer.android.com/jetpack/androidx/releases/pdf: останній випуск 1.0.0-beta01 (26.08.2026); у примітках: рендер/читання бекпортовано до minSdk = 28 (для sdk-extension < 13). Звіт Фази 0 (minSdk 31) застарів.
**НЕ ПЕРЕВІРЕНО досі:** ціна акаунта $25, target API 36, дата 16 KB, DSA, Data safety/AdMob, ціни комерційних SDK, функції пошуку/виділення в androidx.pdf на minSdk 28.
**Що змінено:** лише цей запис.
**Рішення, які ухвалено:** жодних.
**Що передати далі:** рішення власника; прототип-спайк за затвердженим варіантом.
**Відкриті питання:** чи достатньо підтримуваний PDFBox-Android (ризик), альтернатива для злиття/розділення.

## 2026-11-03 — Керівник проєкту (рев'ю звіту Фази 0)
**Що зроблено:** прочитав PHASE0_REPORT.md повністю; документи не стерті. Повторним пошуком (теж лише зведення, не першоджерела) підтверджено: закрите тестування 12×14 для особистих акаунтів, створених після 13.11.2023; target API 36 для нових застосунків. Знайдено нову нитку: androidx.pdf за зведенням підтримує minSdk 28 (суперечить звіту: 31). Не підтверджено.
**Що змінено:** лише цей запис.
**Рішення, які ухвалено:** жодних. Звіт приймається як чернетка; ліцензії й суми НЕ вважаємо перевіреними.
**Що передати далі:** власник звіряє першоджерела за розд. 11 (або дозволяє агенту з мережею); потім рішення R1–R8.
**Відкриті питання:** прогалина звіту: застарілий PdfRenderer (API 21+) рендерить, але без пошуку/виділення. Для minSdk 26 пошук потребує іншого шляху.

## 2026-11-03 — Агент-дослідник (Фаза 0)
**Що зроблено:** виконано дослідження Фази 0 за PHASE0_PROMPT.md, усі 10 розділів. Першоджерела НЕ відкривались (мережа в пісочниці недоступна; search_web дає лише зведення) — факти позначені «[З ЗВЕДЕННЯ ПОШУКУ]»/«НЕ ПЕРЕВІРЕНО».
**Що змінено:** створено docs/research/PHASE0_REPORT.md; допис у docs/project_log.md. MASTER_SPEC, CONTRACTS, THIRD_PARTY не чіпані. Git не використовувався.
**Рішення, які ухвалено:** жодних; лише рекомендації (рушій: платформний PdfRenderer + PDFBox-Android; закритий код; AdMob з обмеженими форматами; не MuPDF/iText без комерційної ліцензії).
**Що передати далі:** перевірити першоджерела за розд. 11 звіту; рішення власника R1–R8 (розд. 8); перевірка назви вручну.
**Відкриті питання:** тип акаунта Play; minSdk; суперечність щодо мін. API для SDK Extension 13 (30 vs 31); дати примусу 16 KB; назва продукту.

## 2026-11-03 — Керівник проєкту
**Що зроблено:** створено початкові документи (MASTER_SPEC, CONTRACTS, THIRD_PARTY, project_log) і промт для дослідження Фази 0.
**Що змінено:** docs/*, docs/research/PHASE0_PROMPT.md.
**Рішення, які ухвалено:** жодних технічних рішень поки немає. Робоча назва репозиторію `pdf-reader-android` (не є назвою продукту).
**Що передати далі:** виконати дослідження Фази 0, результат у docs/research/PHASE0_REPORT.md.
**Відкриті питання:** назва продукту й applicationId (після перевірки знаків), рішення з розділу 8 звіту Фази 0.
