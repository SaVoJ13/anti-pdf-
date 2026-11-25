# QA: гілка `docs/rc3-play-declarations` (SHA 7d64703), 2026-11-25

Роль: незалежний QA (не автор). База порівняння: `origin/main` `fb99f35`. Текст автора не змінювався. Play Console, телефон, ADB, PR, main не використовувались.

## Вердикт: QA Verified (лише документи; з 1 дрібним дефектом)

Усі перевірені твердження за кодом і склеєним маніфестом підтверджені, крім одного неточного формулювання (D-1, Low). Висновок «у Play Console нічого міняти не треба» обґрунтований у межах коду; сама консоль не перевірялась.

## 1. Обсяг diff
`git diff origin/main HEAD --stat`: 4 файли, лише docs: `docs/THIRD_PARTY.md` (1 рядок), `docs/project_log.md` (+11), `docs/store/DATA_SAFETY.md` (+3/-2), `docs/store/PLAY_DECLARATIONS_RC3_REVIEW.md` (новий). Код, `build.gradle.kts`, версія, `docs/governance/*`, `STORE_LISTING.md`, `PRIVACY_POLICY.md`, `DECISIONS.md` не змінені. Підтверджено.

## 2. Таблиця перевірених тверджень
| # | Твердження | Результат |
|---|---|---|
| 1 | Лише docs у diff | Підтверджено |
| 2 | AdMob 25.5.0, UMP 4.0.0 (`core-ads/build.gradle.kts:53-54`) | Підтверджено |
| 3 | androidx.pdf 1.0.0-beta01, PdfBox 2.0.27.0 (`core-pdf/build.gradle.kts:48-49`) | Підтверджено |
| 4 | Room 2.6.1, рядки 110-112; `room-compiler` через `annotationProcessor` на `:112` (THIRD_PARTY:23 було `:55`) | Підтверджено |
| 5 | ML Kit Document Scanner 16.0.0-beta1 (`app/build.gradle.kts:115`) | Підтверджено |
| 6 | Плагін oss-licenses 0.10.10: `build.gradle.kts:5`, `app/build.gradle.kts:6` | Підтверджено |
| 7 | Залежності `play-services-oss-licenses` немає в жодному `*.kts/*.gradle/*.toml/*.properties` (grep по проєкту) | Підтверджено |
| 8 | Тест `LicensesDialogHelperTest.kt:79` перевіряє відсутність `play-services-oss-licenses:` | Підтверджено |
| 9 | `LibraryLicensesActivity.kt:44,46` читає `R.raw.third_party_license_metadata` / `third_party_licenses`; є `OssLicenseParser`, `LibraryLicenseDetailActivity` | Підтверджено |
| 10 | Обидві Activity у маніфесті з `exported=false` | Підтверджено |
| 11 | Ресурси `third_party_license_*` генерує плагін при збірці (не в git) | Підтверджено (`app/build/generated/res/releaseOssLicensesTask/raw/...`) |
| 12 | `DECISIONS.md:104` (D1) і `THIRD_PARTY.md:45` (видалено) | Підтверджено |
| 13 | Склеєний release-маніфест: 9 `uses-permission` (`:app:processReleaseManifest`, JDK 21, BUILD SUCCESSFUL) | Підтверджено: 9; рядки 11, 51-57, 63 збігаються |
| 14 | Список дозволів: INTERNET, ACCESS_NETWORK_STATE, AD_ID, ACCESS_ADSERVICES_AD_ID/ATTRIBUTION/TOPICS, WAKE_LOCK, FOREGROUND_SERVICE, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | Підтверджено |
| 15 | У власному маніфесті лише INTERNET (`AndroidManifest.xml:4`); CAMERA/сховища немає | Підтверджено |
| 16 | `uses-feature screen.portrait required=false` (власний 7-9; склеєний 14) | Підтверджено |
| 17 | `<queries>` із `com.android.vending` (склеєний, рядок 48) і `HsdpShimActivity` (172) | Підтверджено |
| 18 | Activity сканера ML Kit з `screenOrientation=portrait` (185-187) | Підтверджено (рядок 187) |
| 19 | Firebase/Crashlytics/Analytics у gradle-файлах відсутні | Підтверджено (grep без збігів) |
| 20 | У склеєному маніфесті є «транзитивні компоненти `play-services-measurement-*`, firebase» | Частково: є лише `com.google.firebase.components:...mlkit...CommonComponentRegistrar` (202-203); `measurement` у маніфесті не знайдено (D-1) |
| 21 | `allowBackup="false"` (`AndroidManifest.xml:13`) | Підтверджено |
| 22 | `recent_documents` і 7 полів (`RecentDocument.java:8-19`) | Підтверджено |
| 23 | Каталоги `documents` (`DocumentCreationHelper.kt:35`), `scanned` (`DocumentScannerLogic.kt:59`), `pending` (`ReaderViewModel.kt:207`) | Підтверджено |
| 24 | Банер `MainActivity.kt:165`, `ToolsActivity.kt:147`; інтерстишел `ToolsActivity.kt:487`; cooldown 5 хв (`AdMobAdsManager.kt:258`); PG (`:267`); child-directed UNSPECIFIED (`:274`) | Підтверджено |
| 25 | У `ReaderActivity` виклику реклами немає | Підтверджено (`adsManager` у `ui/reader/` не знайдено) |
| 26 | 16 KB: `ELF_16KB_CHECK_2026-11-18.md:6` каже, що `.so` немає | Підтверджено; повторна перевірка AAB не робилась (автор це визнає) |
| 27 | `values-night`, `values-uk/de/es/ru`; `STORE_LISTING.md:38` про нічну тему | Підтверджено |
| 28 | `CURRENT_STATE.md` «9 → 9» | Підтверджено (рядок 138) |
| 29 | Зміни DATA_SAFETY.md (власні екрани ліцензій; склеєний маніфест; примітка Privacy Sandbox) відповідають коду | Підтверджено |

