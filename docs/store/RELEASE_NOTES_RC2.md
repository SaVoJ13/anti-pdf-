# Примітки до випуску RC2 (версія 1.0.1, Google Play «Що нового»)

> Чернетка. У Play Console нічого не вносилось. Вставляє координатор у закритий трек «Alpha» за дозволом власника (див. `docs/release/RC2_CHECKLIST.md`).

## Що справді в цьому випуску (звірено з `docs/project_log.md` і `git log fc5636d..main`)
- Мови інтерфейсу de, ru, es + вибір мови для застосунку в налаштуваннях Android (`feat/localization-de-ru-es`; виправлення перекладів `f89d94a`).
- Екран ліцензій: повні тексти + кнопка «Ліцензії бібліотек» (`fix/stage1-licenses`).
- Реклама AdMob 25.5.0 і форма згоди UMP 4.0.0 (`fix/stage1-ads-16kb`).
- Не згадано навмисно: поділ тестових/бойових ID, 16 КБ, документи (користувачам нецікаво). Окремих «виправлень помилок продукту» після RC1 у `main` немає, тому в примітках лише «виправлення перекладів».

## Довжина (вимога Play 500 символів; практичний ліміт, що пройшов у консолі, 362)
Символів (рахунок Python `len`): en 296, uk 318, de 322, ru 318, es 337. Усі ≤ 362.

## Текст для вставки (кожен тег з нового рядка)

```
<en>
What's new in 1.0.1: the app now speaks German, Russian and Spanish (choose the language in Android settings for this app). A new licenses screen shows the full texts of the licenses used. Updated ads and consent form (you can change your choice in the menu: Privacy settings). Translation fixes.
</en>
<uk>
Що нового в 1.0.1: застосунок тепер німецькою, російською та іспанською (мову можна обрати в налаштуваннях Android для цього застосунку). Новий екран ліцензій з повними текстами використаних ліцензій. Оновлені реклама й форма згоди (вибір можна змінити в меню: «Налаштування конфіденційності»). Виправлення перекладів.
</uk>
<de>
Neu in 1.0.1: Die App gibt es jetzt auf Deutsch, Russisch und Spanisch (Sprache in den Android-Einstellungen für diese App wählen). Neuer Lizenzbildschirm mit den vollständigen Lizenztexten. Aktualisierte Werbung und Einwilligungsformular (Auswahl im Menü änderbar: Datenschutzeinstellungen). Korrekturen an Übersetzungen.
</de>
<ru>
Что нового в 1.0.1: приложение теперь на немецком, русском и испанском (язык можно выбрать в настройках Android для этого приложения). Новый экран лицензий с полными текстами используемых лицензий. Обновлены реклама и форма согласия (выбор можно изменить в меню: «Настройки конфиденциальности»). Исправления переводов.
</ru>
<es>
Novedades de 1.0.1: la aplicación ahora está en alemán, ruso y español (elige el idioma en los ajustes de Android para esta aplicación). Nueva pantalla de licencias con los textos completos. Anuncios y formulario de consentimiento actualizados (puedes cambiar tu elección en el menú: Ajustes de privacidad). Correcciones de traducciones.
</es>
```
