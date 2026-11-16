# QA: feature/ui-a11y-polish (2026-11-16)

Перевірено `origin/feature/ui-a11y-polish` (HEAD 6270530), `origin/main` = f3cd4fc (не пішов далі; гілка вже на ньому). Перевіряв незалежний QA, код продукту не змінював.

## Вердикт: НЕ ЗАВЕРШЕНО (QA Verified не видаю)

Код, збірка, тести й лінт: без критичних і середніх дефектів. Перевірка на телефоні НЕ ВИКОНАНА: екран Galaxy S24 Ultra був заблокований (`isKeyguardShowing=true`, NotificationShade у фокусі) весь час (понад 20 хв очікування). PIN не вводив, не тапав. Без перевірки на пристрої (розміри цілей, TalkBack, нічна смужка, екран помилки, «Останні») вердикт «QA Verified» ставити не можна. Потрібен повтор розділу 3 на розблокованому телефоні.

## Простими словами
- Збірка, 3 набори тестів і лінт зелені, числа збігаються із заявленими.
- У коді не знайшов критичних чи середніх проблем. Контраст перерахував сам: числа автора правильні.
- Екран помилки не ховає від TalkBack кнопки «Спробувати ще / Вибрати інший файл / Закрити» (ховається лише контейнер бібліотеки, який є сусіднім елементом).
- На телефоні нічого не перевірено, бо його заблоковано.

## 1. Збірка, тести, лінт (JDK 21 через `org.gradle.java.home=jbr-21.0.11`)

