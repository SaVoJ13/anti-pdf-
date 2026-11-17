# QA: політика приватності для GitHub Pages (PR #3, docs/privacy-policy-pages, c834aa9 від main 1f8d218)

Дата: 2026-11-17. Роль: незалежний QA. Телефон не використовувався (поведінка застосунку не змінювалась).

## Вердикт: QA Verified З ЗАУВАЖЕННЯМИ
Критичних і середніх дефектів немає; 3 низькі розбіжності політики з кодом (Д1 бажано виправити текст до подачі в Play).

## Результати перевірок
1. Плейсхолдери: `[CONTACT EMAIL]` / `example.com` лишились лише в історичних файлах (старі записи docs/project_log.md, docs/governance/OWNER_INBOX.md, docs/qa/QA_release-prep_2026-11-16.md) та spike/. В app/build.gradle.kts, docs/store/*, docs/privacy/* їх немає. OK.
2. docs/privacy/index.html: жодних script/img/iframe/link/@import/url(); лише inline CSS; єдині http-посилання: дві сторінки policies.google.com (звичайні `<a>`). lang=uk, charset, viewport, color-scheme є; секції uk і en (#uk/#en, перемикач); пошта appvsdeveloper@gmail.com; дата 2026-11-17; docs/.nojekyll є (0 байт). Headless Chrome (знімки у scratchpad, поза репо): при ширині 360 px текст вміщається без горизонтального скролу; темна тема (симуляція prefers-color-scheme:dark) читабельна. Прямий знімок headless на 390 px обрізав праворуч лише через мінімальну ширину вікна headless; в iframe 360 px вада не відтворюється.
3. Звірка з кодом: нижче.
4. BuildConfig.PRIVACY_POLICY_URL = https://savoj13.github.io/anti-pdf-/privacy/ (build.gradle.kts і згенерований BuildConfig.java); PrivacyMenuTest 3/0/0. OK.
5. JDK 21: cleanTestDebugUnitTest, assembleDebug, testDebugUnitTest (:app, :core-pdf, :core-ads), lintDebug: BUILD SUCCESSFUL. XML: :app 353/0/0, :core-pdf 107/0/1, :core-ads 6/0/0. Збігається з очікуванням.
6. Змінені файли LF (CR=0). Секретів/ключів/keystore/паролів у diff немає. CLAUDE.md, AGENTS.md, .agents/**, конституція не змінені (diff: 8 файлів: build.gradle.kts, .nojekyll, DECISIONS.md, privacy/index.html, project_log.md, DATA_SAFETY.md, PRIVACY_POLICY.md, STORE_LISTING.md). Розділ 3.2 не змінено. У project_log додано зайвий порожній рядок (косметика).
7. Тексти зрозумілі; не стверджують «жодних даних не збираємо» (прямо сказано, що AdMob збирає). Критичних юридичних неправд немає.

## Звірка з кодом: підтверджено
- Маніфест: лише INTERNET; allowBackup=false; exported лише MainActivity (MAIN + VIEW application/pdf content/file), решта false; FileProvider, провайдерів, сервісів, акаунтів немає.
- core-ads: AdMob 23.6.0 + UMP 3.1.0; реклама лише при canRequestAds(); isPrivacyOptionsRequired/showPrivacyOptionsForm; банер на головному екрані й екрані інструментів; інтерстиціал лише в ToolsActivity (кулдаун 5 хв). Firebase/аналітики/краш-звітів/власних HTTP-викликів у коді немає.
- Room recent_documents: uri, title, pageCount, fileSizeBytes, lastOpenedTimestamp, lastPageRead, isFavorite: збігається з політикою.
- SharedPreferences: тема (ReadingPreferences), dynamic colors (AppearancePreferences): вірно.
- ViewerCopyCache: cacheDir/view, чиститься: вірно.
- Логи: Log.w/i лише клас/вид помилки, байти/мс, ім'я кеш-копії view_<nano>.pdf, хеш ключа; URI/ім'я файлу/пароль не логуються (ErrorMessages.report навмисно без повідомлення).

## Розбіжності (усе локально на пристрої, нічого не передається)
- Д1 (низький, на межі середнього): фраза «готові сторінки передаються застосунку, який зберігає їх туди, куди ви вкажете» неточна: ScanDocumentActivity/DocumentScannerLogic постійно зберігають скан-PDF у внутрішньому сховищі застосунку filesDir/scanned (і додають у «Останні»); місце обирається лише коли користувач далі сам зберігає/ділиться. Рекомендація: «зберігає їх у власному сховищі застосунку, а копію ви можете зберегти, куди вкажете». Твердження «ми не отримуємо і не зберігаємо скани» лишається правдивим.
- Д2 (низький): політика не згадує filesDir/documents (нові документи), filesDir/pending (незбережені правки: текст, підписи/малюнки, до 7 днів, PendingStore.MAX_AGE_MS) і тимчасові файли в cacheDir (organizer_src_, volum_*_saf_). «Очистити історію» їх не видаляє; видаляються очищенням даних/видаленням застосунку (розділ 6 це каже, тож не хибно, лише неповно). Рекомендація: речення про локальне зберігання створених/відсканованих документів і незбережених правок.
- Д3 (інформативно): AD_ID у маніфесті :app не видно (приходить зі злитого маніфесту SDK; DATA_SAFETY посилається на перевірку в журналі). Дефекту немає.
- Відкрите питання Власника (вказане автором): вікова категорія (13/16 у політиці проти 18+/13+ у PUBLICATION_READINESS).

## Підсумок
Критичних 0, середніх 0, низьких 3. Збірка і тести зелені. Видимість репозиторію і Pages не чіпались.
