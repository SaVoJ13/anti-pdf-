# QA: R1 меню читалки (2026-11-24)

Роль: QA і безпека (незалежний; код не писав). Гілка перевірки: `origin/codex/redesign-r1-reader-menu`, коміти `d35af44` (код+тести), `39be59d` (журнал). База `origin/main` = `484c346`. Телефон не підключався, adb не запускався.

## Вердикт

**QA Verified (код)** з одним дефектом середньої серйозності (D-1, не блокер) і двома низькими. **Device proof ВІДСУТНІЙ**: поведінку меню, діалогу, сірого «Зберегти» і повороту на пристрої не перевірено.

## Числа (лише з XML Gradle, JDK 21, окремий worktree, `--no-daemon`)

| Набір | Тести | Збої | Помилки | Пропущено | Заява автора |
|---|---|---|---|---|---|
| app testDebugUnitTest (46 XML) | 430 | 0 | 0 | 0 | 430/0/0/0 - підтверджено |
| core-pdf debug (16 XML) | 107 | 0 | 0 | 1 | 107/0/0/1 - підтверджено |
| core-pdf release (16 XML) | 107 | 0 | 0 | 1 | підтверджено |
| lint debug (lint-results-debug.xml) | 0 errors, 149 warnings | | | | підтверджено |

`:app:assembleDebug` BUILD SUCCESSFUL. Базис main: app 427 (+3 нові тести = 430).

## 1. Обсяг diff (10 файлів, +157/-35)
Лише заявлені: ReaderActivity.kt, ReaderChromeLogic.kt, menu_reader.xml, strings у 5 локалях, ReaderChromeLogicTest.kt, project_log.md. AndroidManifest, *.gradle.kts, залежності, core-pdf, core-ads, версія, applicationId, ToolType не торкнуті. Пошук `ca-app-pub|keystore|password|secret` у diff порожній. Кінці рядків: в індексі всі файли `i/lf`, numstat без «весь файл»; project_log.md: 30 доданих рядків, 0 видалених.

## 2. Код
- (а) Пункти меню: 12 id у main і 13 у гілці (ті самі + новий `action_print`); жоден не зник. Порядок: Обране, Поділитися, Друкувати, Зберегти копію, Сторінки й файл, Організувати сторінки, Відомості, Тема читання; далі пошук і форми (невидимі за замовчуванням, керуються `onPrepareOptionsMenu`). Стани: звичайне читання (print, saveCopy видно; save/done/add/search приховані); «Редагувати» без змін (Зберегти видно, неактивне; saveCopy/print видно; пошук у ⋮); зі змінами (активне); openFailed (`MenuState` усе false, тест лишився); read-only (saveCopy є, раніше теж); форми (`forms = formFieldCount>0`, без змін).
- (б) `R.id.action_print -> printCurrentDocument()`; шторка (`ReaderToolsBottomSheetDialogFragment`, `action_print`) не чіпана. `action_print_menu` окремий ключ, наявний `action_print` не затінено (є в усіх 5 локалях: «Друк»/«Print»/«Drucken»/«Imprimir»/«Печать»).
- (в) Діалог: `MaterialAlertDialogBuilder...showTracked()` (як інші діалоги Activity). `ReaderActivity` має `configChanges` на поворот/розмір/тему/мову, тож поворот не перестворює Activity, діалог лишається; відкладений `recreate` враховує tracked-діалоги (`isUiBusyForRecreate`). Витоку не знайдено. `savePending`: `if (savingPlace) return`, тож подвійний тап не стартує друге збереження (але тихо нічого не робить). Порожній запит: `onSuccess()` одразу. SAME_URI: `runSave(copy=false, onSuccess)`; `onSuccess()` викликається після Saved і після NothingToSave; при Failed ні (показується повідомлення про збій, не тихий провал). DENIED/UNAVAILABLE: повідомлення, копіювання не стартує. «Копіювати як є»: `launchSaveCopy()`, `copyDocumentTo` копіює файл з диска (зміни не входять, як сказано в тексті діалогу). «Скасувати»: null-слухач.
- **D-1 (середній): NEW_COPY.** Твердження автора «копіювання не стартує» хибне. `confirmCopy(onSuccess)` -> `startCopyPick(onSuccess)` -> `createCopyLauncher` -> `runSave(copy=true, onSuccess)` викликає `onSuccess()` після успіху (і в гілці відкладеного діалогу). Отже для документа, який можна лише копіювати (без права запису) за незбережених змін: користувач підтверджує діалог копії, обирає місце, копія зі змінами створена і стала поточним документом, а далі **відкривається ще один вибір файлу** («Зберегти копію» вже збереженої копії). Втрати даних немає (можна скасувати), але потік плутаний, а запис у журналі описує його неправильно. Те саме через `showReadOnlyOffer(onSuccess)`. Рекомендація (у QA не виправлено): у шляху NEW_COPY не запускати `launchSaveCopy`; лише SAME_URI має його запускати.
- (г) `isEnabled = placeSaveEnabled` виставляється в `onPrepareOptionsMenu`; оновлення через наявний `renderUnsavedUi()` -> `invalidateOptionsMenu()` на `hasUnsaved.collect`. **Нових викликів `invalidateOptionsMenu()` немає** (у diff 0). Урок 22: пошук у читачі це `toggleSearch()` (пігулка/панель), не action view у меню, тож згортання через перебудову меню не загрожує; на пристрої НЕ ПЕРЕВІРЕНО. Коментар біля `renderUnsavedUi` застарів (D-3).
- (д) Тести: два оновлені кодували старий контракт (`saveCopy` лише read-only поза редагуванням; `placeSave` лише за змін); оновлення обґрунтоване рішенням Власника R1 (DECISIONS 2026-11-24); перевірка `openFailed` лишилась і посилена (10 полів). Нові 3 тести змістовні: `print` (стани + openFailed), `saveCopy` (усі 8 комбінацій), `placeSaveEnabled` поза режимом, порядок меню парсить реальний `menu_reader.xml`. D-2 (низький): перший `assertEquals` у тесті порядку тавтологічний, але другий (`expected` vs `sortedBy`) реально перевіряє порядок. Діалоговий потік юніт-тестами не покритий.
- (е) Локалі: 5 нових ключів (`action_print_menu`, `save_copy_unsaved_title/message/save/as_is`) у values, uk, de, es, ru; дублів немає, переклади змістовні; lint без помилок.

