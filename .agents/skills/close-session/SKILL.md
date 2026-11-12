---
name: close-session
description: >-
  Use this skill before finishing a session or task (/close-session) to follow Section 5.4 and 5.5 of the Agent Team Constitution: prepend project_log.md entry, record lessons, and update CURRENT_STATE.md.
---

# Навичка `/close-session` (Закриття сесії, Конституція п. 5.4, 5.5, 6)

Виконуй цю процедуру перед завершенням кожної сесії або завдання.

## Обов'язковий чекліст (Конституція п. 5.4)
1. **Запис у `docs/project_log.md` (нові записи зверху, append-only):**
   - Зверху 5–8 рядків **«Простими словами»**.
   - Далі: **Що зроблено** / **Що змінено** / **Рішення** / **Що передати далі** / **Відкриті питання** / **НЕ ПЕРЕВІРЕНО**.
2. **Уроки (`docs/governance/LESSONS.md`):** Якщо виявлено нову пастку — додай запис у `LESSONS.md` (append-only).
3. **Оновлення стану (Агент 0):** Онови `docs/governance/CURRENT_STATE.md`.
4. **Перевірка відсутності перезапису (п. 5.5):** Переконайся, що в документах немає `<<<<<<<`, а ключові терміни проєкту та старі записи збережені.
5. **Git:** Закоміть усі зміни у відповідну гілку.
