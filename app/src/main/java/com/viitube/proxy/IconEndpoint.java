package com.viitube.proxy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import fi.iki.elonen.NanoHTTPD;

/**
 * Small images the proxy draws itself, for rows whose icon the apps have no drawable for:
 * GET /proxy/icon/add.png is the blue "+" of the Library's "New playlist" row (12.45.56 has only
 * a white "+" in its icon table, which is invisible on the white Library);
 * /proxy/icon/category_Music.png (Gaming, News, Movies) are the Trending categories.
 */
public class IconEndpoint implements Endpoint {

    /** The blue of YouTube's links in the 2017 apps. */
    static final int BLUE = 0xFF167AC6;
    private static volatile byte[] add;

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, String[] params,
                                     Config config, Context context) throws Exception {
        String uri = session.getUri();
        String name = uri.substring(uri.lastIndexOf('/') + 1);
        byte[] png;
        if (name.equals("add.png")) {
            if (add == null) add = drawPlus();
            png = add;
        } else if (name.startsWith("category_") && name.endsWith(".png")) {
            png = category(name.substring("category_".length(), name.length() - 4));
        } else {
            png = null;
        }
        if (png == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    "text/plain", "no such icon");
        }
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                "image/png", new java.io.ByteArrayInputStream(png), png.length);
        response.addHeader("Cache-Control", "max-age=86400");
        return response;
    }

    private static final java.util.Map<String, byte[]> CATEGORIES =
            java.util.Collections.synchronizedMap(new java.util.HashMap<String, byte[]>());

    /**
     * The Trending tab's category pictures of the 2018 apps: a dark grey circle with a white
     * sign - a note for music, a game pad, a newspaper, a film strip. Drawn once each.
     */
    private static byte[] category(String name) {
        byte[] known = CATEGORIES.get(name);
        if (known != null) return known;
        int size = 176;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF606060);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(0xFFFFFFFF);
        float u = size / 100f;
        if (name.equals("Music")) {
            canvas.drawCircle(40 * u, 66 * u, 10 * u, paint);
            canvas.drawCircle(66 * u, 60 * u, 10 * u, paint);
            canvas.drawRect(46 * u, 30 * u, 50 * u, 66 * u, paint);
            canvas.drawRect(72 * u, 24 * u, 76 * u, 60 * u, paint);
            android.graphics.Path beam = new android.graphics.Path();
            beam.moveTo(46 * u, 30 * u);
            beam.lineTo(76 * u, 24 * u);
            beam.lineTo(76 * u, 32 * u);
            beam.lineTo(46 * u, 38 * u);
            beam.close();
            canvas.drawPath(beam, paint);
        } else if (name.equals("Gaming")) {
            canvas.drawRoundRect(new android.graphics.RectF(24 * u, 36 * u, 76 * u, 64 * u),
                    12 * u, 12 * u, paint);
            paint.setColor(0xFF606060);
            canvas.drawRect(32 * u, 48 * u, 46 * u, 52 * u, paint);
            canvas.drawRect(37 * u, 43 * u, 41 * u, 57 * u, paint);
            canvas.drawCircle(60 * u, 46 * u, 3.5f * u, paint);
            canvas.drawCircle(67 * u, 53 * u, 3.5f * u, paint);
        } else if (name.equals("News")) {
            canvas.drawRect(28 * u, 28 * u, 72 * u, 72 * u, paint);
            paint.setColor(0xFF606060);
            canvas.drawRect(34 * u, 34 * u, 48 * u, 48 * u, paint);
            canvas.drawRect(52 * u, 36 * u, 66 * u, 39 * u, paint);
            canvas.drawRect(52 * u, 43 * u, 66 * u, 46 * u, paint);
            for (int line = 0; line < 4; line++) {
                float y = (53 + line * 5) * u;
                canvas.drawRect(34 * u, y, 66 * u, y + 2.5f * u, paint);
            }
        } else if (name.equals("Movies")) {
            canvas.drawRect(30 * u, 26 * u, 70 * u, 74 * u, paint);
            paint.setColor(0xFF606060);
            canvas.drawRect(40 * u, 30 * u, 60 * u, 48 * u, paint);
            canvas.drawRect(40 * u, 52 * u, 60 * u, 70 * u, paint);
            for (int hole = 0; hole < 5; hole++) {
                float y = (29 + hole * 9) * u;
                canvas.drawRect(32.5f * u, y, 36.5f * u, y + 4.5f * u, paint);
                canvas.drawRect(63.5f * u, y, 67.5f * u, y + 4.5f * u, paint);
            }
        } else {
            bitmap.recycle();
            return null;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle();
        byte[] png = out.toByteArray();
        CATEGORIES.put(name, png);
        return png;
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
