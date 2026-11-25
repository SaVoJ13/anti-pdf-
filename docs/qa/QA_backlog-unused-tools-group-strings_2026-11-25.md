# QA: backlog-unused-tools-group-strings (2026-11-25)

Гілка автора: `origin/codex/backlog-unused-tools-group-strings` (3f45d11), від main c65e367. QA-гілка: `qa/backlog-unused-tools-group-strings`. Код автора не змінювався.

## Вердикт: QA Verified

## Що перевірено
- Diff до main: лише 5 файлів `strings.xml` (по 3 видалені рядки, разом 15) і `docs/project_log.md`. Залежності, дозволи, версія, governance не змінені.
- Видалені: `tools_group_pages`, `tools_group_effects`, `tools_group_quick` у values, values-de, values-es, values-ru, values-uk. Паритет зберігся (по 3 у кожній із 5 мов; StringsParityTest зелений).
- Пошук використань по всьому проєкту після видалення (`*.kt, *.java, *.xml, *.kts, *.pro, *.json`; охоплює layout, menu, `ToolGroups.kt`, тести, core-ads, core-pdf): збігів `tools_group` немає. `getIdentifier` і побудови імен ресурсів за рядком не використовуються (єдиний збіг у `proguard-rules.pro` це коментар, що так і є). Кодові збірка й тести компілюються, `R.string.tools_group_*` ніде не потрібні.
- Lint: у звіті жодних згадок `tools_group`; кількість `UnusedResources` 29 (після гілки), warnings 140 (на 3 менше, ніж 143 у гілках без цієї правки).

## Числа (лише з XML, JDK 21, `:app:testDebugUnitTest :app:lintDebug`, BUILD успішний)
- app: 49 XML-файлів, tests=451, failures=0, errors=0, skipped=0.
- lint-results-debug.xml: errors=0, fatal=0, warnings=140.

## Дефекти
Критичних, високих, середніх немає.

## НЕ ПЕРЕВІРЕНО
Запуск застосунку на пристрої; зовнішні посилання на ці ключі поза репозиторієм (немає); історичні згадки в docs/ (навмисно не скановані).
