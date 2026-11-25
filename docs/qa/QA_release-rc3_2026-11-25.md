# QA: `origin/release/rc3` (cb8114e), 2026-11-25

Незалежний QA (не автор). Гілка QA: `qa/release-rc3`. База `main` = `fb99f35`. Ключ, `keystore.properties`, паролі, jks не відкривались. Телефон, ADB, Play Console, PR, main не чіпались.

## Вердикт: QA Verified (код і збірка, без підпису); документи придатні, є 3 дефекти низької серйозності та перелік НЕ ПЕРЕВІРЕНО

## 1. Diff проти origin/main
- 6 файлів: `app/build.gradle.kts` (M), `docs/governance/CURRENT_STATE.md` (M), `docs/project_log.md` (M), `docs/release/RC3_BUILD_GUIDE.md`, `docs/release/RC3_CHECKLIST.md`, `docs/store/RELEASE_NOTES_RC3.md` (A).
- Код: лише `versionCode 2->3`, `versionName "1.0.1"->"1.0.2"`. Залежності, дозволи, маніфест, `app/proguard-rules.pro`, AGENTS.md, CLAUDE.md, `.agents`, конституція: у diff відсутні. Пройдено.

## 2. Примітки RELEASE_NOTES_RC3.md
- Python `len` (текст між тегами): uk 359, en 274. Обидва ≤ 500 і ≤ 362. Пройдено. Тегів de/ru/es немає (визнано автором).
- Звірка з кодом `main`: групи інструментів (`tools_group_*`, `ToolGroups.kt`), порожні стани (`empty_recents_*`, `empty_search_*`), пошук (`SearchView` у `MainActivity`), «Друкувати» (`action_print` у `menu_reader.xml`, `PdfPrintDocumentAdapter`), «Зберегти копію» (`action_save_copy`), вписування в A4 (`ImagePageGeometry`: 595x842 pt), поворот зображення (`SelectedFileAdapter`, коміт 96adaba), EXIF (`ExifOrientation.kt`, коміт 4188580). Вигаданих функцій не виявлено. Усе це відсутнє в `ed93b7e` (RC2), тобто справді нове відносно RC2 (група 79 комітів).

## 3. Склеєний маніфест release RC3 vs RC2 (`ed93b7e`)
`:app:processReleaseManifest` на обох (по черзі в одному worktree, JDK 21), `diff` файлів. Різниць рівно 4, збігаються з заявою автора:
1. `versionCode 2->3`, `versionName 1.0.1->1.0.2`;
2. `+uses-feature android.hardware.screen.portrait required=false`;
3. `+LibraryLicensesActivity`, `+LibraryLicenseDetailActivity` (`exported=false`);
4. `-OssLicensesMenuActivity`, `-OssLicensesActivity` (gms).
Дозволи (9), `queries`, intent-filters, решта компонентів: без змін. Пройдено.

## 4. R8 / AAB
- `:app:bundleRelease --no-daemon` (без підпису, без `admob.properties`: тестові ID, друкує «УВАГА (AdMob)`): BUILD SUCCESSFUL in 5m 48s; задача `minifyReleaseWithR8` виконана.
- AAB: 16 320 010 байт, SHA-256 `3a8f0dba94db9f7f...` (непідписаний). `mapping.txt` створено (556 982 рядки). `.pro` у diff немає.
- У `mapping.txt`: `LibraryLicensesActivity`, `LibraryLicenseDetailActivity`, `LicensesDialogHelper`, `OssLicenseParser` збережено; у `resources.txt` `raw:third_party_licenses` reachable. В AAB (`unzip -l`): `base/res/raw/third_party_licenses` (435 170 б.), `base/res/raw/third_party_license_metadata` (6 244 б.), `base/dex/classes.dex`. Класів `OssLicenses*` (gms) у `mapping.txt` немає (прибрано правильно).
- Роботу R8-збірки на пристрої НЕ ПЕРЕВІРЕНО.

## 5. 16 KB
`unzip -l app-release.aab | grep -c '\.so'` = 0. Нативних бібліотек немає: вимога 16 KB не стосується. Пройдено.

## 6. Гайд і чекліст (читання)
- Команди PowerShell правильні; шлях `app\build\outputs\bundle\release\app-release.aab`; `jarsigner -verify`, `keytool -printcert -jarfile`, порівняння SHA256 з Upload key certificate у Play Console; паролів і ключів немає; кроки по одному з рядком «Що надіслати»; структура збігається з RC2 і навіть повніша (перевірка `Test-Path keystore.properties`, JDK 21 прописано абсолютним шляхом). Придатні Власнику-непрограмісту.
- Команди гайда не виконувались у підписаному вигляді (ключа немає). Синтаксис `& "$env:JAVA_HOME\bin\..."` те саме, що в RC2.

## 7. Тести (JDK 21, числа лише з XML)
| Набір | файлів XML | тестів | failures | errors | skipped |
|---|---|---|---|---|---|
| `:app:testDebugUnitTest` | 49 | 451 | 0 | 0 | 0 |
| `:core-pdf:testDebugUnitTest` | 18 | 137 | 0 | 0 | 1 |
`lint-results-debug.xml` (`:app`): 0 Error, 143 Warning. Збігається з числами автора (451/0/0/0; 137/0/0/1; 0/143). BUILD SUCCESSFUL.
Примітка: перший запуск з `--rerun-tasks` обірвався («Gradle build daemon disappeared», `hs_err` JVM-збій на цій машині, не код); повторний запуск без `--rerun-tasks` пройшов, `hs_err` файл прибрано. Артефакти lint свіжі (час запису після повтору).

## Дефекти
Critical/High/Medium: немає.
- **Low L-1.** У примітках фраза «вписуються в ширину A4» неточна: за `ImagePageGeometry` масштаб = min(1, 595/ширина, 842/висота), тобто вписування в A4 за шириною і висотою, без збільшення малих зображень. Для користувача не критично; за бажанням змінити на «вписуються в розмір сторінки A4».
- **Low L-2.** «Дрібні виправлення»/«Також виправлено помилки» у примітках узагальнені; конкретика (portrait, ліцензії, plurals es) відповідає комітам, але claim не деталізовано. Прийнятно.
- **Low L-3.** Для de/ru/es тегів немає: поведінку Play без тегу НЕ ПЕРЕВІРЕНО (автор це визнав; рішення Власника).

## НЕ ПЕРЕВІРЕНО
Підпис ключем Власника і збіг відбитка з Play Console; запуск release (R8) на пристрої; реклама/UMP/ліцензії/друк/збереження копії/фото/скани на телефоні; фільтрація пристроїв у Play Console після `uses-feature` portrait; Data safety без змін; виконання кроків гайда Власником; TalkBack, Android 9-11, планшет; поведінка Play без тегів de/ru/es.
