/**
 * Google Apps Script — SheetReceiver v2
 * Deploy as Web App (Execute as: Me, Access: Anyone)
 * Paste the web app URL as the Sheet URL in the app.
 *
 * Sheet columns:
 *   A = UID
 *   B = Password (the one set in PW SET)
 *   C = Cookies
 *   D = Hotmail full access string (email|password|token|clientId|recovery)
 *       OR 2FA Key if 2FA enabled — depends on what the app sends
 *   E = Hotmail full access (secondary if D is 2FA key)
 *   F = Kill Switch — write "OFF" in F1 to disable the app remotely
 *                    anything else (or empty) keeps app enabled
 *
 * HOW KILL SWITCH WORKS:
 *   1. Set cell F1 = "OFF" in your sheet
 *   2. Next time the app taps "LOAD D" button, it reads the CSV
 *   3. App sees "OFF" in column F row 1 → triggers kill switch
 *   4. App shows "App Disabled" screen on next launch
 *   5. To re-enable: set F1 to anything else ("ON" or leave blank)
 *      AND tap "✅ ENABLE APP" button inside the app before it restarts
 *
 * HOW HOTMAIL LOAD WORKS:
 *   1. Put hotmail accounts in column D (one per row):
 *      email@hotmail.com|password|access_token|client_id
 *      email@hotmail.com|password|access_token|client_id|recovery@gmail.com
 *   2. Tap "LOAD D" button in app → auto-imported into Hotmail list
 */

function doGet(e) {
  try {
    var params = e.parameter;
    var uid     = params.uid     || "";
    var pw      = params.pw      || "";
    var cookies = params.cookies || "";
    var twofa   = params.twofa  || "";
    var hotmail = params.hotmail || "";

    if (!uid && !cookies) {
      return ContentService.createTextOutput("skip").setMimeType(ContentService.MimeType.TEXT);
    }

    var ss    = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getActiveSheet();

    // Find next empty row (skip row 1 — it may contain kill switch in F1)
    var lastRow  = sheet.getLastRow();
    var nextRow  = Math.max(lastRow + 1, 2);   // always start writing from row 2+

    sheet.getRange(nextRow, 1).setValue(uid);
    sheet.getRange(nextRow, 2).setValue(pw);
    sheet.getRange(nextRow, 3).setValue(cookies);
    sheet.getRange(nextRow, 4).setValue(twofa);    // 2FA key (if enabled), else empty
    sheet.getRange(nextRow, 5).setValue(hotmail);  // hotmail string

    return ContentService.createTextOutput("ok").setMimeType(ContentService.MimeType.TEXT);
  } catch(err) {
    return ContentService.createTextOutput("err: " + err.toString())
      .setMimeType(ContentService.MimeType.TEXT);
  }
}

/**
 * Optional: Run this manually in Apps Script editor to test your sheet setup.
 */
function testSetup() {
  var ss    = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getActiveSheet();
  var f1    = sheet.getRange(1, 6).getValue();
  Logger.log("Kill switch (F1): " + (f1 === "OFF" ? "DISABLED" : "ENABLED (" + f1 + ")"));
  var lastRow = sheet.getLastRow();
  Logger.log("Total data rows: " + (lastRow - 1));
}
