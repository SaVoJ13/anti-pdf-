# QA: backlog-vm-rotation-test (2026-11-25)

Гілка автора: `origin/codex/backlog-vm-rotation-test` (05fce97), від main c65e367. QA-гілка: `qa/backlog-vm-rotation-test`. Код автора не змінювався.

## Вердикт: QA Verified

## Що перевірено
- Diff до main: `ToolsViewModel.kt` (+6/-3), новий `ToolsViewModelImagesRotationTest.kt`, `docs/project_log.md`. Залежності, дозволи, версія, Gradle, AGENTS.md, CLAUDE.md, .agents, governance не змінені.
- Рефакторинг ToolsViewModel: конструктор `(application, engine = PdfBoxPageOperationsEngine())` з `@JvmOverloads`; `repository` став `by lazy`. Єдине місце створення в застосунку: `ToolsActivity` (`by viewModels()`, фабрика за замовчуванням створює через конструктор `(Application)`). `@JvmOverloads` генерує саме такий одно-аргументний конструктор, тож стандартна фабрика його знаходить; інших місць створення (ViewModelProvider, фабрик) у `app/src/main` немає. Рушій за замовчуванням створюється так само, як раніше, по екземпляру на ViewModel. `lazy` бере той самий `VolumPdfApp.repository` і першим доступом (рядок ~266, після успішної операції), поведінка в застосунку не змінюється.
- Тести (4): кути передаються в `imagesToPdf` по порядку ([90,0,180]); 4 натиски дають 0; не-зображення й хибна позиція ігноруються; після видалення кут їде з елементом ([90,270]) і потрапляє в рушій.
- Мутація: у виклику `engine.imagesToPdf(...)` замість `rotations` передано `emptyList()`: ПАДАЮТЬ 2 тести (`passesRotationsInOrder`, `angleTravelsWithItem`), обидва «was [[]]». Після `git checkout` дерево чисте. Тести про 4 натиски й ігнор не-зображень мутації не стосуються, бо перевіряють стан VM, а не виклик.

## Числа (лише з XML, JDK 21, `:app:testDebugUnitTest :app:lintDebug`, BUILD успішний)
- app: 50 XML-файлів, tests=455, failures=0, errors=0, skipped=0; з них у новому класі tests=4, failures=0 (отже у main очікувано 451; сам main не запускався).
- lint-results-debug.xml: errors=0, fatal=0, warnings=143.
- core-pdf на цій гілці не запускався (не змінено).

## Дефекти
Критичних, високих, середніх немає.
- Низький: тест використовує `Proxy` для PageOperationsEngine і `mock(Application)`; якщо інтерфейс рушія зміниться (імена/позиція аргументів `imagesToPdf`, `args[3]`), тест зламається за індексом, а не за змістом.
- Низький: успішну гілку (запис в `repository`) тести не заходять (підробка повертає Progress).

## НЕ ПЕРЕВІРЕНО
Запуск екрана інструментів на пристрої (створення ViewModel у реальному застосунку), фактичне повернення зображень у згенерованому PDF.
