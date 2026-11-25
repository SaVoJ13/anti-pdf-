# QA: audit-d8-fix (2026-11-25)

Гілка автора: `origin/docs/audit-d8-fix` (61c46a3), від main c65e367. QA-гілка: `qa/audit-d8-fix`. Лише документація; Gradle-тести не запускались (код не змінено).

## Вердикт: QA Verified

## Що перевірено
- Diff до main: лише `docs/FEATURE_AUDIT.md` (6 рядків змінено) і `docs/project_log.md`. Код, залежності, дозволи, версія, governance не змінені.
- Звірка 6 виправлених посилань з кодом `app/src/main/java/com/savoj/volumpdf/ui/tools/` (код на гілці ідентичний main):
  1. T5 `error_select_min_two_files :281`: рядок 281 `showError(getString(R.string.error_select_min_two_files))`. Підтверджено.
  2. Запуск `CreateDocument :371`: рядок 371 `createDocumentLauncher.launch(defaultName)`; реєстрація `:82-83` (`registerForActivityResult(... CreateDocument("application/pdf")`) теж правильна. Підтверджено.
  3. Interstitial `:487`: рядок 487 `adsManager.showActionInterstitial(this@ToolsActivity)` (485 це `isEnabled = true`, 486 коментар), тож нове посилання коректне і для потоку, і для рядка P2. Підтверджено.
  4. O1c `:45, 56, 72`: на всіх трьох рядках `SafUtils.takePersistableUriPermissionSafe`. Підтверджено.
  5. T1 `:117`: рядок 117 `if (toolType == ToolType.CREATE_BLANK)`, далі в блоці `createBlankDocument` і `openNewDocument`. Підтверджено (посилання на вхід у гілку, а не діапазон).
  6. `ToolsViewModel.kt:124, 151, 172`: `extractPages`, `rotatePages`, `deletePages`, тобто саме виклики з `password = null` за замовчуванням (`PageOperationsEngine.kt:20, 30, 39` теж на місці). Підтверджено.
- `ErrorMessages.kt:32, 61` (PASSWORD, `error_invalid_password`) залишились правильними.

## Числа
Тести й lint не запускались: зміни лише в `docs/`, XML-результатів немає (не застосовно).

## Дефекти
Критичних, високих, середніх немає.
- Низький: у пункті 4 переліку «виклики password = null» не названо `executeMerge` (`:102`), `executeWatermark`, `executePageNumbers`, `executeCompress` (`:214, :237, :251`), хоча вони теж не передають пароль; висновок не хибний, лише неповний. Старий діапазон `99-118` охоплював merge, тому нове формулювання трохи звужує перелік.

## НЕ ПЕРЕВІРЕНО
Усі твердження аудиту щодо поведінки на пристрої (як у самому документі), решта посилань FEATURE_AUDIT.md поза 6 виправленими.
