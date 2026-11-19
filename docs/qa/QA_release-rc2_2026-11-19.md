# QA: `release/rc2` @ f16e172 (версія 1.0.1), 2026-11-19

Роль: «QA і безпека». Не автор, самоверифікація відсутня. Код і гілку `release/rc2` не змінював, не зливав. Worktree `pdf-qa-rc2` від `origin/release/rc2`. Ключі власника, `keystore.properties`, паролі, Play Console, AdMob не відкривав. Телефон RZCX326349A не використовував. Числа лише з виводу команд.

## 0. Голова гілки
`git fetch`; `origin/release/rc2` = `f16e172fd2e91335189d2aa3486ea428ae3eba65`; `git log main..origin/release/rc2`: 6b2f357, 0c1d3b1, 4de3389, c9f2f0a, f16e172 (5 комітів). `main` = `cfc50582c36f76a530fa0dfb92a692aaebae9d13`. Збігається із завданням.

## 1. Версія і обсяг: OK
- `app/build.gradle.kts`: `versionCode = 2`, `versionName = "1.0.1"` (рядки 32-33). Було 1 / 1.0.0 (RC1). Інших місць із `versionCode|versionName` у `*.kts`, `*.gradle`, `*.properties`, `*.toml` немає.
- `git diff main...HEAD --stat`: рівно 7 файлів (222 додано, 2 видалено): `app/build.gradle.kts`, `docs/governance/CURRENT_STATE.md`, `docs/governance/DECISIONS.md`, `docs/project_log.md`, `docs/release/RC2_BUILD_GUIDE.md`, `docs/release/RC2_CHECKLIST.md`, `docs/store/RELEASE_NOTES_RC2.md`. Код змінено лише двома рядками версії.
- Маркери конфлікту в доданих рядках: немає. Пошук `ca-app-pub|AIza|storePassword=|keyPassword=|BEGIN (RSA|PRIVATE)|.jks|.keystore|password =` у доданих рядках diff: збігів немає. Справжніх AdMob ID, паролів, шляхів до ключа в diff немає (у документах лише згадки назв файлів).

## 2. Збірка JDK 21 (jbr-21.0.11): OK, числа збігаються із заявою автора
Команда: `gradlew :app:assembleDebug :app:bundleRelease :app:testDebugUnitTest :core-pdf:testDebugUnitTest :core-ads:testDebugUnitTest :app:lintDebug :core-ads:lintDebug`, без `-Pproduction`, без `admob.properties`.
- `BUILD SUCCESSFUL in 6m 18s` (275 задач виконано), exit 0. Виведено попередження «УВАГА (AdMob): admob.properties не знайдено…» і `AdMob: маніфест release OK (тестовий Application ID)`.
- XML-звіти (підрахунок скриптом): `:app` 44 файли, 414 тестів, 0 збоїв, 0 помилок, 0 пропущено; `:core-pdf` 16 файлів, 107, 0, 0, **1 пропущено**; `:core-ads` 2 файли, 9, 0, 0, 0.
- Lint: `:app` 0 `severity="Error"` (151 Warning); `:core-ads` 0 Error (4 Warning).
- AAB `app/build/outputs/bundle/release/app-release.aab`: **16 320 218 байт**, SHA-256 починається `64e7d84be8eb9958`: збігається із заявою. Непідписаний (немає `keystore.properties` у worktree).

