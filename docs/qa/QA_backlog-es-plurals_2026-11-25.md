# QA: backlog-es-plurals (2026-11-25)

Гілка автора: `origin/codex/backlog-es-plurals` (d45fd5c), від main b65a684. QA-гілка: `qa/backlog-es-plurals`. Код автора не змінювався.

## Вердикт: QA Verified

## Що перевірено
- Diff до main: `values-es/strings.xml` (+6), `StringsParityTest.kt` (1 рядок), `docs/project_log.md`. Інші мови (en, de, uk, ru), AGENTS.md, CLAUDE.md, .agents, governance, Gradle, залежності, версія не змінені.
- Усі 6 plurals (pages_count, forms_sheet_subtitle, selected_files_count, selected_images_count, document_pages_count, organize_pages_count) у values-es отримали `many`. Усього plurals у кожній мові 6, пропущених немає.
- Форма правильна для CLDR es: `many` застосовується до кратних 1 000 000 (напр. «1 000 000 de páginas»), тому «%d de páginas» / «%d de campos rellenables encontrados» / «%d de archivos seleccionados» / «%d de imágenes seleccionadas» граматично коректна; `one`/`other` не змінені.
- StringsParityTest: вимога для values-es оновлена до {one, many, other}; тест перевіряє `containsAll` і збіг ключів з en; 12 тестів у класі, failures=0.

## Числа (лише з XML, JDK 21, BUILD успішний)
- app: 46 XML-файлів, tests=431, failures=0, errors=0, skipped=0.
- core-pdf: 16 XML-файлів, tests=107, failures=0, errors=0, skipped=1.
- lint-results-debug.xml: errors=0, fatal=0, warnings=143; MissingQuantity=0 (було 6 у гілці без цієї правки: усі «es ... many»).

## Дефекти
Критичних, високих, середніх немає.
- Низький: сам тест `containsAll` не відхилить зайву квантифікацію в es; прийнятно.

## НЕ ПЕРЕВІРЕНО
Відображення на пристрої з es-локаллю; перевірка граматики носієм мови (лише за правилом CLDR); runtime-вибір `many` для великих чисел (тут завжди малі кількості).
