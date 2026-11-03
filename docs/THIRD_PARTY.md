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
| `androidx.room:room-compiler` | 2.6.1 | Apache-2.0 (Google Maven POM) | `app` (KSP кодогенератор для Room) | так | Так, стандартний AndroidX (дозволено Керівником) | 2026-11-03 |
| _(інше порожньо)_ | | | | | | |

## Сервіси
| Сервіс | Призначення | Вартість | Дані, що передаються | Дозвіл власника |
|--------|-------------|----------|----------------------|-----------------|
| Google AdMob (Google Play Services Ads) | Монетизація банерами та інтерстишелами | Безкоштовно (комісія з показу) | Рекламний ідентифікатор, IP, взаємодії (за згодою через UMP) | Так, затверджено власником для MVP 2026-11-03 |
