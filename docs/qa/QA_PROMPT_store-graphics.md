# QA-промт: графіка магазину (гілка `docs/store-graphics`)
Ти НЕ автор. Перевір `docs/store/assets/` (README там же). Не змінюй файли.
1. Розміри й формат (Pillow або `file`): `icon_512.png` 512×512 PNG ≤1 МБ; `feature_graphic_1024x500.png` 1024×500 PNG; усі `phone/*.png` 9:16 (1440×2560), сторона ≥1080, без альфа-каналу (режим RGB).
2. Відсутність особистого: переглянь кожен файл. Немає імен, номерів, сповіщень, чужих застосунків, облич, PIN, шляхів до особистих папок. У статус-барі лише час, значки мережі й батареї.
3. Відповідність значка поточному (`app/src/main/res/drawable/ic_launcher_foreground.xml`, фон `#3949AB`).
4. Feature graphic: назва «Anti PDF», гасло українською, без чужих брендів.
5. Телефон: гігієна (`PROJECT_PROFILE` п. 10.2) виконана: `wm size` без override, density 560, `uimode night yes`, `screen_off_timeout` 15000, немає `/sdcard/Documents/qa_tmp_store`, у «Останніх» немає записів `demo_*`.
6. Запис у `docs/qa/QA_store-graphics_<дата>.md`, вердикт `QA Verified` або дефекти; НЕ ПЕРЕВІРЕНО позначай прямо.
