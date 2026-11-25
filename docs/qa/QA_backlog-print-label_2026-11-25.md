# QA: backlog-print-label (2026-11-25)

Гілка автора: `origin/codex/backlog-print-label` (06fefbd), від main c65e367. QA-гілка: `qa/backlog-print-label`. Код автора не змінювався.

## Вердикт: QA Verified

## Що перевірено
- Diff до main: по одному рядку в `values-ru/strings.xml` і `values-uk/strings.xml` (`action_print`), плюс `docs/project_log.md`. Залежності, дозволи, версія, governance не змінені.
- Узгодженість з меню: `action_print` тепер uk «Друкувати», ru «Печатать», що збігається з `action_print_menu` (uk «Друкувати», ru «Печатать»). У en/de/es `action_print` і `action_print_menu` теж збігаються (Print/Drucken/Imprimir), тож тепер усі 5 мов узгоджені.
- Використання `R.string.action_print`: лише пункт шторки «Друкувати» в `ReaderToolsBottomSheetDialogFragment` (title і desc). Інші пункти шторки й меню (`action_print_menu`) не зачеплені. Тестів із жорстким «Друк»/«Печать» немає.
- Вмістимість: пункт шторки використовує `item_tool_card.xml`; заголовок `tvToolTitle` має ширину 0dp між іконкою й шевроном і переноситься по потребі, тож «Друкувати» (9 символів, коротше за інші заголовки шторки) вміщується.

## Числа (лише з XML, JDK 21, `:app:testDebugUnitTest :app:lintDebug`, BUILD успішний)
- app: 49 XML-файлів, tests=451, failures=0, errors=0, skipped=0.
- lint-results-debug.xml: errors=0, fatal=0, warnings=143.

## Дефекти
Критичних, високих, середніх немає.
- Низький (існував раніше): у шторці опис пункту дублює назву (`descRes = action_print`); не в межах гілки.

## НЕ ПЕРЕВІРЕНО
Візуальний вигляд шторки на пристрої (вміщення на вузьких екранах і великому шрифті).
