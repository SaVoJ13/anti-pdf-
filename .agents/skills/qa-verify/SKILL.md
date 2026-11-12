---
name: qa-verify
description: >-
  Use this skill to perform independent QA verification (QA Verified) of a completed task per Section 2.1 and Red Line 9 of the Agent Team Constitution by invoking an independent QA subagent.
---

# Навичка `qa-verify` (Незалежна верифікація `QA Verified`, Конституція п. 2.1)

Згідно з п. 2.1 та Червоною лінією №9 Конституції, **автор змін (включно з Агентом 0) ніколи не ставить `QA Verified` на власну роботу**.

## Як провести легітимну верифікацію в Antigravity (варіант А)
1. Для перевірки гілки завдання викликай окремого субагента через `invoke_subagent`:
   - `TypeName`: `"self"`
   - `Role`: `"QA and Security Verifier"`
   - `Prompt`: Передай субагенту шлях до репозиторію, назву гілки, вимогу прочитати `docs/governance/CONSTITUTION.uk.md`, `docs/governance/PROJECT_PROFILE.md`, `docs/MASTER_SPEC.md` (зокрема «Гігієна перевірок на пристрої»), перевірити код, запустити тести на JDK 21 (числа брати з XML-звітів Gradle), провести перевірку на пристрої (якщо підключений телефон) і записати звіт у `docs/project_log.md`.
2. Код зливається в `main` після отримання `QA Verified` від QA-субагента.