## Дефекти
| № | Серйозність | Опис |
|---|---|---|
| D-1 | середній | NEW_COPY/read-only: після створення копії запускається другий вибір файлу; опис у журналі автора неточний. Не блокер (без втрати даних, скасовується). |
| D-2 | низький | Тавтологічний перший assert у тесті порядку меню. |
| D-3 | низький | Застарілий коментар біля `renderUnsavedUi`; друк за незбережених змін друкує файл з диска без попередження (задокументовано автором). |

## Відповідь на питання автора (підпис друку в шторці)
Не міняти «Друк» на «Друкувати» в цьому PR: це окрема зміна рядків у 5 локалях поза затвердженим планом R1. У шторці це назва картки інструмента (іменник), у меню дія (дієслово). Якщо Власник захоче єдиний стиль, робити окремим дрібним PR.

## НЕ ПЕРЕВІРЕНО
Усе на пристрої (Galaxy S24 Ultra): вигляд меню ⋮ в читанні й «Редагувати», сірий «Зберегти», діалог і всі три кнопки, поворот з відкритим діалогом, TalkBack, потік NEW_COPY (D-1), друк.

## Повторна перевірка 47281f9

Голова гілки автора `origin/codex/redesign-r1-reader-menu` = `47281f9c2a93b0d80b752f2fd1f6146723302b42`. Гілка QA `qa/r1-reader-menu-2` = merge (без force) гілки автора в `qa/r1-reader-menu`; конфлікт у `docs/project_log.md` (обидві сторони додали записи зверху) вирішено збереженням обох записів. Телефон не підключався. **Device proof ВІДСУТНІЙ.**

**Вердикт: QA Verified (код)** для 47281f9. D-1, D-2, D-3 закрито у коді. Блокерів немає.

1. Diff `39be59d..47281f9`: 4 файли (ReaderActivity.kt, ReaderChromeLogic.kt, ReaderChromeLogicTest.kt, project_log.md; +41/-3). Манифест, дозволи, gradle, core-pdf, ресурси/локалі, версія не змінені. 5 мов без змін, паритет збережено.
2. D-1 закрито. `shouldLaunchCopyAfterSave(before, after) = before != null && before == after`. `before` береться в кліку «Зберегти зміни» (до асинхронного збереження), `after` читається в `onSuccess`. Збереження в нову копію (`confirmCopy`/`startCopyPick`/`runSave(copy=true)`, а також `showReadOnlyOffer`) викликає `switchToDocument(output)`, що синхронно виставляє `documentUri = newUri` до `onSuccess()` (і в гілці відкладеного діалогу теж після `switchToDocument`), тож uri різні й другий вибір файлу не стартує. Збереження на місці лишає `documentUri` незмінним, копія стартує. Документ null: `before == null`, копія не стартує. Гонки не знайдено (усе в головному потоці; `savingPlace` блокує паралельне збереження).
   - Крайній випадок (D-4, низький, інформаційний): `NothingToSave` у шляху copy=true лишає uri незмінним і запустить вибір файлу. Практично недосяжний (`savePending` вже відсікає порожній запит), на пристрої не перевірено.
3. D-2 закрито: перший assert тепер `order.containsAll(expected)`, другий реально перевіряє порядок. Новий тест `shouldLaunchCopyAfterSave` (3 випадки) змістовний. D-3: коментар біля `renderUnsavedUi` оновлено. Друк з незбереженими змінами без попередження лишається (задокументовано автором, низький).
4. Збірка на JDK 21 (`--no-daemon`), BUILD SUCCESSFUL, числа з XML: app 431/0/0/0, core-pdf debug 107/0/0/1, core-pdf release 107/0/0/1, lint 0 errors/149 warnings. Заяву автора підтверджено.

НЕ ПЕРЕВІРЕНО: потік NEW_COPY/read-only, діалоги, поворот, сірий «Зберегти», друк, TalkBack на пристрої.
