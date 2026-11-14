# PROJECT PROFILE / ПРОФІЛЬ ПРОЄКТУ — Volum PDF

> Fills the 🔧 parameters of the constitution (`CONSTITUTION.en.md` / `CONSTITUTION.uk.md`). May only fill in and **tighten** rules, never loosen them.
> Заповнює 🔧-параметри конституції. Може лише заповнювати й **посилювати** правила, а не послаблювати.
> Version / Версія: 1.3 (Затверджено Власником) · Date / Дата: 2026-11-14 · Constitution / Конституція: 2.0

## 1. Project / Проєкт
| Field / Поле | Value / Значення |
|---|---|
| Name / Назва | Volum PDF (у магазині: Volum PDF: Reader & Tools). `applicationId`: у профілі раніше було `com.savoj.volumpdf`; у коді (`app/build.gradle.kts`) станом на 2026-11-14 `com.savoj.volumpdf.anti` (ярлик `Anti PDF`, рішення Власника 2026-11-12, `DECISIONS.md`); `namespace` = `com.savoj.volumpdf`. **ВІДКРИТО (лише Власник):** під яким `applicationId` публікуватись у Play (після першої публікації незмінний), див. `OWNER_INBOX.md`, п. 8 |
| Short description / Короткий опис | Android-застосунок для читання й роботи з PDF: розміщення тексту, ✓/✗, малювання, підпису, заповнення форм AcroForm; інструменти сторінок; монетизація рекламою (AdMob + UMP); мета — Google Play |
| Project licence / Ліцензія проєкту | Proprietary (закритий код, R5) |
| Main branch / Головна гілка | main |
| Repository / Репозиторій | https://github.com/SaVoJ13/anti-pdf- (попередній репозиторій: https://github.com/SaVoJ13/pdf-reader-android; тимчасово публічний, потім приватний; до приватизації не комітити цінного й закритого) |

## 2. Owner / Власник
| Field / Поле | Value / Значення |
|---|---|
| Time for the project / Час на проєкт | Сесіями, у вільний час (точний обсяг н/д) |
| Technical level / Технічний рівень | Не програміст |
| Language of reports and chat / Мова звітів і спілкування | Українська; звіти «простими словами» |
| Language of code, comments, commits / Мова коду, коментарів, комітів | Код і коміти: англійська; коментарі й документи: українська |
| Time zone / Часовий пояс | Europe/Warsaw |

## 3. Autonomy level (constitution 3.4) / Рівень автономії
- Level / Рівень: **Стандартний (п. 3.1 конституції)** у середовищі Google Antigravity — агенти створюють гілки `feature/...`, проводять незалежне QA (варіант А: QA перевіряє гілку до злиття) і самі пушать свої гілки та зливають PR у `main` після отримання `QA Verified` від іншого агента (п. 2.1) і зелених збірки й тестів Gradle. Так записано за прямою командою Власника 2026-11-14 («наступним разом нехай зливає агент, так як написано в конституції»): профіль більше не посилює конституцію щодо пушу й злиття, діє п. 3.1. Розділ 3.2 лишається за Власником.
- Who may give `QA Verified` / Хто може ставити `QA Verified`: агент «QA і безпека» (окремий субагент, не автор коду), або Власник.
- Порядок QA і злиття: варіант А (рішення Власника 2026-11-11): QA перевіряє гілку PR до злиття (запис QA пишеться на гілці від гілки етапу), код зливається (агентом) після `QA Verified`, запис QA зливається слідом.

