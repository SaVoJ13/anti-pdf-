# QA: backlog-release-no-admob-test (2026-11-25)

Гілка автора: `origin/codex/backlog-release-no-admob-test` (027c073), від main b65a684. QA-гілка: `qa/backlog-release-no-admob-test`. Код автора не змінювався.

## Вердикт: QA Verified (з низькими зауваженнями про крихкість)

## Що перевірено
- Diff до main: лише новий `app/src/test/java/com/savoj/volumpdf/AdmobReleaseFallbackPolicyTest.kt` і `docs/project_log.md`. Продуктовий код, Gradle, залежності, версія, AGENTS.md, CLAUDE.md, .agents, governance не змінені.
- Відповідність політиці кореневого `build.gradle.kts` (прочитано): немає `admob.properties` => `release = test`, `releaseIsTest = true`, з `-Pproduction=true` падає через `fail(...)`; є файл з тестовими ID і production => `if (releaseIsTest && production) fail(...)`. Чотири тести відповідають цьому і `.gitignore` (рядок `admob.properties`), `admob.properties.example` (значення порожні), сканер бойових ID у `app/src`, `core-ads/src`, `core-pdf/src` (усі три каталоги існують).
- Мутації (тимчасові правки `build.gradle.kts`, потім `git checkout`, робоче дерево чисте):
  1. `releaseIsTest = true` -> `false` у fallback: тест `without admob properties release falls back to Google test ids` ПАДАЄ (assert «fallback має позначати releaseIsTest»). Спіймано.
  2. `fail(` -> `logger.warn(` у гілці production без файла: тест `production flag fails the build...` ПАДАЄ. Спіймано.
  3. `if (production)` -> `if (!production)`: збірка падає вже на конфігурації Gradle, тож це не показник тесту (неінформативна мутація).
- Хибнопозитив у чистому коді: немає, 4 з 4 зелені.

## Числа (лише з XML, JDK 21, BUILD успішний)
- app: 47 XML-файлів, tests=435 (+4 проти 431 у main), failures=0, errors=0, skipped=0.
- core-pdf: 16 XML-файлів, tests=107, failures=0, errors=0, skipped=1.
- lint-results-debug.xml: errors=0, fatal=0, warnings=149 (як у main).

## Дефекти
Критичних, високих, середніх немає.
- Низький: тест крихкий до форматування `build.gradle.kts` (шукає точні підрядки `if (!file.isFile) {`, `} else {`, `release = test`, `if (production)`); рефакторинг без зміни політики дасть хибне падіння. Це сканер вихідного тексту, а не перевірка реальної збірки (про це сказано в KDoc).
- Низький: сканер бойових ID дивиться лише розширення kt/java/xml/kts/properties/json у трьох каталогах; ID у інших місцях (наприклад, `.gradle`, `docs`) не побачить.
- Низький: перевірка «admob.properties не лежить у репозиторії» опирається лише на `.gitignore`, а не на `git ls-files`.

## НЕ ПЕРЕВІРЕНО
Реальна release-збірка/маніфест без `admob.properties` (задача `:app:verifyAdmobReleaseManifest` не запускалась; release-збірка поза межами завдання), падіння збірки з `-Pproduction=true`, наявність бойових ID поза скануваними каталогами.