## 3. Склеєний маніфест release: OK
Файл `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`:
- `versionCode="2"`, `versionName="1.0.1"`; `minSdkVersion="28"`, `targetSdkVersion="36"`; `allowBackup="false"`; `localeConfig="@xml/locales_config"`.
- `debuggable` і `usesCleartextTraffic` у файлі відсутні (grep без збігів).
- `uses-permission`: 9: INTERNET, ACCESS_NETWORK_STATE, `com.google.android.gms.permission.AD_ID`, ACCESS_ADSERVICES_AD_ID, ACCESS_ADSERVICES_ATTRIBUTION, ACCESS_ADSERVICES_TOPICS, WAKE_LOCK, FOREGROUND_SERVICE, `com.savoj.volumpdf.anti.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Це 9 → 9, перелік збігається з моїм власним QA `fix/stage1-ads-16kb` (9, ті самі імена). Порівняння з `main` `cfc5058`: окремий маніфест `main` я не збирав (6 хв). Підстава: diff `main...HEAD` не торкається залежностей, маніфестів і ресурсів, лише `versionCode/versionName`, тож перелік дозволів тотожний за побудовою. Побайтове порівняння зі зібраним `main` НЕ ПЕРЕВІРЕНО.
- R8/shrinkResources: `isMinifyEnabled = true`, `isShrinkResources = true` (`app/build.gradle.kts:60-61`); створено `mapping.txt` (556 966 рядків), `usage.txt`, `seeds.txt`, `resources.txt`.

## 4. `RELEASE_NOTES_RC2.md`: є зауваження
- Довжини (мій підрахунок Python `len` на тексті між тегами): en 296, uk 318, de 322, ru 318, es 337. Усі ≤ 362. Збігається із заявою автора.
- Теги `<en>…</en>`, `<uk>`, `<de>`, `<ru>`, `<es>`: кожен на окремому рядку (regex по блоку коду знайшов рівно 10 рядків-тегів).
- Зміст проти `main`: мови de/ru/es і вибір мови (`localeConfig`): є в `main`. Екран ліцензій і «Ліцензії бібліотек»: є (`licenses_libraries_button`). AdMob 25.5.0 / UMP 4.0.0: є. «Налаштування конфіденційності» в меню: `action_privacy_settings` є в усіх 5 мовах, назви в тексті приміток збігаються з рядками застосунку (Privacy settings / Налаштування конфіденційності / Datenschutzeinstellungen / Настройки конфиденциальности / Ajustes de privacidad).
- **Дефект D2 (середній, зміст): «Виправлення перекладів» / «Translation fixes» / «Korrekturen an Übersetzungen» / «Исправления переводов» / «Correcciones de traducciones» не відповідає змінам для користувачів RC1.** Автор сам пише, що виправлень продукту після RC1 немає. `git diff fc5636d..main` для `values/strings.xml` і `values-uk/strings.xml` дає лише 4 доданих рядки (`licenses_libraries_button`, `licenses_load_failed` у en і uk), жодного зміненого перекладу. Виправлення `f89d94a` стосувалися перекладів de/ru/es, яких у 1.0.0 не було: користувачі RC1 жодного «виправлення» не побачать. Це вигадане виправлення в публічних примітках. Усунення: прибрати останнє речення в усіх 5 мовах (довжина лише зменшиться).
- Переклади носії не вичитували; на мій огляд явно неприродного немає (de «Neu in 1.0.1…» і es «Novedades de 1.0.1…» звичні). НЕ ПЕРЕВІРЕНО носіями.

## 5. `RC2_BUILD_GUIDE.md` (очима власника) і підпис: є зауваження
Зведено з `docs/store/RELEASE_SIGNING.md` і `app/build.gradle.kts:9-17,40-67`: `keystore.properties` у корені, `hasReleaseSigning` вимагає всі 4 ключі й існування `storeFile`; без цього release збирається непідписаним без помилки. Інструкція сходиться з конфігурацією (команда `.\gradlew.bat :app:bundleRelease`, перевірка `Test-Path .\keystore.properties`). Пароль у тексті немає; сказано вводити лише у власному терміналі; `Test-Path` не показує вміст.
Перевірка команд кроку 3 на копії AAB (у scratchpad), підписаній тимчасовим debug-ключем (`~\.android\debug.keystore`, не ключ власника); копію й розпаковане видалено:
- Крок 3.1 `Get-Item … | Select-Object Name, Length` і `Get-FileHash -Algorithm SHA256`: працюють (підписана копія 16 479 141 байт).
- Крок 3.2 `Select-String … -Pattern 'versionCode|versionName'` на шляху `…\merged_manifests\release\processReleaseManifest\AndroidManifest.xml`: шлях існує, друкує рівно два рядки `android:versionCode="2"` і `android:versionName="1.0.1"`.
- Крок 3.3 `jarsigner.exe -verify $aab` на підписаній копії: `jar verified.` (+ попередження про самопідписаний сертифікат, як сказано в інструкції).
- Крок 3.4 `keytool.exe -printcert -jarfile $aab | Select-String "SHA256|Owner"`: друкує `Owner:` і `SHA256:` лише сертифіката (відкритий відбиток), ключ не розкривається.
- AAB: `base/manifest/AndroidManifest.xml` містить рядок `1.0.1` (1 збіг), `1.0.0` (0).
**Дефект D1 (середній, глухий кут): неправильний очікуваний текст для непідписаного файла.** Крок 3.3 каже: «Якщо написано `jar is unsigned` — файл НЕ підписаний». Фактично на непідписаному AAB (мій реальний вивід) `jarsigner -verify` пише **`no manifest.`**, а `keytool -printcert -jarfile` пише `Not a signed jar file`. Фраза `jar is unsigned` не з'являється; власник побачить незрозуміле `no manifest.`, а розділ «Якщо щось пішло не так» цього не покриває. Усунення: у 3.3 написати «`jar verified.` = підписано; `no manifest.` або `Not a signed jar file` = НЕ підписано».
**Зауваження D3 (низький):** якщо `keystore.properties` немає (`False`), інструкція каже «напиши координатору», але не посилається на `RELEASE_SIGNING.md` (кроки 1-2: створення файла); для випадку «файл є, але `storeFile` вказує на неіснуючий шлях» збірка мовчки дає непідписаний AAB (поведінка `hasReleaseSigning`), що ловиться лише кроком 3.3 (див. D1). Не критично, бо RC1 вже підписувався, ключ існує.
**Зауваження D4 (низький):** шлях JDK `C:\Users\cocro\.jdks\jbr-21.0.11\bin` вписано жорстко; збігається з `org.gradle.java.home` у `gradle.properties`, для цього власника коректно.
Спостереження: у моєму прогоні команди 3.3 і 3.4, виконані в одному скрипті, вивели порожні рядки через форматування `Select-String` (повторний запуск окремо дав правильні `Owner:` і `SHA256:`). Власник запускатиме їх окремо; на коректність це не впливає.

## 6. `RC2_CHECKLIST.md`: у цілому повний, одне НЕ ПЕРЕВІРЕНО
- Покрито: завантаження в Alpha як новий випуск з перевіркою версії 2; примітки (5 тегів); Data safety: Approximate location + Analytics, «видалення» → «Ні»; `<queries>` для `com.android.vending` і `HsdpShimActivity` позначено **НЕ ПЕРЕВІРЕНО**; Advertising ID = Yes; вікова категорія 13+; Publishing overview; пост-крок про `CURRENT_STATE`/`OWNER_INBOX`.
- Звірка з `DATA_SAFETY.md`: п. 39: Approximate location, Required, цілі Advertising, Analytics, Fraud prevention; рядок 26: видалення = «Ні»; рядок 3: у поданій формі поки «Так», зміна запланована; п. 4: AD_ID; п. 29: 13+. Суперечностей немає.
- Маніфестна таблиця чекліста (розд. 2) збігається з моїм власним зведенням (див. п. 3).
- **Зауваження D5 (низький): ідентифікатор треку Alpha `4700642440770331065` і твердження «RC1 завантажено 2026-11-17» не підтверджені жодним документом у `main`** (`git grep` по `main:docs` ідентифікатор не знаходить; `RELEASE_CANDIDATE_2026-11-17.md` каже «до публікації не готовий»). Присутні лише в документах цієї гілки. Правдоподібно (на телефоні стоїть Play-версія 1.0.0), але джерело не вказано. НЕ ПЕРЕВІРЕНО мною (Play Console не відкривав).
- У чеклісті немає: «Release name» випуску, списку країн/тестувальників (поза обсягом завдання; низький).

## 7. Документація: OK
- `project_log.md`: новий запис зверху, є розділ «Простими словами» (5 пунктів, зрозумілі непрограмісту), «Що зроблено» з комітами, команди й результат, «Що передати далі», «НЕ ПЕРЕВІРЕНО». Числа (414/107(1)/9, 16 320 218 байт, 0 помилок lint) збігаються з моїм прогоном.
- `CURRENT_STATE.md`: розділ «Оновлення 2026-11-19» з фактами й НЕ ПЕРЕВІРЕНО, коректно «Не злито: потрібен QA Verified». Заголовок файла все ще каже «Оновлено: 2026-11-14» (низький, застаріла шапка; не нове).
- `DECISIONS.md`: один новий рядок 2026-11-19 (версія, тестові AdMob ID, зупинка на телефоні) з обґрунтуванням.

## Зведення дефектів
| № | Рівень | Де | Суть | Доказ |
|---|---|---|---|---|
| D1 | середній | `RC2_BUILD_GUIDE.md`, крок 3.3 | очікується `jar is unsigned`, насправді `no manifest.` | `jarsigner -verify` на непідписаному AAB: `no manifest.`; `keytool`: `Not a signed jar file` |
| D2 | середній | `RELEASE_NOTES_RC2.md` (5 мов) | «Виправлення перекладів» не відповідає змінам для користувачів RC1 | diff `fc5636d..main` для en/uk: лише 4 нові рядки, змінених немає; de/ru/es у 1.0.0 не було |
| D3 | низький | `RC2_BUILD_GUIDE.md` | немає посилання на `RELEASE_SIGNING.md` при `False`; мовчазний непідписаний збір при хибному `storeFile` | `app/build.gradle.kts:15-17` |
| D4 | низький | `RC2_BUILD_GUIDE.md` | жорсткий шлях JDK | збігається з `gradle.properties` |
| D5 | низький | `RC2_CHECKLIST.md` | ID треку Alpha і дата завантаження RC1 без джерела у `main` | `git grep` у `main:docs` |
Блокуючих дефектів немає. D1 і D2 документальні, код не змінюють; виправляє автор на `release/rc2` до злиття (дві правки тексту).

## НЕ ПЕРЕВІРЕНО (усе на пристрої, підпис ключем власника)
- Усе на пристрої: запуск 1.0.1, мови uk/de/ru/es, шрифт 1.3, екран ліцензій, реклама, UMP, «Налаштування конфіденційності» (телефон не використовувався за завданням; на ньому Play-версія 1.0.0).
- Підпис ключем власника й збіг відбитка з upload key у Play Console; команди перевірено лише на копії, підписаній debug-ключем.
- Побайтове порівняння маніфесту з RC1 і зі зібраним `main` `cfc5058` (RC1 не збережено; `main` не збирав: порівняння за побудовою diff).
- Play Console (трек Alpha, його ID, Data safety, декларації щодо `<queries>`/`HsdpShimActivity`); чи Google Play прийме `<queries>`.
- Переклади приміток носіями мов.

## Вердикт
**QA Verified release/rc2 @ f16e172**, за умови, що автор виправить D1 і D2 (обидва документальні) до злиття; блокуючих дефектів немає.
