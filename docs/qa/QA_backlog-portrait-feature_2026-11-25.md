# QA: backlog-portrait-feature (2026-11-25)

Гілка автора: `origin/codex/backlog-portrait-feature` (83a831c), від main b65a684. QA-гілка: `qa/backlog-portrait-feature`. QA незалежний, код автора не змінювався.

## Вердикт: QA Verified

## Що перевірено
- Diff до main: лише `app/src/main/AndroidManifest.xml` (+5 рядків) і `docs/project_log.md`. AGENTS.md, CLAUDE.md, .agents, docs/governance, Gradle-файли, залежності, версія не змінені.
- У маніфесті один `uses-feature android.hardware.screen.portrait required=false`, дублів немає. Власний `uses-permission` лишився один (INTERNET), як у main.
- Злитий debug-маніфест (`processDebugManifest`): `uses-feature` присутній один раз; решта дозволів (ACCESS_NETWORK_STATE, AD_ID, ADSERVICES_*, WAKE_LOCK, FOREGROUND_SERVICE...) приходять з бібліотек реклами/AndroidX, не від гілки.
- Lint: жодної проблеми про апаратні вимоги/дозволи.

## Числа (лише з XML, JDK 21, `:app:testDebugUnitTest :core-pdf:testDebugUnitTest :app:lintDebug`, BUILD успішний)
- app: 46 XML-файлів, tests=431, failures=0, errors=0, skipped=0.
- core-pdf: 16 XML-файлів, tests=107, failures=0, errors=0, skipped=1.
- lint-results-debug.xml: errors=0, fatal=0, warnings=149 (з них MissingQuantity=6, наявні в main для es).

## Дефекти
Критичних, високих, середніх немає.

## НЕ ПЕРЕВІРЕНО
Фільтрація в Google Play (потрібна консоль/пристрій), поведінка на пристроях без портретного екрана, release-маніфест (перевірено лише debug-злитий).
