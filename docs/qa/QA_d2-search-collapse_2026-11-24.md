# QA: виправлення D2-1 (пошук у бібліотеці згортається), гілка fix/d2-search-collapse

Дата: 2026-11-24. Роль: незалежний QA («QA і безпека»). Код не автор. Телефон не підключався, `adb` не запускався.

## Вердикт

**QA Verified (код).** Блокерів немає. **Device proof відсутній:** на пристрої нічого не перевірено; D2-1 вважається закритим лише після ручної перевірки на S24 Ultra (див. «НЕ ПЕРЕВІРЕНО»).

## Що перевірено

- Гілка `origin/fix/d2-search-collapse` = `7aaa82b`; код+тест `eb888e9`, запис журналу `7aaa82b`; база `878dd4a`.
- Перевірка у окремому git worktree поза репозиторієм (прибрано), JDK 21 (`jbr-21.0.11`), команда `:app:assembleDebug :app:testDebugUnitTest :core-pdf:test :app:lintDebug` — BUILD SUCCESSFUL.

## 1. Різниця 878dd4a..7aaa82b

5 файлів, +59/-1: `HomeLogic.kt` (+7), `MainActivity.kt` (+23), `RecentsFragment.kt` (-1), `HomeOnboardingTest.kt` (+14), `docs/project_log.md` (+15). Лише заявлені файли.
- Манифест, дозволи, `*.gradle.kts`, залежності, applicationId, версія, `core-pdf`, `core-ads`, реклама, ToolType, секрети: **не змінені** (порожня різниця по цих шляхах).
- Кінці рядків: в індексі всі `i/lf`; `numstat` показує малі точкові зміни (немає перезапису файлів на весь розмір). Робоче дерево Windows у CRLF (autocrlf) — норма, у diff не потрапляє.

## 2. Аналіз коректності

а) `grep invalidateOptionsMenu` у `app/src/main`: у `MainActivity` лишилось два виклики: `resetLibraryFilters()` (явне скидання, саме згортає пошук) і `onPageSelected` (зміна вкладки); решта — `ReaderActivity`, до бібліотеки не стосується. Жоден виклик не залежить від зміни відфільтрованого списку чи введення тексту. Виклик у `RecentsFragment` прибрано.

б) Підписка `hasAnyRecentDocuments` (`SharingStarted.Eagerly`, початкове `false`):
- перше створення меню: `onPrepareOptionsMenu` читає `hasAnyRecentDocuments.value` і ставить `isEnabled`, тож порядок «збір раніше за меню» не шкодить: якщо емісія прийшла до `onCreateOptionsMenu`, `clearHistoryMenuItem` ще `null`, але `lastHasAnyRecents` оновлюється, а стан застосує `onPrepareOptionsMenu`;
- зміна (очищення історії, перший документ): `lastHasAnyRecents` відрізняється, пункт ставиться напряму; перебудови меню нема;
- поворот/recreate: нова Activity, `lastHasAnyRecents = null`, перша емісія завжди «змінена»; меню створюється заново і `onPrepareOptionsMenu` бере актуальне `.value`; `onCreateOptionsMenu` перезаписує посилання `clearHistoryMenuItem`;
- початкове `false` у StateFlow: Room-потік швидко видає реальне значення; до цього пункт тимчасово сірий, потім виправляється збором (STARTED) або наступним `onPrepare`. Постійно неправильно сірим пункт лишитись не може, бо кожна зміна значення доходить до збору, а кожна перебудова меню читає `.value`;
- після `invalidateOptionsMenu()` (зміна вкладки) AppCompat викликає `onCreateOptionsMenu` знову, посилання оновлюється.

в) `searchMenuItem`/`clearHistoryMenuItem` — nullable, доступ через `?.`; null у момент збору безпечний (див. б). Крайній випадок (меню перебудовано, поки збір зупинений, тобто Activity не STARTED) покривається `onPrepareOptionsMenu`.

г) Тести: `HomeLogic.clearHistoryStateChanged` — чиста функція `previous != current`; 2 тести перевіряють її таблицю істинності (null→false, true→true тощо). Це **слабка** охорона: регресію «хтось знову додав `invalidateOptionsMenu()` у `RecentsFragment`» вони не ловлять (UI-тесту/Robolectric для SearchView немає). Реальний захист від D2-1 — лише код + урок 22 + пристрій. Не блокер, але обмеження: тести підтверджують логіку дебаунсу, а не відсутність згортання.

Додатково (**НЕ ПЕРЕВІРЕНО**, лише за читанням): `MenuItem.isEnabled` у AppCompat Toolbar не перевикликає `onCreateOptionsMenu`, тому розгорнутий `SearchView` не повинен згортатись; це треба підтвердити на пристрої.

## 3. Збірка, тести, lint (лише XML Gradle)

| Набір | tests | failures | errors | skipped |
|---|---|---|---|---|
| app `testDebugUnitTest` (45 файлів XML; сума `tests` = кількість `<testcase>`) | 422 | 0 | 0 | 0 |
| core-pdf `testDebugUnitTest` (16 XML) | 107 | 0 | 0 | 1 |
| core-pdf `testReleaseUnitTest` (16 XML) | 107 | 0 | 0 | 1 |
| lint `lint-results-debug.xml` (149 `<issue>`) | — | 0 errors | 0 fatal | 149 warnings |

Пояснення розбіжностей:
- app 420 → 422: +2 нові тести `HomeOnboardingTest.clearHistoryStateChange_*` (є у XML). Заява автора 422 підтверджена.
- core-pdf: попередні 214/2 skipped = **debug 107 + release 107** (задача `:core-pdf:test` запускає обидва варіанти; каталоги `testDebugUnitTest` і `testReleaseUnitTest` ідентичні за вмістом; 1 пропущений тест у кожному). Заява автора «107 / 1 skipped» — це один варіант, а не розбіжність у коді: тестів не зникло. Кількість `<testsuite>` = кількість XML-файлів (по одному на клас), сума `tests` збігається з кількістю `<testcase>` (107).
- lint: 0 errors, **149 warnings** — так само, як раніше; заява автора «0 issues» **неточна** (0 помилок, але 149 попереджень). XML повний (формат 6, lint 8.13.0, 149 записів, збігається з підсумком `lint-results-debug.txt`: «0 errors, 149 warnings»), перезаписаний лише варіантом debug (`lint-results-debug.*`), baseline-файлу у проєкті немає. Меншої кількості попереджень не виявлено.

## Виявлені проблеми

1. (Незначна, документація) Журнал автора каже «lint проходить» і в повідомленні «0 issues»: фактично 149 попереджень, 0 помилок; жодних нових.
2. (Обмеження) Нові тести не ловлять повторне введення `invalidateOptionsMenu()` у фрагмент (див. 2г). Рекомендація: окремо, не в цьому виправленні, — лінтерна/текстова перевірка або Robolectric-тест.
3. Блокерів немає.

## НЕ ПЕРЕВІРЕНО

- Поведінка пошуку на пристрої: ввід кількох символів, «нічого не знайдено», скидання; «Очистити історію» (стан до й після очищення, після повороту); перемикання Library/Tools. **Device proof відсутній.**
- Android 9–11, TalkBack, шрифт 150%.
- `isEnabled` без перебудови меню не згортає SearchView (за читанням коду, не на пристрої).

## Рекомендація

Передати Керівнику: повторна ручна перевірка пунктів 7 і 7б на S24 Ultra (QA-копія `...d2qa` з новою збіркою), потім закриття device gate D2. Нічого не злито в `main`.
