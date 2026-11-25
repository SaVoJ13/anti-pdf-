# Збірка підписаного AAB для RC3 (версія 1.0.2): інструкція для Власника

> Простими словами, Windows і PowerShell. **Один крок за раз:** зроби крок, надішли координатору відповідь з рядка «Що надіслати», і лише тоді переходь далі. Пароль і файл ключа нікому не надсилай і в чат не вставляй. Агенти ключ не бачили й не бачитимуть. Спосіб підпису: `docs/store/RELEASE_SIGNING.md`; зразок: `docs/release/RC2_BUILD_GUIDE.md`.

## Що буде в результаті
Файл `app\build\outputs\bundle\release\app-release.aab`, версія **1.0.2** (код версії **3**), підписаний твоїм ключем завантаження (upload key). Його координатор завантажить у закритий трек «Alpha» лише з твого дозволу.

## Реклама: тестові чи справжні ID
- Політика збірки: **без файлу `admob.properties`** release збирається з **тестовими** ID Google (під час збірки друкується велике «УВАГА (AdMob)» і рядок `AdMob: маніфест release OK (тестовий Application ID)`). З тестовими ID публікувати для всіх у Production **не можна**.
- **Справжній реліз** збираєш **на своєму ПК**, де лежить твій `admob.properties` з бойовими ID (опис: `docs/release/ADMOB_SETUP.md`). Файл не в git і не в чат. Якщо його немає, у збірці будуть тестові ID (для закритого тесту Alpha це прийнято).
- Прапорець `-Pproduction=true` без координатора **не використовуй**.

---

## Крок 1. Оновити локальну папку проєкту до `main`
Лише **після** слів координатора, що `release/rc3` злито в `main` (після незалежного QA).

```powershell
cd C:\Users\cocro\AndroidStudioProjects\pdf-reader-android
git fetch
git checkout main
git pull
git log --oneline -3
git status --short
```
**Що надіслати:** три рядки з `git log --oneline -3` і чи `git status --short` щось вивів (порожньо — добре). Перший рядок має бути комітом злиття `release/rc3`, його номер скаже координатор. Конфлікт чи «незбережені зміни» — **зупинись**, надішли текст, нічого не видаляй.

## Крок 2. Зібрати підписаний release AAB
2.1. Файл ключових налаштувань на місці (лише True/False, вміст не показується):
```powershell
Test-Path .\keystore.properties
```
Має бути `True`. Якщо `False` — **не збирай** (вийде непідписаний файл), напиши «False». Якщо `storeFile` у файлі вказує на неіснуючий шлях, збірка НЕ впаде, а вийде непідписаною: див. крок 3.3.

2.2. Java і JDK 21:
```powershell
$env:JAVA_HOME = "C:\Users\cocro\.jdks\jbr-21.0.11"
& "$env:JAVA_HOME\bin\java.exe" -version
```
У першому рядку має бути `21...`.

2.3. Збірка:
```powershell
.\gradlew.bat :app:bundleRelease
```
Триває кілька хвилин, пароль вводити не треба. Успіх: `BUILD SUCCESSFUL`. Якщо є `admob.properties`, рядок `AdMob: ...` буде про справжній Application ID; якщо ні, про тестовий.

**Що надіслати:** «BUILD SUCCESSFUL» (або останні 15 рядків при помилці; рядок, схожий на пароль, не надсилай).

## Крок 3. Перевірити версію й підпис (без розкриття ключа)
3.1. Розмір, час і контрольна сума:
```powershell
$aab = ".\app\build\outputs\bundle\release\app-release.aab"
Get-Item $aab | Select-Object Name, Length, LastWriteTime
(Get-FileHash $aab -Algorithm SHA256).Hash
```

3.2. Версія (очікуєш `versionCode="3"` і `versionName="1.0.2"`):
```powershell
Select-String -Path ".\app\build\intermediates\merged_manifests\release\processReleaseManifest\AndroidManifest.xml" -Pattern 'versionCode|versionName'
```

3.3. Файл підписаний:
```powershell
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify $aab
```
`jar verified.` — підписаний (попередження про самопідписаний сертифікат нормальне). `no manifest.` — непідписаний: **не завантажувати**, повідомити.

3.4. Відбиток сертифіката (відкритий, не секретний):
```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -printcert -jarfile $aab | Select-String "SHA256|Owner"
```
`Not a signed jar file` — непідписаний (те саме, що 3.3). Порівняй `SHA256:` з Play Console: **Налаштування застосунку → Підпис застосунку (App signing) → «Сертифікат ключа завантаження» (Upload key certificate) → SHA-256**. Мають збігатися повністю. Різні — **не завантажуй**.

**Що надіслати:** (а) вивід 3.1 і 3.2; (б) `jar verified.` або `no manifest.`; (в) рядок `SHA256:` з 3.4 і «збігається» / «не збігається» з Play Console.

## Крок 4. Повідомити координатора, що AAB готовий
Одним повідомленням: повний шлях до `app-release.aab`; розмір у байтах (з 3.1); перші 16 символів SHA-256 файла. **Завантаження в Play Console робить координатор лише з твого прямого дозволу**; сам нічого не завантажуй (кроки: `docs/release/RC3_CHECKLIST.md`).

## Якщо щось пішло не так
- `BUILD FAILED` — надішли останні 15 рядків; нічого не видаляй.
- Непідписаний файл — `keystore.properties` не в тій папці або `storeFile` на неіснуючий шлях (2.1).
- Відбитки різні — не завантажувати.
- Версія в 3.2 не `3` / `1.0.2` — ти зібрав не з `main` після злиття; повтори крок 1.
