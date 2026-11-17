# Реліз-кандидат RC1 (2026-11-17)

> Зібрано локально з `main` (`fc5636d` + docs-коміт). НІЧОГО не завантажено й не опубліковано. Ключ і `keystore.properties` не друкувались і не копіювались.

## Артефакт
| Поле | Значення (з виводу збірки) |
|---|---|
| Команда | `./gradlew :app:bundleRelease` (JDK 21) |
| Файл | `app/build/outputs/bundle/release/app-release.aab` (у git не потрапляє) |
| Розмір | **15 402 232 байти** (≈14,7 МБ) |
| SHA-256 (початок) | `8cb1bf8acb4170c5…` |
| `applicationId` | `com.savoj.volumpdf.anti` (рішення про Play лишається за власником, `OWNER_INBOX.md` п. 8) |
| `versionCode` / `versionName` | **1 / 1.0.0** |
| Підпис | `jarsigner -verify`: «jar verified», сертифікат самопідписаний (ключ завантаження, очікувано). Деталі сертифіката не друкувались. |
| R8 / shrinkResources | увімкнено |
| `allowBackup` | `false` |
| `debuggable` | немає в склеєному маніфесті релізу |

## Перевірки маніфесту (склеєний release)
- `com.google.android.gms.permission.AD_ID`: **присутній** (потрібна декларація Advertising ID у Play Console: Yes).
- Також від рекламного SDK: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_ADSERVICES_*`, `WAKE_LOCK`, `FOREGROUND_SERVICE`. Широкого доступу до сховища немає.

## Тестові ідентифікатори AdMob
- Тестовий APPLICATION_ID у `AndroidManifest.xml:19` і три константи `TEST_*` у `AdMobAdsManager.kt:250-252`; використані лише для блоку банера (рядок 138) і interstitial (рядок 212).
- У коді немає `setTestDeviceIds`, `ConsentDebugSettings` чи `BuildConfig.DEBUG`-гілок для реклами: тестових налаштувань у логіці, що має бути бойовою, немає.
- **Тестові ID лишаються до рішення власника.** З ними публікувати НЕ МОЖНА: реклама не принесе доходу й порушує політику. Заміна: створити застосунок і блоки в AdMob, передати ID агенту через локальний файл (`OWNER_INBOX.md` п. 10.5).

## Тести на тому ж `main` (XML Gradle, JDK 21)
`:app` 403/0/0/0; `:core-pdf` 214 записів 0/0/2 пропущено; `:core-ads` 14 записів 0/0/0; `lintDebug` `:app` 0 помилок.

## Що блокує публікацію (усе за власником, п. 3.2)
1. Рішення `applicationId`/назви (незмінні після першої публікації).
2. Акаунт Play Console ($25), статус торговця; закрите тестування 12×14 днів.
3. Реальні AdMob-ідентифікатори.
4. Play App Signing, заповнення App content (13+, реклама Yes, Data safety, Advertising ID Yes).
5. Матеріали магазину (чернетки: `docs/store/STORE_ASSETS_DRAFT.md`).
6. Незакриті НЕ ПЕРЕВІРЕНО: `NOT_VERIFIED_2026-11-17.md`, особливо Android 9–11 і підписаний реліз на пристрої.

Вердикт: технічно RC1 зібраний і підписаний; **до публікації не готовий** (п. 1-5 вище).
