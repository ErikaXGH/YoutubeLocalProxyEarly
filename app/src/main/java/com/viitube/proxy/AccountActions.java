package com.viitube.proxy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.viitube.proxy.proto.ProtoWriter;

/**
 * What the apps do to the signed-in account: liking and subscribing. The apps send these as
 * protobuf /youtubei/v1 requests carrying the proxy's stand-in token; the proxy reads the video or
 * channel id out of the request and makes the same call as youtube.com does, signed with the web
 * session ({@link YouTubeAuth}). The apps get an empty answer, which they take as done.
 */
final class AccountActions {

    private static final String TAG = "AccountActions";
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_-]{22}");

    /** The channels the account subscribes to, so the subscribe buttons show the real state. */
    private static final java.util.Set<String> SUBSCRIBED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static final long SUBSCRIPTIONS_TTL = 10L * 60 * 1000;
    private static volatile long subscriptionsLoadedAt;
    /** Changes whenever the set does; part of the cache keys of answers holding a subscribe button. */
    private static volatile int subscriptionsVersion;

    private AccountActions() { }

    static boolean isSubscribed(String channelId) {
        return channelId != null && SUBSCRIBED.contains(channelId);
    }

    static int subscriptionsVersion() {
        return subscriptionsVersion;
    }

    /** Bumped by every rating, so cached watch pages show the new like state. */
    private static volatile int ratingsVersion;

    static int ratingsVersion() {
        return ratingsVersion;
    }

    /** Reloads the account's channels when the list is older than ten minutes. */
    static void refreshSubscriptions(Config config) {
        if (!YouTubeAuth.signedIn()) return;
        if (System.currentTimeMillis() - subscriptionsLoadedAt < SUBSCRIPTIONS_TTL) return;
        InnertubeEndpoint.subscribedChannels(config);
    }

    /** Called with the channels youtube.com's guide listed. */
    static void setSubscriptions(List<String[]> channels) {
        if (channels.isEmpty()) return;
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (String[] channel : channels) ids.add(channel[0]);
        synchronized (SUBSCRIBED) {
            if (!ids.equals(SUBSCRIBED)) {
                SUBSCRIBED.clear();
                SUBSCRIBED.addAll(ids);
                subscriptionsVersion++;
            }
        }
        subscriptionsLoadedAt = System.currentTimeMillis();
    }

    /**
     * A message with only a field the apps do not know: they drop a zero-length answer without
     * calling back, so the actions answer with this one.
     */
    static final byte[] DONE = {(byte) 0xC0, 0x3E, 0x00};
    private static final Pattern PLAYLIST_ID = Pattern.compile("WL|LL|(?:PL|FL|OL|RD|UU)[A-Za-z0-9_-]{10,}");

    /**
     * The answer for {@code path} (after /youtubei/v1/) when it is one of the account's actions,
     * or null when it is not.
     */
    static byte[] answer(String path, byte[] body, Config config) {
        if (path.equals("playlist/get_add_to_playlist")) return addToPlaylist(body, config);
        if (path.equals("playlist/create")) return createPlaylist(body, config);
        if (path.equals("comment/perform_comment_action")) {
            performCommentAction(body, config);
            return DONE;
        }
        if (path.equals("browse/edit_playlist")) {
            editPlaylist(body, config);
            return DONE;
        }
        return handle(path, body, config) ? DONE : null;
    }

    /** True when {@code path} (after /youtubei/v1/) was one of these actions and was handled. */
    static boolean handle(String path, byte[] body, Config config) {
        if (path.equals("like/like") || path.equals("like/dislike") || path.equals("like/removelike")) {
            List<String> ids = strings(body, VIDEO_ID);
            if (ids.isEmpty()) {
                // Saving a playlist to the library is a like of the playlist (no video in it).
                for (String playlist : strings(body, PLAYLIST_ID)) {
                    if (playlist.length() > 2) {
                        call(path, "playlist:" + playlist, config);
                        return true;
                    }
                }
            }
            if (ids.isEmpty()) {
                android.util.Log.w(TAG, path + ": no video id in the request");
                return true;
            }
            call(path, ids.get(0), config);
            return true;
        }
        if (path.equals("subscription/subscribe") || path.equals("subscription/unsubscribe")) {
            List<String> ids = strings(body, CHANNEL_ID);
            if (ids.isEmpty()) {
                android.util.Log.w(TAG, path + ": no channel id in the request");
                return true;
            }
            call(path, ids.get(0), config);
            return true;
        }
        return false;
    }

    /** Video id for like/*, channel id for subscription/*. */
    private static void call(String path, String value, Config config) {
        if (!YouTubeAuth.signedIn()) {
            android.util.Log.w(TAG, path + ": not signed in, ignored");
            return;
        }
        try {
            JSONObject payload = InnertubeEndpoint.context(config);
            if (path.startsWith("like/") && value.startsWith("playlist:")) {
                payload.put("target", new JSONObject().put("playlistId", value.substring(9)));
                InnertubeEndpoint.forgetAccountLists();
            } else if (path.startsWith("like/")) {
                payload.put("target", new JSONObject().put("videoId", value));
            } else {
                payload.put("channelIds", new JSONArray().put(value));
            }
            InnertubeEndpoint.request(path, payload, config);
            android.util.Log.i(TAG, path + " " + value + ": done");
            if (path.startsWith("like/")) ratingsVersion++;
            if (path.startsWith("subscription/")) InnertubeEndpoint.forgetAccountLists();
            if (path.equals("subscription/subscribe") && SUBSCRIBED.add(value)) subscriptionsVersion++;
            if (path.equals("subscription/unsubscribe") && SUBSCRIBED.remove(value)) subscriptionsVersion++;
        } catch (Exception e) {
            android.util.Log.w(TAG, path + " " + value + " failed: " + e);
        }
    }

    /**
     * The "Save to playlist" list (GetAddToPlaylistResponse, adeo of YouTube 12.45.56): contents = 2
     * {addToPlaylistRenderer 88978004: {playlists = 1 {playlistAddToOptionRenderer 74079946:
     * {playlistId = 1, title = 2}}}}, filled from youtube.com's own list of the account's playlists.
     * Tapping one makes the app send browse/edit_playlist with ACTION_ADD_VIDEO itself (han).
     */
    private static byte[] addToPlaylist(byte[] body, Config config) {
        List<String> ids = strings(body, VIDEO_ID);
        if (!YouTubeAuth.signedIn() || ids.isEmpty()) return null;
        ProtoWriter renderer = new ProtoWriter();                      // abpc
        try {
            JSONObject payload = InnertubeEndpoint.context(config)
                    .put("videoIds", new JSONArray().put(ids.get(0)))
                    .put("excludeWatchLater", false);
            JSONObject answer = InnertubeEndpoint.request("playlist/get_add_to_playlist", payload, config);
            List<JSONObject> options = new ArrayList<JSONObject>();
            collect(answer, "playlistAddToOptionRenderer", options);
            for (JSONObject option : options) {
                String id = option.optString("playlistId", "");
                if (id.isEmpty()) continue;
                ProtoWriter entry = new ProtoWriter();                 // aerw
                entry.writeString(1, id);
                entry.writeBytes(2, formatted(text(option.opt("title"))));
                ProtoWriter union = new ProtoWriter();                 // abpb
                union.writeBytes(74079946, entry.toByteArray());
                renderer.writeBytes(1, union.toByteArray());
            }
            android.util.Log.i(TAG, "add to playlist: " + options.size() + " playlists");
        } catch (Exception e) {
            android.util.Log.w(TAG, "get_add_to_playlist failed: " + e);
            return null;
        }
        ProtoWriter contents = new ProtoWriter();                      // adep
        contents.writeBytes(88978004, renderer.toByteArray());
        ProtoWriter response = new ProtoWriter();                      // adeo
        response.writeBytes(2, contents.toByteArray());
        return response.toByteArray();
    }

    /**
     * The app's "New playlist" dialog (CreatePlaylistRequest acmy of 12.45.56: title = 2, videoIds
     * = 3, privacy = 4 - 0 when "Private" is ticked, 1 when not). Answered with
     * CreatePlaylistResponse acmz {playlistId = 2}.
     */
    private static byte[] createPlaylist(byte[] body, Config config) {
        if (!YouTubeAuth.signedIn()) return DONE;
        String title = "";
        long privacy = 0;
        List<String> videoIds = new ArrayList<String>();
        try {
            int i = 0;
            while (i < body.length) {
                long[] tag = varint(body, i);
                i = (int) tag[1];
                int field = (int) (tag[0] >>> 3);
                int wire = (int) (tag[0] & 7);
                if (wire == 0) {
                    long[] value = varint(body, i);
                    i = (int) value[1];
                    if (field == 4) privacy = value[0];
                } else if (wire == 2) {
                    long[] length = varint(body, i);
                    int from = (int) length[1];
                    int to = from + (int) length[0];
                    String text = new String(body, from, to - from, "UTF-8");
                    if (field == 2) title = text;
                    if (field == 3) videoIds.add(text);
                    i = to;
                } else if (wire == 1) {
                    i += 8;
                } else if (wire == 5) {
                    i += 4;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "playlist/create: unreadable request: " + e);
        }
        if (title.isEmpty()) return DONE;
        try {
            JSONArray videos = new JSONArray();
            for (String id : videoIds) videos.put(id);
            JSONObject payload = InnertubeEndpoint.context(config)
                    .put("title", title)
                    .put("privacyStatus", privacy == 1 ? "PUBLIC" : "PRIVATE")
                    .put("videoIds", videos);
            JSONObject answer = InnertubeEndpoint.request("playlist/create", payload, config);
            InnertubeEndpoint.forgetAccountLists();
            String id = answer.optString("playlistId", "");
            android.util.Log.i(TAG, "playlist/create \"" + title + "\": " + id);
            ProtoWriter response = new ProtoWriter();                  // acmz
            if (!id.isEmpty()) response.writeString(2, id);
            byte[] bytes = response.toByteArray();
            return bytes.length == 0 ? DONE : bytes;
        } catch (Exception e) {
            android.util.Log.w(TAG, "playlist/create failed: " + e);
            return DONE;
        }
    }

    /**
     * Liking or disliking a post (or undoing it): PerformCommentActionRequest acee of 12.45.56
     * carries youtube.com's own action tokens at field 2, as the proxy put them in the post's
     * buttons; they go back to youtube.com unchanged.
     */
    private static void performCommentAction(byte[] body, Config config) {
        if (!YouTubeAuth.signedIn()) return;
        List<String> actions = topLevelStrings(body, 2);
        if (actions.isEmpty()) {
            android.util.Log.w(TAG, "perform_comment_action: no action in the request");
            return;
        }
        try {
            JSONArray list = new JSONArray();
            for (String action : actions) list.put(action);
            InnertubeEndpoint.request("comment/perform_comment_action",
                    InnertubeEndpoint.context(config).put("actions", list), config);
            android.util.Log.i(TAG, "perform_comment_action: done (" + actions.size() + ")");
        } catch (Exception e) {
            android.util.Log.w(TAG, "perform_comment_action failed: " + e);
        }
    }

    /** The strings of one top-level field of a protobuf message. */
    private static List<String> topLevelStrings(byte[] body, int wanted) {
        List<String> found = new ArrayList<String>();
        try {
            int i = 0;
            while (i < body.length) {
                long[] tag = varint(body, i);
                i = (int) tag[1];
                int field = (int) (tag[0] >>> 3);
                int wire = (int) (tag[0] & 7);
                if (wire == 0) {
                    i = (int) varint(body, i)[1];
                } else if (wire == 2) {
                    long[] length = varint(body, i);
                    int from = (int) length[1];
                    int to = from + (int) length[0];
                    if (field == wanted) found.add(new String(body, from, to - from, "UTF-8"));
                    i = to;
                } else if (wire == 1) {
                    i += 8;
                } else if (wire == 5) {
                    i += 4;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            // What was read stands.
        }
        return found;
    }

    /** The app's browse/edit_playlist: adds the video to the playlist on the account. */
    private static void editPlaylist(byte[] body, Config config) {
        List<String> playlists = strings(body, PLAYLIST_ID);
        List<String> videos = strings(body, VIDEO_ID);
        if (!YouTubeAuth.signedIn() || playlists.isEmpty() || videos.isEmpty()) {
            android.util.Log.w(TAG, "edit_playlist: no playlist or video in the request");
            return;
        }
        try {
            JSONObject payload = InnertubeEndpoint.context(config)
                    .put("playlistId", playlists.get(0))
                    .put("actions", new JSONArray().put(new JSONObject()
                            .put("action", "ACTION_ADD_VIDEO")
                            .put("addedVideoId", videos.get(0))));
            InnertubeEndpoint.request("browse/edit_playlist", payload, config);
            InnertubeEndpoint.forgetAccountLists();
            android.util.Log.i(TAG, "edit_playlist " + playlists.get(0) + " +" + videos.get(0) + ": done");
        } catch (Exception e) {
            android.util.Log.w(TAG, "edit_playlist failed: " + e);
        }
    }

    private static void collect(Object node, String key, List<JSONObject> out) {
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) collect(array.opt(i), key, out);
        } else if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            JSONObject found = object.optJSONObject(key);
            if (found != null) out.add(found);
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                if (!name.equals(key)) collect(object.opt(name), key, out);
            }
        }
    }

    /** simpleText or the joined runs of a youtube.com text object. */
    private static String text(Object node) {
        if (!(node instanceof JSONObject)) return node == null ? "" : String.valueOf(node);
        JSONObject object = (JSONObject) node;
        if (object.has("simpleText")) return object.optString("simpleText", "");
        JSONArray runs = object.optJSONArray("runs");
        StringBuilder out = new StringBuilder();
        for (int i = 0; runs != null && i < runs.length(); i++) {
            out.append(runs.optJSONObject(i).optString("text", ""));
        }
        return out.toString();
    }

    /** addp FormattedString with one run: {runs = 1 {text = 1}}. */
    private static byte[] formatted(String text) {
        ProtoWriter run = new ProtoWriter();
        run.writeString(1, text);
        ProtoWriter string = new ProtoWriter();
        string.writeBytes(1, run.toByteArray());
        return string.toByteArray();
    }

    /** Every string anywhere in a protobuf message that matches {@code pattern} entirely. */
    static List<String> strings(byte[] message, Pattern pattern) {
        List<String> found = new ArrayList<String>();
        collect(message, 0, message.length, pattern, found, 0);
        return found;
    }

    private static void collect(byte[] b, int start, int end, Pattern pattern, List<String> out, int depth) {
        if (depth > 12) return;
        int i = start;
        try {
            while (i < end) {
                long[] tag = varint(b, i);
                i = (int) tag[1];
                int wire = (int) (tag[0] & 7);
                if (wire == 0) {
                    i = (int) varint(b, i)[1];
                } else if (wire == 1) {
                    i += 8;
                } else if (wire == 5) {
                    i += 4;
                } else if (wire == 2) {
                    long[] length = varint(b, i);
                    int from = (int) length[1];
                    int to = from + (int) length[0];
                    if (length[0] < 0 || to > end) return;
                    String text = printable(b, from, to);
                    if (text != null) {
                        if (pattern.matcher(text).matches()) out.add(text);
                    } else {
                        collect(b, from, to, pattern, out, depth + 1);
                    }
                    i = to;
                } else {
                    return;
                }
            }
        } catch (RuntimeException e) {
            // Not a message after all (a bytes field); whatever was found so far stands.
        }
    }

    /** The bytes as text when they are all printable ASCII, else null. */
    private static String printable(byte[] b, int from, int to) {
        if (to <= from) return null;
        for (int k = from; k < to; k++) {
            if (b[k] < 0x20 || b[k] > 0x7e) return null;
        }
        return new String(b, from, to - from);
    }

    /** {value, index after it}. */
    private static long[] varint(byte[] b, int i) {
        long value = 0;
        int shift = 0;
        while (true) {
            int x = b[i++] & 0xff;
            value |= (long) (x & 0x7f) << shift;
            if ((x & 0x80) == 0) break;
            shift += 7;
            if (shift > 63) throw new IllegalArgumentException("varint");
        }
        return new long[]{value, i};
    }
}