Підсумок: перевірено 29 тверджень, підтверджено 28, частково 1, спростовано 0.

## 3. Обґрунтованість висновку для Play Console
- Дозволи і залежності реклами/сканера не змінились (9 → 9, версії ті самі), тож відповіді Data safety і Advertising ID не залежать від D1/R1/O-1: висновок обґрунтований.
- Автор чесно виніс відкритими питання: мета «Analytics» і відповідь про видалення (розд. 5 DATA_SAFETY), `<queries>`/`HsdpShimActivity`/`ACCESS_ADSERVICES_*`, поточний стан консолі. Фактів про Play Console не вигадано: скрізь «НЕ ПЕРЕВІРЕНО».
- Розбіжностей з `main` у `DECISIONS.md`, `STORE_LISTING.md`, `PRIVACY_POLICY.md` не виявлено (файли не змінені; політика «лише INTERNET», AD_ID, нічна тема, 13+ узгоджені з кодом).

## 4. Дефекти
| ID | Серйозність | Опис | Рекомендація |
|---|---|---|---|
| D-1 | Low | `PLAY_DECLARATIONS_RC3_REVIEW.md` розд. 3.1 (рядок «Аналітика / крашлог») стверджує, що у склеєному маніфесті є `play-services-measurement-*`. У маніфесті таких компонентів немає; є лише firebase components registrar від ML Kit. `measurement` згадано в `THIRD_PARTY.md:48` (транзитивні залежності), не в маніфесті. Висновок «власної аналітики немає» не страждає. | Автору: переформулювати. |
| D-2 | Info | DATA_SAFETY.md посилається на «`docs/project_log.md` (запис release-prep)» без дати. Не блокує. | Необов'язково. |

Critical/High/Medium: немає.

## 5. НЕ ПЕРЕВІРЕНО
- Поточні відповіді у Play Console; чи вимагає консоль окремих декларацій для `<queries>`, `HsdpShimActivity`, `ACCESS_ADSERVICES_*`.
- Повторна збірка AAB і 16 KB на `fb99f35` (є лише звіт 2026-11-18).
- Склад транзитивних залежностей (`measurement`) через `dependencies`; доступність GitHub Pages політики за URL; формулювання `STORE_LISTING.md` про виділення тексту на пристрої.
- Робота на пристрої.
