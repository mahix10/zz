package com.cookies.tool.fb;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // ── Prefs Keys ──────────────────────────────────────────────────────────
    private static final String PREF              = "fb_prefs";
    private static final String KEY_PW            = "saved_password";
    private static final String KEY_NUM           = "saved_number";
    private static final String KEY_FIRSTNAME     = "saved_firstname";
    private static final String KEY_LASTNAME      = "saved_lastname";
    private static final String KEY_SHEET         = "sheet_url";
    private static final String KEY_AGENTS        = "custom_user_agents";
    private static final String KEY_DEVICE_NAMES  = "custom_device_names";
    private static final String KEY_SEL_AGENT     = "selected_agent";
    private static final String KEY_DESKTOP       = "desktop_mode";
    private static final String KEY_2FA_AUTO      = "auto_2fa";
    private static final String KEY_NUM_REMOVE    = "num_remove";
    private static final String KEY_HOTMAIL_AUTO  = "hotmail_auto";
    private static final String KEY_HOTMAIL_LIST  = "hotmail_list";
    private static final String KEY_APP_ON        = "app_enabled";

    // ── UI ───────────────────────────────────────────────────────────────────
    private WebView webView;
    private SharedPreferences prefs;
    private Handler mainHandler;
    private ExecutorService executor;

    // button refs for toggle text update
    private Button btn2faAuto, btnNumRemove;

    // ── State ────────────────────────────────────────────────────────────────
    private int hotmailIndex = 0;
    private int smsResendCount = 0;
    private static final int MAX_RESEND = 3;

    // ── Colors (matching original app) ───────────────────────────────────────
    private static final String C_BLUE    = "#1877F2";
    private static final String C_GREEN   = "#25D366";
    private static final String C_PURPLE  = "#8B5CF6";
    private static final String C_TEAL    = "#06B6D4";
    private static final String C_YELLOW  = "#F59E0B";
    private static final String C_PINK    = "#EC4899";
    private static final String C_VIOLET  = "#7C3AED";
    private static final String C_ORANGE  = "#F97316";
    private static final String C_RED     = "#EF4444";
    private static final String C_DARK    = "#1C1C1E";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREF, Context.MODE_PRIVATE);
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newCachedThreadPool();

        // Kill switch check
        if (!prefs.getBoolean(KEY_APP_ON, true)) {
            showDisabledScreen();
            return;
        }

        buildUI();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DISABLED SCREEN
    // ══════════════════════════════════════════════════════════════════════════
    private void showDisabledScreen() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER);
        ll.setBackgroundColor(Color.BLACK);
        TextView tv = new TextView(this);
        tv.setText("App Disabled");
        tv.setTextColor(Color.RED);
        tv.setTextSize(28);
        tv.setGravity(Gravity.CENTER);
        ll.addView(tv);
        setContentView(ll);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BUILD UI  — matches original layout exactly
    // ══════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(C_DARK));

        // ── ROW 1: UID | COOKIES ──────────────────────────────────────────
        LinearLayout r1 = row();
        r1.addView(mkBtn("🔑  UID",     C_BLUE,   v -> grabUID()));
        r1.addView(mkBtn("📋  COOKIES", C_GREEN,  v -> grabCookies()));
        root.addView(r1);

        // ── ROW 2: 2FA | 2FA OTP ─────────────────────────────────────────
        LinearLayout r2 = row();
        r2.addView(mkBtn("🛡  2FA",     C_PURPLE, v -> open2faPage()));
        r2.addView(mkBtn("🔑  2FA OTP", C_TEAL,   v -> showOtpDialog()));
        root.addView(r2);

        // ── WEBVIEW ───────────────────────────────────────────────────────
        webView = new WebView(this);
        LinearLayout.LayoutParams wvp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(wvp);
        setupWebView();
        root.addView(webView);

        // ── ROW 3: AUTO CRET | NUMBER SET | PW SET ───────────────────────
        LinearLayout r3 = row();
        r3.addView(mkBtn("⚡  AUTO CRET",  C_YELLOW, v -> openAutoCret()));
        r3.addView(mkBtn("📞  NUMBER SET", C_PINK,   v -> showNumberDialog()));
        r3.addView(mkBtn("🔒  PW SET",     C_VIOLET, v -> showPwDialog()));
        root.addView(r3);

        // ── ROW 4: DESKTOP | AGENT SET ───────────────────────────────────
        LinearLayout r4 = row();
        r4.addView(mkBtn("🖥  DESKTOP",   "#6366F1", v -> toggleDesktop()));
        r4.addView(mkBtn("📱  AGENT SET", C_TEAL,    v -> showAgentDialog()));
        root.addView(r4);

        // ── ROW 5: AUTO 2FA ON/OFF | NUM REMOVE ON/OFF ───────────────────
        boolean twoFaOn  = prefs.getBoolean(KEY_2FA_AUTO, false);
        boolean numRemOn = prefs.getBoolean(KEY_NUM_REMOVE, false);
        btn2faAuto   = mkBtn("AUTO 2FA: " + (twoFaOn  ? "ON" : "OFF"),
                twoFaOn  ? "#16A34A" : "#6B7280", v -> toggle2faAuto());
        btnNumRemove = mkBtn("NUM REM: " + (numRemOn ? "ON" : "OFF"),
                numRemOn ? "#16A34A" : "#6B7280", v -> toggleNumRemove());
        LinearLayout r5 = row();
        r5.addView(btn2faAuto);
        r5.addView(btnNumRemove);
        root.addView(r5);

        // ── ROW 6: HOTMAIL AUTO | SHEET URL ──────────────────────────────
        boolean hmAuto = prefs.getBoolean(KEY_HOTMAIL_AUTO, false);
        Button btnHmAuto = mkBtn("HOTMAIL: " + (hmAuto ? "ON" : "OFF"),
                hmAuto ? "#16A34A" : "#6B7280",
                v -> toggleHotmailAuto((Button) v));
        LinearLayout r6 = row();
        r6.addView(btnHmAuto);
        r6.addView(mkBtn("⚙  SHEET SET", "#0EA5E9", v -> showSheetDialog()));
        root.addView(r6);

        // ── ROW 7: RELOAD | ALL CLEAR ────────────────────────────────────
        LinearLayout r7 = row();
        r7.addView(mkBtn("🔄  RELOAD",    C_ORANGE, v -> webView.reload()));
        r7.addView(mkBtn("🗑  ALL CLEAR", C_RED,    v -> showClearDialog()));
        root.addView(r7);

        setContentView(root);
        webView.loadUrl("https://limited.facebook.com");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WEBVIEW
    // ══════════════════════════════════════════════════════════════════════════
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        applyAgent(ws);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                onPage(url);
            }
        });
    }

    private void applyAgent(WebSettings ws) {
        String sel = prefs.getString(KEY_SEL_AGENT, "");
        if (!sel.isEmpty()) {
            ws.setUserAgentString(sel);
        } else if (prefs.getBoolean(KEY_DESKTOP, false)) {
            ws.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);
        }
    }

    private void onPage(String url) {
        // Auto-capture cookies
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null && !cookies.isEmpty()) {
            prefs.edit().putString("last_cookies", cookies).apply();
        }

        // Auto 2FA flow
        if (prefs.getBoolean(KEY_2FA_AUTO, false)) {
            if (url.contains("two_factor") || url.contains("checkpoint") || url.contains("2fa")) {
                smsResendCount = 0;
                handle2faFlow();
            }
            // After login success — push cookies to sheet col B, 2FA key to col C
            if (url.contains("facebook.com") && !url.contains("login") &&
                    !url.contains("checkpoint") && !url.contains("two_factor")) {
                pushToSheet(cookies);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UID + COOKIES
    // ══════════════════════════════════════════════════════════════════════════
    private void grabUID() {
        webView.evaluateJavascript(
            "(function(){var m=document.cookie.match(/c_user=(\\d+)/);return m?m[1]:'';})()",
            uid -> {
                if (uid != null && !uid.equals("\"\"")) {
                    uid = uid.replace("\"", "").trim();
                    copyClip("UID", uid);
                    toast("UID copied: " + uid);
                } else {
                    toast("UID not found — log in first");
                }
            });
    }

    private void grabCookies() {
        String url = webView.getUrl();
        if (url == null) url = "https://facebook.com";
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null && !cookies.isEmpty()) {
            copyClip("Cookies", cookies);
            toast("Cookies copied!");
        } else {
            toast("No cookies yet");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  2FA + OTP DIALOG  (with custom name input option)
    // ══════════════════════════════════════════════════════════════════════════
    private void open2faPage() {
        webView.loadUrl("https://accountscenter.facebook.com/password_and_security/two_factor");
    }

    private void showOtpDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("2FA OTP");

        LinearLayout ll = vll(16);

        // Custom name option
        TextView tvHint = label("Optional: Custom name for OTP entry");
        ll.addView(tvHint);
        EditText etFirst = styledEdit("First Name (optional)");
        EditText etLast  = styledEdit("Last Name (optional)");
        ll.addView(etFirst);
        ll.addView(etLast);

        // 2FA key input
        ll.addView(label("2FA Key / TOTP Secret:"));
        EditText etKey = styledEdit("Paste 2FA key here");
        ll.addView(etKey);

        // Show saved key
        String sheetUrl = prefs.getString(KEY_SHEET, "").trim();
        if (!sheetUrl.isEmpty()) {
            TextView tvSheet = label("Will also save to Sheet Column C");
            tvSheet.setTextColor(Color.parseColor("#06B6D4"));
            ll.addView(tvSheet);
        }

        b.setView(wrap(ll));
        b.setPositiveButton("SAVE & USE", (d, w) -> {
            String first = etFirst.getText().toString().trim();
            String last  = etLast.getText().toString().trim();
            String key   = etKey.getText().toString().trim();

            if (!first.isEmpty()) prefs.edit().putString(KEY_FIRSTNAME, first).apply();
            if (!last.isEmpty())  prefs.edit().putString(KEY_LASTNAME,  last).apply();

            if (!key.isEmpty()) {
                prefs.edit().putString("twofa_key", key).apply();
                // Push to sheet column C
                if (!sheetUrl.isEmpty()) pushColC(sheetUrl, key);
                toast("2FA key saved");
            }
        });
        b.setNegativeButton("CANCEL", null);
        b.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  AUTO CRET
    // ══════════════════════════════════════════════════════════════════════════
    private void openAutoCret() {
        webView.loadUrl("https://m.facebook.com/reg");
        mainHandler.postDelayed(this::injectReg, 3000);
    }

    private void injectReg() {
        String pw    = prefs.getString(KEY_PW,  "");
        String num   = prefs.getString(KEY_NUM, "");
        String first = prefs.getString(KEY_FIRSTNAME, "");
        String last  = prefs.getString(KEY_LASTNAME,  "");

        String js =
            "(function(){" +
            "  function fill(sel,val){" +
            "    var el=document.querySelector(sel);" +
            "    if(!el)return;" +
            "    var ns=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;" +
            "    ns.call(el,val);" +
            "    el.dispatchEvent(new Event('input',{bubbles:true}));" +
            "    el.dispatchEvent(new Event('change',{bubbles:true}));" +
            "  }" +
            (first.isEmpty() ? "" : "  fill('input[name=\"firstname\"]','" + esc(first) + "');") +
            (first.isEmpty() ? "" : "  fill('input[placeholder*=\"First\"]','" + esc(first) + "');") +
            (last.isEmpty()  ? "" : "  fill('input[name=\"lastname\"]','" + esc(last) + "');") +
            (last.isEmpty()  ? "" : "  fill('input[placeholder*=\"Last\"]','" + esc(last) + "');") +
            "  fill('input[name=\"reg_email\"]','" + esc(num) + "');" +
            "  fill('input[name=\"reg_email__\"]','" + esc(num) + "');" +
            "  fill('input[name=\"reg_email_phone__\"]','" + esc(num) + "');" +
            "  fill('input[type=\"tel\"]','" + esc(num) + "');" +
            "  fill('input[name=\"reg_passwd\"]','" + esc(pw) + "');" +
            "  fill('input[name=\"reg_passwd__\"]','" + esc(pw) + "');" +
            "  fill('input[type=\"password\"]','" + esc(pw) + "');" +
            "})()";
        webView.evaluateJavascript(js, null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NUMBER SET
    // ══════════════════════════════════════════════════════════════════════════
    private void showNumberDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("NUMBER SET");
        LinearLayout ll = vll(16);
        EditText et = styledEdit("Phone number (+880...)");
        et.setInputType(InputType.TYPE_CLASS_PHONE);
        et.setText(prefs.getString(KEY_NUM, ""));
        ll.addView(et);
        b.setView(wrap(ll));
        b.setPositiveButton("SET", (d, w) -> {
            String n = et.getText().toString().trim();
            prefs.edit().putString(KEY_NUM, n).apply();
            injectNumber(n);
            toast("Number set");
        });
        b.setNegativeButton("CANCEL", null);
        b.show();
    }

    private void injectNumber(String num) {
        String js =
            "(function(){" +
            "  function fill(sel,val){" +
            "    var el=document.querySelector(sel);if(!el)return;" +
            "    var ns=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;" +
            "    ns.call(el,val);el.dispatchEvent(new Event('input',{bubbles:true}));}" +
            "  fill('input[type=\"tel\"]','" + esc(num) + "');" +
            "  fill('input[name=\"reg_email_phone__\"]','" + esc(num) + "');" +
            "  fill('input[placeholder*=\"Mobile\"]','" + esc(num) + "');" +
            "  fill('input[placeholder*=\"Phone\"]','" + esc(num) + "');" +
            "})()";
        webView.evaluateJavascript(js, null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PW SET  — visible toggle, password NOT cleared by ALL CLEAR
    // ══════════════════════════════════════════════════════════════════════════
    private void showPwDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("PW SET");
        LinearLayout ll = vll(16);

        EditText etPw = styledEdit("New password");
        // Start as password type
        etPw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPw.setText(prefs.getString(KEY_PW, ""));

        // Visible toggle checkbox
        CheckBox cbShow = new CheckBox(this);
        cbShow.setText("Show password");
        cbShow.setTextColor(Color.WHITE);
        cbShow.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                etPw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                etPw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            etPw.setSelection(etPw.getText().length());
        });

        ll.addView(label("Current: " + maskPw(prefs.getString(KEY_PW, ""))));
        ll.addView(etPw);
        ll.addView(cbShow);
        b.setView(wrap(ll));

        b.setPositiveButton("SET", (d, w) -> {
            String pw = etPw.getText().toString();
            prefs.edit().putString(KEY_PW, pw).apply();
            toast("Password saved");
        });
        b.setNeutralButton("INJECT", (d, w) -> {
            injectPw(prefs.getString(KEY_PW, ""));
        });
        b.setNegativeButton("CANCEL", null);
        b.show();
    }

    private String maskPw(String pw) {
        if (pw == null || pw.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pw.length(); i++) sb.append('•');
        return sb.toString();
    }

    private void injectPw(String pw) {
        String js =
            "(function(){" +
            "  function fill(sel,val){" +
            "    var el=document.querySelector(sel);if(!el)return;" +
            "    var ns=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;" +
            "    ns.call(el,val);el.dispatchEvent(new Event('input',{bubbles:true}));}" +
            "  fill('input[type=\"password\"]','" + esc(pw) + "');" +
            "  fill('input[name=\"password\"]','" + esc(pw) + "');" +
            "  fill('input[placeholder*=\"Password\"]','" + esc(pw) + "');" +
            "})()";
        webView.evaluateJavascript(js, null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DESKTOP TOGGLE
    // ══════════════════════════════════════════════════════════════════════════
    private void toggleDesktop() {
        boolean cur = prefs.getBoolean(KEY_DESKTOP, false);
        prefs.edit().putBoolean(KEY_DESKTOP, !cur).apply();
        applyAgent(webView.getSettings());
        webView.reload();
        toast("Desktop: " + (!cur ? "ON" : "OFF"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  AUTO 2FA TOGGLE
    // ══════════════════════════════════════════════════════════════════════════
    private void toggle2faAuto() {
        boolean cur = prefs.getBoolean(KEY_2FA_AUTO, false);
        boolean nw  = !cur;
        prefs.edit().putBoolean(KEY_2FA_AUTO, nw).apply();
        btn2faAuto.setText("AUTO 2FA: " + (nw ? "ON" : "OFF"));
        btn2faAuto.setBackground(roundBg(nw ? "#16A34A" : "#6B7280"));
        toast("Auto 2FA: " + (nw ? "ON" : "OFF"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NUM REMOVE TOGGLE
    // ══════════════════════════════════════════════════════════════════════════
    private void toggleNumRemove() {
        boolean cur = prefs.getBoolean(KEY_NUM_REMOVE, false);
        boolean nw  = !cur;
        prefs.edit().putBoolean(KEY_NUM_REMOVE, nw).apply();
        btnNumRemove.setText("NUM REM: " + (nw ? "ON" : "OFF"));
        btnNumRemove.setBackground(roundBg(nw ? "#16A34A" : "#6B7280"));
        toast("Number Remove: " + (nw ? "ON" : "OFF"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HOTMAIL AUTO TOGGLE
    // ══════════════════════════════════════════════════════════════════════════
    private void toggleHotmailAuto(Button btn) {
        boolean cur = prefs.getBoolean(KEY_HOTMAIL_AUTO, false);
        boolean nw  = !cur;
        prefs.edit().putBoolean(KEY_HOTMAIL_AUTO, nw).apply();
        btn.setText("HOTMAIL: " + (nw ? "ON" : "OFF"));
        btn.setBackground(roundBg(nw ? "#16A34A" : "#6B7280"));
        if (nw) loadHotmailFromSheet();
        toast("Hotmail Auto: " + (nw ? "ON" : "OFF"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SHEET DIALOG
    // ══════════════════════════════════════════════════════════════════════════
    private void showSheetDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Google Sheet Setup");
        LinearLayout ll = vll(16);

        ll.addView(label("Paste Google Sheet share link:"));
        EditText etUrl = styledEdit("https://docs.google.com/spreadsheets/d/...");
        etUrl.setText(prefs.getString(KEY_SHEET, ""));
        ll.addView(etUrl);

        // Sheet column layout info
        TextView tvInfo = label(
            "Sheet columns:\n" +
            "  A = UID\n" +
            "  B = Full Cookies (auto-push)\n" +
            "  C = 2FA Key (from OTP dialog)\n" +
            "  D = Hotmail list\n" +
            "       (email|password|token|clientId)\n" +
            "  E = Kill Switch (write OFF to disable app)"
        );
        tvInfo.setTextColor(Color.parseColor("#06B6D4"));
        ll.addView(tvInfo);

        b.setView(wrap(ll));
        b.setPositiveButton("SAVE", (d, w) -> {
            String url = etUrl.getText().toString().trim();
            prefs.edit().putString(KEY_SHEET, url).apply();
            toast("Sheet URL saved");
        });
        b.setNeutralButton("LOAD HOTMAIL", (d, w) -> {
            prefs.edit().putString(KEY_SHEET, etUrl.getText().toString().trim()).apply();
            loadHotmailFromSheet();
        });
        b.setNegativeButton("CANCEL", null);
        b.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LOAD HOTMAIL FROM SHEET COLUMN D
    // ══════════════════════════════════════════════════════════════════════════
    private void loadHotmailFromSheet() {
        String sheetUrl = prefs.getString(KEY_SHEET, "").trim();
        if (sheetUrl.isEmpty()) {
            toast("Set Sheet URL first (SHEET SET)");
            return;
        }
        String csvUrl = toCsvUrl(sheetUrl);
        if (csvUrl == null) {
            toast("Invalid sheet URL");
            return;
        }
        toast("Loading hotmail from sheet...");
        final String fUrl = csvUrl;
        executor.execute(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(fUrl).openConnection();
                con.setConnectTimeout(12000);
                con.setReadTimeout(12000);
                con.setInstanceFollowRedirects(true);
                if (con.getResponseCode() != 200) {
                    mainHandler.post(() -> toast("Sheet load failed"));
                    return;
                }
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(con.getInputStream(), "UTF-8"));
                List<String[]> rows = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) rows.add(line.split(",", -1));
                br.close();

                // Check Column E row 1 — kill switch
                if (!rows.isEmpty() && rows.get(0).length >= 5) {
                    if ("OFF".equalsIgnoreCase(rows.get(0)[4].trim())) {
                        prefs.edit().putBoolean(KEY_APP_ON, false).apply();
                        mainHandler.post(this::showDisabledScreen);
                        return;
                    }
                }

                // Read Column D — hotmail entries
                List<String> hmList = new ArrayList<>();
                for (String[] r : rows) {
                    if (r.length < 4) continue;
                    String d = r[3].trim();
                    if (d.isEmpty() || d.equalsIgnoreCase("hotmail") || d.equalsIgnoreCase("d")) continue;
                    if (d.contains("|")) hmList.add(d);
                }

                // Count how many hotmails sheet has
                final int count = hmList.size();

                // Merge with existing
                List<String> existing = loadHotmailList();
                for (String h : hmList) {
                    boolean found = false;
                    for (String e : existing) {
                        if (e.split("\\|")[0].equalsIgnoreCase(h.split("\\|")[0])) { found = true; break; }
                    }
                    if (!found) existing.add(h);
                }
                saveHotmailList(existing);

                mainHandler.post(() -> toast("Sheet has " + count + " hotmail(s). Loaded OK."));
            } catch (Exception e) {
                mainHandler.post(() -> toast("Error: " + e.getMessage()));
            }
        });
    }

    private String toCsvUrl(String url) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("/spreadsheets/d/([a-zA-Z0-9_-]+)").matcher(url);
        if (!m.find()) return null;
        String id = m.group(1);
        java.util.regex.Matcher gm = java.util.regex.Pattern
            .compile("[#&?]gid=(\\d+)").matcher(url);
        String gid = gm.find() ? gm.group(1) : "0";
        return "https://docs.google.com/spreadsheets/d/" + id + "/export?format=csv&gid=" + gid;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PUSH TO SHEET — Column B = cookies, Column C = 2FA key
    // ══════════════════════════════════════════════════════════════════════════
    private void pushToSheet(String cookies) {
        String sheetUrl = prefs.getString(KEY_SHEET, "").trim();
        if (sheetUrl.isEmpty()) return;
        // Only push to Apps Script URLs
        if (!sheetUrl.contains("script.google.com")) return;

        String uid    = "";
        String twofa  = prefs.getString("twofa_key", "");
        final String fUrl = sheetUrl +
            "?cookies=" + Uri.encode(cookies != null ? cookies : "") +
            "&uid="     + Uri.encode(uid) +
            "&twofa="   + Uri.encode(twofa);

        executor.execute(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(fUrl).openConnection();
                con.setConnectTimeout(8000);
                con.getResponseCode();
            } catch (Exception ignored) {}
        });
    }

    private void pushColC(String sheetUrl, String key) {
        if (!sheetUrl.contains("script.google.com")) return;
        final String fUrl = sheetUrl + "?twofa=" + Uri.encode(key);
        executor.execute(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(fUrl).openConnection();
                con.setConnectTimeout(8000);
                con.getResponseCode();
            } catch (Exception ignored) {}
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  AUTO 2FA FLOW  (NUM REMOVE uses Sheet Column D hotmail)
    // ══════════════════════════════════════════════════════════════════════════
    private void handle2faFlow() {
        if (!prefs.getBoolean(KEY_NUM_REMOVE, false)) return;
        List<String> list = loadHotmailList();
        if (list.isEmpty()) {
            toast("No hotmail — load from sheet first");
            return;
        }
        String entry = list.get(hotmailIndex % list.size());
        String[] parts = entry.split("\\|", -1);
        if (parts.length < 2) return;

        String email    = parts[0];
        String password = parts[1];
        String token    = parts.length > 2 ? parts[2] : "";
        String clientId = parts.length > 3 ? parts[3] : "";

        // Wait a moment then fetch OTP
        mainHandler.postDelayed(() -> fetchOtp(email, password, token, clientId, otp -> {
            if (otp != null) {
                injectOtp(otp);
                // After success — remove number & hotmail entry
                mainHandler.postDelayed(() -> {
                    if (prefs.getBoolean(KEY_NUM_REMOVE, false)) removePhoneNumbers();
                    List<String> l2 = loadHotmailList();
                    int idx = hotmailIndex % l2.size();
                    l2.remove(idx);
                    saveHotmailList(l2);
                    hotmailIndex = 0;
                }, 4000);
            } else {
                smsResendCount++;
                if (smsResendCount < MAX_RESEND) {
                    mainHandler.postDelayed(() -> handle2faFlow(), 30000);
                } else {
                    hotmailIndex++;
                    smsResendCount = 0;
                    mainHandler.postDelayed(() -> handle2faFlow(), 5000);
                }
            }
        }), 3000);
    }

    private void injectOtp(String otp) {
        webView.evaluateJavascript(
            "(function(){" +
            "  var inputs=document.querySelectorAll('input');" +
            "  for(var i=0;i<inputs.length;i++){" +
            "    var t=inputs[i].type;" +
            "    if(t==='number'||t==='tel'||t==='text'){" +
            "      var ns=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;" +
            "      ns.call(inputs[i],'" + otp + "');" +
            "      inputs[i].dispatchEvent(new Event('input',{bubbles:true}));" +
            "      break;" +
            "    }" +
            "  }" +
            "  var s=document.querySelector('button[type=submit],input[type=submit]');" +
            "  if(s)s.click();" +
            "})()", null);
    }

    private void removePhoneNumbers() {
        webView.evaluateJavascript(
            "(function(){" +
            "  var btns=document.querySelectorAll('button,a');" +
            "  for(var i=0;i<btns.length;i++){" +
            "    var t=(btns[i].innerText||'').toLowerCase();" +
            "    if(t.includes('remove')||t.includes('delete')){btns[i].click();}" +
            "  }" +
            "})()", null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HOTMAIL OTP via Microsoft Graph
    // ══════════════════════════════════════════════════════════════════════════
    interface OtpCb { void onOtp(String otp); }

    private void fetchOtp(String email, String pw, String token, String clientId, OtpCb cb) {
        executor.execute(() -> {
            try {
                String accessToken = token.isEmpty()
                    ? getMsToken(email, pw, clientId)
                    : token;
                if (accessToken == null) { mainHandler.post(() -> cb.onOtp(null)); return; }

                String otp = readOtpFromMail(accessToken);
                mainHandler.post(() -> cb.onOtp(otp));
            } catch (Exception e) {
                mainHandler.post(() -> cb.onOtp(null));
            }
        });
    }

    private String getMsToken(String email, String pw, String clientId) throws Exception {
        URL url = new URL("https://login.microsoftonline.com/common/oauth2/v2.0/token");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        String body = "grant_type=password&client_id=" + Uri.encode(clientId) +
            "&username=" + Uri.encode(email) + "&password=" + Uri.encode(pw) +
            "&scope=https://graph.microsoft.com/Mail.Read";
        con.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        if (con.getResponseCode() != 200) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String l; while ((l = br.readLine()) != null) sb.append(l);
        return new JSONObject(sb.toString()).optString("access_token", null);
    }

    private String readOtpFromMail(String token) throws Exception {
        URL url = new URL(
            "https://graph.microsoft.com/v1.0/me/messages" +
            "?$filter=from/emailAddress/address eq 'security@facebookmail.com'" +
            "&$orderby=receivedDateTime desc&$top=1&$select=body");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Authorization", "Bearer " + token);
        if (con.getResponseCode() != 200) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String l; while ((l = br.readLine()) != null) sb.append(l);
        JSONArray vals = new JSONObject(sb.toString()).optJSONArray("value");
        if (vals == null || vals.length() == 0) return null;
        String body = vals.getJSONObject(0).getJSONObject("body").optString("content", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6,8})\\b").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HOTMAIL LIST storage
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> loadHotmailList() {
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_HOTMAIL_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (Exception ignored) {}
        return list;
    }

    private void saveHotmailList(List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        prefs.edit().putString(KEY_HOTMAIL_LIST, arr.toString()).apply();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ALL CLEAR  — does NOT clear password or name
    // ══════════════════════════════════════════════════════════════════════════
    private void showClearDialog() {
        new AlertDialog.Builder(this)
            .setTitle("ALL CLEAR")
            .setMessage("Clear cookies, cache & session?\n\n(Password and Names are kept safe)")
            .setPositiveButton("ALL CLEAR", (d, w) -> {
                // Save name + password before clearing
                String savedPw    = prefs.getString(KEY_PW,        "");
                String savedNum   = prefs.getString(KEY_NUM,       "");
                String savedFirst = prefs.getString(KEY_FIRSTNAME, "");
                String savedLast  = prefs.getString(KEY_LASTNAME,  "");
                String savedSheet = prefs.getString(KEY_SHEET,     "");
                String savedHm    = prefs.getString(KEY_HOTMAIL_LIST, "[]");
                boolean twoFaOn   = prefs.getBoolean(KEY_2FA_AUTO, false);
                boolean numRemOn  = prefs.getBoolean(KEY_NUM_REMOVE, false);
                boolean hmAuto    = prefs.getBoolean(KEY_HOTMAIL_AUTO, false);
                boolean desktop   = prefs.getBoolean(KEY_DESKTOP, false);
                String agents     = prefs.getString(KEY_AGENTS, "[]");
                String devNames   = prefs.getString(KEY_DEVICE_NAMES, "[]");
                String selAgent   = prefs.getString(KEY_SEL_AGENT, "");
                String twoFaKey   = prefs.getString("twofa_key", "");

                // Clear cookies and cache
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                webView.clearCache(true);
                webView.clearHistory();
                webView.clearFormData();

                // Clear prefs fully then restore important keys
                prefs.edit().clear()
                    .putString(KEY_PW,        savedPw)
                    .putString(KEY_NUM,       savedNum)
                    .putString(KEY_FIRSTNAME, savedFirst)
                    .putString(KEY_LASTNAME,  savedLast)
                    .putString(KEY_SHEET,     savedSheet)
                    .putString(KEY_HOTMAIL_LIST, savedHm)
                    .putBoolean(KEY_2FA_AUTO,   twoFaOn)
                    .putBoolean(KEY_NUM_REMOVE, numRemOn)
                    .putBoolean(KEY_HOTMAIL_AUTO, hmAuto)
                    .putBoolean(KEY_DESKTOP,    desktop)
                    .putBoolean(KEY_APP_ON,     true)
                    .putString(KEY_AGENTS,     agents)
                    .putString(KEY_DEVICE_NAMES, devNames)
                    .putString(KEY_SEL_AGENT,  selAgent)
                    .putString("twofa_key",    twoFaKey)
                    .apply();

                webView.loadUrl("https://limited.facebook.com");
                toast("Cleared! Password & names safe.");
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  AGENT DIALOG
    // ══════════════════════════════════════════════════════════════════════════
    private void showAgentDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("AGENT SET");
        LinearLayout ll = vll(12);

        ll.addView(label("Add custom user-agent:"));
        EditText etAgent = styledEdit("Mozilla/5.0 ...");
        EditText etName  = styledEdit("Device name (label)");
        ll.addView(etName);
        ll.addView(etAgent);

        List<String> agents  = loadJsonList(KEY_AGENTS);
        List<String> devNames= loadJsonList(KEY_DEVICE_NAMES);
        if (!agents.isEmpty()) {
            ll.addView(label("Saved agents:"));
            for (int i = 0; i < agents.size(); i++) {
                String dn = i < devNames.size() ? devNames.get(i) : "Agent " + i;
                TextView tv = label("• " + dn);
                tv.setTextColor(Color.LTGRAY);
                ll.addView(tv);
            }
        }

        b.setView(wrap(ll));
        b.setPositiveButton("ADD", (d, w) -> {
            String ag = etAgent.getText().toString().trim();
            String dn = etName.getText().toString().trim();
            if (!ag.isEmpty()) {
                agents.add(ag);
                devNames.add(dn.isEmpty() ? "Agent " + agents.size() : dn);
                saveJsonList(KEY_AGENTS, agents);
                saveJsonList(KEY_DEVICE_NAMES, devNames);
                prefs.edit().putString(KEY_SEL_AGENT, ag).apply();
                applyAgent(webView.getSettings());
                webView.reload();
                toast("Agent added & selected");
            }
        });
        b.setNeutralButton("CLEAR ALL", (d, w) -> {
            prefs.edit().putString(KEY_AGENTS, "[]")
                .putString(KEY_SEL_AGENT, "").apply();
            applyAgent(webView.getSettings());
            toast("Agents cleared");
        });
        b.setNegativeButton("CANCEL", null);
        b.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private LinearLayout row() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        ll.setLayoutParams(lp);
        return ll;
    }

    private LinearLayout vll(int pad) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(pad), dp(pad), dp(pad), dp(pad));
        return ll;
    }

    private LinearLayout wrap(View v) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.addView(v);
        return ll;
    }

    private Button mkBtn(String text, String color, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setAllCaps(true);
        btn.setBackground(roundBg(color));
        btn.setPadding(dp(8), dp(14), dp(8), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    private GradientDrawable roundBg(String color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(color));
        gd.setCornerRadius(dp(10));
        return gd;
    }

    private EditText styledEdit(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.GRAY);
        et.setTextColor(Color.WHITE);
        et.setBackgroundColor(Color.parseColor("#2A2A2A"));
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        et.setLayoutParams(lp);
        return et;
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.LTGRAY);
        tv.setTextSize(12);
        return tv;
    }

    private void toast(String msg) {
        mainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private void copyClip(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private List<String> loadJsonList(String key) {
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (Exception ignored) {}
        return list;
    }

    private void saveJsonList(String key, List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        prefs.edit().putString(key, arr.toString()).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
