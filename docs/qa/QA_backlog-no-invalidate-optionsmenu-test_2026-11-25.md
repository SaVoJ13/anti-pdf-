# QA: backlog-no-invalidate-optionsmenu-test (2026-11-25)

Гілка автора: `origin/codex/backlog-no-invalidate-optionsmenu-test` (86374c0), від main b65a684. QA-гілка: `qa/backlog-no-invalidate-optionsmenu-test`. Код автора не змінювався.

## Вердикт: QA Verified

## Що перевірено
- Diff до main: лише новий `app/src/test/java/com/savoj/volumpdf/ui/main/NoInvalidateOptionsMenuOnListEmissionTest.kt` і `docs/project_log.md`. Продуктовий код, Gradle, залежності, версія, AGENTS.md, CLAUDE.md, .agents, governance не змінені.
- Дозволений список (`resetLibraryFilters`, `onPageSelected`) збігається з реальним кодом: у `MainActivity.kt` рівно два виклики `invalidateOptionsMenu()` (рядок 229 у `resetLibraryFilters`, рядок 290 в `onPageSelected`); у RecentsFragment/RecentsAdapter викликів немає. Згадка в KDoc перед `observeClearHistoryState` не дає хибного спрацювання (коментарі вирізаються).
- Мутації (тимчасові, потім `git checkout`, дерево чисте):
  1. `requireActivity().invalidateOptionsMenu()` у `recentDocuments.collect` у RecentsFragment: ПАДАЮТЬ тести `RecentsFragment never invalidates...` і `no invalidate inside collect or observe blocks`. Спіймано.
  2. `invalidateOptionsMenu()` у `hasAnyRecentDocuments.collect` у MainActivity: ПАДАЮТЬ `MainActivity invalidates only from allowed...` (функція `observeClearHistoryState`) і `no invalidate inside collect...`. Спіймано.
- Хибнопозитив у чистому коді: немає, 4 з 4 зелені. Четвертий тест перевіряє сам сканер на зразку регресії.

## Числа (лише з XML, JDK 21, BUILD успішний)
- app: 47 XML-файлів, tests=435 (+4 проти 431 у main), failures=0, errors=0, skipped=0.
- core-pdf: 16 XML-файлів, tests=107, failures=0, errors=0, skipped=1.
- lint-results-debug.xml: errors=0, fatal=0, warnings=149 (як у main).

## Дефекти
Критичних, високих, середніх немає.
- Низький: сканер текстовий (регулярні вирази, лічильник дужок); `invalidateOptionsMenu()` усередині `onPageSelected` або `resetLibraryFilters` з доданого туди collect не відрізнить, а виклик через `invalidateMenu()` у MainActivity (альтернативне API) не ловиться (перевіряється лише в RecentsFragment).
- Низький: інші екрани (ReaderActivity) тестом не покриті, це в межах заявленого обсягу (урок 22 про бібліотеку/пошук).

## НЕ ПЕРЕВІРЕНО
Реальна поведінка SearchView на пристрої (згортання при вводі); інші фрагменти/активності поза каталогом `ui/main`.
