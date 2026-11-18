# Privacy Policy for Anti PDF

> Джерело тексту публічної сторінки https://savoj13.github.io/anti-pdf-/privacy/ (файл `docs/privacy/index.html`, uk + en). Обидва файли мають змінюватися разом. Контакт: appvsdeveloper@gmail.com (публічна адреса, рішення Власника 2026-11-17).

**Last Updated:** 2026-11-18
**Application:** Anti PDF
**Package Name:** `com.savoj.volumpdf.anti`
**Developer Contact:** `appvsdeveloper@gmail.com`

---

## 1. Overview

Anti PDF ("we", "the app") is a PDF reader and editor. In short:
1. **Your documents stay on your device.** Reading, editing, signing, watermarking, compressing, merging, splitting, rotating and scanning are performed locally. We do not upload your documents to our servers and we do not run servers for your files.
2. **No account.** You do not create an account and do not give us your name, email or phone.
3. **The app shows ads** from Google AdMob. To show them, Google's advertising software collects some data about your device (see section 3).
4. **Two Google components collect data:** AdMob (ads, section 3) and ML Kit (document scanner, technical diagnostics, section 2). We ourselves collect no data.
5. **No analytics or crash-reporting SDK of our own** (no Firebase Analytics, Crashlytics, Facebook SDK or similar). The scanner's technical diagnostics are collected by Google ML Kit, not by us.
6. **No cloud backup.** The app disables Android automatic backup (`allowBackup=false`), so app data (recent files list) is not copied to Google Drive backups or device transfers.

---

## 2. Files and Storage

- Files are opened and saved only through Android's system file picker (Storage Access Framework). The app does not request broad storage permissions.
- The app keeps a local list of recently opened files in a local database on your device: file address (URI), name, size, page count, last read page, last opened time and favorite flag. It is never sent to a server. You can clear it in the app menu ("Clear history") or in Android Settings (Apps, Anti PDF, Storage, Clear data).
- The app also keeps on your device, in its own private storage: new documents you create (`documents`), scans (`scanned`), unsaved edits (`pending`, including any text and signatures you added, deleted automatically after 7 days) and temporary files in the cache. "Clear history" removes only the "Recent" list and does not delete these; they are removed by clearing the app data in Android Settings or by uninstalling the app.
- Appearance and reading settings (for example the theme) are stored locally on your device.
- To show a result, the app may create a temporary copy of the PDF in its own cache; it never leaves the device.
- Files are also opened when you send a PDF to the app from another app ("Open with").
- Document scanner: scanning uses the Google ML Kit Document Scanner, provided through Google Play services. The app receives the finished pages from the scanner and saves the scan as a PDF in its own private storage on your device (and adds it to the "Recent" list). You can save or share a copy anywhere you choose. We do not receive or store your scans on any server. The data that ML Kit sends to Google is listed in section 2; image content is not in that list.

### Data collected by Google ML Kit (scanner)
According to Google's official information, ML Kit may collect for diagnostics and usage analytics: device information (manufacturer, model, OS version and build, available ML hardware accelerators); app information (package name, version); a device identifier and per-installation identifiers; performance metrics (for example latency); API configuration (for example image format and resolution); input and output size; feature version; event type (for example initialization, model download, detection, resource release) and error codes. The scanner runs through Google Play services, so these rules apply to it as well. The data is encrypted in transit (HTTPS) and ML Kit does not transfer it to third parties. Your scans and documents are not sent by this. Learn more: [Google ML Kit data disclosure (Android)](https://developers.google.com/ml-kit/android-data-disclosure).

---

## 3. Advertising (Google AdMob) and Consent (Google UMP)

The app is free and shows ads from **Google AdMob** (Google Mobile Ads SDK). A banner is shown on the home and tools screens and, at most once every 5 minutes, a full-screen ad after you finish an action in the tools. There are no ads while you read a document or when a file is opened.

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
The app's own manifest requests only the `INTERNET` permission. Internet is used by the advertising software (ads and consent form); the scanner's technical diagnostics are sent by Google ML Kit (section 2). Reading and editing documents works offline. When offline, no ad is shown and no empty ad space remains.

---

## 5. Data Sharing and Security
- Data collected by Google AdMob and Google ML Kit is processed by Google under Google's policies. We do not sell your data.
- Data sent by the ad SDK is encrypted in transit (HTTPS).
- We do not share your documents with anyone.
- The app may write technical messages (for example an error type) to the Android system log; they stay on the device and are not sent to us.

---

## 6. Your Rights
- Manage ad personalization and the advertising ID in Android Settings.
- Change your consent in the app menu ("Privacy settings") when shown.
- Delete local app data: "Clear history" in the app removes the "Recent" list only; clearing data in Android Settings or uninstalling the app also removes new documents, scans, unsaved edits and temporary files kept by the app (PDF files you saved elsewhere are not deleted).
- We store no personal data on our servers, so there is nothing to delete on our side; requests about data processed by Google AdMob go to Google.
- For questions or requests about your data, contact `appvsdeveloper@gmail.com`.

---

## 7. Children
The app is intended for users aged 13 and over and is not directed at children under 13. We do not knowingly collect personal data from children under 13. In the European Economic Area (EEA) the age from which a person can give consent to data processing on their own differs by country (from 13 to 16). If you are below that age in your country, do not agree in the ads consent form without the permission of a parent or guardian; you can withdraw or change your choice in the menu "Privacy settings". Ad content is limited by age rating (no higher than PG).

---

## 8. Changes
We may update this policy. The "Last Updated" date shows the latest change.

---

## 9. Contact
`appvsdeveloper@gmail.com`
