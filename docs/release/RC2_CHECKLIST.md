# RC2 (версія 1.0.1): чекліст і кроки в Play Console

> Гілка `release/rc2` від `main` `cfc5058`. Чекає незалежного QA (`QA Verified` від іншого агента). Нічого не завантажено в Play Console й AdMob. Ключ власника, `keystore.properties` і паролі не відкривались і не читались; усі перевірки нижче зроблено **непідписаним** release (підпис лише тимчасовим debug-ключем на копії для перевірки команд інструкції).

## 1. Свідомий стан закритого тесту: тестові AdMob ID
- `admob.properties` немає, тому release зібрано з **тестовими** ID Google; збірка друкує попередження «УВАГА (AdMob)» і задача `verifyAdmobReleaseManifest` вивела `AdMob: маніфест release OK (тестовий Application ID)`. Прапорець `-Pproduction=true` НЕ вмикався.
- Це **свідомо прийнятий** стан для закритого тесту: бойових ID у власника ще немає, реклама не приноситиме доходу. Публікувати для всіх у Production з тестовими ID **не можна** (правила AdMob). Заміна: `docs/release/ADMOB_SETUP.md`.

## 2. Версія і склеєний маніфест release (з файла збірки, не з пам'яті)
Файл: `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml` після `:app:bundleRelease`.

