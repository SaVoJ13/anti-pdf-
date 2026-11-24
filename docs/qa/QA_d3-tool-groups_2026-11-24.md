# QA-звіт: D3 згрупована вкладка «Інструменти» (2026-11-24)

Роль: незалежний QA («QA і безпека»). Код не писав і не змінював.
Перевірено: `origin/codex/redesign-d3-tools` — c780898 (код, ресурси, тести) і 60d2113 (журнал); база 9a63817.

## Вердикт

**QA Verified (код).** Блокерів немає.
**Device proof відсутній: телефон не перевірявся** (adb не запускався). TalkBack, шрифт 200%, ландшафт/планшет, темна/світла тема, візуальний вигляд — НЕ ПЕРЕВІРЕНО. Повного «QA Verified» (з пристроєм) немає.

## 1. Diff і гігієна

- `git diff 9a63817..origin/codex/redesign-d3-tools --stat`: 11 файлів, +204/−13. Лише заявлені: ToolsFragment.kt, ToolGroups.kt (новий), ToolsAdapter.kt, item_tool_group_header.xml (новий), strings.xml x5, ToolGroupingLogicTest.kt (новий), docs/project_log.md.
- Не торкнуто: AndroidManifest/дозволи, *.gradle.kts, залежності, core-pdf, core-ads, MainActivity, ToolsActivity, версія, applicationId. Enum `ToolType` оголошено в ToolsAdapter.kt: у diff змінено лише клас адаптера, enum без змін (до того ж є тест на порядок enum). Секретів немає.
- Кінці рядків: `git ls-files --eol` показує i/lf для всіх змінених файлів (у робочій копії w/crlf — це autocrlf Windows). `numstat` project_log.md: 30 додано / 0 видалено — чисто, лише додані рядки. Заява про «mixed» у робочій копії автора не позначилась на коміті.

## 2. Читання коду

а) Усі 11 ToolType є рівно в одній групі (CREATE 3, PAGES 5, ENHANCE 3 — збігається із заданим). Список ToolDefinition не змінено (лише перейменовано поле `tools`→`definitions`, порядок і дані ті самі). Клік: `onToolClicked(tool.type)` — за типом, а не за позицією, тому заголовки не зсувають індекси. `onToolSelected` у ToolsFragment не змінено; маршрути/intent-и ті самі.
б) `SpanSizeLookup`: заголовок = `gridManager.spanCount` (читається в момент виклику, тож актуальний після зміни spanCount у `AdaptiveGrid.onMeasure`); картка = 1. `getItemViewType` ґрунтується на `isHeader(position)` над одним списком `items` — розбіжності немає. AdaptiveGrid не змінювався; при 1 колонці заголовок = span 1 — коректно. Ризик падіння при повороті не виявлено (spanSize завжди ≤ spanCount).
в) Макет заголовка: `accessibilityHeading=true` (minSdk 28 — атрибут доступний, lint без зауважень), `minHeight=48dp`, `wrap_content` (багаторядковий при 200%, `maxLines`/ellipsize не задано, тож текст не обрізається), розмір через TextAppearance.Material3.TitleMedium (sp), колір `?attr/colorPrimary` (з теми). Контраст colorPrimary на фоні в обох темах — НЕ ПЕРЕВІРЕНО візуально.
г) Рядки: `tools_section_create/pages/enhance` є у values, -uk, -de, -ru, -es (по 3, `&amp;` екрановано). uk: «Створити», «Сторінки», «Оформити й оптимізувати» — збігається із заданим. Дублів ключів немає; старі `tools_group_*` (values: pages/effects/quick) існують окремо й у коді не використовуються (grep по .kt/.xml поза strings.xml — порожньо).
д) Тести (5): повнота (кожен ToolType рівно раз, ==11), порядок груп, точний склад/порядок груп, «заголовок перед своїми» (послідовний обхід, 14 елементів), порядок enum. Не тавтологічні: ловлять додавання нового ToolType без групи, дублікат, переставляння. Прогалина (не блокер): тести не охоплюють відповідність `ToolDefinition` кожному типу у ToolsAdapter (`toolsByType.getValue` впаде в рантаймі, якщо для типу немає definition) і SpanSizeLookup (Android-залежні).
е) `ToolGroupingLogic.buildItems()` реально використовується в `ToolsAdapter` (`items`); не мертвий код. Дублювання: склад груп лише в `ToolGroup`; definitions окремо — прийнятно.

## 3. Збірка/тести/lint (JDK 21, чистий worktree)

`:app:assembleDebug :app:testDebugUnitTest :core-pdf:test :app:lintDebug` — BUILD SUCCESSFUL.
Числа з XML (tests/failures/errors/skipped):
- app testDebugUnitTest: 427/0/0/0 (базис 422 +5 нових — збігається)
- core-pdf debug: 107/0/0/1; release: 107/0/0/1 (без змін)
- lint-results-debug.xml: 0 errors, 149 warnings (без змін; жодного зауваження до нових файлів)
Заява автора підтверджена повністю.

## 4. Локалізація

MissingTranslation/ExtraTranslation (error) — lint 0 errors, нові ключі пройдені у 5 локалях.

## Відповіді на відкриті питання автора

1. Старі `tools_group_pages/effects/quick`: у коді не використовуються. Рекомендація: не прибирати в D3 (поза скоупом, ризик змішати зміни); прибрати окремим дрібним чищенням разом з усіма 5 локалями (або відразу після D-серії) — лінт UnusedResources наразі їх не позначає як error.
2. Сітка на широких екранах: заголовок на всю ширину при будь-якому spanCount — за кодом коректно. Побічний ефект: остання рядок групи може бути неповним (напр. 5 карток «Сторінки» у 3–4 колонках), візуально з порожніми клітинками — це нормально, але потребує огляду на планшеті/ландшафті. Також колір заголовка і відступ 4dp не вирівняні з картками — естетика, перевірити на пристрої.

## Не перевірено / ризики

Device proof відсутній. НЕ ПЕРЕВІРЕНО: TalkBack (озвучення «заголовок»), шрифт 200%, ландшафт/планшет, темна/світла тема, фактичні переходи по кліку на 11 картках на пристрої.
