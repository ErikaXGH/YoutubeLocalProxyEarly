package com.viitube.proxy;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

/**
 * A signed-in YouTube session, the way a browser holds one: the Google session cookies of
 * youtube.com (SID, HSID, SSID, SAPISID...) and the account's DATASYNC_ID. Requests made with the
 * web clients are then signed like youtube.com signs its own: the cookies, and an
 * {@code Authorization: SAPISIDHASH} header computed from SAPISID, the time and the origin.
 *
 * <p>The session is kept in this app's private preferences, not in config.json, so it never
 * ends up in a crash report or a copied config.
 */
final class YouTubeAuth {

    private static final String PREFS = "youtube_auth";
    /** The cookies a signed-in youtube.com request carries. */
    static final String[] COOKIES = {"SID", "HSID", "SSID", "APISID", "SAPISID",
            "__Secure-1PSID", "__Secure-3PSID", "__Secure-1PAPISID", "__Secure-3PAPISID",
            "SIDCC", "__Secure-1PSIDCC", "__Secure-3PSIDCC", "LOGIN_INFO"};

    private static volatile SharedPreferences prefs;
    private static volatile String cookieHeader = "";
    private static volatile String sapisid = "";
    private static volatile String datasyncId = "";
    private static volatile String accountName = "";

    private YouTubeAuth() { }

    /** Loads the saved session; called when the service and the settings start. */
    static synchronized void init(Context context) {
        if (prefs != null) return;
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cookieHeader = prefs.getString("cookies", "");
        sapisid = prefs.getString("sapisid", "");
        datasyncId = prefs.getString("datasync", "");
        accountName = prefs.getString("name", "");
    }

    static boolean signedIn() {
        return !sapisid.isEmpty() && !cookieHeader.isEmpty();
    }

    static String accountName() {
        return accountName;
    }

    /**
     * Saves a session from a WebView's cookie string ("a=b; c=d"). Only the Google session cookies
     * are kept; false when the string has no signed-in session in it (no SAPISID or SID).
     */
    static synchronized boolean saveFromCookies(String cookies) {
        Map<String, String> found = new java.util.LinkedHashMap<String, String>();
        if (cookies != null) {
            for (String part : cookies.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                String name = part.substring(0, eq).trim();
                String value = part.substring(eq + 1).trim();
                for (String wanted : COOKIES) {
                    if (wanted.equals(name)) found.put(name, value);
                }
            }
        }
        String apisid = found.containsKey("SAPISID") ? found.get("SAPISID") : found.get("__Secure-3PAPISID");
        if (apisid == null || apisid.isEmpty() || !found.containsKey("SID")) return false;
        if (!found.containsKey("SAPISID")) found.put("SAPISID", apisid);
        if (!found.containsKey("__Secure-3PAPISID")) found.put("__Secure-3PAPISID", apisid);
        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : found.entrySet()) {
            if (header.length() > 0) header.append("; ");
            header.append(entry.getKey()).append('=').append(entry.getValue());
        }
        cookieHeader = header.toString();
        sapisid = apisid;
        prefs.edit().putString("cookies", cookieHeader).putString("sapisid", sapisid).commit();
        return true;
    }

    static synchronized void saveAccount(String datasync, String name) {
        datasyncId = datasync == null ? "" : datasync;
        accountName = name == null ? "" : name;
        prefs.edit().putString("datasync", datasyncId).putString("name", accountName).commit();
    }

    static synchronized void signOut() {
        cookieHeader = "";
        sapisid = "";
        datasyncId = "";
        accountName = "";
        if (prefs != null) prefs.edit().clear().commit();
    }

    /**
     * Adds the session to a request for {@code origin} ("https://www.youtube.com"): the cookies,
     * SAPISIDHASH over "[datasync ]time SAPISID origin", the origin and the account index.
     */
    static void sign(Map<String, String> headers, String origin) {
        if (!signedIn()) return;
        long now = System.currentTimeMillis() / 1000;
        String user = lastPart(datasyncId);
        String input = (user.isEmpty() ? "" : user + " ") + now + " " + sapisid + " " + origin;
        headers.put("Cookie", cookieHeader);
        headers.put("Authorization", "SAPISIDHASH " + now + "_" + sha1(input) + (user.isEmpty() ? "" : "_u"));
        headers.put("Origin", origin);
        headers.put("X-Origin", origin);
        headers.put("X-Goog-AuthUser", "0");
    }

    /** The cookie header alone, for plain page requests. */
    static String cookieHeader() {
        return cookieHeader;
    }

    /** DATASYNC_ID is "channel||user" or "user||"; the hash takes its last non-empty part. */
    private static String lastPart(String datasync) {
        String result = "";
        for (String part : datasync.split("\\|\\|")) {
            if (!part.isEmpty()) result = part;
        }
        return result;
    }

    private static String sha1(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
