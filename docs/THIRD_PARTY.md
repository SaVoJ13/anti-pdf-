# THIRD_PARTY — реєстр залежностей і сервісів
Веде лише Керівник проєкту. Інші агенти додають рядки лише за дозволом.
Правило: ліцензія береться з першоджерела (LICENSE/POM), не з памʼяті. Без запису й дозволу залежність не додається.

| Назва | Версія | Ліцензія (першоджерело, посилання) | Де використовується | Сумісна із закритим комерційним продуктом | Дозвіл власника | Дата |
|-------|--------|-------------------------------------|---------------------|-------------------------------------------|-----------------|------|
| `androidx.pdf:pdf-viewer-fragment` | 1.0.0-beta01 | Apache-2.0 (Google Maven POM, META-INF/LICENSE.txt; перевірено спайком 2026-11-03) | `core-pdf` (перегляд PDF, жест зуму, навігація, системне виділення й пошук тексту) | так | Так, затверджено власником для MVP 2026-11-03 | 2026-11-03 |
| `com.tom-roush:pdfbox-android` | 2.0.27.0 | Apache-2.0 (Maven Central POM, LICENSE.txt; BouncyCastle під адаптованою MIT; перевірено 2026-11-03) | `core-pdf` (операції зі сторінками: злиття, спліт, поворот, видалення, зображення->PDF, паролі) | так | Так, затверджено власником для MVP 2026-11-03 | 2026-11-03 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 | Apache-2.0 (GitHub / Maven Central POM) | `core-pdf` (асинхронні потоки Flow, корутини) | так | Так, стандартна бібліотека середовища (дозволено Керівником) | 2026-11-03 |
| `junit:junit` | 4.13.2 | EPL-1.0 (Maven Central POM) | `core-pdf` (виключно JVM unit-тести, не потрапляє в релізний APK) | так (test only) | Так (дозволено Керівником для тестів) | 2026-11-03 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.10.1 | Apache-2.0 (Maven Central POM) | `core-pdf` (виключно unit-тести, не потрапляє в APK) | так (test only) | Так (дозволено Керівником для тестів) | 2026-11-03 |
| `org.mockito:mockito-core` | 5.15.2 | MIT (Maven Central POM) | `core-pdf` (виключно unit-тести, не потрапляє в APK) | так (test only) | Так (дозволено Керівником для тестів) | 2026-11-03 |
| `androidx.appcompat:appcompat` | 1.7.0 | Apache-2.0 (Google Maven POM) | `app` (базові компоненти UI, теми) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| `com.google.android.material:material` | 1.12.0 | Apache-2.0 (Google Maven POM) | `app` (Material 3 компоненти, кнопки, діалоги) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| `androidx.constraintlayout:constraintlayout` | 2.2.1 | Apache-2.0 (Google Maven POM) | `app` (розмітка екранів) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.8.7 | Apache-2.0 (Google Maven POM) | `app` (MVVM архітектура, ViewModels) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| `androidx.activity:activity-ktx` | 1.10.1 | Apache-2.0 (Google Maven POM) | `app` (SAF Intent-лаунчери, back press) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| `androidx.fragment:fragment-ktx` | 1.8.6 | Apache-2.0 (Google Maven POM) | `app` (робота з PdfViewerFragment) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| `androidx.room:room-runtime` | 2.6.1 | Apache-2.0 (Google Maven POM) | `app` (локальна SQLite база «Останні документи» та закладки) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| `androidx.room:room-ktx` | 2.6.1 | Apache-2.0 (Google Maven POM) | `app` (Flow та Coroutines підтримка в Room) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| `androidx.room:room-compiler` | 2.6.1 | Apache-2.0 (Google Maven POM) | `app` (Java annotation processor для Room, підключено через `annotationProcessor`, не KSP; за `app/build.gradle.kts:55`) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| `com.google.android.gms:play-services-ads` | 23.6.0 | Google Play Services SDK (AdMob SDK License; сумісна із закритими комерційними додатками) | `core-ads` (показ банерів та інтерстишелів за затвердженими правилами) | так | Так, затверджено власником для MVP 2026-11-03 | 2026-11-03 |
| `com.google.android.ump:user-messaging-platform` | 3.1.0 | Google Play Services / UMP SDK License (сумісна із закритими комерційними додатками) | `core-ads` (форма згоди користувача GDPR / EEA) | так | Так, затверджено власником для MVP 2026-11-03 | 2026-11-03 |
| Roboto Regular (статичний TTF; шрифт, не бібліотека) | Файл `Roboto-Regular.ttf` (171 676 байт), SHA-256 `79e851404657dac2106b3d22ad256d47824a9a5765458edb72c9102a45816d95`; джерело: `https://raw.githubusercontent.com/google/fonts/113bac0943d5b85dcc6c2c01b48fae88d4b5c52a/ofl/roboto/static/Roboto-Regular.ttf`, коміт `google/fonts` 113bac0943d5b85dcc6c2c01b48fae88d4b5c52a (2020-04-13, «Update Roboto to v3»). Каталог `ofl/roboto/static` на актуальному HEAD google/fonts відсутній (там лише змінні шрифти), тому взято з цього коміту. `OFL.txt` з того ж коміту: SHA-256 `9fb77dcb5cfef0de20f4b1ba296e3127320bcd7cc408ded5f61cd884f7bbb4dc`. Номер версії шрифту всередині файла: НЕ ПЕРЕВІРЕНО | SIL OFL 1.1 (`google/fonts`, `ofl/roboto/OFL.txt`, «Copyright 2011 The Roboto Project Authors», без Reserved Font Name; за прочитаним Архітектором 2026-11-07, окремо не перевірялось) | `core-pdf` (`src/main/assets/fonts/`; вбудовується в PDF як підмножина для тексту з кирилицею) | так (OFL дозволяє вбудовування й постачання з програмою; не продавати окремо, не змінювати й не перейменовувати, `OFL.txt` постачати поруч) | Так, власник затвердив рішення Архітектора 2026-11-07; сам файл у репозиторій додає етап 1b | 2026-11-07 |
| _(інше порожньо)_ | | | | | | |

## Сервіси
| Сервіс | Призначення | Вартість | Дані, що передаються | Дозвіл власника |
|--------|-------------|----------|----------------------|-----------------|
| Google AdMob (Google Play Services Ads) | Монетизація банерами та інтерстишелами | Безкоштовно (комісія з показу) | Рекламний ідентифікатор, IP, взаємодії (за згодою через UMP) | Так, затверджено власником для MVP 2026-11-03 |

## Відкриті розбіжності з build.gradle (звірка 2026-11-07)
Звірено `app/build.gradle.kts`, `core-pdf/build.gradle.kts`, `core-ads/build.gradle.kts` з реєстром вище. Рядки не додавалися (потрібен дозвіл власника).
- `androidx.recyclerview:recyclerview` і `androidx.viewpager2:viewpager2` використовуються в коді `app` (імпорти `androidx.recyclerview.widget.*`, `androidx.viewpager2.adapter.FragmentStateAdapter`), але не оголошені в жодному `build.gradle.kts` і відсутні в реєстрі. Імовірно, приходять транзитивно через `com.google.android.material:material:1.12.0`: НЕ ПЕРЕВІРЕНО.
- Стовпець «Де використовується» неповний: `kotlinx-coroutines-android` також підключено в `app` і `core-ads`; `junit`, `kotlinx-coroutines-test`, `mockito-core` також у `app` і `core-ads` (у реєстрі вказано лише `core-pdf`).
