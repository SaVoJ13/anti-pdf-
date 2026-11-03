# Google Play Data Safety Form Responses

**Application:** Volum PDF: Reader & Tools  
**Package:** `com.savoj.volumpdf`  
**Target Release:** Android 9.0 (API 28) – Android 16 (API 36)  
**SDKs with Network Access:** Google AdMob SDK (`com.google.android.gms:play-services-ads:23.6.0`), Google User Messaging Platform SDK (`com.google.android.ump:user-messaging-platform:3.1.0`)  
**Primary References:**  
- Google Play Console Help: *Provide information for Google Play's Data safety section*  
- Google AdMob Documentation: *Google Mobile Ads SDK Data Disclosure* (`https://developers.google.com/admob/android/data-disclosure`)

---

## 1. Overview Questions

| Question | Answer | Details / Justification |
|---|---|---|
| **Does your app collect or share any of the required user data types?** | **Yes** | Data collection is strictly performed by third-party advertising SDK (Google AdMob). The app itself does not collect any first-party data. |
| **Is all of the user data collected by your app encrypted in transit?** | **Yes** | All network traffic between Google Mobile Ads SDK and Google servers is transmitted over HTTPS/TLS encrypted connections. |
| **Do you provide a way for users to request that their data be deleted?** | **Yes** | Users can reset or delete their Google Advertising ID directly in Android Settings, clear local app data, or contact the developer (`privacy@volumpdf.com`). |

---

## 2. Data Types Breakdown

### 2.1 Files and Documents (PDFs, Images)
- **Status:** **NOT COLLECTED (No)**
- **Explanation:** Files and documents opened by the user via Storage Access Framework (SAF) are processed **100% locally on the device**. No files, text, page contents, or images are ever uploaded, collected, or shared.

### 2.2 Location
- **Data Type:** **Approximate Location** (Network-derived coarse location, e.g., country/city level based on IP address)
  - **Collected:** Yes (by Google AdMob)
  - **Shared:** Yes (shared with Google AdMob / advertising partners)
  - **Processed ephemerally:** No
  - **Required or Optional:** **Optional** (Subject to UMP consent in EEA/UK; non-personalized or disabled if consent revoked)
  - **Purposes:**
    - *Advertising or marketing*
    - *Fraud prevention, security, and compliance*

### 2.3 Device or Other IDs
- **Data Type:** **Device or other IDs** (Google Advertising ID / GAID, Android advertising identifiers)
  - **Collected:** Yes (by Google AdMob)
  - **Shared:** Yes (with Google AdMob)
  - **Processed ephemerally:** No
  - **Required or Optional:** **Optional** (Governed by UMP consent dialog; users can also reset/delete advertising ID in device settings)
  - **Purposes:**
    - *Advertising or marketing* (ad personalization, frequency capping)
    - *Fraud prevention, security, and compliance* (invalid traffic detection)
    - *Analytics* (ad delivery performance)

### 2.4 App Activity
- **Data Type:** **App interactions** (Interactions with ads: impressions, clicks, video views)
  - **Collected:** Yes (by Google AdMob)
  - **Shared:** Yes
  - **Processed ephemerally:** No
  - **Required or Optional:** **Optional**
  - **Purposes:**
    - *Advertising or marketing*
    - *Analytics*
    - *Fraud prevention, security, and compliance*

### 2.5 App Info and Performance
- **Data Type:** **Crash logs** (Ad-related crashes and diagnostic traces)
  - **Collected:** Yes (by Google AdMob)
  - **Shared:** Yes
  - **Processed ephemerally:** No
  - **Required or Optional:** **Optional**
  - **Purposes:**
    - *Analytics*
    - *Fraud prevention, security, and compliance*
- **Data Type:** **Diagnostics** (Ad load latency, response rates)
  - **Collected:** Yes (by Google AdMob)
  - **Shared:** Yes
  - **Purposes:**
    - *Analytics*
    - *Advertising or marketing*

### 2.6 All Other Categories
- **Personal info (Name, Email, Phone, Address, User IDs):** **NOT COLLECTED**
- **Financial Info:** **NOT COLLECTED**
- **Health and Fitness:** **NOT COLLECTED**
- **Messages / SMS / Emails:** **NOT COLLECTED**
- **Photos and Videos:** **NOT COLLECTED**
- **Audio Files:** **NOT COLLECTED**
- **Calendar:** **NOT COLLECTED**
- **Contacts:** **NOT COLLECTED**
- **Web Browsing History:** **NOT COLLECTED**

---

## 3. Privacy Policy URL for Play Console
- URL to be entered in Google Play Console Policy Section:  
  `https://volumpdf.com/privacy-policy` *(or hosted raw GitHub/public URL prior to domain deployment)*
