package com.viitube.proxy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import fi.iki.elonen.NanoHTTPD;

/**
 * Small images the proxy draws itself, for rows whose icon the apps have no drawable for:
 * GET /proxy/icon/add.png is the blue "+" of the Library's "New playlist" row (12.45.56 has only
 * a white "+" in its icon table, which is invisible on the white Library).
 */
public class IconEndpoint implements Endpoint {

    /** The blue of YouTube's links in the 2017 apps. */
    static final int BLUE = 0xFF167AC6;
    private static volatile byte[] add;

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, String[] params,
                                     Config config, Context context) throws Exception {
        if (!session.getUri().endsWith("/add.png")) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    "text/plain", "no such icon");
        }
        if (add == null) add = drawPlus();
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                "image/png", new java.io.ByteArrayInputStream(add), add.length);
        response.addHeader("Cache-Control", "max-age=86400");
        return response;
    }

    private static byte[] drawPlus() {
        int size = 96;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(BLUE);
        float stroke = size * 0.09f;
        float from = size * 0.22f;
        float to = size * 0.78f;
        float middle = size / 2f;
        canvas.drawRect(middle - stroke / 2, from, middle + stroke / 2, to, paint);
        canvas.drawRect(from, middle - stroke / 2, to, middle + stroke / 2, paint);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle();
        return out.toByteArray();
    }
}
