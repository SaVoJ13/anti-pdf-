# QA: дослідження «Захистити паролем» (2026-11-25)

Гілка автора: `docs/research-password-protect` (f00cec3). QA-гілка: `qa/research-password-protect`. Перевіряв незалежний QA (не автор). Текст автора не змінювався.

## Вердикт: QA Verified (лише документ, не код)
Документ точний, нічого не впроваджує, зрозумілий Власнику. Критичних і середніх дефектів немає. Пристрій, Play, ADB не використовувались.

## 1. Обсяг diff проти origin/main
Лише `docs/research/PASSWORD_PROTECT.md` і `docs/project_log.md`. Код і governance не змінені: підтверджено.

## 2. Перевірені твердження
| # | Твердження | Результат |
|---|---|---|
| 1 | pdfbox-android 2.0.27.0 у `core-pdf/build.gradle.kts:49` як `api` | Підтверджено |
| 2 | `copyEncryption`: `StandardProtectionPolicy(pw,pw,AccessPermission())`, `encryptionKeyLength` (40/128/256, інакше 128), `isPreferAES = keyLength>=128`, `protect`; лише якщо `isEncrypted` | Підтверджено (`PdfIo.kt:175-182`) |
| 3 | `keepEncryption` викликає `copyEncryption`; використовується в рушіях і `ReaderActivity.kt:2648` | Підтверджено |
| 4 | Рушії мають `password: String? = null`, інструменти пароль не передають | Підтверджено (`PageOperationsEngine.kt:20,30,39`; у `ui/tools` згадок password немає) |
| 5 | `ErrorMessages.kt:32,61` дає `error_invalid_password` | Підтверджено |
| 6 | `allowBackup="false"` (`AndroidManifest.xml:13`) | Підтверджено |
| 7 | `dataExtractionRules` немає; `res/xml` лише `locales_config.xml` | Підтверджено |
| 8 | `ReaderViewModel.documentPassword` :190, скидання :197 | Підтверджено |
| 9 | Діалог пароля `DialogDocumentPasswordBinding`, `password_toggle`, `password_wrong` | Підтверджено (`ReaderActivity.kt:435-436`, макет:31) |
| 10 | DECISIONS.md:106 (захист паролем у беклозі) | Підтверджено |
| 11 | BouncyCastle 1.72, «шифрування PDF» у `THIRD_PARTY.md:46` | Підтверджено |
| 12 | Проміжні файли PdfBox у `cacheDir` | Підтверджено (`PdfIo.defaultMemorySetting`: `setupMixed(16 МБ)`, `setTempDir(cacheDir)`) |
| 13 | `FLAG_SECURE` у `app/src/main` відсутній | Підтверджено (grep) |
| 14 | minSdk 28 | Підтверджено |
| 15 | `FEATURE_AUDIT.md` §2.3 п.4, §5.3 | Підтверджено |

## 3. Зовнішні факти
| Твердження | Статус |
|---|---|
| AES-256 (AESV3) підтримується pdfbox-android 2.0.27.0 | Підтверджено за `sources.jar` у кеші Gradle: `StandardSecurityHandler.java` має гілку `AESV3`, 256-бітний ключ, `prepareEncryptionDictAES(..., AESV3)` (рядки 188-191, 467). Автор позначив НЕ ПЕРЕВІРЕНО. Робота на Android і сумісність переглядачів лишаються НЕ ПЕРЕВІРЕНО. |

## 4. Вимоги до документа
Нічого не впроваджує: так. Простими словами: так. Оцінка обсягу (розд. 9), ризики (10), рекомендація (11), зведення НЕ ПЕРЕВІРЕНО: так.

## Дефекти
- Критичні: немає. Середні: немає.
- Низький L1: розд. 3 п.2 і зведення вважають AES-256 цілком неперевіреним, хоча вихідний код бібліотеки підтримку підтверджує. Не помилка; можна уточнити.
- Низький L2: терміни RC4/AES/«довжина ключа» не пояснені Власнику-непрограмісту (розд. 3, 5). Косметика.
