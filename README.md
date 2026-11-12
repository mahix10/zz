# Cookies Tool FB — v2

## What's New in v2
- ✅ Google Sheet direct share link support (no Apps Script needed for read)
- ✅ Hotmail auto-load from Sheet Column D (LOAD D button)
- ✅ Kill switch — disable app remotely via Sheet Column F or in-app button
- ✅ Update popup — users with old installs see changelog on first launch

---

## Project Structure
```
fb_tool_v2/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/cookies/tool/fb/
│       │   └── MainActivity.java
│       └── res/
│           ├── values/strings.xml
│           ├── values/styles.xml
│           └── xml/network_security_config.xml
├── build.gradle
├── settings.gradle
├── SheetReceiver.gs
└── README.md
```

---

## Build Steps
1. Open Android Studio → "Open an existing project" → select this folder
2. Add launcher icon PNGs to res/mipmap-* folders (optional — name: ic_launcher.png)
3. Build → Generate Signed APK  OR  `./gradlew assembleDebug`

---

## Button Map

| Button           | Action                                                      |
|------------------|-------------------------------------------------------------|
| COOKIES          | Grab current FB cookies → clipboard                         |
| 2FA OTP          | Opens FB account center 2FA page                            |
| AUTO CRET        | Opens FB reg page, injects saved PW                         |
| NUMBER SET       | Dialog → inject phone number via JS                         |
| PW SET           | Set/view app password (shown as **), inject PW into page    |
| DESKTOP          | Toggle desktop UA + reload                                   |
| HOTMAIL          | Add/clear Hotmail accounts (email\|pw\|token\|clientId)     |
| AGENT SET        | Manage user-agent strings, select active                    |
| 2FA ON/OFF       | Toggle automatic 2FA handling via Hotmail                   |
| RELOAD           | Reload WebView                                              |
| ALL CLEAR        | Clear cookies/cache (password NOT cleared)                  |
| ⛔ DISABLE APP   | Kill switch — manually lock the app                         |
| ✅ ENABLE APP    | Re-enable after kill switch                                 |
| Sheet URL + SET  | Paste Google Sheet share URL or Apps Script URL → tap SET   |
| LOAD D           | Fetch Hotmail accounts from Column D of the linked sheet    |

---

## Sheet URL — Two Modes

### Mode 1: Direct Google Sheet Share Link (NEW)
```
https://docs.google.com/spreadsheets/d/SHEET_ID/edit#gid=0
```
Paste this directly. Use the LOAD D button to pull hotmails from Column D.
The app extracts SHEET_ID and builds a CSV export URL automatically.
Make sure the sheet is set to "Anyone with the link can view."

### Mode 2: Apps Script Web App URL (original behavior)
```
https://script.google.com/macros/s/XXXXXXXX/exec
```
Used for pushing data FROM the app TO the sheet (auto-push on login).

You can store BOTH — the app detects which one to use:
- share link → used for LOAD D
- script.google.com URL → used for pushToSheet (auto-push)

---

## Sheet Column Layout

| Col | Key         | Notes                                      |
|-----|-------------|--------------------------------------------|
| A   | UID         | Facebook user ID                           |
| B   | Password    | App password (from PW SET)                 |
| C   | Cookies     | Full Facebook cookies string               |
| D   | Hotmail     | email\|password\|token\|clientId[\|recovery] |
| E   | Hotmail 2   | Secondary hotmail or 2FA key               |
| F   | Kill Switch | Write "OFF" in F1 to remotely disable app  |

---

## Kill Switch

### Remote disable (via Sheet)
1. Open your Google Sheet
2. Click cell F1
3. Type: `OFF`
4. User taps LOAD D in app → app reads OFF → shows "App Disabled" screen on next launch

### Remote re-enable
1. Clear F1 (or type anything other than OFF)
2. User taps ✅ ENABLE APP button inside the app before restarting

### In-app disable
Tap **⛔ DISABLE APP** button → confirm → app locks on next launch.
Tap **✅ ENABLE APP** to unlock before restarting.

---

## Hotmail from Column D — LOAD D Flow
1. Put Sheet share link in the Sheet URL field → tap SET
2. In your sheet Column D, add hotmail entries (one per row):
   ```
   email@hotmail.com|password|access_token|client_id
   email@hotmail.com|password|access_token|client_id|recovery@gmail.com
   ```
3. In the app, tap **LOAD D**
4. App downloads the CSV, reads Column D, imports all accounts into the Hotmail list
5. Duplicates (same email) are skipped — won't add twice

---

## Update Popup System
When you release a new APK version:
1. Bump `APP_VERSION_CODE` in `MainActivity.java` (e.g., from 2 → 3)
2. Edit the `showUpdatePopup()` message to list what changed
3. Build and distribute the APK
4. Users who had the old version installed → see the popup on first launch of new version
5. After they tap OK, they won't see it again

---

## Hotmail Input Format
```
email@hotmail.com|password|access_token|client_id
email@hotmail.com|password|access_token|client_id|recovery@gmail.com
```
- token: paste a valid Graph API access_token (skips OAuth login)
- If token empty: uses email+password OAuth2 ROPC flow to get token

---

## 2FA Auto Flow (when 2FA ON)
1. Detects FB checkpoint/2fa page
2. Removes any confirmed numbers
3. Fetches OTP from latest Hotmail inbox email (from facebookmail.com)
4. Injects OTP into page, submits
5. On success: removes all added numbers, removes used Hotmail from list
6. On OTP fail: resends 2–3 times → waits 30 sec → tries next Hotmail

---

## Google Sheet Setup (Apps Script — for push mode)
1. Create a Google Sheet
2. Open Extensions → Apps Script
3. Paste SheetReceiver.gs content
4. Deploy → New deployment → Web App
   - Execute as: Me
   - Who has access: Anyone
5. Copy the web app URL → paste in app's Sheet URL field → tap SET
