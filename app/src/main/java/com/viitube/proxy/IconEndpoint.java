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

    private static final java.util.Map<String, byte[]> CATEGORIES =
            java.util.Collections.synchronizedMap(
                    new java.util.HashMap<String, byte[]>());

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
            png = category(
                    name.substring("category_".length(), name.length() - 4),
                    context
            );
        } else {
            png = null;
        }

        if (png == null) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    "text/plain",
                    "no such icon"
            );
        }

        NanoHTTPD.Response response =
                NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK,
                        "image/png",
                        new java.io.ByteArrayInputStream(png),
                        png.length
                );

        response.addHeader("Cache-Control", "max-age=86400");
        return response;
    }

    /**
     * Loads custom category icons from app/src/main/assets/.
     */
    private static byte[] category(String name, Context context) {
        if (!name.equals("Music") &&
            !name.equals("Gaming") &&
            !name.equals("News") &&
            !name.equals("Movies")) {
            return null;
        }

        byte[] cached = CATEGORIES.get(name);
        if (cached != null) {
            return cached;
        }

        String filename = "category_" + name + ".png";

        try (java.io.InputStream input =
                     context.getAssets().open(filename);
             java.io.ByteArrayOutputStream output =
                     new java.io.ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int length;

            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }

            byte[] png = output.toByteArray();
            CATEGORIES.put(name, png);
            return png;

        } catch (Exception e) {
            android.util.Log.e(
                    "IconEndpoint",
                    "Failed to load " + filename,
                    e
            );
            return null;
        }
    }

    private static byte[] drawPlus() {
        int size = 96;
        Bitmap bitmap = Bitmap.createBitmap(
                size, size, Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(BLUE);

        float stroke = size * 0.09f;
        float from = size * 0.22f;
        float to = size * 0.78f;
        float middle = size / 2f;

        canvas.drawRect(
                middle - stroke / 2,
                from,
                middle + stroke / 2,
                to,
                paint
        );

        canvas.drawRect(
                from,
                middle - stroke / 2,
                to,
                middle + stroke / 2,
                paint
        );

        java.io.ByteArrayOutputStream out =
                new java.io.ByteArrayOutputStream();

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle();

        return out.toByteArray();
    }
}