| Пункт | Вимога | Факт у маніфесті |
|---|---|---|
| `versionCode` / `versionName` | вище завантаженого RC1 (1 / 1.0.0, завантажено 2026-11-17) | **2 / 1.0.1** (`app/build.gradle.kts`, єдине місце; коміт `6b2f357`) |
| `minSdk` / `targetSdk` | 28 / 36 | 28 / 36 |
| `android:allowBackup` | false | `false` |
| `android:debuggable` | немає / false | атрибута в склеєному маніфесті немає |
| `usesCleartextTraffic` | не ввімкнено | атрибута немає (за замовчуванням для targetSdk 36 вимкнено) |
| `android:localeConfig` | `@xml/locales_config` | є; `locales_config.xml`: en, uk, de, ru, es |
| R8 / shrinkResources | увімкнено | `isMinifyEnabled = true`, `isShrinkResources = true`; створено `mapping.txt` (556 966 рядків) |
| `uses-permission` | 9 | **9**: INTERNET, ACCESS_NETWORK_STATE, `com.google.android.gms.permission.AD_ID`, ACCESS_ADSERVICES_AD_ID, ACCESS_ADSERVICES_ATTRIBUTION, ACCESS_ADSERVICES_TOPICS, WAKE_LOCK, FOREGROUND_SERVICE, `com.savoj.volumpdf.anti.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |

**Різниця проти попередніх записів журналу.** Окремого збереженого маніфесту RC1 у репозиторії немає, тому порівняно з текстом: `RELEASE_CANDIDATE_2026-11-17.md` (RC1: AD_ID, INTERNET, ACCESS_NETWORK_STATE, ACCESS_ADSERVICES_*, WAKE_LOCK, FOREGROUND_SERVICE) і запис `project_log.md` 2026-11-18 (маніфест проти `def755a`: 9 → 9). Перелік дозволів збігається; нових дозволів немає, геолокації (FINE/COARSE) немає. Від AdMob 25.5.0 у маніфесті є не-дозвільне нове: `<queries><package android:name="com.android.vending"/></queries>` і `HsdpShimActivity` (`com.google.android.play.core.hsdp.service`, `hsdp` 2.0.1). Побайтове порівняння з маніфестом RC1 **НЕ ПЕРЕВІРЕНО** (його не збережено).

## 3. Результати перевірок цього коміту (JDK 21, вивід Gradle і XML-звіти)
Команда: `./gradlew :app:assembleDebug :app:bundleRelease :app:testDebugUnitTest :core-pdf:testDebugUnitTest :core-ads:testDebugUnitTest :app:lintDebug :core-ads:lintDebug` → `BUILD SUCCESSFUL in 7m 45s`.

| Перевірка | Результат (з XML `build/test-results/testDebugUnitTest`, підрахунок скриптом) |
|---|---|
| `:app` unit | 414 тестів, 0 збоїв, 0 помилок, 0 пропущено (44 файли звітів) |
| `:core-pdf` unit | 107 тестів, 0 збоїв, 0 помилок, **1 пропущено** (як і раніше: symlink на Windows) |
| `:core-ads` unit | 9 тестів, 0 збоїв, 0 помилок, 0 пропущено |
| `lint-results-debug.xml` `:app`, `:core-ads` | 0 елементів із `severity="Error"` в обох |
| AAB | `app/build/outputs/bundle/release/app-release.aab`, 16 320 218 байт, **непідписаний**; SHA-256 починається `64e7d84be8eb9958` |

Перевірено на копії AAB, підписаній тимчасовим debug-ключем: `jarsigner -verify` → `jar verified.`; `keytool -printcert -jarfile` друкує `SHA256:`; пошук рядка `1.0.1` у `base/manifest/AndroidManifest.xml` AAB → знайдено, `1.0.0` → немає. Це підтверджує, що команди з `RC2_BUILD_GUIDE.md` працюють. Підпис ключем власника **НЕ ПЕРЕВІРЕНО** (ключ не відкривався).

## 4. Телефон RZCX326349A (debug-збірка): ЗУПИНЕНО, потрібне рішення координатора
На телефоні вже стоїть **Anti PDF з Google Play**: `com.savoj.volumpdf.anti`, `installerPackageName=com.android.vending`, `versionCode=1`, `versionName=1.0.0`, розділений APK (base + split_config.*). Моя debug-збірка має той самий `applicationId`, але підписана debug-ключем (SHA-256 сертифіката `4170d604…d19d17`), а встановлена з Play підписана ключем Google Play. Встановлення поверх неможливе (конфлікт підпису), а видалення чи заміна Play-версії заборонені завданням. Тому на телефоні **нічого не встановлювалось і не змінювалось** (лише читання `pm`/`dumpsys`; `phone.lock` не створювався, налаштування екрана не чіпались: `screen_off_timeout` 30000, `font_scale` 1.0).
Не виконано, **НЕ ПЕРЕВІРЕНО на пристрої**: запуск 1.0.1, мови uk/de/ru/es (per-app language), шрифт 1.3, екран ліцензій (діалог і «Ліцензії бібліотек»), тестова реклама, форма UMP і пункт «Налаштування конфіденційності» на 25.5.0/4.0.0 у release.
Варіанти для координатора (рекомендація — перший): (1) інший пристрій або емулятор без Play-версії; (2) власник сам вирішує прибрати Play-версію з цього телефона (втрата локальних даних застосунку; рішення власника); (3) тимчасова зміна `applicationId` лише для локальної debug-збірки (зміна конфігурації збірки, потребує окремого рішення, у RC2 не робилась).

## 5. Кроки в Play Console після готовності підписаного AAB (координатор з власником; агент нічого в консолі не робить)
Порядок: власник збирає AAB за `docs/release/RC2_BUILD_GUIDE.md` і повідомляє координатора (шлях, розмір, SHA-256 сертифіката, збіг із відбитком upload-ключа). Далі в браузері, **лише з прямого дозволу власника**:

1. **Завантажити AAB** у закритий трек «Alpha» (id `4700642440770331065`) як **новий випуск**; перевірити, що консоль показує версію 2 (1.0.1) і не скаржиться на підпис.
2. **Примітки до випуску**: вставити `docs/store/RELEASE_NOTES_RC2.md` (5 тегів, кожен ≤ 362 символи; формат тегів на окремих рядках той, що вже пройшов у консолі).
3. **Data safety** (за `docs/store/DATA_SAFETY.md`, п. 5):
   - тип «Приблизне місцезнаходження» (Approximate location): до цілей «Реклама або маркетинг» і «Запобігання шахрайству, безпека й відповідність» **додати «Аналітика»** (Analytics), бо сторінка Google для SDK 25.5.0 називає цю ціль;
   - питання «Чи можна запросити видалення даних»: змінити «Так» на **«Ні»** (обґрунтування в `DATA_SAFETY.md`, розд. 1). Зміна після завершення поточної перевірки Google попередньої форми (подано 2026-11-18).
4. **Декларації й опитування через `<queries>`** для `com.android.vending` і `HsdpShimActivity` (від `hsdp` 2.0.1 у складі AdMob 25.5.0): **НЕ ПЕРЕВІРЕНО**, лише відзначено. Невідомо: чи вимагає політика пакетної видимості Play окремої декларації для `<queries><package>`, і чи потрібні зміни в Data safety / App content через `hsdp`. Координатор перевіряє на сторінках Google і повідомляє: цей чекліст висновку не робить.
5. **Інше, що перевірити в консолі, без змін**: «Реклама» = Yes; Advertising ID = Yes (дозвіл AD_ID є в маніфесті); вікова категорія 13+ (групи «до 13» не вибирати); посилання на політику приватності працює.
6. **Розділ «Огляд публікації»** (Publishing overview) переглянути **перед відправкою**: не повинно бути помилок і незавершених декларацій; відправляти на розгляд лише за прямою командою власника.
7. Після прийняття Google: відновити стан у `CURRENT_STATE.md`/`OWNER_INBOX.md` (запис про те, що випуск 2 відправлено; хто й коли).

## 6. Лише власник (поза цим релізом, нагадування)
Бойові AdMob ID і заміна тестових; рішення про `applicationId` для Production; публікація поза закритим тестом; будь-які платежі.
