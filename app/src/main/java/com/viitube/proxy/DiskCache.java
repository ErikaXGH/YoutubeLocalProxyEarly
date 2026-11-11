package com.viitube.proxy;

import android.content.Context;

import java.io.File;

/**
 * A small byte cache in the app's cache directory, bounded in bytes: the oldest files (by the
 * time they were last read or written) go first. Play Music's songs and covers are kept here so
 * playing a song again, or scrolling back to a list after the proxy restarted, does not fetch
 * them over the phone's link a second time. Android may also clear the cache directory itself.
 */
final class DiskCache {

    private static volatile File root;

    private DiskCache() { }

    /** Called with any context before the first use (the endpoints all have one). */
    static void init(Context context) {
        if (root == null && context != null) root = context.getCacheDir();
    }

    /** The bytes kept under {@code key} in {@code area}, or null. */
    static byte[] get(String area, String key) {
        File file = file(area, key);
        if (file == null || !file.isFile()) return null;
        try {
            byte[] bytes = HttpClient.readBytes(file);
            file.setLastModified(System.currentTimeMillis());
            return bytes;
        } catch (Exception e) {
            file.delete();
            return null;
        }
    }

    /** Keeps {@code bytes}, then trims {@code area} to {@code maxBytes}. Failures are ignored. */
    static void put(String area, String key, byte[] bytes, long maxBytes) {
        File file = file(area, key);
        if (file == null || bytes == null || bytes.length == 0 || bytes.length > maxBytes) return;
        File partial = new File(file.getPath() + ".part");
        try {
            HttpClient.writeBytes(partial, bytes);
            if (!partial.renameTo(file)) {
                partial.delete();
                return;
            }
        } catch (Exception e) {
            partial.delete();
            return;
        }
        trim(file.getParentFile(), maxBytes);
    }

    private static synchronized void trim(File dir, long maxBytes) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= maxBytes) return;
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                long x = a.lastModified();
                long y = b.lastModified();
                return x < y ? -1 : x > y ? 1 : 0;
            }
        });
        for (File f : files) {
            if (total <= maxBytes) break;
            long size = f.length();
            if (f.delete()) total -= size;
        }
    }

    private static File file(String area, String key) {
        File base = root;
        if (base == null || !key.matches("[A-Za-z0-9_-]{1,64}")) return null;
        File dir = new File(base, area);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        return new File(dir, key);
    }
}
