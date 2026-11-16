# Підписування релізу Anti PDF (простими словами)

Це інструкція для Власника. Агенти ключ не створюють, не бачать і не зберігають.

## Навіщо це
Google Play приймає лише підписаний пакет (AAB). Підпис робиться «ключем завантаження» (upload key). Сам «справжній» ключ підпису застосунку зберігає Google (режим **Play App Signing**). Якщо ти загубиш ключ завантаження, його можна замінити через підтримку Play. Якщо загубиш справжній ключ без Play App Signing, оновити застосунок буде неможливо. Тому Play App Signing вмикаємо.

## Золоті правила
1. Файл ключа (`.jks`) і файл `keystore.properties` **ніколи** не потрапляють у git, у чат, у пошту, у скріншоти. У `.gitignore` вони вже прописані (`*.jks`, `*.keystore`, `keystore.properties`).
2. Паролі зберігай у менеджері паролів. Зроби дві копії файла ключа в різних безпечних місцях (не в репозиторії).
3. Агентам і в чат ключ, паролі й шлях до ключа не надсилай.

## Крок 1. Створи ключ завантаження (один раз)
У PowerShell (JDK має `keytool`; наприклад, із Android Studio: `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`):

```
New-Item -ItemType Directory -Force "$env:USERPROFILE\keys"
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore "$env:USERPROFILE\keys\anti-pdf-upload.jks" `
  -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Keytool сам запитає пароль сховища, пароль ключа та твої дані (ім'я, країна). Запам'ятай пароль.

## Крок 2. Створи `keystore.properties`
У корені проєкту скопіюй `keystore.properties.example` у `keystore.properties` і заповни:

```
storeFile=C:/Users/<твоє_ім'я>/keys/anti-pdf-upload.jks
storePassword=<пароль сховища>
keyAlias=upload
keyPassword=<пароль ключа>
```

Шлях пиши зі скісними рисками `/`. Цей файл лише в тебе на комп'ютері.

## Крок 3. Збери підписаний пакет
```
./gradlew :app:bundleRelease
```
Готовий файл: `app/build/outputs/bundle/release/app-release.aab`. Саме його завантажуєш у Play Console.

## Що буде, якщо `keystore.properties` немає
Збірка НЕ падає: release збирається **непідписаним** (APK `app-release-unsigned.apk`). Такий файл не встановлюється на телефон і не годиться для Play. Це зроблено навмисно, щоб тести й CI працювали без ключа.

## Крок 4. Play App Signing
При створенні застосунку в Play Console залиши увімкненим **Play App Signing** (типово увімкнено для нових застосунків). Перший AAB, підписаний твоїм ключем завантаження, Google прийме, а розповсюджувати користувачам буде вже своїм ключем.

## Якщо ключ завантаження загублено
Play Console: Setup / App signing / Request upload key reset. Тому Play App Signing обов'язковий.

## Для локальної перевірки без ключа
Для димового запуску на власному телефоні можна підписати release-APK **debug-ключем** локально (лише на своєму комп'ютері). Такий APK у Play не завантажують і в git не кладуть.

## Версії
`versionCode` і `versionName` задано в `app/build.gradle.kts`. Для кожного нового завантаження в Play `versionCode` має бути більшим за попередній.
