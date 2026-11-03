# Privacy Policy for Volum PDF: Reader & Tools

**Last Updated:** November 3, 2026  
**Application:** Volum PDF: Reader & Tools  
**Package Name:** `com.savoj.volumpdf`  
**Developer Contact:** `privacy@volumpdf.com` / `support@volumpdf.com`  

---

## 1. Overview and Core Privacy Principles

Volum PDF ("we", "our", or "the app") is designed with a strict **Privacy-First** philosophy. Our fundamental commitment:
1. **Your documents are yours alone.** All PDF processing (reading, rendering, merging, splitting, rotating, deleting pages, and converting images to PDF) is performed **100% locally on your device**.
2. **Zero server uploads.** Files selected by the user never leave the local device storage. We do not operate external processing servers or cloud storage for your files.
3. **No account or registration.** You do not need to create an account, log in, or provide any personal details (name, email, phone number) to use the application.
4. **No first-party tracking or behavioral analytics.** We do not integrate user tracking, analytics SDKs (such as Firebase Analytics, Facebook SDK, or third-party telemetries), or background data harvesting.

---

## 2. Storage and File Access (Storage Access Framework)

- **Storage Access Framework (SAF):** Volum PDF accesses files exclusively through Android's secure Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_OPEN_MULTIPLE_DOCUMENTS`, `ACTION_CREATE_DOCUMENT`).
- **No Broad Storage Permissions:** The app **does not request** broad filesystem access permissions such as `MANAGE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE`. Access is strictly limited to the specific documents and export directories explicitly chosen by the user.
- **Local Database:** The app stores metadata for recently opened files (file name, file size, page count, last read page, and favorite status) strictly in a local SQLite database (Room) on your device. This data is never synchronized with any remote server.

---

## 3. Advertising and Google UMP Consent (GDPR / EEA)

To support ongoing development and offer core PDF reading and editing tools for free, Volum PDF displays non-intrusive advertisements provided by **Google AdMob** (`com.google.android.gms:play-services-ads`).

### 3.1 Strict Ad Placement Standards
In accordance with our Master Specification and Google Play Better Ads policies:
- **No ads during document viewing or reading.**
- **No ads on opening a file.**
- **No disruptive App Open ads.**
- **No ads covering navigation or functional controls.**
- Banners are displayed only at the bottom of document lists or tool screens, and collapse automatically (View.GONE) when offline without blank placeholders.
- Interstitial ads are limited strictly to completed tool actions with a minimum 5-minute cooldown between displays.

### 3.2 User Consent and Google UMP (GDPR / ePrivacy Directive)
- For users located in the European Economic Area (EEA), the UK, and Switzerland, the application integrates the official **Google User Messaging Platform (UMP) SDK** (`com.google.android.ump:user-messaging-platform`).
- When launching the application in applicable regions, a standardized consent form is presented to the user.
- Personalized advertising and tracking identifiers are utilized **only after explicit, informed user consent**.
- If consent is denied or not required, non-personalized advertising is served or ad loading is restricted in compliance with Google AdMob policies.

### 3.3 Data Processed by Google AdMob
Depending on user consent, Google AdMob may collect and process:
- **Device Identifiers:** Google Advertising ID (GAID), device specifications (model, OS version).
- **Approximate Location:** Derived from IP address for regional ad serving.
- **Ad Interaction Data:** Views, clicks, diagnostics, and anti-fraud telemetry.

For more details on how Google processes advertising data:
- [Google Privacy Policy](https://policies.google.com/privacy)
- [How Google uses information from sites or apps that use our services](https://policies.google.com/technologies/partner-sites)
- [Google AdMob Data Disclosure Guidelines](https://developers.google.com/admob/android/data-disclosure)

---

## 4. Offline Functionality

Volum PDF is fully functional in offline mode. Reading, searching, page extraction, merging, rotating, and converting operate entirely without an active internet connection. When offline, advertising containers remain hidden and no network requests are dispatched.

---

## 5. User Rights and Data Control

- **Resetting or Opting Out of Advertising Identifiers:** You can manage or delete your Android Advertising ID at any time in your device settings:  
  *Android Settings → Google → Ads → Reset advertising ID / Delete advertising ID.*
- **Clearing Local History:** You can clear all recent document history directly within the app's settings or via system settings:  
  *Android Settings → Apps → Volum PDF → Storage → Clear Data.*
- **Withdrawing Consent:** You can review or change your advertising consent preferences through the app's settings menu at any time.

---

## 6. Children's Privacy

Volum PDF is intended for general audiences and professionals (18+). It is not directed at children under the age of 13 (or under 16 in the EEA), and we do not knowingly collect personal information from children.

---

## 7. Changes to this Privacy Policy

We may update this Privacy Policy periodically to reflect updates in legal requirements or Google Play policies. Any changes will be posted on this page with an updated "Last Updated" date.

---

## 8. Contact Us

If you have any questions, suggestions, or concerns regarding your privacy while using Volum PDF:
- **Email:** `privacy@volumpdf.com`
