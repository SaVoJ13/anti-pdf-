# CURRENT_STATE — знімок проєкту Volum PDF

> За п. 5.1 конституції. Веде Агент 0 / Керівник проєкту. Оновлено: 2026-11-12 (`main`, після злиття й приймання Власником Етапу 3 включно з `3f` та `feature/reading-progress`, репозиторій `https://github.com/SaVoJ13/anti-pdf-`).
> Джерела: `docs/research/MARKUP_STAGES.md`, `docs/project_log.md`, `docs/governance/DECISIONS.md`, `docs/research/LARGE_FILES_REPORT.md`. Що там не підтверджено, позначено «НЕ ПЕРЕВІРЕНО».

## Етап
Етап 3 («Поставити» на сторінці, **3a–3f** + **`feature/reading-progress`**) **ПОВНІСТЮ ЗАВЕРШЕНО** (`QA Verified` + `Owner Approved`). Переходимо до **Етапу 4** (`feature/core-large-files` + `feature/qa-markup`). Паралельно на гілці `feature/core-large-files` (`71e027e`) підготовлено JVM-полігон великих/пошкоджених файлів і виправлення F4/F5 для Етапу 4.

## Стан
| Підетап | Гілка | Стан |
|---|---|---|
| 3a ✓/✗/текст | `feature/place-text-marks` | ЗЛИТО (PR #16), QA пройдено (PR #17) |
| 3b виділення, рух, зміна, видалення, undo | `feature/place-edit-objects` | ЗЛИТО, QA пройдено (PR #19) |
| 3c малювання й підпис | `feature/place-ink-signature` | ЗЛИТО (PR #20), QA пройдено (PR #21) |
| 3d форми й єдине «Зберегти» | `feature/place-forms-save` | ЗЛИТО (PR #23 код, #24 QA) |
| 3e захищені документи (R15) | `feature/place-protected-docs` | ЗЛИТО (PR #27 код `2605245`, PR #28 QA `154d055`) |
| **прогрес читання** | `feature/reading-progress` | **ЗЛИТО** (2026-11-12, `QA Verified`: `:app` 173/173, `:core-pdf` 72/73; **живий прогін на телефоні S24 Ultra підтверджено Власником — `Owner Approved`**) |
| Ядро: великі й пошкоджені файли | `feature/core-large-files` (`71e027e`) | Код і звіт готові (F4 і F5 виправлено, 78/78 тестів JVM); чекає рішень Архітектора за F1/F2/F6/F7 і QA Етапу 4 на пристрої |
| **3f приймання** | `feature/qa-place-final` | **ЗЛИТО** (2026-11-12, `QA Verified` + **`Owner Approved` на S24 Ultra**: S Pen, щипок, другий палець, непідтримувані символи, 4 краї, 120 стор., 4 мови) |
| **Етап 4 QA і безпека** | `feature/qa-markup` | **НАСТУПНИЙ** (разом із злиттям `feature/core-large-files`) |
| Підготовка до публікації | — | після Етапу 4 |

## Процес і середовище (оновлено 2026-11-12)
Робота ведеться в середовищі **Google Antigravity** з новим репозиторієм `https://github.com/SaVoJ13/anti-pdf-` (локальна робоча папка: `C:\Users\cocro\.gemini\antigravity\scratch\pdf-reader-android`). Правила Конституції v2.0 підвантажуються автоматично через `AGENTS.md`, `CLAUDE.md`, `.agents/rules/constitution.md` та глобально `~/.gemini/config/rules/AGENTS.md`. Рівень автономії — стандартний за п. 3.1 Конституції з порядком QA за варіантом А (незалежний QA перевіряє гілку до злиття в `main`).

## Відомі відкриті питання й низькі дефекти (після QA 3e та `LARGE_FILES_REPORT.md`)
- **Зі звіту QA 3e (критичних і середніх 0, низьких 3):**
  - Д1 (низький): після `am kill` під час відкритого системного пікера місця для копії лишається 0-байтний файл `<ім'я>_edited.pdf` (оригінал і незбережені зміни цілі).
  - Д2 (низький): після збереження копії файла з user-паролем переглядач просить пароль ще раз.
  - Д3 (низький): у ландшафті з відкритою клавіатурою вікно пароля перекриває кнопки «Скасувати»/«Продовжити», доки не згорнути клавіатуру.
- **Зі звіту `LARGE_FILES_REPORT.md` (`feature/core-large-files`):**
  - F1/F2: `PDFBox` у пам'яті тримає ≈ 2,1–2,2 × розміру файлу (60 МБ → 133 МБ, скан 324 МБ зависає в купі 512 МБ); перехід на `PDDocument.load(File, MemoryUsageSetting)` зменшує пік у 2–6 разів (потребує рішення Архітектора).
  - F6/F7: політика `rotatePages`/`deletePages`/`extractPages` на зашифрованих файлах і перехід `PageOperationsEngine` на `PdfIo.saveSafely`.

## Блокери
- Технічних блокерів немає.

## Наступні 3 кроки
1. **Рішення та злиття `feature/core-large-files`:** злити фікси F4/F5 та ухвалити рішення щодо F1/F2 (`MemoryUsageSetting.setupMixed`) і F6/F7.
2. **Рішення за `feature/core-large-files`:** злити фікси F4/F5 та ухвалити рішення щодо F1/F2/F6/F7.
3. **Етап 4 (`feature/qa-markup`):** повний QA на пристрої (великі, скановані, зашифровані, пошкоджені файли) й емуляторах API 28/30.

## НЕ ПЕРЕВІРЕНО (перенесено з джерел)
`PdfView` на Android 9–11 (API 28, 30); інкрементальне збереження зашифрованих у pdfbox-android 2.0.27.0; екран ліцензій OFL/Apache; транзитивність `recyclerview` і `viewpager2`; великі файли (60–324 МБ) і `imagesToPdf` на реальному пристрої (ART).
