# QA: backlog-licenses-error-test (2026-11-25)

Гілка автора: `origin/codex/backlog-licenses-error-test` (939d471), від main b65a684. QA-гілка: `qa/backlog-licenses-error-test`. Код автора не змінювався.

## Вердикт: QA Verified

## Що перевірено
- Diff до main: `OssLicenseParser.kt` (+15), `LibraryLicensesActivity.kt`, `LibraryLicenseDetailActivity.kt`, `OssLicenseParserTest.kt` (+34), `docs/project_log.md`. AGENTS.md, CLAUDE.md, .agents, governance, Gradle, залежності, версія не змінені.
- Еквівалентність поведінки (diff з main прочитано):
  - Список: було `runCatching { parseMetadata(читання metadata); читання blob; entries.takeIf{непорожній && усі тексти читаються}.orEmpty() }.getOrNull()`; стало `loadIndex` з тим самим порядком (спершу metadata, потім blob), тією ж умовою й `getOrDefault(emptyList())` замість `getOrNull`. Активність перевіряє `result.isNullOrEmpty()` => null і порожній список давали й дають однаковий `showError()`. Потоки, `runOnUiThread`, перевірка `isFinishing/isDestroyed` не змінені.
  - Деталь: було `runCatching { читання blob; licenseText }.getOrNull()`; стало `loadText` з тим самим виразом. Ідентично.
  - Лямбди читання ресурсів (`resources.openRawResource(...).use{}`) перенесені дослівно.
- Нові 5 тестів (усього в класі 8, failures=0): успішний індекс із сортуванням; L8 нема metadata; L8 нема blob; L8 пошкоджений рядок / порожній індекс / запис поза blob; L8 `loadText` (успіх, виняток, некоректний зріз).
- Мутації (тимчасові, потім `git checkout`, дерево чисте):
  1. `getOrDefault(emptyList())` -> `getOrThrow()` і `loadText` без `runCatching`: падають 3 тести (`loadText ...`, `loadIndex ... blob cannot be read`, `loadIndex ... metadata resource cannot be read`) з IOException. Спіймано.
  2. Прибрано перевірку `isNotEmpty && all{licenseText != null}`: падає `L8 loadIndex returns empty for corrupt or empty index and for entries outside blob` (повернувся запис `outside`). Спіймано.

## Числа (лише з XML, JDK 21, BUILD успішний)
- app: 46 XML-файлів, tests=436 (+5 проти 431 у main), failures=0, errors=0, skipped=0.
- core-pdf: 16 XML-файлів, tests=107, failures=0, errors=0, skipped=1.
- lint-results-debug.xml: errors=0, fatal=0, warnings=149 (як у main).

## Дефекти
Критичних, високих, середніх немає.
- Низький: тести перевіряють чисту логіку `loadIndex/loadText`; сама проводка в двох Activity (виклик `loadIndex` у потоці, показ `licenses_load_failed`) тестами не покрита (потрібен Robolectric/пристрій), лише перевірена читанням diff.
- Низький (інфо): `loadIndex` більше не повертає null, тому `isNullOrEmpty()` в Activity тепер лише перестраховка; прибирати не обов'язково.

## НЕ ПЕРЕВІРЕНО
Екрани «Ліцензії бібліотек» і деталь на пристрої (успішний і аварійний шлях), реальне пошкодження `res/raw`, TalkBack.
