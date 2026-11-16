# Обов'язковий пункт QA для змін `VolumPdfApp`, тем і ініціалізації

Причина: К-1 (2026-11-16). `Application.onCreate` виконується не лише в основному процесі, а й в ізольованому процесі PDF-сервісу (`androidx.pdf.service.PdfDocumentServiceImpl`, `isolatedProcess=true`), де `SharedPreferences` кидає `IllegalStateException`. Падіння там не видно в основному процесі: читач просто вічно показує спіннер.

Перевірити на пристрої, якщо зміна торкається `VolumPdfApp`, `Application`-колбеків, тем, `DynamicColors` або будь-чого, що читає сховище при старті:
1. Холодний старт (`am force-stop`), відкрити PDF через «Відкрити PDF» і з «Нещодавніх»: показується «Стор. 1 з N», сторінка малюється (не спіннер).
2. `adb logcat -d | grep -E "FATAL EXCEPTION|PdfDocumentServiceImpl"`: жодного `FATAL EXCEPTION` у процесі `...:androidx.pdf.service.PdfDocumentServiceImpl`.
3. Повторити при увімкненому й вимкненому «Кольори зі шпалер».
