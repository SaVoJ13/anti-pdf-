# PROJECT PROFILE / ПРОФІЛЬ ПРОЄКТУ — Volum PDF

> Fills the 🔧 parameters of the constitution (`CONSTITUTION.en.md` / `CONSTITUTION.uk.md`). May only fill in and **tighten** rules, never loosen them.
> Заповнює 🔧-параметри конституції. Може лише заповнювати й **посилювати** правила, а не послаблювати.
> Version / Версія: 1.1 (DRAFT / ЧЕРНЕТКА, чекає затвердження Власника) · Date / Дата: 2026-11-11 · Constitution / Конституція: 2.0

## 1. Project / Проєкт
| Field / Поле | Value / Значення |
|---|---|
| Name / Назва | Volum PDF (у магазині: Volum PDF: Reader & Tools), `applicationId` `com.savoj.volumpdf` |
| Short description / Короткий опис | Android-застосунок для читання й роботи з PDF: розміщення тексту, ✓/✗, малювання, підпису, заповнення форм AcroForm; інструменти сторінок; монетизація рекламою (AdMob + UMP); мета — Google Play |
| Project licence / Ліцензія проєкту | Proprietary (закритий код, R5) |
| Main branch / Головна гілка | main |
| Repository / Репозиторій | https://github.com/SaVoJ13/pdf-reader-android (тимчасово публічний, потім приватний; до приватизації не комітити цінного й закритого) |

## 2. Owner / Власник
| Field / Поле | Value / Значення |
|---|---|
| Time for the project / Час на проєкт | Сесіями, у вільний час (точний обсяг н/д) |
| Technical level / Технічний рівень | Не програміст |
| Language of reports and chat / Мова звітів і спілкування | Українська; звіти «простими словами» |
| Language of code, comments, commits / Мова коду, коментарів, комітів | Код і коміти: англійська; коментарі й документи: українська |
| Time zone / Часовий пояс | Europe/Warsaw |

## 3. Autonomy level (constitution 3.4) / Рівень автономії
- Level / Рівень: **the Owner merges** (посилення). Агенти НЕ пушать і НЕ зливають. Власник пушить гілки; Агент 0 відкриває PR і зливає лише за явним «злий PR #N» від Власника (або Власник зливає сам).
- Who may give `QA Verified` / Хто може ставити `QA Verified`: агент «QA і безпека» (не автор коду), або Власник.
- Порядок QA і злиття: варіант А (рішення Власника 2026-11-11): QA перевіряє гілку PR до злиття (запис QA пишеться на гілці від гілки етапу), код зливається після QA Verified і «злий» від Власника, запис QA зливається слідом.

## 4. Roles and areas / Ролі й зони
| Role / Роль | Folder / Папка | May touch / Може змінювати | May not touch / Не чіпає |
|---|---|---|---|
| Agent 0 (координатор, чат Claude) | Увесь репозиторій через GitHub API (лише PR) | Промти, PR, огляд, дорожня карта | Код застосунку; не пушить гілок агентів; не зливає без «злий» |
| Архітектор (окремий чат claude.ai) | — | Технічні рішення, контракти, промти етапів | Репозиторій напряму |
| Керівник проєкту (агент Claude Code) | `docs/` | Документи, журнал, плани, `docs/governance/` | Код, `.claude/**`, конституція |
| PDF-ядро | `core-pdf/` | Код, тести ядра | `app/`, `core-ads/` |
| Android і UI | `app/` | UI, міст, збереження | `core-pdf/` (прогалини описує в звіті) |
| Монетизація і Google Play | `core-ads/`, `docs/store/` | Реклама, тексти магазину (чернетки) | Реальні ключі й одиниці |
| QA і безпека | Тести, перевірка на пристрої, запис QA в журналі | Тести, `docs/project_log.md` | Код продукту |

