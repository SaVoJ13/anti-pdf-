---
name: daily-report
description: >-
  Use this skill when the Owner asks for a period report ("звіт за сьогодні", "звіт за тиждень", /daily-report) per Section 6 of the Agent Team Constitution.
---

# Навичка `/daily-report` (Звіт за період, Конституція п. 6)

Використовуй цю процедуру на вимогу Власника («звіт за сьогодні/тиждень» або `/daily-report`).

## Кроки виконання
1. Прочитай `docs/governance/CURRENT_STATE.md`, `docs/research/MARKUP_STAGES.md`, `docs/project_log.md`, `docs/governance/DECISIONS.md` та `docs/governance/OWNER_INBOX.md`.
2. Перевір `git log` за вказаний період.
3. Сформуй чесний звіт українською мовою (зверху «Простими словами»):
   - Усі цифри бери виключно з XML-звітів Gradle, логів або файлів.
   - Прямо вкажи, що **НЕ ПЕРЕВІРЕНО**.
   - Виведи відкриті питання з `OWNER_INBOX.md` з рекомендаціями.
4. Збережи копію звіту у `docs/reports/REPORT-<YYYY-MM-DD>.md` та покажи Власнику в чаті.
