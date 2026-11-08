package com.viitube.proxy;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * Signs in to YouTube the way a browser does: Google's own sign-in page in a WebView, then the
 * session cookies it leaves on youtube.com are taken over by {@link YouTubeAuth}. No password
 * ever passes through this app; it only reads the cookies once the sign-in has finished.
 */
public class LoginActivity extends Activity {

    /**
     * Google's light sign-in for YouTube (flowName=WebLiteSignIn, the one TubeReplacer opens): the
     * plain older page instead of the full one, coming back to youtube.com's ytscframe so the
     * youtube.com session cookies get set.
     */
    private static final String SIGN_IN_URL = "https://accounts.google.com/ServiceLogin"
            + "?service=youtube&uilel=3&passive=true"
            + "&continue=https://m.youtube.com/ytscframe&app=m&hl=pl&next=%2F&flowName=WebLiteSignIn";

    private volatile boolean finishing;

    /** Named, because the dexer of this build fails on an anonymous class that captures. */
    private static final class Client extends WebViewClient {
        private final LoginActivity activity;

        Client(LoginActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            activity.checkCookies();
        }
    }

    /** Reads the account behind the new session, off the main thread. */
    private static final class AccountLoader implements Runnable {
        private final LoginActivity activity;

        AccountLoader(LoginActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            String datasync = "";
            String name = "";
            String email = "";
            try {
                Config config = Config.get(activity);
                JSONObject menu = InnertubeEndpoint.request("account/account_menu",
                        InnertubeEndpoint.context(config), config);
                datasync = find(menu, "datasyncId");
                name = find(menu, "accountName");
                email = find(menu, "email");
                if (email.isEmpty()) email = emailLike(menu);
                if (email.isEmpty()) email = listAccountsEmail();
            } catch (Exception e) {
                android.util.Log.w("Login", "account menu failed: " + e);
            }
            YouTubeAuth.saveAccount(datasync, name, email);
            activity.runOnUiThread(new Done(activity, name));
        }
    }

    private static final class Done implements Runnable {
        private final LoginActivity activity;
        private final String name;

        Done(LoginActivity activity, String name) {
            this.activity = activity;
            this.name = name;
        }

        @Override
        public void run() {
            Toast.makeText(activity, name.isEmpty() ? "Signed in to YouTube"
                    : "Signed in as " + name, Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Sign in to YouTube");
        YouTubeAuth.init(this);
        CookieSyncManager.createInstance(this);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        // A clean start, so a previous account's cookies are not mixed into the new session.
        cookies.removeAllCookie();
        WebView web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        web.setWebViewClient(new Client(this));
        setContentView(web);
        web.loadUrl(SIGN_IN_URL);
    }

    /** Once youtube.com holds a signed-in session, it is saved and the account is read. */
    void checkCookies() {
        if (finishing) return;
        CookieSyncManager.getInstance().sync();
        CookieManager manager = CookieManager.getInstance();
        String cookies = join(manager.getCookie("https://www.youtube.com"),
                manager.getCookie("https://m.youtube.com"));
        if (!YouTubeAuth.saveFromCookies(cookies)) return;
        finishing = true;
        new Thread(new AccountLoader(this), "youtube-account").start();
    }

    private static String join(String a, String b) {
        if (a == null) return b == null ? "" : b;
        if (b == null) return a;
        return a + "; " + b;
    }

    /**
     * The signed-in email from Google's ListAccounts, which the session cookies alone unlock.
     * Returns the first "x@y.z" in the answer, or "" if the call fails.
     */
    private static String listAccountsEmail() {
        String cookies = YouTubeAuth.cookieHeader();
        if (cookies.isEmpty()) return "";
        java.net.HttpURLConnection c = null;
        try {
            java.net.URL url = new java.net.URL(
                    "https://accounts.google.com/ListAccounts?listPages=0&json=standard");
            c = (java.net.HttpURLConnection) url.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(7000);
            c.setRequestProperty("Cookie", cookies);
            java.util.Scanner scanner = new java.util.Scanner(c.getInputStream(), "UTF-8");
            scanner.useDelimiter("\\A");
            String body = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}").matcher(body);
            if (m.find()) return m.group();
        } catch (Exception e) {
            android.util.Log.w("Login", "ListAccounts failed: " + e);
        } finally {
            if (c != null) c.disconnect();
        }
        return "";
    }

    /** First string value anywhere that looks like an email ("x@y.z", no spaces). */
    private static String emailLike(Object node) {
        if (node instanceof String) {
            String s = (String) node;
            int at = s.indexOf('@');
            if (at > 0 && s.indexOf(' ') < 0 && s.indexOf('.', at) > at) return s;
            return "";
        }
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            java.util.Iterator<String> names = object.keys();
            if (names != null) {
                for (int i = 0; names.hasNext(); i++) {
                    String found = emailLike(object.opt(names.next()));
                    if (!found.isEmpty()) return found;
                }
            }
        } else if (node instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                String found = emailLike(array.opt(i));
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    /** The first value of a key anywhere in the answer, as text (simpleText for a text object). */
    private static String find(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            Object value = object.opt(key);
            if (value instanceof String && !((String) value).isEmpty()) return (String) value;
            if (value instanceof JSONObject) {
                JSONObject text = (JSONObject) value;
                String simple = text.optString("simpleText", "");
                if (simple.isEmpty() && text.optJSONArray("runs") != null) {
                    simple = text.optJSONArray("runs").optJSONObject(0).optString("text", "");
                }
                if (!simple.isEmpty()) return simple;
            }
            java.util.Iterator<String> names = object.keys();
            if (names != null) {
                for (int i = 0; names.hasNext(); i++) {
                    String found = find(object.opt(names.next()), key);
                    if (!found.isEmpty()) return found;
                }
            }
        } else if (node instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                String found = find(array.opt(i), key);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }
}
