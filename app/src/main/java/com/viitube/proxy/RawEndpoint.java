package com.viitube.proxy;

import android.content.Context;

import fi.iki.elonen.NanoHTTPD;

import org.json.JSONObject;

/**
 * A look at youtube.com's own answers, for working out what the proxy can show: GET
 * /proxy/raw?endpoint=browse&browseId=FElibrary (any other parameter goes into the request
 * as a string) answers with the JSON of that web request, signed with the session when there is
 * one. Only reachable on the device itself, like the rest of the proxy.
 */
public class RawEndpoint implements Endpoint {

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, String[] pathParams,
                                     Config config, Context context) throws Exception {
        YouTubeAuth.init(context);
        java.util.Map<String, String> params = session.getParms();
        String endpoint = params.containsKey("endpoint") ? params.get("endpoint") : "browse";
        JSONObject payload = InnertubeEndpoint.context(config);
        for (java.util.Map.Entry<String, String> entry : params.entrySet()) {
            if (!entry.getKey().equals("endpoint")) payload.put(entry.getKey(), entry.getValue());
        }
        String body;
        try {
            body = InnertubeEndpoint.request(endpoint, payload, config).toString();
        } catch (Exception e) {
            body = "{\"error\":" + JSONObject.quote(String.valueOf(e)) + "}";
        }
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                "application/json; charset=utf-8", body);
    }
}