## 5. Technology and environment / Технології й середовище
- Stack / Стек: Kotlin/Java, Gradle, JDK 21; `androidx.pdf:pdf-viewer-fragment:1.0.0-beta01` (`@ExperimentalPdfApi` лише в `PdfViewerBridge.kt`), PdfBox-Android 2.0.27.0, Room, Material 3.
- Platforms / Платформи: Android, minSdk 28, targetSdk 36; файли лише через SAF.
- Owner's computer / Комп'ютер Власника: Windows, PowerShell, Android Studio; один важкий процес за раз.
- Devices for testing / Пристрої для перевірки: Samsung Galaxy S24 Ultra (Android 17), підключає Власник кабелем; Android 9–11 лише емулятор (Етап 4).
- Enabled modules / Підключені модулі: Android/Google Play, монетизація (AdMob + UMP).

## 6. Licences and third-party components / Ліцензії й сторонні компоненти
- Register file / Файл реєстру: `docs/THIRD_PARTY.md`
- Accepted licences / Прийнятні ліцензії: Apache-2.0, MIT, BSD, OFL-1.1 (шрифти) та інші сумісні з закритою комерційною публікацією
- Conditional / Умовно: ліцензії з вимогою атрибуції чи копілефт — після окремої перевірки
- Forbidden / Заборонені: GPL/AGPL для залінкованого коду, NC, ND, «лише особисте використання», відсутність ліцензії; платні бібліотеки
- Rule of verification / Правило перевірки: звірка з оригінальним джерелом автора, запис у реєстрі (назва, URL, версія/коміт, SHA-256 для файлів)

## 7. Owner-only list (constitution 3.2) / Лише Власник
- Accounts and consoles / Акаунти й консолі: Google Play Console ($25), AdMob, налаштування й секрети GitHub, приватизація репозиторію, підписувальний ключ
- Paid services / Платні сервіси: жодних без окремої згоди Власника з точною сумою й альтернативою
- Publishing channels (beyond internal testing) / Канали публікації: Google Play (закритий тест ≥12 тестувальників × ≥14 днів, R1) та будь-який публічний
- Approved in advance / Затверджено наперед: реклама лише в розміщеннях з R4 (банер у списках/інструментах, рідкий interstitial після завершеної дії; без реклами при відкритті файлу й під час читання; App Open заборонено); UMP-згода
- Phases and gates / Етапи й ворота: етапи розміщення 0, 1a–1e, 2, 3a–3f, `reading-progress`, 4, підготовка до публікації (`docs/research/MARKUP_STAGES.md`). `Owner Approved` потрібен: після 3f, після Етапу 4, перед будь-якою публікацією. Ворота = незалежний QA на пристрої (`QA Verified`) + закриті чи прийняті дефекти.

## 8. Reports and commands / Звіти й команди
| Purpose / Призначення | Command or file / Команда або файл |
|---|---|
| One Owner action / Одна дія Власника | Крок у повідомленні Агента 0 (готова команда + «що надіслати у відповідь»); окремої команди немає |
| Period report / Звіт за період | Дорожня карта (Claude Docs) + запит Власнику «звіт»; зберігати в `docs/reports/` (н/д, створити за потреби) |
| Phase gate / Ворота етапу | Запис QA в `docs/project_log.md` + приймання Власником (3f, Етап 4) |
| Session close / Закриття сесії | Запис ролі в `docs/project_log.md` + оновлення `CURRENT_STATE.md` (Агент 0) |
| Report template / Шаблон звіту | Формат запису в `docs/project_log.md`: зверху 5–8 рядків «Простими словами», далі Що зроблено / Що змінено / Рішення / Що передати далі / Відкриті питання; «НЕ ПЕРЕВІРЕНО» позначається прямо |

