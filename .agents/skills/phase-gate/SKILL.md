---
name: phase-gate
description: >-
  Use this skill when a project phase is ready for completion (/phase-gate) to verify all Definition of Done criteria and prepare the Phase Gate report for Owner approval per Section 3.2 and 6 of the Agent Team Constitution.
---

# Навичка `/phase-gate` (Ворота етапу, Конституція п. 3.2, п. 6)

Перехід між етапами (`Owner Approved`, зокрема після 3f та після Етапу 4) дозволений виключно Власнику (п. 3.2 Конституції).

## Кроки виконання
1. Перевір `docs/research/MARKUP_STAGES.md` та `docs/project_log.md`: чи всі підетапи пройшли незалежний QA на пристрої (`QA Verified`).
2. Запусти перевірку збірки й тестів на JDK 21 (один процес за раз):
   ```powershell
   ./gradlew :app:assembleDebug :app:testDebugUnitTest :core-pdf:test
   ```
3. Перевір цілісність документації за п. 5.5 Конституції (відсутність `<<<<<<<`, наявність ключових термінів з розд. 9 `PROJECT_PROFILE.md`).
4. Сформуй звіт воріт етапу для `Owner Approved`.
