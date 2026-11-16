# Privacy Policy for Anti PDF

> ЧЕРНЕТКА для Власника. Не опублікована. Перед публікацією Власник підставляє контактну адресу (замість `[CONTACT EMAIL]`) і розміщує текст за публічною URL (її вписують у Play Console та в `BuildConfig.PRIVACY_POLICY_URL`). Адреси `volumpdf.com` / `privacy@volumpdf.com`: володіння НЕ ПІДТВЕРДЖЕНО, тому не використовуються.

**Last Updated:** November 16, 2026
**Application:** Anti PDF
**Package Name:** `com.savoj.volumpdf.anti`
**Developer Contact:** `[CONTACT EMAIL]`

---

## 1. Overview

Anti PDF ("we", "the app") is a PDF reader and editor. In short:
1. **Your documents stay on your device.** Reading, editing, signing, watermarking, compressing, merging, splitting, rotating and scanning are performed locally. We do not upload your documents to our servers and we do not run servers for your files.
2. **No account.** You do not create an account and do not give us your name, email or phone.
3. **The app shows ads** from Google AdMob. To show them, Google's advertising software collects some data about your device (see section 3). This is the only data collection in the app.
4. **No analytics or crash-reporting SDK of our own** (no Firebase Analytics, Crashlytics, Facebook SDK or similar).
5. **No cloud backup.** The app disables Android automatic backup (`allowBackup=false`), so app data (recent files list) is not copied to Google Drive backups or device transfers.

---

## 2. Files and Storage

- Files are opened and saved only through Android's system file picker (Storage Access Framework). The app does not request broad storage permissions.
- The app keeps a local list of recently opened files in a local database on your device: file name, size, page count, last read page, and favorite flag. It is never sent to a server. You can clear it in the app menu ("Clear history") or in Android Settings (Apps, Anti PDF, Storage, Clear data).
- Document scanner: scanning uses the Google ML Kit Document Scanner, provided through Google Play services. Per Google, scanning and image processing run on the device; the scanned pages are given to the app, and the app saves them where you choose. We do not receive or store your scans on any server.

---

## 3. Advertising (Google AdMob) and Consent (Google UMP)

The app is free and shows ads from **Google AdMob** (Google Mobile Ads SDK). There are no ads while you read a document or when a file is opened.

### 3.1 Data processed by Google AdMob
Depending on your region and consent, Google may collect and process:
- **Device identifiers**, including the advertising ID (when you have not reset or deleted it), and device and OS information.
- **Approximate location** derived from the IP address.
- **Ad interaction data** (impressions, clicks) and **diagnostics** (ad load speed, crash and performance data of the ad SDK).

This data is used for showing ads, limiting how often ads are shown, fraud prevention, security and measuring ad performance. We do not receive your personal data from Google.
Learn more: [Google Privacy Policy](https://policies.google.com/privacy) and [How Google uses information from sites or apps that use its services](https://policies.google.com/technologies/partner-sites).

### 3.2 Consent (GDPR / ePrivacy)
- In the European Economic Area, the UK and Switzerland the app uses Google's **User Messaging Platform (UMP)** to ask for your consent before ads that use personal data are requested.
- If consent is required and not given, the app does not request ads.
- You can review or change your choice at any time in the app menu: **"Privacy settings"**. This item is shown when Google requires it for your region.

### 3.3 Advertising ID
You can reset or delete the advertising ID in Android Settings (Google, Ads). The app's manifest includes the advertising ID permission through the ads SDK, so it is declared in the Play Console "Advertising ID" declaration.

---

## 4. Internet Use
Internet is used only by the advertising software (ads and consent form). Reading and editing documents works offline. When offline, no ad is shown and no empty ad space remains.

---

## 5. Data Sharing and Security
- Data collected by Google AdMob is processed by Google under Google's policies. We do not sell your data.
- Data sent by the ad SDK is encrypted in transit (HTTPS).
- We do not share your documents with anyone.

---

## 6. Your Rights
- Manage ad personalization and the advertising ID in Android Settings.
- Change your consent in the app menu ("Privacy settings") when shown.
- Delete local app data by clearing storage in Android Settings.
- For questions or requests about your data, contact `[CONTACT EMAIL]`.

---

## 7. Children
The app is intended for general audiences and is not directed at children under 13 (under 16 in the EEA). We do not knowingly collect personal data from children.

---

## 8. Changes
We may update this policy. The "Last Updated" date shows the latest change.

---

## 9. Contact
`[CONTACT EMAIL]`