## 4. Roles and areas / Ролі й зони
| Role / Роль | Folder / Папка | May touch / Може змінювати | May not touch / Не чіпає |
|---|---|---|---|
| Agent 0 (Керівник проєкту / Координатор, головна сесія Antigravity / Claude) | Увесь репозиторій | `docs/`, `docs/governance/`, координація гілок, PR, дорожня карта | Не ставить `QA Verified` на власний код; не змінює `CONSTITUTION.*` без прямої команди Власника |
| Архітектор | — | Технічні рішення, контракти, промти етапів | Не ставить `QA Verified` на власний код |
| PDF-ядро | `core-pdf/` | Код, тести ядра | `app/`, `core-ads/` |
| Android і UI | `app/` | UI, міст, збереження | `core-pdf/` (прогалини описує в звіті) |
| Монетизація і Google Play | `core-ads/`, `docs/store/` | Реклама, тексти магазину (чернетки) | Реальні ключі й одиниці |
| QA і безпека | Тести, перевірка на пристрої, запис QA в журналі | Тести, `docs/project_log.md` | Код продукту |

## 5. Technology and environment / Технології й середовище
- Stack / Стек: Kotlin/Java, Gradle, JDK 21; `androidx.pdf:pdf-viewer-fragment:1.0.0-beta01` (`@ExperimentalPdfApi` лише в `PdfViewerBridge.kt`), PdfBox-Android 2.0.27.0, Room, Material 3.
- Platforms / Платформи: Android, minSdk 28, targetSdk 36; файли лише через SAF.
- Owner's computer / Комп'ютер Власника: Windows, PowerShell, Google Antigravity, Android Studio; один важкий процес за раз.
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
| One Owner action / Одна дія Власника | Навичка `owner-step` (`.agents/skills/owner-step/SKILL.md`): 1 готова команда + «що надіслати у відповідь» |
| Period report / Звіт за період | Навичка `daily-report` (`.agents/skills/daily-report/SKILL.md`), зберігається в `docs/reports/` |
| Phase gate / Ворота етапу | Навичка `phase-gate` (`.agents/skills/phase-gate/SKILL.md`): запис QA в `docs/project_log.md` + приймання Власником (3f, Етап 4) |
| Session close / Закриття сесії | Навичка `close-session` (`.agents/skills/close-session/SKILL.md`): запис ролі в `docs/project_log.md` + оновлення `CURRENT_STATE.md` (Агент 0) |
| Independent QA / Незалежний QA | Навичка `qa-verify` (`.agents/skills/qa-verify/SKILL.md`) |
| Report template / Шаблон звіту | Формат запису в `docs/project_log.md`: зверху 5–8 рядків «Простими словами», далі Що зроблено / Що змінено / Рішення / Що передати далі / Відкриті питання; «НЕ ПЕРЕВІРЕНО» позначається прямо |

## 9. Paths and checks / Шляхи й перевірки
| What / Що | Path / Шлях |
|---|---|
| Constitution and profile / Конституція й профіль | `docs/governance/` |
| Decision log / Журнал рішень | `docs/MASTER_SPEC.md` (таблиця R1–R15), `docs/research/ARCHITECT_DECISION_*.md`; нові рішення Власника й Керівника → `docs/governance/DECISIONS.md` |
| Current state / Поточний стан | `docs/governance/CURRENT_STATE.md` |
| Task board / Дошка завдань | розділ підетапів у `docs/research/MARKUP_STAGES.md` + `docs/governance/CURRENT_STATE.md` |
| Session log / Журнал сесій | `docs/project_log.md` (нові записи зверху) |
| Lessons / Уроки | `docs/governance/LESSONS.md` |
| Owner inbox / Скринька Власника | `docs/governance/OWNER_INBOX.md` |
| Task cards / Картки завдань | Окремих карток немає: роль задається промтом/завданням за `MARKUP_STAGES.md`; хід роботи — запис у `docs/project_log.md` |
| Product and plan documents / Документи продукту й плану | `docs/MASTER_SPEC.md`, `docs/CONTRACTS.md`, `docs/research/MARKUP_STAGES.md` |
| Definition of Done | `docs/MASTER_SPEC.md`, розділ «Якість» (+ «Гігієна перевірок на пристрої») |
| Agent working rules / Робочі правила агентів | `AGENTS.md`, `CLAUDE.md`, `.agents/rules/constitution.md` (та глобально `~/.gemini/config/rules/AGENTS.md`) |
| Key terms for the overwrite check / Ключові терміни | R1…R15; `§1.5`, `§1.5.1`; `MarkupLayout`; `MarkupEngine`; `PdfPermissionException`; `DocumentEditability`; `checkEditability`; `PendingStore`; `com.savoj.volumpdf`; `Roboto-Regular`, `OFL.txt`; підетапи 3a–3f; мітка «НЕ ПЕРЕВІРЕНО» |
| Required CI checks / Обов'язкові перевірки CI | CI на GitHub відсутній (н/д). Замість нього: `./gradlew :app:assembleDebug :app:testDebugUnitTest :core-pdf:test` на JDK 21, цифри з XML-звітів Gradle |