## 9. Paths and checks / Шляхи й перевірки
| What / Що | Path / Шлях |
|---|---|
| Constitution and profile / Конституція й профіль | `docs/governance/` |
| Decision log / Журнал рішень | `docs/MASTER_SPEC.md` (таблиця R1–R15), `docs/research/ARCHITECT_DECISION_*.md`; нові рішення Власника й Керівника → `docs/governance/DECISIONS.md` (створити; посилання на R-таблицю замість дублювання) |
| Current state / Поточний стан | `docs/governance/CURRENT_STATE.md` (створити) |
| Task board / Дошка завдань | розділ підетапів у `docs/research/MARKUP_STAGES.md` + дорожня карта «Дорожня карта Volum PDF» |
| Session log / Журнал сесій | `docs/project_log.md` (нові записи зверху) |
| Lessons / Уроки | `docs/governance/LESSONS.md` (створити) |
| Owner inbox / Скринька Власника | `docs/governance/OWNER_INBOX.md` (створити) |
| Task cards / Картки завдань | Окремих карток немає: роль задається промтом Архітектора; хід роботи — запис у `docs/project_log.md` |
| Product and plan documents / Документи продукту й плану | `docs/MASTER_SPEC.md`, `docs/CONTRACTS.md`, `docs/research/MARKUP_STAGES.md` |
| Definition of Done | `docs/MASTER_SPEC.md`, розділ «Якість» (+ «Гігієна перевірок на пристрої») |
| Agent working rules / Робочі правила агентів | Промти ролей від Архітектора + цей профіль; `AGENTS.md` н/д |
| Key terms for the overwrite check / Ключові терміни | R1…R15; `§1.5`, `§1.5.1`; `MarkupLayout`; `MarkupEngine`; `PdfPermissionException`; `DocumentEditability`; `checkEditability`; `PendingStore`; `com.savoj.volumpdf`; `Roboto-Regular`, `OFL.txt`; підетапи 3a–3f; мітка «НЕ ПЕРЕВІРЕНО» |
| Required CI checks / Обов'язкові перевірки CI | CI на GitHub відсутній (н/д). Замість нього: `./gradlew :app:assembleDebug :app:testDebugUnitTest :core-pdf:test` на JDK 21, цифри з XML-звітів Gradle |

## 10. Project-specific tightening and exceptions / Посилення й винятки проєкту
1. **Не пушать (посилення 3.4, п. 5.4.2).** Агенти комітять локально й у журналі пишуть, що не пушили; пуш виконує Власник командою `git push -u origin <гілка>`. Сумісно з 5.4.2 («або пояснити в журналі, чому ні»).
   Agents never push; the Owner pushes. Compatible with 5.4.2 ("or explain in the log why not").
2. **Зливає Власник** (див. п. 3). Видалення гілок: лише Власник.
3. **Контракти.** Зміни `docs/CONTRACTS.md` і `docs/MASTER_SPEC.md` (R-таблиця) лише за рішенням Архітектора чи Власника; записує Керівник проєкту. `core-pdf` не змінює агент UI; прогалину контракту він описує, не виправляє.
4. **Гігієна перевірок на пристрої** (MASTER_SPEC, «Якість»): працювати з копіями тестових PDF; записувати й повертати початкові налаштування; перед знімком екран розблоковано й у фокусі тестовий застосунок; знімок із чужим вмістом видаляти одразу й не згадувати вміст; не переглядати особисті каталоги (Download, DCIM); скріншоти й логи не комітити; після прогону прибрати спайк і файли з /sdcard; PIN не вводити, при блокуванні зупинитись і попросити Власника.
5. **Приватність.** Файли користувача не покидають пристрій без явної згоди; жодної аналітики понад необхідне для реклами; паролі документів лише в пам'яті (не в стані, файлах, логах); секрети й ключі підпису не в репозиторії й чаті.
6. **Фізичні дії (3.2).** Підключення й розблокування телефона, пуш гілок — Власник. QA-агент працює з телефоном через adb лише коли Власник його підключив.
7. **Прийняті відступи від конституції:** немає.