Команда: `./gradlew cleanTestDebugUnitTest :app:assembleDebug :app:testDebugUnitTest :core-pdf:testDebugUnitTest :core-ads:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL.

| Модуль | Тестів / падінь+помилок / пропущено (з XML) | Очікування автора | Результат |
|---|---|---|---|
| :app | 328 / 0 / 0 | 328/0/0 | збігається |
| :core-pdf | 107 / 0 / 1 | 107/0/1 | збігається |
| :core-ads | 6 / 0 / 0 | 6/0/0 | збігається |
| lintDebug | 0 помилок, 139 попереджень | не заявлено | прийнятно (див. примітку 3) |

Тести не послаблені: у diff лише додано тести (StringsParityTest +2, TouchTargetsTest новий, ReadingThemeLogicTest +1, ErrorMessagesTest +1), видалених чи ослаблених перевірок немає.

## 2. Огляд коду

| # | Перевірка | Результат | Доказ | Серйозність |
|---|---|---|---|---|
| 1 | Зони ≥ 48 dp у розмітці | OK | `activity_reader.xml`: btnHudPage/Zoom/Search 48 dp (inset 6 dp зберігає вигляд 36 dp); `item_recent_document`: ivFavorite/ivMoreMenu 48 dp; `fragment_recents`: btnClearHistory 48 dp; `item_page_organizer`: btnDelete/btnRotate 48 dp (inset 8 dp); `item_selected_file`: btnRemoveFile 48 dp; `dialog_place_text`: усі кнопки 48 dp. TouchTargetsTest читає справжню розмітку (XML). Ручка `btnTextBoxResize` 28 dp свідомо не змінена (заявлено автором) | дрібне: тест містить порожню перевірку `assertTrue(wrapW)` |
| 2 | Haptics без VIBRATE | OK | `Haptics.kt` лише `View.performHapticFeedback`, без IGNORE_GLOBAL_SETTING; `AndroidManifest.xml` у diff відсутній; запасні константи для API < 30 (minSdk 28); `PlaceBarController`/`ReaderActivity` викликають на view | OK |
| 3 | Переходи fade | OK за кодом | `Animation.VolumPdf.Activity` (150 мс) у `values/themes.xml` і `values-night/themes.xml` | не перевірено на екрані |
| 4 | Д5, смужка між сторінками | OK за кодом і юніт-тестом | `filteredBackgroundPreimage`: покановне обернення діагональної матриці з округленням і обмеженням 0..255; тест: похибка ≤ 1 на канал, нічна смужка темна (<40) | на пристрої НЕ ПЕРЕВІРЕНО |
| 5 | Контраст (власний розрахунок, WCAG) | OK | світла outline #6B6C7B: 5,18 на білому, 4,88 на фоні #FAF8F5, 4,56 на surfaceVariant (було 4,48/4,23/3,94); темна outline #9A9AA8: 6,68/6,18/5,06 на #121318/#1A1B22/#292B36 (було 5,89/5,45/4,47); підказка #6B7280 на білому 4,83. Усе ≥ 4,5 | OK |
| 6 | TalkBack-тексти | OK за кодом | `NO_HIDE_DESCENDANTS` застосовано до `pdfViewerContainer`, а `openErrorPanel` (з кнопками btnOpenErrorRetry/Pick/Close) є СУСІДНІМ елементом у `viewerWrapper`, тож кнопки не ховаються. `hideOpenFailure` повертає `AUTO`. Live region POLITE на btnHudPage; `accessibilityPaneTitle` на панелі помилки | на пристрої НЕ ПЕРЕВІРЕНО; дрібне: опис пігулки «Сторінка 3 з 12. Перейти до сторінки» читається повністю при кожній зміні сторінки (багатослівно) |
| 7 | plurals pages_count | OK | en/de: one+other; uk/ru: one/few/many/other (1 сторінка, 2-4 сторінки, 5+ сторінок; ru так само); StringsParityTest перевіряє набір quantity у 4 мовах | OK |
| 8 | Прибирання запису з «Останніх» | OK з застереженнями | `shouldDropFromRecents` true лише для Kind.IO і Kind.OTHER; false для PASSWORD, NO_PERMISSION, READ_ONLY, NOT_FOUND, NO_SPACE, TOO_BIG (тест). Зачіпає лише запис, що з'явився при цьому відкритті (`openedAsNew`); давній запис не стирається. Після збереження/оновлення виклик йде лише з `showOpenFailure`, тож «оновлення після збереження» не зачеплене | дрібне (a): `dropFailedFirstOpen` читає `openedAsNew` ДО `recordJob.join()`; якщо помилка прийшла раніше, ніж корутина встигла встановити прапорець, запис не прибереться (безпечна сторона, мертвий рядок лишиться). Дрібне (b): будь-який IOException/невідомий виняток при ПЕРШОМУ відкритті прибирає запис, навіть якщо збій тимчасовий (файл справний): користувач втратить рядок, але зможе відкрити файл знову |
| 9 | «Назад» замість «Скасувати» | OK | `navigationContentDescription=action_back` у читачі, інструментах, організаторі, сканері; `action_back` у 4 мовах | OK |
| 10 | Перейменування | OK | «Тема читання» / Reading Theme / Lesethema / Тема чтения (= `action_reading_theme`); «Сторінки й файл» / Pages & file / Seiten & Datei / Страницы и файл (`action_tools` = `tools_sheet_title`, перевіряє тест); нова іконка ic_pages_file | дрібне: у en «Reading Theme» (Title Case) проти «Pages & file» (sentence case); старий ключ `place_menu_reading_mode` лишено для тесту |
| 11 | Нові дозволи | OK | `AndroidManifest.xml` не змінено | OK |
| 12 | LF | OK | `git ls-files --eol`: індекс `i/lf` для змінених файлів (робоче дерево Windows показує crlf через autocrlf, це не проблема) | OK |
| 13 | Пароль | OK | у diff немає password / SavedStateHandle / PendingStore / Log для пароля; `KEY_URI` у SavedStateHandle існував раніше | OK |
| 14 | Решта «N pages» | дрібне | у `strings.xml` лишилися `document_pages_count` і `organize_pages_count` у вигляді «%1$d pages» (lint PluralsCandidate: 7), використовуються в `RecentsAdapter` і `PageOrganizerActivity`; для 1 сторінки в en буде «1 pages» (поза заявленим обсягом) | дрібна (існувало до гілки) |

## 3. Телефон (Galaxy S24 Ultra, `--user 0`)

Телефон був заблокований увесь час, тож усе нижче НЕ ПЕРЕВІРЕНО. Нічого не тапав, PIN не вводив.

Виконано лише безпечне: взято файл-замок, встановлено debug-збірку гілки (`adb install -r --user 0`), `pm clear` застосунку, скопійовано 4 тестові PDF (нормальний, багатосторінковий, порожній, пошкоджений) в `/sdcard/Documents/qa_tmp_a11y2/`; потім видалено ту папку й замок. Застосунок не запускався; «Останні» порожні (після `pm clear`; старий запис `qa_a11y.pdf` прибрано очищенням даних). font_scale = 1.0, accelerometer_rotation = 1, enabled_accessibility_services порожні (TalkBack вимкнено) — як було, не змінювались. Тема читання і мова застосунку не чіпались (після `pm clear` стандартні).

| Сценарій | Результат |
|---|---|
| Розміри цілей через uiautomator (пігулка, обране/меню/Очистити, організатор, панель тексту) | НЕ ПЕРЕВІРЕНО (екран заблоковано) |
| Нічна тема, смужка між сторінками при 100% | НЕ ПЕРЕВІРЕНО |
| Haptics | НЕ ПЕРЕВІРЕНО (екран заблоковано; dumpsys vibrator без дій у застосунку не інформативний) |
| TalkBack (фокус на екрані помилки і пігулці; обидва пакети TalkBack встановлені) | НЕ ПЕРЕВІРЕНО |
| Пошкоджений/порожній PDF → запис зникає з «Останніх» | НЕ ПЕРЕВІРЕНО на пристрої (лише юніт-тест) |
| READ_ONLY-файл не прибирається | НЕ ПЕРЕВІРЕНО на пристрої (лише юніт-тест; автор заявляв ручну перевірку) |
| ru/en/de, font_scale 1.3, ландшафт, сепія, «Сторінки й файл» не обрізається | НЕ ПЕРЕВІРЕНО |
| Регресія: читання, редагування, збереження, пошук | НЕ ПЕРЕВІРЕНО |

## Дефекти
- Критичних: 0. Середніх: 0 (за кодом).
- Дрібні: п. 1 (порожній assert у тесті), п. 6 (багатослівний опис пігулки), п. 8 (a, b), п. 10, п. 14.

## Що робити далі
1. Повторити розділ 3 на розблокованому телефоні (особливо: екран помилки під TalkBack; пошкоджений та порожній PDF → запис зникає, нормальний лишається; нічна смужка; 48 dp; «Сторінки й файл» при font_scale 1.3).
2. Дрібне п. 8a: перенести читання `openedAsNew` після `recordJob?.join()` всередині корутини.
