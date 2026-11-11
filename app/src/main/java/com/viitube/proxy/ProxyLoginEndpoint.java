package com.viitube.proxy;

import android.content.Context;

import fi.iki.elonen.NanoHTTPD;

/**
 * The login bridge for the patched YouTube apps. Their GmsCore auth client (class {@code lqf}) is
 * patched to ask this endpoint for a YouTube token instead of microG, which on this device cannot
 * mint one (Google answers UNREGISTERED_ON_API_CONSOLE for a resigned app). When the proxy holds a
 * signed-in session ({@link YouTubeAuth}) a token is handed back and the app enters signed-in mode;
 * the proxy then serves that account's own feeds. The token's value does not matter - the proxy
 * backs the data with its cookie session, not the token - so a constant is returned, and an empty
 * body when signed out, which leaves the app signed out.
 */
public class ProxyLoginEndpoint implements Endpoint {

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, String[] pathParams,
                                     Config config, Context context) {
        YouTubeAuth.init(context);
        String body;
        if (session.getUri().contains("/proxy/signedin")) {
            body = YouTubeAuth.signedIn() ? "1" : "0";
        } else if (session.getUri().contains("/proxy/account")) {
            body = YouTubeAuth.signedIn() ? YouTubeAuth.accountEmail() : "";
        } else {
            body = YouTubeAuth.signedIn() ? "proxy-youtube-token" : "";
        }
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, "text/plain; charset=utf-8", body);
        response.addHeader("Cache-Control", "no-cache, no-store");
        return response;
    }
}
