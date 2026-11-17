# QA (дельта): політика приватності, PR #3, c834aa9..fd726cf

Дата: 2026-11-17. Роль: незалежний QA. Телефон не використовувався; розділ 3.2 і Pages не чіпались.

## Вердикт: QA Verified (на fd726cf)
Критичних 0, середніх 0. Д1 і Д2 попереднього QA виправлені й підтверджені кодом. Низьких зауважень немає (є лише одна інформативна примітка нижче).

## 1. Обсяг дельти
Змінено рівно 3 файли: `docs/privacy/index.html` (uk і en), `docs/store/PRIVACY_POLICY.md`, `docs/project_log.md` (лише переніс порожнього рядка). Коду, маніфесту, дозволів, build-файлів, секретів у дельті немає. CR=0 у всіх трьох файлах (LF).

## 2. Звірка тверджень із кодом
- «скани (`scanned`)»: `DocumentScannerLogic.persistScannedPdf` пише у `filesDir/scanned` (його викликає `ScanDocumentActivity:132`); запасний шлях `imagesToPdf` теж у `filesDir/scanned` і викликає `recordDocumentOpened` (додає в «Останні»). Нове формулювання («зберігає скан як PDF у власному закритому сховищі і додає до “Останні”; копію можна зберегти чи надіслати») точне. Д1 закрито. «Ми не отримуємо і не зберігаємо ваші скани» лишається правдивим (мережевого коду немає).
- «нові документи (`documents`)»: `DocumentCreationHelper:35`, `filesDir/documents`. Вірно.
- «незбережені правки (`pending`) ... видаляються автоматично через 7 днів»: `PendingStore` (`filesDir/pending`, `MAX_AGE_MS = 7 діб`), `VolumPdfApp.onCreate` викликає `purgeOlderThan` при запуску. Вірно (чистка відбувається під час запуску застосунку, формулювання «автоматично» не вводить в оману). Текст і підписи, додані користувачем, зберігаються в pending: вірно.
- «тимчасові файли в кеші»: `cacheDir/view` (ViewerCopyCache), `organizer_src_*`, `volum_markup_saf_*`, `volum_annot_saf_*` у cacheDir. Вірно, узагальнення прийнятне.
- «Очистити історію» прибирає лише «Останні»: `DocumentRepository.clearHistory` викликає тільки `recentDocumentDao.clearAll()` / `clearNonFavorites()`; файли в filesDir не чіпає. Твердження в політиці (розділи 2 і 6) точне. Д2 закрито.
- Розділ 6 (права/видалення): очищення даних Android або видалення застосунку видаляє filesDir і cacheDir; «PDF, збережені в інших місцях, не видаляються» вірно.
- Нічого зайвого не стверджується і нічого суттєвого не замовчується; формулювання «ми нічого не збираємо» відсутнє (AdMob/UMP описано).

## 3. Синхронність uk/en
У `index.html` і `PRIVACY_POLICY.md` ті самі три правки (локальне сховище, сканер, розділ 6) за змістом збігаються в uk та en; назви каталогів, термін 7 днів, формулювання про «Очистити історію» однакові.

## 4. Зовнішні ресурси і вигляд
`<script>`, `<link>`, `<img>`, `<iframe>`, `@import`, `url()`, `src=`: немає. Єдині http(s)-посилання: два `<a href="https://policies.google.com/...">` у розділі 3.1 (uk, en). Headless Chrome, iframe шириною 360 px: scrollWidth = clientWidth = 345 (без горизонтального скролу; різниця 15 px це смуга прокрутки). Знімки/обгортка лише в scratchpad, поза репо.

## 5. Збірка і тести (JDK 21, `org.gradle.java.home=jbr-21.0.11`, детачений HEAD origin/docs/privacy-policy-pages)
`cleanTestDebugUnitTest :app:assembleDebug :app:testDebugUnitTest :core-pdf:testDebugUnitTest :core-ads:testDebugUnitTest :app:lintDebug`: BUILD SUCCESSFUL (150 завдань, 147 виконано, тести не з кешу). Числа з XML (tests/failures/errors): `:app` 353/0/0, `:core-pdf` 107/0/0 (1 skipped), `:core-ads` 6/0/0. Збігаються з очікуваними.

## Примітки (не дефекти)
- Відкрите питання Власника з попереднього QA (вікова категорія 13/16 проти 18+/13+) лишається за Власником.
- AD_ID у маніфесті :app не видно (приходить зі злитого маніфесту SDK); без змін щодо попереднього QA.