## 10. Project-specific tightening and exceptions / Посилення й винятки проєкту
1. **Контракти.** Зміни `docs/CONTRACTS.md` і `docs/MASTER_SPEC.md` (R-таблиця) лише за рішенням Архітектора чи Власника; записує Керівник проєкту. `core-pdf` не змінює агент UI; прогалину контракту він описує, не виправляє.
2. **Гігієна перевірок на пристрої** (MASTER_SPEC, «Якість»): працювати з копіями тестових PDF; записувати й повертати початкові налаштування; перед знімком екран розблоковано й у фокусі тестовий застосунок; знімок із чужим вмістом видаляти одразу й не згадувати вміст; не переглядати особисті каталоги (Download, DCIM); скріншоти й логи не комітити; після прогону прибрати спайк і файли з /sdcard; PIN не вводити, при блокуванні зупинитись і попросити Власника.
3. **Приватність.** Файли користувача не покидають пристрій без явної згоди; жодної аналітики понад необхідне для реклами; паролі документів лише в пам'яті (не в стані, файлах, логах); секрети й ключі підпису не в репозиторії й чаті.
4. **Фізичні дії (3.2).** Підключення й розблокування телефона — Власник. QA-агент працює з телефоном через adb лише коли Власник його підключив.
5. **Прийняті відступи від конституції:** немає.
6. **Пуш і злиття (на пряму команду Власника, 2026-11-14).** Раніше діяло посилення «агенти не пушать, зливає Власник» (профіль 1.0–1.2). Знято. Тепер агенти самі пушать свої гілки (без `--force`) і зливають PR у `main` після `QA Verified` від іншого агента (QA ≠ автор; QA, що писав частину коду, вердикту не дає) та зелених збірки й тестів, як у п. 3.1 конституції. Порядок А лишається: QA перевіряє гілку до злиття. Розділ 3.2 конституції (гроші, акаунти й консолі, публікація, ключі підпису, зміна затвердженого, переходи між етапами, повідомлення назовні, незворотні видалення, бойові ключі й реальні платежі, розширення дозволів і збору даних, фізичні дії на пристрої, зміна конституції) лишається лише за Власником.

## 11. Version history / Історія версій
| Версія | Дата | Зміна |
|---|---|---|
| 1.0 | 2026-11-11 | Профіль затверджено Власником (джерело: `DECISIONS.md`, 2026-11-11). |
| 1.1 | 2026-11-11 | Порядок QA і злиття = варіант А (`DECISIONS.md`). |
| 1.2 | 2026-11-12 | Середовище Antigravity, стандартний рівень автономії, позначку DRAFT знято (`DECISIONS.md`, 2026-11-12). |
| 1.3 | 2026-11-14 | Знято посилення «агенти не пушать, зливає Власник» (розд. 3 і 10, п. 6) на пряму команду Власника; факт про `applicationId` (код `com.savoj.volumpdf.anti`) і відкрите рішення Власника щодо Play. Слова «ЧЕРНЕТКА» в заголовку вже немає (знято в 1.2), конституції це не суперечить. |
