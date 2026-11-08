package com.viitube.proxy;

import com.viitube.proxy.proto.AndroidFullNextProto;
import com.viitube.proxy.proto.LikeProto;
import com.viitube.proxy.proto.AndroidGuideProto;
import com.viitube.proxy.proto.AndroidPlayerProto;
import com.viitube.proxy.proto.AndroidResolveProto;
import com.viitube.proxy.proto.AndroidSearchProto;
import com.viitube.proxy.proto.VideoListProto;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Builds the protobuf answers of the Innertube API used by the 2015-2016 YouTube Android apps
 * (/youtubei/v1/browse, next, player, search, guide, navigation/resolve_url). The message
 * layouts and the values the apps expect come from the gdatav2innertube server; this class only
 * turns data into messages and does no network access, so it can be tested on a desktop JVM.
 */
final class YoutubeiResponses {

    static final String DEFAULT_AVATAR = "https://yt3.ggpht.com/a/default-user=s88-c-k-c0x00ffffff-no-rj";

    /**
     * Apps from this version on get the classic bottom bar (Home, Trending, Library, Account)
     * instead of tabs above the feed, and the watch page of the 2017 apps.
     */
    static final int PIVOT_BAR_VERSION = 12;

    // IconType values of the 2017 apps (the enum of YouTube 12.45.56). Only the ones the app has
    // a drawable for are used; it draws an unknown icon as an empty image.
    private static final int ICON_MIX = 17;
    private static final int ICON_DISLIKE = 51;
    private static final int ICON_TAB_TRENDING = 67;
    private static final int ICON_TAB_ACCOUNT = 69;
    private static final int ICON_LIKE = 160;
    private static final int ICON_LIKE_SELECTED = 301;
    private static final int ICON_DISLIKE_SELECTED = 302;
    /** SHARE_ARROW: the curved arrow (ic_share); SHARE (48) is the three dots instead. */
    private static final int ICON_SHARE = 100;
    private static final int ICON_ADDTO = 46;
    /** The circled arrow of the Download button, which the apps from version 14 have a row for. */
    private static final int ICON_DOWNLOAD = 147;
    /** The bottom bar's own icons */
    private static final int ICON_PIVOT_SUBSCRIPTIONS = 408;
    private static final int ICON_PIVOT_HOME = 406;
    private static final int ICON_PIVOT_TRENDING = 407;
    private static final int ICON_PIVOT_LIBRARY = 410;

    private YoutubeiResponses() { }

    // ------------------------------------------------------------------ text formatting

    /**
     * The language of the response being built, set once per request from the phone's language, so
     * the labels the proxy writes itself (not the data YouTube sends) match the rest of the app.
     */
    private static final ThreadLocal<String> LANGUAGE = new ThreadLocal<String>();

    static void setLanguage(String hl) {
        LANGUAGE.set(hl == null ? "" : hl.toLowerCase(Locale.US));
    }

    private static boolean isPolish() {
        String language = LANGUAGE.get();
        return language != null && language.startsWith("pl");
    }

    /** One of the fixed labels the proxy writes, in the request's language (English otherwise). */
    static String tr(String english) {
        if (!isPolish()) return english;
        if ("Sort by".equals(english)) return "Sortuj według";
        if ("Upload".equals(english)) return "Prześlij";
        if ("Save to playlist".equals(english)) return "Zapisz na playliście";
        if ("Play all".equals(english)) return "Odtwórz wszystko";
        if ("Videos only".equals(english)) return "Tylko filmy";
        if ("Music".equals(english)) return "Muzyka";
        if ("Subscriptions".equals(english)) return "Subskrypcje";
        if ("Gaming".equals(english)) return "Gry";
        if ("News".equals(english)) return "Wiadomości";
        if ("Movies".equals(english)) return "Filmy";
        if ("Top comments".equals(english)) return "Najlepsze komentarze";
        if ("Add a public comment...".equals(english)) return "Dodaj publiczny komentarz...";
        if ("Newest first".equals(english)) return "Najpierw najnowsze";
        if ("Playlists".equals(english)) return "Playlisty";
        if ("New playlist".equals(english)) return "Nowa playlista";
        if ("Watch later".equals(english)) return "Do obejrzenia";
        if ("Liked videos".equals(english)) return "Filmy, które mi się podobają";
        if ("Search".equals(english)) return "Szukaj";
        if ("My channel".equals(english)) return "Mój kanał";
        if ("Switch account".equals(english)) return "Przełącz konto";
        if ("Settings".equals(english)) return "Ustawienia";
        if ("Sign out".equals(english)) return "Wyloguj się";
        if ("Uploads".equals(english)) return "Przesłane";
        if ("Most popular".equals(english)) return "Najpopularniejsze";
        if ("Date added (oldest)".equals(english)) return "Data dodania (najstarsze)";
        if ("Date added (newest)".equals(english)) return "Data dodania (najnowsze)";
        if ("views".equals(english)) return "wyświetleń";
        if ("view".equals(english)) return "wyświetlenie";
        if ("Up next".equals(english)) return "Następny";
        if ("Comments".equals(english)) return "Komentarze";
        if ("Autoplay".equals(english)) return "Autoodtwarzanie";
        if ("Like".equals(english)) return "Lubię";
        if ("Dislike".equals(english)) return "Nie lubię";
        if ("Share".equals(english)) return "Udostępnij";
        if ("Download".equals(english)) return "Pobierz";
        if ("likes".equals(english)) return "polubień";
        if ("About".equals(english)) return "Informacje";
        if ("Channels".equals(english)) return "Kanały";
        if ("dislikes".equals(english)) return "nie lubi\u0119";
        if ("comments".equals(english)) return "komentarzy";
        return english;
    }

    /** "1.2M views", like formatViewCount() in the reference server. */
    static String shortCount(long count, String unit) {
        unit = tr(unit);
        if (count >= 1_000_000_000L) return oneDecimal(count / 1_000_000_000d) + "B " + unit;
        if (count >= 1_000_000L) return oneDecimal(count / 1_000_000d) + "M " + unit;
        if (count >= 1_000L) return oneDecimal(count / 1_000d) + "K " + unit;
        return count + " " + unit;
    }

    /** "1.2M", the label YouTube put on the like button. */
    static String shortNumber(long count) {
        return shortCount(count, "").trim();
    }

    private static String oneDecimal(double value) {
        String text = String.format(Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /** "1,234,567 views" / "1 view", the long form YouTube showed under videos. */
    static String fullCount(long count, String singular, String plural) {
        return String.format(Locale.US, "%,d", count) + " " + tr(count == 1 ? singular : plural);
    }

    static long parseCount(String value) {
        try {
            return Long.parseLong(InnertubeEndpoint.numericCount(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** m:ss or h:mm:ss; empty when the length is unknown (live streams). */
    static String lengthText(String duration) {
        int seconds = InnertubeEndpoint.durationSecondsPublic(duration);
        if (seconds <= 0) return "";
        int hours = seconds / 3600;
        int minutes = seconds % 3600 / 60;
        int rest = seconds % 60;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest);
        return String.format(Locale.US, "%d:%02d", minutes, rest);
    }

    /** "3 days ago" for feed items; YouTube's relative labels are passed through. */
    static String relativeTime(String published, long now) {
        if (published == null || published.trim().isEmpty()) return "";
        String text = published.trim();
        // Related videos use compact labels ("1d ago", "2mo ago"); the apps showed "1 day ago".
        boolean compact = COMPACT_AGE.matcher(text).matches();
        if (!compact && !text.matches("\\d{4}-\\d{2}-\\d{2}.*")) return text;
        long time = parseAtomDate(InnertubeEndpoint.toAtomDate(text));
        if (time <= 0) return "";
        long minutes = Math.max(0, (now - time) / 60_000L);
        if (minutes < 60) return ago(Math.max(1, minutes), "minute");
        long hours = minutes / 60;
        if (hours < 24) return ago(hours, "hour");
        long days = hours / 24;
        if (days < 7) return ago(days, "day");
        if (days < 30) return ago(days / 7, "week");
        if (days < 365) return ago(days / 30, "month");
        return ago(days / 365, "year");
    }

    private static final java.util.regex.Pattern COMPACT_AGE =
            java.util.regex.Pattern.compile("\\d+\\s*(?:s|m|h|d|w|mo|y) ago");

    /** "3 days ago" / "3 dni temu": the number, the unit, and the "ago" word, in the app's language. */
    private static String ago(long amount, String unit) {
        if (isPolish()) {
            String pl;
            if ("minute".equals(unit)) pl = "min";
            else if ("hour".equals(unit)) pl = "godz.";
            else if ("day".equals(unit)) pl = amount == 1 ? "dzień" : "dni";
            else if ("week".equals(unit)) pl = "tyg.";
            else if ("month".equals(unit)) pl = "mies.";
            else pl = "lat";
            return amount + " " + pl + " temu";
        }
        return amount + " " + unit + (amount == 1 ? "" : "s") + " ago";
    }

    /** "Published on Oct 3, 2024" from an ISO date, or from YouTube's own date label. */
    static String publishedOn(String published, String dateText) {
        if (published != null && published.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            long time = parseAtomDate(InnertubeEndpoint.toAtomDate(published));
            if (time > 0) {
                SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy", Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                return "Published on " + format.format(new java.util.Date(time));
            }
        }
        if (dateText != null && !dateText.isEmpty()) {
            return dateText.matches("[A-Z][a-z]{2} \\d{1,2}, \\d{4}") ? "Published on " + dateText : dateText;
        }
        return "";
    }

    private static long parseAtomDate(String atom) {
        try {
            return InnertubeEndpoint.ATOM_DATE.get().parse(atom).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String hqThumbnail(String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }

    /** The wide picture the feed cards show; YouTube falls back to the smaller file itself. */
    private static String maxresThumbnail(String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg";
    }

    private static String avatarOrDefault(String avatar) {
        return avatar == null || avatar.isEmpty() ? DEFAULT_AVATAR : avatar;
    }

    /**
     * The same picture at 240 px: YouTube's avatar addresses carry their size ("=s88-c-k-..." or
     * "/s88-c-k/"), and the channel header otherwise scales up an 88 px one.
     */
    static String largeAvatar(String url) {
        if (url == null) return "";
        return url.replaceFirst("([=/])s\\d+(-|$|/)", "$1s240$2");
    }

    // ------------------------------------------------------------------ browse (video_list_proto)

    /** A line the app draws in bold, like the subscribe button's own label. */
    private static VideoListProto.TextRuns browseRunsBold(String text) {
        return new VideoListProto.TextRuns().addRun(
                new VideoListProto.TextRuns.TextRun().setText(text).setBold(1));
    }

    private static VideoListProto.TextRuns browseRuns(String text) {
        return new VideoListProto.TextRuns().addRun(new VideoListProto.TextRuns.TextRun().setText(text));
    }

    private static VideoListProto.Thumbnails browseThumbnails(String url, int width, int height) {
        return new VideoListProto.Thumbnails().addThumb(new VideoListProto.Thumbnails.Thumbnail()
                .setUrl(url).setWidth(width).setHeight(height));
    }

    private static VideoListProto.BrowseNavigation browseNavigation(String browseId, String params,
                                                                   String canonicalBaseUrl) {
        VideoListProto.BrowseNavigation.BrowseData data = new VideoListProto.BrowseNavigation.BrowseData()
                .setBrowseId(browseId);
        if (params != null && !params.isEmpty()) data.setParams(params);
        if (canonicalBaseUrl != null && !canonicalBaseUrl.isEmpty()) data.setCanonicalBaseUrl(canonicalBaseUrl);
        return new VideoListProto.BrowseNavigation().setData(data);
    }

    private static VideoListProto.AuthorData browseAuthor(String name, String channelId) {
        VideoListProto.AuthorData.AuthorDataContent content = new VideoListProto.AuthorData.AuthorDataContent()
                .setAuthorDisplayName(name);
        if (channelId != null && !channelId.isEmpty()) {
            content.setNavigation(browseNavigation(channelId, null, "/channel/" + channelId));
        }
        return new VideoListProto.AuthorData().setAc(content);
    }

    /** generateVideo() of the reference server. */
    static VideoListProto.CompactVideoRenderer browseVideo(InnertubeEndpoint.Video video,
                                                          String authorAvatar, long now) {
        long views = parseCount(video.views);
        VideoListProto.AuthorData author = browseAuthor(video.channel, video.channelId);
        return new VideoListProto.CompactVideoRenderer()
                .setVideoId(video.id)
                .setThumblist(browseThumbnails(hqThumbnail(video.id), 480, 360))
                .setTitleData(browseRuns(video.title))
                .setPublishedTimeText(browseRuns(relativeTime(video.published, now)))
                .setViewCountText(browseRuns(fullCount(views, "view", "views")))
                .setLengthText(browseRuns(lengthText(video.duration)))
                .setShortViewCounts(browseRuns(shortCount(views, "views")))
                .setShortAuthorData(author)
                .setLongAuthorData(author)
                .setNavData(new VideoListProto.NavigationData().setNavProperties(
                        new VideoListProto.NavigationData.NavData().setNavId(video.id)))
                .setAuthorAvatar(browseThumbnails(avatarOrDefault(authorAvatar), 88, 88));
    }

    /** generatePlaylist() of the reference server. */
    static VideoListProto.PlaylistRenderer browsePlaylist(String id, String title, String author,
                                                         String authorId, String count,
                                                         String firstVideoId, String thumbnail) {
        String thumbUrl = firstVideoId != null && !firstVideoId.isEmpty() ? hqThumbnail(firstVideoId)
                : thumbnail != null && !thumbnail.isEmpty() ? InnertubeEndpoint.absoluteUrl(thumbnail)
                : DEFAULT_AVATAR;
        VideoListProto.AuthorData owner = browseAuthor(author, authorId);
        String videoCount = count + " videos";
        return new VideoListProto.PlaylistRenderer()
                .setId(id)
                .setPlaylistName(browseRuns(title))
                .setThumbnail(browseThumbnails(thumbUrl, 480, 360))
                .setSeparatedThumbnail(browseThumbnails(thumbUrl, 480, 360))
                .setShortOwner(owner)
                .setLongOwner(owner)
                .setVideoCount(browseRuns(videoCount))
                .setVideoCount2(browseRuns(videoCount))
                .setPlaylistBrowse(browseNavigation("VL" + id, null, null))
                .setBareVideoCount(browseRuns(count))
                .setWebUrl("https://www.youtube.com/playlist?list=" + id);
    }

    private static VideoListProto.Root browseRoot() {
        VideoListProto.Root.ContextType.ClientType client = new VideoListProto.Root.ContextType.ClientType()
                .setService(6)
                .addParam(new VideoListProto.Root.ContextType.ClientType.ClientParam()
                        .setName("client.name").setValue("ANDROID"))
                .addParam(new VideoListProto.Root.ContextType.ClientType.ClientParam()
                        .setName("client.version").setValue("1.0"));
        return new VideoListProto.Root().addContext(new VideoListProto.Root.ContextType()
                .addClient(client).setMaxAgeSeconds(300));
    }

    private static VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs tab(
            String identifier, String title, boolean selected, int icon,
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                    .ContentEntry.ContentData.ContentActualData content) {
        return tab(identifier, title, selected, icon, content, null);
    }

    /**
     * One tab. With a continuation token the app loads the next entries when the list is
     * scrolled to its end and sends the token back with the browse request.
     */
    private static VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs tab(
            String identifier, String title, boolean selected, int icon,
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                    .ContentEntry.ContentData.ContentActualData content, String continuationToken) {
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer renderer =
                new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer()
                        .addContentsEntry(new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer
                                .Tabs.TabRenderer.ContentEntry().addContentArray(
                                        sectionList(content, continuationToken)))
                        .setSelected(selected ? 1 : 0)
                        .setTabIdentifier(identifier)
                        .setTabTitle(title);
        if (icon > 0) {
            renderer.addIconData(new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer
                    .Tabs.TabRenderer.Icon().setIconType(icon));
        }
        return new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs().addTabRender(renderer);
    }

    /** The section list of a tab, with the token for its next page when there is one. */
    private static VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer.ContentEntry.ContentData sectionList(
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer.ContentEntry.ContentData.ContentActualData content, String continuationToken) {
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer.ContentEntry.ContentData data =
                new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer.ContentEntry.ContentData().addC(content);
        if (continuationToken != null && !continuationToken.isEmpty()) {
            data.addLoadCommand(new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer.ContentEntry.ContentData.LoadData().addNext(
                    new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer.ContentEntry.ContentData.LoadData.NextContinuationData()
                            .setToken(continuationToken)));
        }
        return data;
    }

    /** One item section holding every video, as the channel tabs and their next pages use. */
    private static VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer.ContentEntry.ContentData.ContentActualData videoSection(
            List<InnertubeEndpoint.Video> videos, String authorAvatar, long now) {
        VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
        for (InnertubeEndpoint.Video video : videos) {
            section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents()
                    .addVideo(browseVideo(video, authorAvatar, now)));
        }
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer.ContentEntry.ContentData.ContentActualData content = tabContent();
        content.addSection(section);
        return content;
    }

    /**
     * The answer to a browse request with a continuation token: more videos for the list the app
     * is showing, and the token for the page after them.
     */
    static byte[] browseVideosContinuation(List<InnertubeEndpoint.Video> videos, String authorAvatar,
                                           long now, String continuationToken) {
        return new VideoListProto.Root().setContinuation(new VideoListProto.Root.ContinuationContents()
                .addSectionListContinuation(sectionList(videoSection(videos, authorAvatar, now),
                        continuationToken))).toByteArray();
    }

    /** Sort parameter of a channel browse: opens the Videos tab in that order (see YoutubeiEndpoint). */
    static final String SORT_PARAMS = "vtpsort:";

    /**
     * The Videos tab's "Sort by" menu: newest first, most popular, oldest first; each choice opens
     * the channel again with its Videos tab sorted that way.
     */
    static com.viitube.proxy.proto.SortMenuProto sortMenu(String channelId, int selected) {
        com.viitube.proxy.proto.ProtoMessage[] endpoints = new com.viitube.proxy.proto.ProtoMessage[3];
        for (int i = 0; i < 3; i++) endpoints[i] = browseNavigation(channelId, SORT_PARAMS + i, "/channel/" + channelId);
        return new com.viitube.proxy.proto.SortMenuProto(tr("Sort by"),
                new String[]{tr("Date added (newest)"), tr("Most popular"), tr("Date added (oldest)")},
                endpoints, selected);
    }

    /** Videos for a reloaded or continued Videos tab, with the sort menu when it was reloaded. */
    static byte[] browseVideosContinuation(List<InnertubeEndpoint.Video> videos, String authorAvatar,
                                           long now, String continuationToken,
                                           com.viitube.proxy.proto.ProtoMessage sortMenu) {
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData data = sectionList(videoSection(videos, authorAvatar, now),
                continuationToken);
        if (sortMenu != null) data.getC(0).getSection(0).setHeader(sortMenu);
        return new VideoListProto.Root().setContinuation(new VideoListProto.Root.ContinuationContents()
                .addSectionListContinuation(data)).toByteArray();
    }

    /** More posts of a channel's Community tab, as the answer to a continuation request. */
    static byte[] communityContinuation(List<ChannelEndpoint.Post> posts, String continuationToken) {
        VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
        for (ChannelEndpoint.Post post : posts) {
            section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents()
                    .addPost(communityPost(post)));
        }
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData content = tabContent();
        content.addSection(section);
        return new VideoListProto.Root().setContinuation(new VideoListProto.Root.ContinuationContents()
                .addSectionListContinuation(sectionList(content, continuationToken))).toByteArray();
    }

    /** More playlists of a channel, as the answer to a continuation request. */
    static byte[] playlistsContinuation(List<ChannelEndpoint.PlaylistSummary> playlists,
                                        String channelName, String channelId,
                                        String continuationToken) {
        VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
        for (ChannelEndpoint.PlaylistSummary playlist : playlists) {
            section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents().addPlaylist(
                    browsePlaylist(playlist.id, playlist.title, channelName, channelId,
                            playlist.count, playlist.firstVideoId, playlist.thumbnail)));
        }
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData content = tabContent();
        content.addSection(section);
        return new VideoListProto.Root().setContinuation(new VideoListProto.Root.ContinuationContents()
                .addSectionListContinuation(sectionList(content, continuationToken))).toByteArray();
    }

    private static VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
            .ContentEntry.ContentData.ContentActualData tabContent() {
        return new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData();
    }

    /**
     * The card the 2017 apps show in the home and Trending feeds: a wide thumbnail with the
     * duration over it, the title under it and one line of "Channel - views - age". The compact
     * renderer below is what the 2015-2016 apps use, and it is what those feeds keep.
     */
    static VideoListProto.VideoWithContextRenderer browseFeedVideo(InnertubeEndpoint.Video video,
                                                                   String authorAvatar, long now) {
        StringBuilder byline = new StringBuilder();
        if (!video.channel.isEmpty()) byline.append(video.channel);
        long views = parseCount(video.views);
        if (views > 0) {
            if (byline.length() > 0) byline.append(" \u2022 ");
            byline.append(shortCount(views, "views"));
        }
        String age = relativeTime(video.published, now);
        if (!age.isEmpty()) {
            if (byline.length() > 0) byline.append(" \u2022 ");
            byline.append(age);
        }
        VideoListProto.VideoWithContextRenderer card = new VideoListProto.VideoWithContextRenderer()
                .setVideoId(video.id)
                .setTitle(browseRuns(video.title))
                .setByline(browseRuns(byline.toString()))
                // maxresdefault first; not every upload has one, and the smaller file listed
                // after it is what the app loads when the big one is missing.
                .setThumbnail(new VideoListProto.Thumbnails()
                        .addThumb(new VideoListProto.Thumbnails.Thumbnail()
                                .setUrl(maxresThumbnail(video.id)).setWidth(1280).setHeight(720))
                        .addThumb(new VideoListProto.Thumbnails.Thumbnail()
                                .setUrl(hqThumbnail(video.id)).setWidth(480).setHeight(360)))
                .setNavData(new VideoListProto.NavigationData().setNavProperties(
                        new VideoListProto.NavigationData.NavData().setNavId(video.id)));
        VideoListProto.VideoWithContextRenderer.ChannelAvatar.Avatar avatar =
                new VideoListProto.VideoWithContextRenderer.ChannelAvatar.Avatar()
                        .setThumbnail(browseThumbnails(avatarOrDefault(
                                video.channelAvatar.isEmpty() ? authorAvatar : video.channelAvatar),
                                88, 88));
        if (!video.channelId.isEmpty()) {
            avatar.setNavigation(browseNavigation(video.channelId, null, "/channel/" + video.channelId));
        }
        card.setAvatar(new VideoListProto.VideoWithContextRenderer.ChannelAvatar().addImage(avatar));
        String length = lengthText(video.duration);
        if (!length.isEmpty()) {
            card.setDuration(browseRuns(length));
            card.addOverlay(new VideoListProto.VideoWithContextRenderer.Overlays().addTime(
                    new VideoListProto.VideoWithContextRenderer.Overlays.TimeStatus()
                            .addText(browseRuns(length))));
        }
        return card;
    }

    /** One item section per video, as on the reference server's home and trending tabs. */
    private static VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
            .ContentEntry.ContentData.ContentActualData videoSections(List<InnertubeEndpoint.Video> videos,
                                                                       long now) {
        return videoSections(videos, now, false, NO_AVATARS);
    }

    private static final java.util.Map<String, String> NO_AVATARS =
            java.util.Collections.unmodifiableMap(new java.util.HashMap<String, String>());

    private static VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
            .ContentEntry.ContentData.ContentActualData videoSections(List<InnertubeEndpoint.Video> videos,
                                                                      long now, boolean feedCards,
                                                                      java.util.Map<String, String> avatars) {
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData content = tabContent();
        for (InnertubeEndpoint.Video video : videos) {
            VideoListProto.ItemSectionRenderer.IcsContents item =
                    new VideoListProto.ItemSectionRenderer.IcsContents();
            if (feedCards) item.addFeedVideo(browseFeedVideo(video, avatars.get(video.channelId), now));
            else item.addVideo(browseVideo(video, null, now));
            content.addSection(new VideoListProto.ItemSectionRenderer().addIcsContent(item));
        }
        return content;
    }

    private static byte[] finishBrowse(VideoListProto.Root root,
                                       VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer tabs,
                                       VideoListProto.Root.HeaderRender header) {
        root.addContents(new VideoListProto.Root.ResponseBody().addBrowseData(tabs));
        if (header != null) root.addHeader(header);
        return root.toByteArray();
    }

    private static VideoListProto.Root.HeaderRender textHeader(String text) {
        return new VideoListProto.Root.HeaderRender().addContent(
                new VideoListProto.Root.HeaderRender.HeaderContent().addText(browseRuns(text)));
    }

    /**
     * FEwhat_to_watch. Apps from version 12 get a single Home tab, because they navigate with
     * the bottom bar of the guide response and leave out the tab strip when a browse answer has
     * one tab; version 11 shows tabs (Home, Trending, Account) above the feed, and older ones
     * get Home and Music tabs under a "What to watch" header.
     */
    static byte[] home(int clientMajorVersion, List<InnertubeEndpoint.Video> home,
                       List<InnertubeEndpoint.Video> second, long now,
                       java.util.Map<String, String> avatars) {
        boolean v11Tabs = clientMajorVersion >= 11;
        VideoListProto.Root root = browseRoot();
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer tabs =
                new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer();
        if (clientMajorVersion >= PIVOT_BAR_VERSION) {
            tabs.addTab(tab("FEwhat_to_watch", "Home", true, 0, videoSections(home, now, true, avatars)));
            return finishBrowse(root, tabs, null);
        }
        tabs.addTab(tab("FEwhat_to_watch", "Home", true, v11Tabs ? 65 : 0, videoSections(home, now)));
        if (!v11Tabs) {
            tabs.addTab(tab("FEmusic", "Music", false, 0, videoSections(second, now)));
            return finishBrowse(root, tabs, textHeader("What to watch"));
        }
        tabs.addTab(tab("FEtrending", "Trending", false, 67, videoSections(second, now)));
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData account = tabContent();
        account.addSection(new VideoListProto.ItemSectionRenderer().addIcsContent(
                new VideoListProto.ItemSectionRenderer.IcsContents().addAccountLink(
                        new VideoListProto.AccountTabLink()
                                .setIconData(new VideoListProto.AccountTabLink.Icon().setIconType(69))
                                .addText(browseRuns("Account")))));
        tabs.addTab(tab("FEaccount", "Account", false, 69, account));
        return finishBrowse(root, tabs, textHeader("YouTube"));
    }

    private static VideoListProto.AccountTabLink browseLink(String text, int icon, String browseId) {
        VideoListProto.AccountTabLink link = new VideoListProto.AccountTabLink()
                .setIconData(new VideoListProto.AccountTabLink.Icon().setIconType(icon))
                .addText(browseRuns(text));
        if (browseId != null) link.setNavigation(browseNavigation(browseId, null, null));
        return link;
    }

    /** One tab of link rows, as the Library and Account pages of the bottom bar are built. */
    private static byte[] linkPage(String identifier, String title,
                                   List<VideoListProto.AccountTabLink> links) {
        VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
        for (VideoListProto.AccountTabLink link : links) {
            section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents().addAccountLink(link));
        }
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData content = tabContent();
        content.addSection(section);
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer tabs =
                new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer()
                        .addTab(tab(identifier, title, true, 0, content));
        return finishBrowse(browseRoot(), tabs, textHeader(title));
    }

    // Icons of the Library rows (IconType numbers from the app's icon table, gsu).
    /** The blue "+" the proxy draws (IconEndpoint): the app's own "+" icons are white. */
    private static final String ADD_ICON_URL = "http://127.0.0.1:8080/proxy/icon/add.png";
    private static final int ICON_WATCH_LATER = 3;
    private static final int ICON_LIKES_PLAYLIST = 16;
    /** NavigationEndpoint field of createPlaylistServiceEndpoint (acmx): opens the app's dialog. */
    private static final int CREATE_PLAYLIST_ENDPOINT = 85650004;

    // The Library rows are compactLinkRenderers (acgl: icon 1, thumbnail 2, title 3, endpoint 4,
    // subtitle 13).

    /** FormattedString addp {runs = 1 {text = 1, textColor = 8 (ARGB)}}. */
    private static byte[] coloredText(String text, int argb) {
        com.viitube.proxy.proto.ProtoWriter run = new com.viitube.proxy.proto.ProtoWriter();  // afmq
        run.writeString(1, text);
        run.writeUInt32(8, argb);
        com.viitube.proxy.proto.ProtoWriter string = new com.viitube.proxy.proto.ProtoWriter();
        string.writeBytes(1, run.toByteArray());
        return string.toByteArray();
    }

    /** One Library row: {id, title, count, thumbnail}, with an icon or else the thumbnail. */
    private static VideoListProto.ItemSectionRenderer.IcsContents libraryRow(String[] playlist,
                                                                           String title, int icon) {
        VideoListProto.AccountTabLink link = new VideoListProto.AccountTabLink()
                .addText(browseRuns(title))
                .setSubtitle(browseRuns(videoCount(playlist[2])))
                .setNavigation(browseNavigation("VL" + playlist[0], null, null));
        if (icon != 0) {
            link.setIconData(new VideoListProto.AccountTabLink.Icon().setIconType(icon));
        } else {
            link.setImage(browseThumbnails(playlist[3].isEmpty() ? DEFAULT_PLAYLIST_THUMB : playlist[3],
                    120, 90));
        }
        return new VideoListProto.ItemSectionRenderer.IcsContents().addAccountLink(link);
    }

    private static final String DEFAULT_PLAYLIST_THUMB = "https://i.ytimg.com/img/no_thumbnail.jpg";

    /** "12 videos", "No videos"; Polish with its plural forms. */
    static String videoCount(String count) {
        String digits = count.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.equals("0")) return isPolish() ? "Brak filmów" : "No videos";
        long n;
        try {
            n = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return count;
        }
        String number = String.format(Locale.US, "%,d", n);
        if (!isPolish()) return number + (n == 1 ? " video" : " videos");
        number = number.replace(',', ' ');
        if (n == 1) return "1 film";
        long tens = n % 100;
        long ones = n % 10;
        if (ones >= 2 && ones <= 4 && (tens < 12 || tens > 14)) return number + " filmy";
        return number + " filmów";
    }

    /**
     * FElibrary for the signed-in account, as 12.x drew it: one "Playlists" section with "New
     * playlist" (blue, the app's own create dialog), Watch later and Liked videos with their
     * icons, then the account's playlists as compact rows (thumbnail, title, count).
     */
    static byte[] library(List<String[]> playlists) {
        if (playlists.isEmpty()) return library();
        String[] watchLater = null;
        String[] liked = null;
        for (String[] playlist : playlists) {
            if (playlist[0].equals("WL")) watchLater = playlist;
            else if (playlist[0].equals("LL")) liked = playlist;
        }
        VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
        section.setHeader(new com.viitube.proxy.proto.SortMenuProto.TitleHeader(tr("Playlists")));
        com.viitube.proxy.proto.ProtoWriter create = new com.viitube.proxy.proto.ProtoWriter(); // acdz
        create.writeBytes(CREATE_PLAYLIST_ENDPOINT, new byte[0]);
        section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents().addAccountLink(
                new VideoListProto.AccountTabLink()
                        .setImage(browseThumbnails(ADD_ICON_URL, 96, 96))
                        .setRawTitle(coloredText(tr("New playlist"), IconEndpoint.BLUE))
                        .setRawNavigation(create.toByteArray())));
        if (watchLater != null) section.addIcsContent(libraryRow(watchLater, tr("Watch later"), ICON_WATCH_LATER));
        if (liked != null) section.addIcsContent(libraryRow(liked, tr("Liked videos"), ICON_LIKES_PLAYLIST));
        for (String[] playlist : playlists) {
            if (playlist == watchLater || playlist == liked) continue;
            section.addIcsContent(libraryRow(playlist, playlist[1], 0));
        }
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData content = tabContent();
        content.addSection(section);
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer tabs =
                new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer()
                        .addTab(tab("FElibrary", tr("Library"), true, 0, content));
        return finishBrowse(browseRoot(), tabs, textHeader(tr("Library")));
    }

    /**
     * FElibrary, the third item of the bottom bar. Watch history, Watch later and playlists need
     * an account, so the page links to the feeds this proxy does serve.
     */
    static byte[] library() {
        List<VideoListProto.AccountTabLink> links = new java.util.ArrayList<VideoListProto.AccountTabLink>();
        links.add(browseLink("Popular on YouTube", ICON_TAB_TRENDING, "FEtrending"));
        links.add(browseLink("Music", ICON_MIX, "FEmusic"));
        return linkPage("FElibrary", "Library", links);
    }

    /** FEsubscriptions, the third item of the bottom bar. Subscriptions need an account. */
    static byte[] subscriptions() {
        List<VideoListProto.AccountTabLink> links = new java.util.ArrayList<VideoListProto.AccountTabLink>();
        links.add(browseLink("Subscriptions need an account, which the proxy cannot sign in to",
                ICON_PIVOT_SUBSCRIPTIONS, null));
        links.add(browseLink("Popular on YouTube", ICON_TAB_TRENDING, "FEtrending"));
        links.add(browseLink("Music", ICON_MIX, "FEmusic"));
        return linkPage("FEsubscriptions", "Subscriptions", links);
    }

    /** FEaccount. Signing in is not supported. */
    static byte[] account() {
        List<VideoListProto.AccountTabLink> links = new java.util.ArrayList<VideoListProto.AccountTabLink>();
        if (YouTubeAuth.signedIn()) {
            String name = YouTubeAuth.accountName();
            links.add(browseLink(name.isEmpty() ? "Signed in" : name, ICON_TAB_ACCOUNT, null));
            links.add(browseLink("Subscriptions", ICON_PIVOT_SUBSCRIPTIONS, "FEsubscriptions"));
        } else {
            links.add(browseLink("Signing in is not available", ICON_TAB_ACCOUNT, null));
        }
        links.add(browseLink("Popular on YouTube", ICON_TAB_TRENDING, "FEtrending"));
        links.add(browseLink("Music", ICON_MIX, "FEmusic"));
        return linkPage("FEaccount", "Account", links);
    }

    /** A single list of videos (FEtrending, FEmusic). */
    static byte[] videoList(String identifier, String title, List<InnertubeEndpoint.Video> videos,
                            long now) {
        return videoList(identifier, title, videos, now, false, NO_AVATARS);
    }

    /** With {@code feedCards} the videos are the wide cards of the 2017 apps' feeds. */
    static byte[] videoList(String identifier, String title, List<InnertubeEndpoint.Video> videos,
                            long now, boolean feedCards, java.util.Map<String, String> avatars) {
        return videoList(identifier, title, videos, now, feedCards, avatars, null);
    }

    /** The same, with the bar of the account's channels over the list (the Subscriptions feed). */
    static byte[] videoList(String identifier, String title, List<InnertubeEndpoint.Video> videos,
                            long now, boolean feedCards, java.util.Map<String, String> avatars,
                            List<String[]> channelBar) {
        com.viitube.proxy.proto.ChannelBarProto bar = null;
        com.viitube.proxy.proto.ProtoMessage header = null;
        if (channelBar != null && !channelBar.isEmpty()) {
            List<com.viitube.proxy.proto.ChannelBarProto.Channel> channels =
                    new java.util.ArrayList<com.viitube.proxy.proto.ChannelBarProto.Channel>();
            for (String[] channel : channelBar) {
                channels.add(new com.viitube.proxy.proto.ChannelBarProto.Channel(
                        channel[0], channel[1], avatarOrDefault(channel[2])));
            }
            // The Subscriptions feed of 12.x: the channels, "ALL" at the end of them (the list
            // of every subscription) and "Videos only" over the feed.
            bar = new com.viitube.proxy.proto.ChannelBarProto(channels).withAll(ALL_CHANNELS);
            String label = tr("Videos only");
            header = new com.viitube.proxy.proto.SortMenuProto(label, new String[]{label},
                    new com.viitube.proxy.proto.ProtoMessage[]{browseNavigation("FEsubscriptions", null, null)},
                    0).withoutIcon().header(label);
        }
        return videoList(identifier, title, videos, now, feedCards, avatars, bar, header);
    }

    /** The browse id of the list of every subscribed channel (the bar's "ALL"). */
    static final String ALL_CHANNELS = "FEchannels";

    /** With a bar over the list (channels, categories) and a header on its first section. */
    static byte[] videoList(String identifier, String title, List<InnertubeEndpoint.Video> videos,
                            long now, boolean feedCards, java.util.Map<String, String> avatars,
                            com.viitube.proxy.proto.ChannelBarProto bar,
                            com.viitube.proxy.proto.ProtoMessage sectionHeader) {
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs tab =
                tab(identifier, title, true, 0, videoSections(videos, now, feedCards, avatars));
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData data = tab.getTabRender(0).getContentsEntry(0).getContentArray(0);
        if (bar != null) data.setSubMenu(bar);
        if (sectionHeader != null && data.getCCount() > 0 && data.getC(0).getSectionCount() > 0) {
            data.getC(0).getSection(0).setHeader(sectionHeader);
        }
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer tabs =
                new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer().addTab(tab);
        return finishBrowse(browseRoot(), tabs, textHeader(title));
    }

    /** UC... channel page: header, Home (latest uploads shelf), Videos and Playlists tabs. */
    /** Apps from this version on get the Community tab of a channel. */
    static final int COMMUNITY_TAB_VERSION = 11;

    static byte[] channel(ChannelEndpoint.ChannelInfo info, List<InnertubeEndpoint.Video> uploads,
                          List<ChannelEndpoint.PlaylistSummary> playlists, long now,
                          String uploadsToken, List<ChannelEndpoint.Post> posts,
                          int clientMajorVersion, String communityToken, String playlistsToken) {
        return channel(info, uploads, playlists, now, uploadsToken, posts, clientMajorVersion,
                communityToken, playlistsToken, -1);
    }

    /** With {@code videosSort} 0-2 the Videos tab opens selected, sorted as the menu says. */
    static byte[] channel(ChannelEndpoint.ChannelInfo info, List<InnertubeEndpoint.Video> uploads,
                          List<ChannelEndpoint.PlaylistSummary> playlists, long now,
                          String uploadsToken, List<ChannelEndpoint.Post> posts,
                          int clientMajorVersion, String communityToken, String playlistsToken,
                          int videosSort) {
        String channelId = info.id;
        String name = info.name.isEmpty() ? channelId : info.name;
        VideoListProto.Root root = browseRoot();

        VideoListProto.SubscribeButtonData.SubContents subscribe = new VideoListProto.SubscribeButtonData.SubContents()
                .setChannelId(channelId)
                // Field 3 is "subscribed" (subscribeButtonRenderer afmv.c).
                .setInt3(AccountActions.isSubscribed(channelId) ? 1 : 0).setInt4(1).setInt6(0).setInt8(0)
                .addSubscribeButton(browseRunsBold("SUBSCRIBE"))
                .addSubscribedLabel(browseRunsBold("SUBSCRIBED"))
                .addSubscribeLabel(browseRunsBold("SUBSCRIBE"))
                .addUnsubscribeLabel(browseRunsBold("UNSUBSCRIBE"))
                .addAction(new VideoListProto.SubscribeButtonData.SubContents.SubAction().addSubscr1(
                        new VideoListProto.SubscribeButtonData.SubContents.SubAction.Sub1()
                                .setId(channelId).setParams("b")))
                // The reference server put the unsubscribe action into the subscribe field too.
                .addAction(new VideoListProto.SubscribeButtonData.SubContents.SubAction().addSubscr2(
                        new VideoListProto.SubscribeButtonData.SubContents.SubAction.Sub2()
                                .setId(channelId).setParams("c")))
                .setIdentifier("c4-subscribe");
        VideoListProto.Root.HeaderRender.C4Header c4 = new VideoListProto.Root.HeaderRender.C4Header()
                .setChannelId(channelId)
                .setChannelName(name)
                .addAvatars(browseThumbnails(largeAvatar(avatarOrDefault(info.avatar)), 240, 240))
                .addSubscribeCount(browseRuns(subscriberText(info.subscribers)));
        // No subscribe button on the account's own channel.
        boolean own = YouTubeAuth.signedIn() && channelId.equals(YouTubeAuth.ownChannel());
        if (!own) c4.addSubscribeButton(new VideoListProto.SubscribeButtonData().addContent(subscribe));
        if (!info.banner.isEmpty()) c4.addMobileBanners(browseThumbnails(info.banner, 1060, 175));
        VideoListProto.Root.HeaderRender header = new VideoListProto.Root.HeaderRender().addChannelHeader(c4);

        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer tabs =
                new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer();
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData home = tabContent();
        // youtube.com's Home tab: the spotlight video, then its shelves. Each goes in a content
        // entry of its own (two in one entry merge into a single message).
        List<VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData> homeParts =
                new java.util.ArrayList<VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer
                        .Tabs.TabRenderer.ContentEntry.ContentData.ContentActualData>();
        if (info.spotlight != null) {
            VideoListProto.ItemSectionRenderer spotlight = new VideoListProto.ItemSectionRenderer();
            VideoListProto.ItemSectionRenderer.IcsContents item = new VideoListProto.ItemSectionRenderer.IcsContents();
            if (clientMajorVersion >= PIVOT_BAR_VERSION) {
                item.addFeedVideo(browseFeedVideo(info.spotlight, info.avatar, now));
            } else {
                item.addVideo(browseVideo(info.spotlight, info.avatar, now));
            }
            spotlight.addIcsContent(item);
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                    .ContentEntry.ContentData.ContentActualData part = tabContent();
            part.addSection(spotlight);
            homeParts.add(part);
        }
        List<String[]> featuredChannels = new java.util.ArrayList<String[]>();
        for (ChannelEndpoint.HomeShelf shelf : info.shelves) {
            VideoListProto.ShelfRenderer.Contents.ShelfContents shelfContents =
                    new VideoListProto.ShelfRenderer.Contents.ShelfContents();
            for (int i = 0; i < shelf.videos.size() && i < 12; i++) {
                shelfContents.addContent(new VideoListProto.ShelfRenderer.Contents.ShelfContents.ShelfContentsContents()
                        .addVideo(browseVideo(shelf.videos.get(i), info.avatar, now)));
            }
            for (int i = 0; i < shelf.playlists.size() && i < 12; i++) {
                ChannelEndpoint.PlaylistSummary playlist = shelf.playlists.get(i);
                shelfContents.addContent(new VideoListProto.ShelfRenderer.Contents.ShelfContents.ShelfContentsContents()
                        .addPlaylist(browsePlaylist(playlist.id, playlist.title, name, channelId,
                                playlist.count, playlist.firstVideoId, playlist.thumbnail)));
            }
            for (String[] channel : shelf.channels) {
                featuredChannels.add(channel);
                shelfContents.addContent(new VideoListProto.ShelfRenderer.Contents.ShelfContents.ShelfContentsContents()
                        .addChannel(browseChannel(channel)));
            }
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                    .ContentEntry.ContentData.ContentActualData part = tabContent();
            part.addShelf(new VideoListProto.ShelfRenderer()
                    .setHeader(new VideoListProto.ShelfRenderer.ShelfHeader().addContent(
                            new VideoListProto.ShelfRenderer.ShelfHeader.Contents().setShelfName(shelf.title)))
                    .setContent(new VideoListProto.ShelfRenderer.Contents().setShelfContent(shelfContents)));
            homeParts.add(part);
        }
        if (!homeParts.isEmpty()) {
            home = homeParts.remove(0);
        } else if (!uploads.isEmpty()) {
            VideoListProto.ShelfRenderer.Contents.ShelfContents shelfContents =
                    new VideoListProto.ShelfRenderer.Contents.ShelfContents();
            for (int i = 0; i < uploads.size() && i < 10; i++) {
                shelfContents.addContent(new VideoListProto.ShelfRenderer.Contents.ShelfContents.ShelfContentsContents()
                        .addVideo(browseVideo(uploads.get(i), info.avatar, now)));
            }
            home.addShelf(new VideoListProto.ShelfRenderer()
                    .setHeader(new VideoListProto.ShelfRenderer.ShelfHeader().addContent(
                            new VideoListProto.ShelfRenderer.ShelfHeader.Contents().setShelfName("Latest uploads")))
                    .setContent(new VideoListProto.ShelfRenderer.Contents().setShelfContent(shelfContents)));
        }
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs homeTab =
                tab("Home", "Home", videosSort < 0, 0, home);
        for (VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData part : homeParts) {
            homeTab.getTabRender(0).getContentsEntry(0).getContentArray(0).addC(part);
        }
        tabs.addTab(homeTab);

        if (!uploads.isEmpty()) {
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs videos =
                    tab("Videos", "Videos", videosSort >= 0, 0, videoSection(uploads, info.avatar, now), uploadsToken);
            videos.getTabRender(0).getContentsEntry(0).getContentArray(0).getC(0).getSection(0)
                    .setHeader(sortMenu(channelId, Math.max(0, videosSort)).header(tr("Uploads")));
            tabs.addTab(videos);
        }

        if (clientMajorVersion >= COMMUNITY_TAB_VERSION && !posts.isEmpty()) {
            VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
            for (ChannelEndpoint.Post post : posts) {
                section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents()
                        .addPost(communityPost(post)));
            }
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                    .ContentEntry.ContentData.ContentActualData content = tabContent();
            content.addSection(section);
            tabs.addTab(tab("Community", "Community", false, 0, content, communityToken));
        }

        if (clientMajorVersion >= COMMUNITY_TAB_VERSION && (!info.description.isEmpty()
                || !info.views.isEmpty() || !info.links.isEmpty())) {
            // The About view (hkn) reads field 1 (description), 6 (the line under it: country),
            // 4 and 5 (the stats block: joined, views) and 2 (links, each opening its address).
            VideoListProto.AboutRenderer about = new VideoListProto.AboutRenderer()
                    .addChannelDescription(linkedBrowseRuns(info.description));
            if (!info.country.isEmpty()) about.addJoinDate(browseRuns(info.country));
            if (!info.joined.isEmpty()) about.setStatsLine(browseRuns(info.joined));
            StringBuilder counts = new StringBuilder(info.views);
            for (String part : new String[]{info.subscribers, info.uploads}) {
                if (part.isEmpty()) continue;
                if (counts.length() > 0) counts.append(" · ");
                counts.append(part);
            }
            if (counts.length() > 0) about.addTotalViews(browseRuns(counts.toString()));
            for (String[] link : info.links) {
                String label = link[0].isEmpty() ? link[1] : link[0] + ": " + link[1];
                String url = link[1].toLowerCase(Locale.US).startsWith("http") ? link[1] : "https://" + link[1];
                about.addSite(new VideoListProto.AboutRenderer.ExternalSite().addName(browseRuns(label))
                        .setEndpoint(new AndroidFullNextProto.NavigationData().setUrl(url)));
            }
            VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
            section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents().addAbout(about));
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                    .ContentEntry.ContentData.ContentActualData content = tabContent();
            content.addSection(section);
            tabs.addTab(tab("About", tr("About"), false, 0, content));
        }

        if (!playlists.isEmpty()) {
            VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
            for (ChannelEndpoint.PlaylistSummary playlist : playlists) {
                section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents().addPlaylist(
                        browsePlaylist(playlist.id, playlist.title, name, channelId, playlist.count,
                                playlist.firstVideoId, playlist.thumbnail)));
            }
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                    .ContentEntry.ContentData.ContentActualData content = tabContent();
            content.addSection(section);
            tabs.addTab(tab("Playlists", "Playlists", false, 0, content, playlistsToken));
        }

        // Channels: the channels the Home tab features (youtube.com dropped the tab itself).
        if (!featuredChannels.isEmpty()) {
            VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (String[] channel : featuredChannels) {
                if (!seen.add(channel[0])) continue;
                section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents()
                        .addChannel(browseChannel(channel)));
            }
            VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                    .ContentEntry.ContentData.ContentActualData content = tabContent();
            content.addSection(section);
            tabs.addTab(tab("Channels", tr("Channels"), false, 0, content));
        }
        return finishBrowse(root, tabs, header);
    }

    /** A page listing channels ({id, name, avatar}), as cards opening them. */
    static byte[] channelList(String identifier, String title, List<String[]> channels) {
        VideoListProto.ItemSectionRenderer section = new VideoListProto.ItemSectionRenderer();
        for (String[] channel : channels) {
            section.addIcsContent(new VideoListProto.ItemSectionRenderer.IcsContents().addChannel(
                    browseChannel(new String[]{channel[0], channel[1], channel[2], ""})));
        }
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData content = tabContent();
        content.addSection(section);
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer tabs =
                new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer()
                        .addTab(tab(identifier, title, true, 0, content));
        return finishBrowse(browseRoot(), tabs, textHeader(title));
    }

    /** A channel card from {id, name, avatar, subscribers}; tapping it opens the channel. */
    private static VideoListProto.ChannelRenderer browseChannel(String[] channel) {
        VideoListProto.ChannelRenderer renderer = new VideoListProto.ChannelRenderer()
                .setId(channel[0])
                .setName(browseRuns(channel[1]))
                // The channel card (acgf, hlu) shows its title from field 9.
                .setName2(browseRuns(channel[1]))
                .setAvatar(browseThumbnails(avatarOrDefault(channel[2]), 88, 88))
                .setAvatar2(browseThumbnails(avatarOrDefault(channel[2]), 176, 176))
                .setNavigation(browseNavigation(channel[0], null, "/channel/" + channel[0]));
        String subscribers = subscriberText(channel[3]);
        if (!subscribers.isEmpty()) renderer.setSubCount(browseRuns(subscribers));
        return renderer;
    }

    /**
     * One post of a channel's Community tab, as the post renderer the apps draw with their
     * comment view: who wrote it with their picture, the text, when it was written, and the
     * picture attached to it when it has one.
     */
    private static VideoListProto.CommunityPostThreadRenderer communityPost(ChannelEndpoint.Post post) {
        VideoListProto.CommunityPostRenderer renderer = new VideoListProto.CommunityPostRenderer()
                .setPostId(post.id)
                .setAuthor(browseRuns(post.author))
                .setAuthorThumbnail(browseThumbnails(avatarOrDefault(post.avatar), 88, 88))
                .setContent(post.runs.isEmpty() ? browseRuns(post.text) : postRuns(post.runs));
        if (!post.authorId.isEmpty()) {
            renderer.setAuthorEndpoint(browseNavigation(post.authorId, null, null));
        }
        boolean toolbar = !post.likeAction.isEmpty() && !post.dislikeAction.isEmpty();
        if (toolbar) {
            // The comment view's own row: like and dislike toggles with the like count next to
            // them, and the comment count on the reply button.
            renderer.setPublishedTime(browseRuns(post.published));
            renderer.addRawField(10, postActionButtons(post));
            renderer.addRawField(28, formattedBytes(post.likes.isEmpty() ? "0" : post.likes));
        } else {
            StringBuilder line = new StringBuilder();
            if (!post.published.isEmpty()) line.append(post.published);
            // Likes, dislikes and comments, the counts the post's line showed. YouTube no longer
            // publishes dislikes and Return YouTube Dislike only knows videos, so that one is 0.
            if (line.length() > 0) line.append(" \u00b7 ");
            line.append(post.likes.isEmpty() ? "0" : post.likes).append(' ').append(tr("likes"));
            line.append(" \u00b7 0 ").append(tr("dislikes"));
            if (!post.comments.isEmpty()) {
                line.append(" \u00b7 ").append(post.comments).append(' ').append(tr("comments"));
            }
            if (line.length() > 0) renderer.setPublishedTime(browseRuns(line.toString()));
        }
        if (!post.id.isEmpty()) renderer.addRawField(8, postMenu(post));
        if (!post.image.isEmpty()) {
            VideoListProto.CommunityPostRenderer.Attachment.Image image =
                    new VideoListProto.CommunityPostRenderer.Attachment.Image()
                            .setPicture(browseThumbnails(post.image, 480, 480));
            // A tap opens the picture at full size, in the app's viewer (it takes the largest
            // copy of the picture up to 4096 pixels).
            String full = post.imageFull.isEmpty() ? post.image : post.imageFull;
            if (!post.imageFull.isEmpty() && !post.imageFull.equals(post.image)) {
                image.getPicture().addThumb(new VideoListProto.Thumbnails.Thumbnail()
                        .setUrl(post.imageFull).setWidth(1440).setHeight(1440));
            }
            image.setEndpoint(new AndroidFullNextProto.NavigationData().setUrl(full));
            renderer.setImageViewer(true);
            renderer.setBackstageAttachment(new VideoListProto.CommunityPostRenderer.Attachment()
                    .addBackstageImage(image));
        }
        return new VideoListProto.CommunityPostThreadRenderer().setPost(
                new VideoListProto.CommunityPostThreadRenderer.PostWrapper().setPost(renderer));
    }

    // A post's row of the comment view (12.45.56): commentRenderer acey field 10 = union acec
    // {commentActionButtonsRenderer aceb, ext 66744747}: likeButton = 1, replyButton = 2,
    // dislikeButton = 7, each a button union abxg (buttonRenderer abxa 65153809, toggle afqg
    // 79971800). The row is drawn only with both toggles. A toggle (afqg): isToggled = 3, icon =
    // 5, endpoint = 7, toggled icon = 8, toggled endpoint = 10 (run when tapped while toggled).
    // The endpoints are performCommentActionEndpoint (acdz 65929205, aemq {action = 1}); the app
    // sends the action to comment/perform_comment_action, which the proxy passes to youtube.com.
    private static final int PERFORM_COMMENT_ACTION = 65929205;
    private static final int ICON_COMMENT = 243;

    private static byte[] postActionButtons(ChannelEndpoint.Post post) {
        boolean liked = "LIKE".equals(post.voteStatus);
        String base = post.likes.isEmpty() ? "0" : post.likes;
        return actionButtons(post.voteStatus, post.likeAction, post.unlikeAction, post.dislikeAction,
                post.undislikeAction, liked ? base : bumpCount(base, 1),
                liked ? bumpCount(base, -1) : base, post.comments);
    }

    /**
     * The action row of the comment view (aceb, see above) for a post or a comment: like and
     * dislike toggles carrying youtube.com's action tokens, and the reply button with the reply
     * count. {@code withLike}/{@code withoutLike} are the like counts shown after a tap.
     */
    private static byte[] actionButtons(String voteStatus, String likeAction, String unlikeAction,
                                        String dislikeAction, String undislikeAction,
                                        String withLike, String withoutLike, String replies) {
        com.viitube.proxy.proto.ProtoWriter buttons = new com.viitube.proxy.proto.ProtoWriter(); // aceb
        buttons.writeBytes(1, rawField(79971800, postToggle("LIKE".equals(voteStatus), ICON_LIKE,
                ICON_LIKE_SELECTED, likeAction, withLike, unlikeAction, withoutLike)));
        com.viitube.proxy.proto.ProtoWriter reply = new com.viitube.proxy.proto.ProtoWriter();   // abxa
        if (replies != null && !replies.isEmpty()) reply.writeBytes(5, formattedBytes(replies));
        reply.writeBytes(8, iconBytes(ICON_COMMENT));
        buttons.writeBytes(2, rawField(65153809, reply.toByteArray()));
        buttons.writeBytes(7, rawField(79971800, postToggle("DISLIKE".equals(voteStatus),
                ICON_DISLIKE, ICON_DISLIKE_SELECTED, dislikeAction, withoutLike,
                undislikeAction, withoutLike)));
        return rawField(66744747, buttons.toByteArray());                            // acec
    }

    /** A plain count moved by {@code delta}; "172K" and the like stay as they are. */
    private static String bumpCount(String count, int delta) {
        if (!count.matches("[0-9]{1,9}")) return count;
        return Long.toString(Math.max(0, Long.parseLong(count) + delta));
    }

    private static byte[] postToggle(boolean toggled, int icon, int toggledIcon, String action,
                                     String actionCount, String undo, String undoCount) {
        com.viitube.proxy.proto.ProtoWriter toggle = new com.viitube.proxy.proto.ProtoWriter();  // afqg
        if (toggled) toggle.writeBool(3, true);
        toggle.writeBytes(5, iconBytes(icon));
        toggle.writeBytes(7, commentAction(action, actionCount));
        toggle.writeBytes(8, iconBytes(toggledIcon));
        toggle.writeBytes(10, commentAction(undo.isEmpty() ? action : undo, undoCount));
        return toggle.toByteArray();
    }

    /**
     * performCommentActionEndpoint aemq {action = 1, actions = 2}. The app reads the first of
     * the actions without checking there is one, so it always carries the count update: acdz
     * 119065485 (afty {text = 1}), the like count the row shows afterwards.
     */
    private static byte[] commentAction(String action, String count) {
        com.viitube.proxy.proto.ProtoWriter update = new com.viitube.proxy.proto.ProtoWriter();  // afty
        update.writeBytes(1, formattedBytes(count));
        com.viitube.proxy.proto.ProtoWriter endpoint = new com.viitube.proxy.proto.ProtoWriter(); // aemq
        endpoint.writeString(1, action);
        endpoint.writeBytes(2, rawField(119065485, update.toByteArray()));
        return rawField(PERFORM_COMMENT_ACTION, endpoint.toByteArray());               // acdz
    }

    /**
     * The post's ⋮ menu (commentRenderer field 8, menuRenderer aeal 66439850 -> aeaj {items = 1,
     * each aeah 66441108 -> menu item aeai {text = 1, icon = 2, endpoint = 3}}): Share, which
     * hands the post's address to Android's share sheet.
     */
    private static byte[] postMenu(ChannelEndpoint.Post post) {
        String address = "https://www.youtube.com/post/" + post.id;
        com.viitube.proxy.proto.ProtoWriter share = new com.viitube.proxy.proto.ProtoWriter();   // aeai
        share.writeBytes(1, formattedBytes(tr("Share")));
        share.writeBytes(2, iconBytes(ICON_SHARE));
        AndroidFullNextProto.ButtonEndpoint.ShareIntent intent = new AndroidFullNextProto.ButtonEndpoint.ShareIntent()
                .setType("text/plain")
                .setChooserTitle(tr("Share"))
                .addExtras(new AndroidFullNextProto.ButtonEndpoint.ShareIntent.Extra()
                        .setKey("android.intent.extra.TEXT").setValue(address));
        share.writeBytes(3, new AndroidFullNextProto.ButtonEndpoint().setShare(intent).toByteArray());
        com.viitube.proxy.proto.ProtoWriter menu = new com.viitube.proxy.proto.ProtoWriter();    // aeaj
        menu.writeBytes(1, rawField(66441108, share.toByteArray()));
        return rawField(66439850, menu.toByteArray());                                 // aeal
    }

    /** The addToPlaylistEndpoint of a NavigationEndpoint (acdz 69487224, abpa {videoId = 1}). */
    private static final int ADD_TO_PLAYLIST_ENDPOINT = 69487224;

    /**
     * A video's menu (menuRenderer aeal 66439850 -> aeaj {items = 1, each aeah 66441108 ->
     * aeai {text = 1, icon = 2, endpoint = 3}}): Save to playlist (the app's own dialog, which
     * lists the account's playlists) when signed in, and Share.
     */
    private static byte[] videoMenu(String videoId, String title) {
        com.viitube.proxy.proto.ProtoWriter menu = new com.viitube.proxy.proto.ProtoWriter();    // aeaj
        if (YouTubeAuth.signedIn()) {
            com.viitube.proxy.proto.ProtoWriter save = new com.viitube.proxy.proto.ProtoWriter(); // aeai
            save.writeBytes(1, formattedBytes(tr("Save to playlist")));
            save.writeBytes(2, iconBytes(ICON_ADDTO));
            save.writeBytes(3, rawField(ADD_TO_PLAYLIST_ENDPOINT, stringField(1, videoId)));
            menu.writeBytes(1, rawField(66441108, save.toByteArray()));
        }
        com.viitube.proxy.proto.ProtoWriter share = new com.viitube.proxy.proto.ProtoWriter();   // aeai
        share.writeBytes(1, formattedBytes(tr("Share")));
        share.writeBytes(2, iconBytes(ICON_SHARE));
        share.writeBytes(3, shareEndpoint(videoId, title).toByteArray());
        menu.writeBytes(1, rawField(66441108, share.toByteArray()));
        return rawField(66439850, menu.toByteArray());
    }

    /** The post's text as runs, the linked ones opening their address. */
    private static VideoListProto.TextRuns postRuns(List<String[]> pieces) {
        VideoListProto.TextRuns runs = new VideoListProto.TextRuns();
        for (String[] piece : pieces) {
            VideoListProto.TextRuns.TextRun run = new VideoListProto.TextRuns.TextRun().setText(piece[0]);
            if (piece[1] != null) run.setNavigation(new AndroidFullNextProto.NavigationData().setUrl(piece[1]));
            runs.addRun(run);
        }
        return runs;
    }

    private static byte[] rawField(int field, byte[] value) {
        com.viitube.proxy.proto.ProtoWriter writer = new com.viitube.proxy.proto.ProtoWriter();
        writer.writeBytes(field, value);
        return writer.toByteArray();
    }

    /** FormattedString addp with one run. */
    private static byte[] formattedBytes(String text) {
        com.viitube.proxy.proto.ProtoWriter run = new com.viitube.proxy.proto.ProtoWriter();
        run.writeString(1, text);
        return rawField(1, run.toByteArray());
    }

    private static byte[] iconBytes(int iconType) {
        com.viitube.proxy.proto.ProtoWriter icon = new com.viitube.proxy.proto.ProtoWriter();
        icon.writeInt32(1, iconType);
        return icon.toByteArray();
    }

    /** "1.2M subscribers" from YouTube's label ("1.23M subscribers") or a plain number. */
    static String subscriberText(String subscribers) {
        if (subscribers == null || subscribers.trim().isEmpty()) return "";
        return shortCount(parseCount(subscribers), "subscribers");
    }

    // The playlist header's buttons (playlistHeaderRenderer aesr of 12.45.56, drawn by gbk).
    private static final int ICON_PLAYLIST_ADD = 46;
    private static final int ICON_OFFLINE_DOWNLOAD = 147;
    private static final int ICON_PLAY = 247;
    private static final int WATCH_ENDPOINT = 48687757;   // acdz {videoId 1, playlistId 2, index 3}
    private static final int LIKE_ENDPOINT = 63158558;    // acdz adqn {status 1, target 2}

    /**
     * The red play button floating over the header's edge (field 50: union aeso {95228729: adjm
     * {icon = 1, endpoint = 2, accessibility = 4}}, drawn by the app's fab controller dla; its
     * endpoint a watchEndpoint of the first video in the playlist). Field 47 is the share
     * button's endpoint, not this. For the signed-in
     * account the save toggle (field 45: union abxg {toggle afqg 79971800}), whose endpoints
     * like/removelike the playlist (adqn target adqq {playlistId = 2}). Watch later and Liked
     * videos are the account's own lists and have no save toggle.
     */
    private static void addPlaylistButtons(VideoListProto.Root.HeaderRender.VlHeader vl, String playlistId,
                                           PlaylistEndpoint.Playlist playlist) {
        if (!playlist.videos.isEmpty()) {
            com.viitube.proxy.proto.ProtoWriter watch = new com.viitube.proxy.proto.ProtoWriter();
            watch.writeString(1, playlist.videos.get(0).id);
            watch.writeString(2, playlistId);
            com.viitube.proxy.proto.ProtoWriter play = new com.viitube.proxy.proto.ProtoWriter(); // adjm
            play.writeBytes(1, iconBytes(ICON_PLAY));
            play.writeBytes(2, rawField(WATCH_ENDPOINT, watch.toByteArray()));
            play.writeBytes(4, stringField(1, tr("Play all")));
            vl.addRawField(50, rawField(95228729, play.toByteArray()));
        }
        if (YouTubeAuth.signedIn() && !playlistId.equals("WL") && !playlistId.equals("LL")) {
            com.viitube.proxy.proto.ProtoWriter toggle = new com.viitube.proxy.proto.ProtoWriter(); // afqg
            toggle.writeBytes(5, iconBytes(ICON_PLAYLIST_ADD));
            toggle.writeBytes(7, playlistLike(playlistId, 0));
            toggle.writeBytes(8, iconBytes(ICON_PLAYLIST_ADD));
            toggle.writeBytes(10, playlistLike(playlistId, 2));
            vl.addRawField(45, rawField(79971800, toggle.toByteArray()));
        }
    }

    /** likeEndpoint for a playlist: status 0 like, 2 remove the like. */
    private static byte[] playlistLike(String playlistId, int status) {
        com.viitube.proxy.proto.ProtoWriter like = new com.viitube.proxy.proto.ProtoWriter();     // adqn
        if (status != 0) like.writeInt32(1, status);
        like.writeBytes(2, stringField(2, playlistId));
        return rawField(LIKE_ENDPOINT, like.toByteArray());
    }

    private static byte[] stringField(int field, String value) {
        com.viitube.proxy.proto.ProtoWriter writer = new com.viitube.proxy.proto.ProtoWriter();
        writer.writeString(field, value);
        return writer.toByteArray();
    }

    /** VL... playlist page. */
    static byte[] playlist(String playlistId, PlaylistEndpoint.Playlist playlist) {
        VideoListProto.Root root = browseRoot();
        int total = Math.max(playlist.total, playlist.videos.size());
        String count = total + " videos";
        VideoListProto.Root.HeaderRender.VlHeader vl = new VideoListProto.Root.HeaderRender.VlHeader()
                .setId(playlistId)
                .addName(browseRuns(playlist.title))
                .addVideoCount(browseRuns(count));
        if (!playlist.description.isEmpty()) vl.addDescription(browseRuns(playlist.description));
        vl.addOwner(browseAuthor(playlist.author, playlist.authorId))
                .addUnk11(new VideoListProto.Root.HeaderRender.VlHeader.Pb11().setInt1(1))
                .setPublic(1)
                .addNavigationData(browseNavigation(playlistId, null, null))
                .addInlineProperty(browseRuns(count));
        addPlaylistButtons(vl, playlistId, playlist);
        VideoListProto.Root.HeaderRender header = new VideoListProto.Root.HeaderRender().addPlaylistHeader(vl);

        VideoListProto.PlaylistData data = new VideoListProto.PlaylistData().setId(playlistId);
        for (int index = 0; index < playlist.videos.size(); index++) {
            InnertubeEndpoint.Video video = playlist.videos.get(index);
            VideoListProto.PlaylistData.PlaylistVideo.Contents contents =
                    new VideoListProto.PlaylistData.PlaylistVideo.Contents()
                            .setId(video.id)
                            .addTitle(browseRuns(video.title))
                            .addIndex(browseRuns(Integer.toString(index + 1)))
                            .setThumbnail(browseThumbnails(hqThumbnail(video.id), 480, 360))
                            .setOwner(browseAuthor(video.channel, video.channelId))
                            .addLength(browseRuns(lengthText(video.duration)))
                            .addEndpoint(new VideoListProto.PlaylistData.PlaylistVideo.Contents.EndpointData()
                                    .addContent(new VideoListProto.PlaylistData.PlaylistVideo.Contents
                                            .EndpointData.EndpointDataContents()
                                            .setId(video.id).setPlaylistId(playlistId)
                                            // The place in the queue the app asks /next for.
                                            .setIndex(index)));
            contents.setRawMenu(videoMenu(video.id, video.title));
            data.addVideo(new VideoListProto.PlaylistData.PlaylistVideo().addContent(contents));
        }
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer.Tabs.TabRenderer
                .ContentEntry.ContentData.ContentActualData content = tabContent();
        content.addPlaylist(data);
        VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer tabs =
                new VideoListProto.Root.ResponseBody.SingleColumnBrowseResultsRenderer()
                        .addTab(tab("Playlist", playlist.title, true, 0, content));
        return finishBrowse(root, tabs, header);
    }

    // ------------------------------------------------------------------ search (android_search)

    private static AndroidSearchProto.TextRuns searchRuns(String text) {
        return new AndroidSearchProto.TextRuns().addRun(new AndroidSearchProto.TextRuns.TextRun().setText(text));
    }

    private static AndroidSearchProto.Thumbnails searchThumbnails(String url, int width, int height) {
        return new AndroidSearchProto.Thumbnails().addThumb(new AndroidSearchProto.Thumbnails.Thumbnail()
                .setUrl(url).setWidth(width).setHeight(height));
    }

    private static AndroidSearchProto.AuthorData searchAuthor(String name, String channelId) {
        AndroidSearchProto.AuthorData.AuthorDataContent content = new AndroidSearchProto.AuthorData.AuthorDataContent()
                .setAuthorDisplayName(name);
        if (channelId != null && !channelId.isEmpty()) {
            content.setNavigation(new AndroidSearchProto.BrowseNavigation().addData(
                    new AndroidSearchProto.BrowseNavigation.BrowseData()
                            .setBrowseId(channelId).setCanonicalBaseUrl("/channel/" + channelId)));
        }
        return new AndroidSearchProto.AuthorData().setAc(content);
    }

    static AndroidSearchProto.CompactVideoRenderer searchVideo(InnertubeEndpoint.Video video, long now) {
        long views = parseCount(video.views);
        AndroidSearchProto.AuthorData author = searchAuthor(video.channel, video.channelId);
        return new AndroidSearchProto.CompactVideoRenderer()
                .setVideoId(video.id)
                .setThumblist(searchThumbnails(hqThumbnail(video.id), 480, 360))
                .setTitleData(searchRuns(video.title))
                .setPublishedTimeText(searchRuns(relativeTime(video.published, now)))
                .setViewCountText(searchRuns(fullCount(views, "view", "views")))
                .setLengthText(searchRuns(lengthText(video.duration)))
                .setShortViewCounts(searchRuns(shortCount(views, "views")))
                .setShortAuthorData(author)
                .setLongAuthorData(author)
                .setNavData(new AndroidSearchProto.NavigationData().setNavProperties(
                        new AndroidSearchProto.NavigationData.NavData().setNavId(video.id)))
                .setAuthorAvatar(searchThumbnails(DEFAULT_AVATAR, 88, 88));
    }

    /** The channel cards YouTube mixes into the results. */
    private static AndroidSearchProto.ChannelRenderer searchChannel(InnertubeEndpoint.SearchChannel channel) {
        AndroidSearchProto.ChannelRenderer renderer = new AndroidSearchProto.ChannelRenderer()
                .setId(channel.id)
                .setName(searchRuns(channel.name))
                .setAvatar(searchThumbnails(avatarOrDefault(channel.avatar), 88, 88))
                .setAvatar2(searchThumbnails(avatarOrDefault(channel.avatar), 176, 176))
                .setNavigation(new AndroidSearchProto.BrowseNavigation().addData(
                        new AndroidSearchProto.BrowseNavigation.BrowseData()
                                .setBrowseId(channel.id)
                                .setCanonicalBaseUrl("/channel/" + channel.id)));
        String subscribers = subscriberText(channel.subscribers);
        if (!subscribers.isEmpty()) renderer.setSubCount(searchRuns(subscribers));
        if (!channel.handle.isEmpty()) renderer.setHandle(searchRuns(channel.handle));
        return renderer;
    }

    /** The playlist cards; tapping one opens the playlist with browse VL<id>. */
    private static AndroidSearchProto.PlaylistRenderer searchPlaylist(InnertubeEndpoint.SearchPlaylist playlist) {
        String thumbnail = !playlist.firstVideoId.isEmpty() ? hqThumbnail(playlist.firstVideoId)
                : !playlist.thumbnail.isEmpty() ? playlist.thumbnail : DEFAULT_AVATAR;
        AndroidSearchProto.AuthorData owner = searchAuthor(playlist.author, playlist.authorId);
        AndroidSearchProto.PlaylistRenderer renderer = new AndroidSearchProto.PlaylistRenderer()
                .setId(playlist.id)
                .setPlaylistName(searchRuns(playlist.title))
                .setThumbnail(searchThumbnails(thumbnail, 480, 360))
                .setSeparatedThumbnail(searchThumbnails(thumbnail, 480, 360))
                .setShortOwner(owner)
                .setLongOwner(owner)
                .setPlaylistBrowse(new AndroidSearchProto.BrowseNavigation().addData(
                        new AndroidSearchProto.BrowseNavigation.BrowseData().setBrowseId("VL" + playlist.id)))
                .setWebUrl("https://www.youtube.com/playlist?list=" + playlist.id);
        if (!playlist.count.isEmpty()) {
            String count = playlist.count + " videos";
            renderer.setVideoCount(searchRuns(count))
                    .setVideoCount2(searchRuns(count))
                    .setBareVideoCount(searchRuns(playlist.count));
        }
        return renderer;
    }

    private static AndroidSearchProto.Continuations searchContinuations(String token) {
        return new AndroidSearchProto.Continuations().addNext(
                new AndroidSearchProto.Continuations.NextContinuationData().setToken(token));
    }

    private static AndroidSearchProto.Root.Contents.SearchResults.SearchResultsContents
            .ItemSectionRender.ItemSectionRenderContents searchItem() {
        return new AndroidSearchProto.Root.Contents.SearchResults.SearchResultsContents
                .ItemSectionRender.ItemSectionRenderContents();
    }

    /**
     * One page of results: the channels and playlists YouTube found first, then the videos. The
     * token is what the app sends back when the last result is on screen; it is empty on the
     * last page, which ends the list.
     */
    static byte[] search(List<InnertubeEndpoint.Video> videos,
                         List<InnertubeEndpoint.SearchChannel> channels,
                         List<InnertubeEndpoint.SearchPlaylist> playlists,
                         long now, String continuationToken) {
        AndroidSearchProto.Root.Contents.SearchResults results = searchResults(videos, channels,
                playlists, now, continuationToken);
        return new AndroidSearchProto.Root().addContent(new AndroidSearchProto.Root.Contents()
                .addResultsContainer(results)).toByteArray();
    }

    /** The same page as an answer to a continuation request, which the app appends to the list. */
    static byte[] searchContinuation(List<InnertubeEndpoint.Video> videos, long now,
                                     String continuationToken) {
        AndroidSearchProto.Root.Contents.SearchResults results = searchResults(videos,
                new java.util.ArrayList<InnertubeEndpoint.SearchChannel>(),
                new java.util.ArrayList<InnertubeEndpoint.SearchPlaylist>(), now, continuationToken);
        return new AndroidSearchProto.Root().setContinuation(
                new AndroidSearchProto.Root.ContinuationContents().addSectionListContinuation(results))
                .toByteArray();
    }

    private static AndroidSearchProto.Root.Contents.SearchResults searchResults(
            List<InnertubeEndpoint.Video> videos,
            List<InnertubeEndpoint.SearchChannel> channels,
            List<InnertubeEndpoint.SearchPlaylist> playlists,
            long now, String continuationToken) {
        AndroidSearchProto.Root.Contents.SearchResults.SearchResultsContents.ItemSectionRender section =
                new AndroidSearchProto.Root.Contents.SearchResults.SearchResultsContents.ItemSectionRender();
        for (InnertubeEndpoint.SearchChannel channel : channels) {
            section.addContent(searchItem().addChannel(searchChannel(channel)));
        }
        for (InnertubeEndpoint.SearchPlaylist playlist : playlists) {
            section.addContent(searchItem().addPlaylist(searchPlaylist(playlist)));
        }
        for (InnertubeEndpoint.Video video : videos) {
            section.addContent(searchItem().addVideo(searchVideo(video, now)));
        }
        AndroidSearchProto.Root.Contents.SearchResults results =
                new AndroidSearchProto.Root.Contents.SearchResults().addContent(
                        new AndroidSearchProto.Root.Contents.SearchResults.SearchResultsContents()
                                .addSection(section));
        if (continuationToken != null && !continuationToken.isEmpty()) {
            results.addContinuation(searchContinuations(continuationToken));
        }
        return results;
    }

    // ------------------------------------------------------------------ next (android_full_next)

    /** Addresses in a description: full links, "www." ones and bare "site.tld/path" ones. */
    private static final java.util.regex.Pattern LINK = java.util.regex.Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s<>\"]+|\\b[a-z0-9][a-z0-9.-]*\\.[a-z]{2,6}/[^\\s<>\"]*");

    /** A text cut into {piece, address} pairs; the address is null for plain text. */
    private static List<String[]> linkPieces(String text) {
        List<String[]> pieces = new java.util.ArrayList<String[]>();
        if (text == null) return pieces;
        java.util.regex.Matcher matcher = LINK.matcher(text);
        int last = 0;
        while (matcher.find()) {
            String link = matcher.group();
            // A sentence's full stop or a closing bracket after an address is not part of it.
            int end = link.length();
            while (end > 0 && ".,;:!?)]}'\"".indexOf(link.charAt(end - 1)) >= 0) end--;
            if (end == 0) continue;
            link = link.substring(0, end);
            int start = matcher.start();
            if (start > last) pieces.add(new String[]{text.substring(last, start), null});
            String url = link.toLowerCase(Locale.US).startsWith("http") ? link : "https://" + link;
            pieces.add(new String[]{link, url});
            last = start + link.length();
        }
        if (last < text.length() || pieces.isEmpty()) pieces.add(new String[]{text.substring(last), null});
        return pieces;
    }

    /**
     * A description as runs, with every address in it a run that opens the address when tapped
     * (urlEndpoint), the way YouTube's own descriptions are clickable.
     */
    private static AndroidFullNextProto.TextRuns linkedRuns(String text) {
        AndroidFullNextProto.TextRuns runs = new AndroidFullNextProto.TextRuns();
        for (String[] piece : linkPieces(text)) {
            AndroidFullNextProto.TextRuns.TextRun run = new AndroidFullNextProto.TextRuns.TextRun().setText(piece[0]);
            if (piece[1] != null) run.setNavigation(new AndroidFullNextProto.NavigationData().setUrl(piece[1]));
            runs.addRun(run);
        }
        return runs;
    }

    /** The same for the browse answers (a channel's About description). */
    private static VideoListProto.TextRuns linkedBrowseRuns(String text) {
        VideoListProto.TextRuns runs = new VideoListProto.TextRuns();
        for (String[] piece : linkPieces(text)) {
            VideoListProto.TextRuns.TextRun run = new VideoListProto.TextRuns.TextRun().setText(piece[0]);
            if (piece[1] != null) run.setNavigation(new AndroidFullNextProto.NavigationData().setUrl(piece[1]));
            runs.addRun(run);
        }
        return runs;
    }

    private static AndroidFullNextProto.TextRuns nextRunsBold(String text) {
        return new AndroidFullNextProto.TextRuns().addRun(
                new AndroidFullNextProto.TextRuns.TextRun().setText(text).setBold(1));
    }

    private static AndroidFullNextProto.TextRuns nextRuns(String text) {
        return new AndroidFullNextProto.TextRuns().addRun(new AndroidFullNextProto.TextRuns.TextRun().setText(text));
    }

    private static AndroidFullNextProto.Thumbnails nextThumbnails(String url, int width, int height) {
        return new AndroidFullNextProto.Thumbnails().addThumb(new AndroidFullNextProto.Thumbnails.Thumbnail()
                .setUrl(url).setWidth(width).setHeight(height));
    }

    private static AndroidFullNextProto.BrowseNavigation nextChannelNavigation(String channelId) {
        return new AndroidFullNextProto.BrowseNavigation().addData(
                new AndroidFullNextProto.BrowseNavigation.BrowseData()
                        .setBrowseId(channelId).setCanonicalBaseUrl("/channel/" + channelId));
    }

    static AndroidFullNextProto.CompactVideoRenderer nextVideo(InnertubeEndpoint.Video video, long now) {
        long views = parseCount(video.views);
        AndroidFullNextProto.AuthorData.AuthorDataContent content =
                new AndroidFullNextProto.AuthorData.AuthorDataContent().setAuthorDisplayName(video.channel);
        if (!video.channelId.isEmpty()) content.setNavigation(nextChannelNavigation(video.channelId));
        AndroidFullNextProto.AuthorData author = new AndroidFullNextProto.AuthorData().setAc(content);
        return new AndroidFullNextProto.CompactVideoRenderer()
                .setVideoId(video.id)
                .setThumblist(nextThumbnails(hqThumbnail(video.id), 480, 360))
                .setTitleData(nextRuns(video.title))
                .setPublishedTimeText(nextRuns(relativeTime(video.published, now)))
                .setViewCountText(nextRuns(fullCount(views, "view", "views")))
                .setLengthText(nextRuns(lengthText(video.duration)))
                .setShortViewCounts(nextRuns(shortCount(views, "views")))
                .setShortAuthorData(author)
                .setLongAuthorData(author)
                .setNavData(new AndroidFullNextProto.NavigationData().setNavProperties(
                        new AndroidFullNextProto.NavigationData.NavData().setNavId(video.id)))
                .setAuthorAvatar(nextThumbnails(DEFAULT_AVATAR, 88, 88));
    }

    /** The playlist a video is being played as part of, as the queue of the watch page. */
    static final class Queue {
        String playlistId = "";
        String title = "";
        /** Whose playlist it is, for the line under its name. */
        String author = "";
        /** Which entry is playing, counted from zero. */
        int index;
        int total;
        final List<InnertubeEndpoint.Video> videos = new java.util.ArrayList<InnertubeEndpoint.Video>();

        /** The entry after the one playing, which is what autoplay moves on to. */
        int next() {
            return index + 1 < videos.size() ? index + 1 : -1;
        }
    }

    /**
     * A watch endpoint. With a playlist it also carries which entry of it this is, so the app
     * asks /next for the same queue again and keeps playing down the list.
     */
    private static AndroidFullNextProto.WatchNavigation watchNavigation(String videoId,
                                                                       String playlistId, int index) {
        AndroidFullNextProto.WatchNavigation.WatchData data =
                new AndroidFullNextProto.WatchNavigation.WatchData().setVideoId(videoId);
        if (playlistId != null && !playlistId.isEmpty()) {
            data.setPlaylistId(playlistId).setIndex(index);
        }
        return new AndroidFullNextProto.WatchNavigation().setWatchProperties(data);
    }

    /** The queue of the playlist: the app plays its way down this list on its own. */
    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.PlaylistPanel
            playlistPanel(Queue queue, long now) {
        AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.PlaylistPanel
                .PlaylistPanelRenderer panel = new AndroidFullNextProto.Root.Contents
                .SingleColumnWatchNextResults.PlaylistPanel.PlaylistPanelRenderer()
                        .setTitle(queue.title)
                        .setTitleText(nextRuns(queue.title))
                        .setPlaylistId(queue.playlistId)
                        .setCurrentIndex(queue.index)
                        .setTotalVideos(Math.max(queue.total, queue.videos.size()));
        int total = Math.max(queue.total, queue.videos.size());
        String byline = total + (total == 1 ? " video" : " videos")
                + (queue.author.isEmpty() ? "" : " \u00b7 " + queue.author);
        panel.setShortByline(nextRuns(byline)).setLongByline(nextRuns(byline));
        for (int i = 0; i < queue.videos.size(); i++) {
            InnertubeEndpoint.Video video = queue.videos.get(i);
            AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.PlaylistPanel
                    .PlaylistPanelRenderer.Items.PlaylistPanelVideoRenderer entry =
                    new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.PlaylistPanel
                            .PlaylistPanelRenderer.Items.PlaylistPanelVideoRenderer()
                            .setVideoId(video.id)
                            .setTitle(nextRuns(video.title))
                            .setThumbnail(nextThumbnails(hqThumbnail(video.id), 480, 360))
                            .setByline(nextRuns(video.channel))
                            .setLength(nextRuns(lengthText(video.duration)))
                            .setNavData(watchNavigation(video.id, queue.playlistId, i));
            if (i == queue.index) entry.setSelected(1);
            panel.addItem(new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                    .PlaylistPanel.PlaylistPanelRenderer.Items().addVideo(entry));
        }
        return new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.PlaylistPanel()
                .addPanel(panel);
    }

    /**
     * What the app plays when this video ends. The set it reads is the one whose mode is 1: the
     * endpoint in field 2 for AUTOPLAY, in field 3 for the next button, and the one wrapped in
     * field 5 for AUTONAV, which is the move the app makes by itself.
     */
    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.Autoplay
            autoplaySet(String videoId) {
        // The next button steps to the video that would autoplay (the first related one), so the
        // forward arrow beside play is there on every video, not only inside a list - which is how
        // YouTube drew it. There is nothing before a video outside a list, so no previous button.
        AndroidFullNextProto.WatchNavigation target = watchNavigation(videoId, "", 0);
        return autoplay(newSet().setAutoplayVideo(target)
                .setNextButtonVideo(target).setAutonavVideo(autonav(target)));
    }

    /** The playlist being played: what comes next, and the buttons that step through it. */
    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.Autoplay
            queueSet(Queue queue) {
        AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.Autoplay.AutoplayRenderer
                .AutoplaySet set = newSet();
        if (queue.next() >= 0) {
            AndroidFullNextProto.WatchNavigation next = watchNavigation(
                    queue.videos.get(queue.next()).id, queue.playlistId, queue.next());
            set.setAutoplayVideo(next).setNextButtonVideo(next).setAutonavVideo(autonav(next));
        }
        if (queue.index > 0) {
            set.setPreviousButtonVideo(watchNavigation(queue.videos.get(queue.index - 1).id,
                    queue.playlistId, queue.index - 1));
        }
        return autoplay(set);
    }

    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.Autoplay
            .AutoplayRenderer.AutoplaySet newSet() {
        return new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .Autoplay.AutoplayRenderer.AutoplaySet().setMode(1);
    }

    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.Autoplay
            .AutoplayRenderer.AutoplaySet.Autonav autonav(AndroidFullNextProto.WatchNavigation target) {
        return new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.Autoplay
                .AutoplayRenderer.AutoplaySet.Autonav()
                        .addVideo(new AndroidFullNextProto.Root.Contents
                                .SingleColumnWatchNextResults.Autoplay.AutoplayRenderer
                                .AutoplaySet.Autonav.AutonavVideo().setNavData(target));
    }

    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.Autoplay
            autoplay(AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.Autoplay
                    .AutoplayRenderer.AutoplaySet set) {
        return new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults.Autoplay()
                .addAutoplay(new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                        .Autoplay.AutoplayRenderer().addSet(set).setCountDownSecs(8));
    }

    /**
     * "Up next" with the Autoplay switch on its right, as the header above the related videos.
     * The switch itself is the app's own: it draws a toggleButtonRenderer as a Switch and reads
     * and writes its autonav setting with it, so only the name of the switch is sent.
     */
    /**
     * The line the comments start with, which is what sets them apart from the related videos
     * above them. It belongs to the section itself (itemSectionHeaderRenderer), so it stays with
     * the comments however the list is scrolled.
     */
    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
            .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer.Header
            commentsTitleHeader() {
        return new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer
                .Header().addSectionHeader(new AndroidFullNextProto.Root.Contents
                        .SingleColumnWatchNextResults.SingleColumnWatchNextResultsContents.Results
                        .ResultsContents.ItemSectionRenderer.Header.ItemSectionHeaderRenderer()
                                .setTitle(nextRuns(tr("Comments"))));
    }

    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
            .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer.Header
            commentsHeader(String videoId, boolean newest) {
        return new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer
                .Header().setRaw(commentsHeaderRenderer(videoId, newest));
    }

    /**
     * The comments' header with its sort menu: commentsHeaderRenderer acfr (ext 105977417 of the
     * section header union) {title = 1, sortMenu = 3 (union acfh {sortFilterSubMenuRenderer
     * afkc 76818770})}. The menu's items (afkb) carry reloadContinuationData (field 3, union afkd
     * {60487319: afad {continuation = 1}}): choosing one makes the app ask /next with that token
     * and replace the comments with the answer (commentsReload).
     */
    private static byte[] commentsHeaderRenderer(String videoId, boolean newest) {
        com.viitube.proxy.proto.ProtoWriter menu = new com.viitube.proxy.proto.ProtoWriter();   // afkc
        String[] labels = {tr("Top comments"), tr("Newest first")};
        for (int i = 0; i < 2; i++) {
            com.viitube.proxy.proto.ProtoWriter reload = new com.viitube.proxy.proto.ProtoWriter(); // afad
            reload.writeString(1, YoutubeiEndpoint.commentsSortToken(videoId, i == 1));
            com.viitube.proxy.proto.ProtoWriter item = new com.viitube.proxy.proto.ProtoWriter();   // afkb
            item.writeString(1, labels[i]);
            if ((i == 1) == newest) item.writeBool(2, true);
            item.writeBytes(3, rawField(60487319, reload.toByteArray()));
            menu.writeBytes(1, item.toByteArray());
        }
        menu.writeString(2, tr("Sort by"));
        com.viitube.proxy.proto.ProtoWriter header = new com.viitube.proxy.proto.ProtoWriter();     // acfr
        header.writeBytes(1, formattedBytes(tr("Comments")));
        // The heading the app draws: title = 10, the count beside it = 11.
        header.writeBytes(10, formattedBytes(tr("Comments")));
        String count = InnertubeEndpoint.commentsCount(videoId);
        if (!count.isEmpty()) header.writeBytes(11, formattedBytes(count));
        if (YouTubeAuth.signedIn()) {
            // "Add a public comment..." with the account's picture: createRenderer = 2 (union
            // aceq {commentSimpleboxRenderer acfi 93764576 {picture = 3, placeholder = 4}}).
            String photo = YouTubeAuth.accountPhoto();
            com.viitube.proxy.proto.ProtoWriter box = new com.viitube.proxy.proto.ProtoWriter();  // acfi
            box.writeBytes(3, browseThumbnails(avatarOrDefault(photo), 88, 88).toByteArray());
            box.writeBytes(4, formattedBytes(tr("Add a public comment...")));
            header.writeBytes(2, rawField(93764576, box.toByteArray()));
        }
        header.writeBytes(3, rawField(76818770, menu.toByteArray()));
        return rawField(105977417, header.toByteArray());
    }

    private static AndroidFullNextProto.ShelfRenderer relatedShelf(
            List<InnertubeEndpoint.Video> related, long now) {
        AndroidFullNextProto.ShelfRenderer.Contents.ShelfContents items =
                new AndroidFullNextProto.ShelfRenderer.Contents.ShelfContents();
        for (InnertubeEndpoint.Video video : related) {
            items.addItem(new AndroidFullNextProto.ShelfRenderer.Contents.ShelfContents.Items()
                    .addVideo(nextVideo(video, now)));
        }
        return new AndroidFullNextProto.ShelfRenderer()
                .setTitle(nextRuns(tr("Up next")))
                .setContent(new AndroidFullNextProto.ShelfRenderer.Contents().setShelfContent(
                        items.setCollapsedItemCount(related.size())))
                .setAutoplayToggle(new AndroidFullNextProto.ShelfRenderer.Toggle().addButton(
                        new AndroidFullNextProto.ToggleButtonRenderer()
                                .setDefaultText(nextRuns(tr("Autoplay")))
                                .setToggledText(nextRuns(tr("Autoplay")))));
    }

    /**
     * The watch page: video metadata, channel with subscribe button, related videos. Apps from
     * version 12 get the metadata as slimVideoMetadataRenderer: they still parse
     * videoMetadataRenderer but have no view for it, so with the older message their watch page
     * stayed empty above the related videos.
     */
    static byte[] next(InnertubeEndpoint.WatchPage page, long now, int clientMajorVersion,
                       String relatedToken, String commentsToken,
                       List<InnertubeEndpoint.Comment> comments, Queue queue) {
        InnertubeEndpoint.Video video = page.video;
        String channelId = video.channelId;
        long views = parseCount(video.views);
        long likes = parseCount(video.likes);

        AndroidFullNextProto.VideoMetadataRenderer metadata = new AndroidFullNextProto.VideoMetadataRenderer()
                .setTitle(nextRuns(video.title))
                .setViewCount(nextRuns(fullCount(views, "view", "views")))
                .setDescription(linkedRuns(video.description))
                .setPublishDate(nextRuns(publishedOn(video.published, page.dateText)))
                .setVideoId(video.id)
                .setShortViewCount(nextRuns(shortCount(views, "views")))
                .setAllowRatings(1)
                .setIsLimitedState(0)
                .addPb13(new AndroidFullNextProto.VideoMetadataRenderer.PbVarious13().addPb(
                        new AndroidFullNextProto.VideoMetadataRenderer.PbVarious13.PbVarious()
                                .setVideoId(nextRuns(video.id))
                                .addCount1(new AndroidFullNextProto.SimpleText().setText(likes + " likes"))
                                .setInt2(2)
                                .setInt3((int) Math.min(likes, Integer.MAX_VALUE))
                                .setInt13(1)
                                .setInt23(0)));

        AndroidFullNextProto.AuthorRendererData.SubscribeButtonData.SubContents subscribe =
                nextSubscribeButton(channelId, page.subscribers);
        AndroidFullNextProto.AuthorRendererData author = new AndroidFullNextProto.AuthorRendererData()
                .setAvatars(nextThumbnails(avatarOrDefault(page.ownerAvatar), 48, 48))
                .setName(nextRuns(video.channel))
                .setNaviData(nextChannelNavigation(channelId))
                .addSubscribeButton(new AndroidFullNextProto.AuthorRendererData.SubscribeButtonData()
                        .addContent(subscribe));
        String subscribers = subscriberText(page.subscribers);
        if (!subscribers.isEmpty()) author.setSubscribeCount(nextRuns(subscribers));

        // One entry of the list per section: the app reads each entry as a single renderer and
        // merges what arrives in one of them, so sections written together would end up as one
        // and two headers would end up as a single header.
        AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results results =
                new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                        .SingleColumnWatchNextResultsContents.Results();
        if (clientMajorVersion >= PIVOT_BAR_VERSION) {
            results.addContent(listEntry().addItemSectionRender(nextSection().addContent(
                    nextSectionContents().addSlimVideoMetadata(
                            slimMetadata(page, subscribe, now, clientMajorVersion)))));
        } else {
            results.addContent(listEntry().addItemSectionRender(nextSection()
                    .addContent(nextSectionContents().addVideoMetadata(metadata))));
            results.addContent(listEntry().addItemSectionRender(nextSection()
                    .addContent(nextSectionContents().addAuthorData(author))));
        }
        // The related videos. From version 11 they go into a shelf of the section list, which is
        // what carries "Up next" and the app's own Autoplay switch above them; the older apps
        // get the plain section they have always had.
        if (clientMajorVersion >= AUTOPLAY_SWITCH_VERSION && !page.related.isEmpty()) {
            results.addContent(listEntry().addShelf(relatedShelf(page.related, now)));
        } else {
            results.addContent(listEntry().addItemSectionRender(relatedSection(page.related, now)));
        }
        // The comments come after the related videos, under a header of their own. The list of
        // related videos then ends after one page - the token of the list goes to the comments
        // instead - so scrolling to the bottom of the watch page arrives at the comments and
        // goes on through them.
        boolean sortable = clientMajorVersion >= PIVOT_BAR_VERSION;
        if (!comments.isEmpty() || (commentsToken != null && !commentsToken.isEmpty())) {
            AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                    .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer
                    commentSection = nextSection().setSectionHeader(sortable
                            ? commentsHeader(video.id, false) : commentsTitleHeader());
            for (InnertubeEndpoint.Comment comment : comments) {
                commentSection.addContent(nextSectionContents().addCommentThread(commentThread(comment, now)));
            }
            if (sortable && commentsToken != null && !commentsToken.isEmpty()) {
                commentSection.addCommentRequest(commentRequest(commentsToken));
            }
            results.addContent(listEntry().addItemSectionRender(commentSection));
        }
        // One token for the whole list: the comments when they are switched on - whether the
        // first ones made it into this answer or not - and more related videos otherwise. From
        // 12 on the comments carry their own token (in their section, for the sort menu).
        String listToken = commentsToken == null || commentsToken.isEmpty()
                ? relatedToken : sortable ? "" : commentsToken;
        if (listToken != null && !listToken.isEmpty()) {
            results.addContinuation(nextContinuations(listToken));
        }
        AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults watchResults =
                new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults().addContent(
                        new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                                .SingleColumnWatchNextResultsContents().addResult(results));
        if (queue != null && !queue.videos.isEmpty()) {
            watchResults.setPlaylist(playlistPanel(queue, now));
        }
        // Where the app goes when the video ends. In a playlist that is the next entry of the
        // queue, and the queue is also what the buttons beside the play button step through;
        // outside one it is the first related video, played only when autoplay is switched on.
        if (queue != null) {
            watchResults.setAutoplayNext(queueSet(queue));
        } else if (!page.related.isEmpty()) {
            watchResults.setAutoplayNext(autoplaySet(page.related.get(0).id));
        }
        return new AndroidFullNextProto.Root()
                .addContent(new AndroidFullNextProto.Root.Contents().addNextResults(watchResults))
                .setNavigationEndpoint(new AndroidFullNextProto.NavigationData().setNavProperties(
                        new AndroidFullNextProto.NavigationData.NavData().setNavId(video.id)))
                .setPoverlays(new AndroidFullNextProto.Root.PlayerOverlays().setOverlay(
                        playerOverlay(video, page.related, now)))
                .toByteArray();
    }

    /**
     * What the player draws over the video: the title and channel in fullscreen, and the list of
     * videos that comes up when fullscreen is swiped up and when the video ends.
     */
    private static AndroidFullNextProto.Root.PlayerOverlays.PlayerOverlaysRenderer playerOverlay(
            InnertubeEndpoint.Video video, List<InnertubeEndpoint.Video> related, long now) {
        AndroidFullNextProto.OverlayVideoDetails details = new AndroidFullNextProto.OverlayVideoDetails()
                .setTitle(nextRuns(video.title));
        if (!video.channel.isEmpty()) details.setSubtitle(nextRuns(video.channel));
        AndroidFullNextProto.Root.PlayerOverlays.PlayerOverlaysRenderer overlay =
                new AndroidFullNextProto.Root.PlayerOverlays.PlayerOverlaysRenderer()
                        .setDetails(new AndroidFullNextProto.OverlayDetailsSlot().setDetails(details))
                        .setShowButtons(true);
        // The share arrow of the top bar: the same button and share sheet as under the video.
        AndroidFullNextProto.ButtonEndpoint share = shareEndpoint(video.id, video.title);
        overlay.setShareButton(new AndroidFullNextProto.SlimMetadataButton.SlimMetadataButtonRenderer
                .ButtonContainer().setButton(new AndroidFullNextProto.ButtonRenderer()
                        .setText(nextRuns(tr("Share")))
                        .setIcon(new AndroidFullNextProto.IconContainer().setIconType(ICON_SHARE))
                        .setServiceEndpoint(share).setNavigationEndpoint(share)));
        if (related != null && !related.isEmpty()) {
            AndroidFullNextProto.EndScreen endScreen = new AndroidFullNextProto.EndScreen()
                    .setTitle(nextRuns(tr("Up next")));
            for (int i = 0; i < related.size() && i < 12; i++) {
                InnertubeEndpoint.Video item = related.get(i);
                long views = parseCount(item.views);
                AndroidFullNextProto.EndScreenVideo entry = new AndroidFullNextProto.EndScreenVideo()
                        .setVideoId(item.id)
                        .setThumbnail(nextThumbnails(hqThumbnail(item.id), 480, 360))
                        .setTitle(nextRuns(item.title))
                        .setByline(nextRuns(item.channel))
                        .setLength(nextRuns(lengthText(item.duration)))
                        .setViews(nextRuns(shortCount(views, "views")))
                        .setLengthSeconds(InnertubeEndpoint.durationSecondsPublic(item.duration))
                        .setNavigation(new AndroidFullNextProto.NavigationData().setNavProperties(
                                new AndroidFullNextProto.NavigationData.NavData().setNavId(item.id)));
                endScreen.addResult(new AndroidFullNextProto.EndScreenItem().setVideo(entry));
            }
            overlay.setEndScreen(new AndroidFullNextProto.EndScreenSlot().setEndScreen(endScreen));
        }
        return overlay;
    }

    /** One entry of the section list, which holds exactly one renderer. */
    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
            .SingleColumnWatchNextResultsContents.Results.ResultsContents listEntry() {
        return new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results.ResultsContents();
    }

    private static AndroidFullNextProto.Continuations nextContinuations(String token) {
        return new AndroidFullNextProto.Continuations().addNext(
                new AndroidFullNextProto.Continuations.NextContinuationData().setToken(token));
    }

    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
            .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer
            relatedSection(List<InnertubeEndpoint.Video> related, long now) {
        AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer section =
                nextSection();
        for (InnertubeEndpoint.Video item : related) {
            section.addContent(nextSectionContents().addNextVideo(nextVideo(item, now)));
        }
        return section;
    }

    /** One comment, as the apps of 2015-2017 draw it, with a "View N replies" line under it. */
    private static AndroidFullNextProto.CommentThreadRenderer commentThread(
            InnertubeEndpoint.Comment comment, long now) {
        AndroidFullNextProto.CommentThreadRenderer thread = new AndroidFullNextProto.CommentThreadRenderer()
                .setComment(new AndroidFullNextProto.CommentThreadRenderer.CommentWrapper()
                        .setComment(commentRenderer(comment, now)));
        long replies = parseCount(comment.replyCount);
        if (replies > 0) {
            // commentRepliesRenderer acez (thread field 2, union acfd 65060753): its button
            // (field 8, a buttonRenderer abxa {text = 5}) is the "View N replies" line the thread
            // view (rbg -> rba) draws under the comment. It opens nothing yet: the app's replies
            // panel (showEngagementPanel) is not served.
            com.viitube.proxy.proto.ProtoWriter button = new com.viitube.proxy.proto.ProtoWriter(); // abxa
            button.writeBytes(5, formattedBytes(viewReplies(comment.replyCount, replies)));
            com.viitube.proxy.proto.ProtoWriter renderer = new com.viitube.proxy.proto.ProtoWriter(); // acez
            renderer.writeBytes(8, rawField(65153809, button.toByteArray()));
            thread.setRawReplies(rawField(65060753, renderer.toByteArray()));
        }
        return thread;
    }

    /** "View 66 replies", "View reply"; YouTube's own count text is kept ("1.2K"). */
    private static String viewReplies(String countText, long count) {
        if (isPolish()) {
            return count == 1 ? "Wyświetl odpowiedź" : "Wyświetl " + countText + " odpowiedzi";
        }
        return count == 1 ? "View reply" : "View " + countText + " replies";
    }

    private static AndroidFullNextProto.CommentRenderer commentRenderer(
            InnertubeEndpoint.Comment comment, long now) {
        AndroidFullNextProto.CommentRenderer renderer = new AndroidFullNextProto.CommentRenderer()
                .setCommentId(comment.id)
                .setAuthor(nextRuns(comment.author))
                .setContent(nextRuns(comment.text))
                .setAuthorThumbnail(nextThumbnails(avatarOrDefault(comment.avatar), 48, 48));
        if (!comment.authorId.isEmpty()) {
            renderer.setAuthorEndpoint(nextChannelNavigation(comment.authorId));
        }
        // The subtitle line under the author is field 5 (field 28 is not drawn by these apps, which
        // is why the time used to be missing). Keep the time there; the counts go on the buttons.
        String age = relativeTime(comment.published, now);
        if (!age.isEmpty()) renderer.setSecondary(nextRuns(age));
        if (!comment.likeAction.isEmpty() && !comment.dislikeAction.isEmpty()) {
            // Like / dislike / reply row with the counts (commentRenderer fields 10 and 28).
            boolean liked = "LIKE".equals(comment.voteStatus);
            String without = comment.likeCount.isEmpty() ? "0" : comment.likeCount;
            String with = comment.likeCountLiked.isEmpty() ? bumpCount(without, 1) : comment.likeCountLiked;
            renderer.addRawField(10, actionButtons(comment.voteStatus, comment.likeAction,
                    comment.unlikeAction, comment.dislikeAction, comment.undislikeAction, with,
                    without, comment.replyCount));
            renderer.addRawField(28, formattedBytes(liked ? with : without));
        }
        return renderer;
    }

    /**
     * The comments under a video, as the answer to the continuation the watch page asked for.
     * They go back as more entries for the section that is already on screen.
     */
    static byte[] commentsContinuation(List<InnertubeEndpoint.Comment> comments, long now,
                                       String continuationToken) {
        AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer section =
                nextSection();
        for (InnertubeEndpoint.Comment comment : comments) {
            section.addContent(nextSectionContents().addCommentThread(commentThread(comment, now)));
        }
        AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results results =
                new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                        .SingleColumnWatchNextResultsContents.Results()
                        .addContent(listEntry().addItemSectionRender(section));
        if (continuationToken != null && !continuationToken.isEmpty()) {
            results.addContinuation(nextContinuations(continuationToken));
        }
        // The same kind of answer the related videos give: the comments are the end of the list
        // of the watch page, and the app asks for the next page of that list as it scrolls.
        return new AndroidFullNextProto.Root().setContinuation(
                new AndroidFullNextProto.Root.ContinuationContents()
                        .addSectionListContinuation(results)).toByteArray();
    }

    /**
     * A page of the comments' own section (apps from 12 on): itemSectionContinuation (Root
     * continuationContents 50195462, itemSectionRenderer adpe {contents = 1, continuations = 2,
     * header = 5}). With {@code reloadVideoId} it is the first page of a sort choice, which
     * replaces the section and carries the menu showing that choice.
     */
    static byte[] commentsSection(List<InnertubeEndpoint.Comment> comments, long now,
                                  String continuationToken, String reloadVideoId, boolean newest) {
        AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer section =
                nextSection();
        if (reloadVideoId != null) section.setSectionHeader(commentsHeader(reloadVideoId, newest));
        for (InnertubeEndpoint.Comment comment : comments) {
            section.addContent(nextSectionContents().addCommentThread(commentThread(comment, now)));
        }
        if (continuationToken != null && !continuationToken.isEmpty()) {
            section.addCommentRequest(commentRequest(continuationToken));
        }
        return new AndroidFullNextProto.Root().setContinuation(
                new AndroidFullNextProto.Root.ContinuationContents()
                        .addItemSectionContinuation(section)).toByteArray();
    }

    /** The token the app sends back to ask for the comments of a video. */
    private static AndroidFullNextProto.NextRequest commentRequest(String token) {
        return new AndroidFullNextProto.NextRequest().addContinuation(
                new AndroidFullNextProto.NextRequest.ContinuationData().setToken(token));
    }

    /** More related videos for the list under a video, as the answer to a continuation request. */
    static byte[] relatedContinuation(List<InnertubeEndpoint.Video> related, long now,
                                      String continuationToken) {
        AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results results =
                new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                        .SingleColumnWatchNextResultsContents.Results()
                        .addContent(listEntry().addItemSectionRender(relatedSection(related, now)));
        if (continuationToken != null && !continuationToken.isEmpty()) {
            results.addContinuation(nextContinuations(continuationToken));
        }
        return new AndroidFullNextProto.Root().setContinuation(
                new AndroidFullNextProto.Root.ContinuationContents()
                        .addSectionListContinuation(results)).toByteArray();
    }

    /** The subscribe button; the 2017 apps read the subscriber count out of it. */
    private static AndroidFullNextProto.AuthorRendererData.SubscribeButtonData.SubContents
            nextSubscribeButton(String channelId, String subscribers) {
        AndroidFullNextProto.AuthorRendererData.SubscribeButtonData.SubContents subscribe =
                new AndroidFullNextProto.AuthorRendererData.SubscribeButtonData.SubContents()
                        .setChannelId(channelId)
                        .setInt3(AccountActions.isSubscribed(channelId) ? 1 : 0).setInt4(1).setInt6(0).setInt8(0)
                        .setSubscribeButton(nextRunsBold("SUBSCRIBE"))
                        .setSubscribedLabel(nextRunsBold("SUBSCRIBED"))
                        .setSubscribeLabel(nextRunsBold("SUBSCRIBE"))
                        .setUnsubscribeLabel(nextRunsBold("UNSUBSCRIBE"))
                        .addAction(new AndroidFullNextProto.AuthorRendererData.SubscribeButtonData.SubContents
                                .SubAction().addSubscr1(new AndroidFullNextProto.AuthorRendererData
                                        .SubscribeButtonData.SubContents.SubAction.Sub1()
                                        .setId(channelId).setParams("b")))
                        .addAction(new AndroidFullNextProto.AuthorRendererData.SubscribeButtonData.SubContents
                                .SubAction().addSubscr2(new AndroidFullNextProto.AuthorRendererData
                                        .SubscribeButtonData.SubContents.SubAction.Sub2()
                                        .setId(channelId).setParams("c")))
                        .setIdentifier("watch-subscribe");
        long count = parseCount(subscribers);
        if (count > 0) {
            // Field 20 is the line of the expanded panel and field 23 the one of the collapsed
            // panel, the other way round from what their names on the reference server suggest.
            subscribe.setShortSubscriberCount(nextRuns(fullCount(count, "subscriber", "subscribers")));
            subscribe.setLongSubscriberCount(nextRuns(shortCount(count, "subscribers")));
        }
        return subscribe;
    }

    /**
     * The share sheet of the system, as the 2017 apps build it: every extra of an ACTION_SEND
     * intent, its type and the title of the chooser.
     */
    private static AndroidFullNextProto.ButtonEndpoint shareEndpoint(String videoId, String title) {
        AndroidFullNextProto.ButtonEndpoint.ShareIntent share =
                new AndroidFullNextProto.ButtonEndpoint.ShareIntent()
                        .setType("text/plain")
                        .setChooserTitle(tr("Share"))
                        .addExtras(new AndroidFullNextProto.ButtonEndpoint.ShareIntent.Extra()
                                .setKey("android.intent.extra.TEXT")
                                .setValue("https://youtu.be/" + videoId))
                        .addExtras(new AndroidFullNextProto.ButtonEndpoint.ShareIntent.Extra()
                                .setKey("android.intent.extra.SUBJECT")
                                .setValue(title));
        return new AndroidFullNextProto.ButtonEndpoint().setShare(share);
    }

    /**
     * The like or the dislike button: a toggle whose taps send likeEndpoints (like/like or
     * like/dislike, then like/removelike), which the proxy carries out on the signed-in account.
     * Toggled, it shows the count one higher.
     */
    private static AndroidFullNextProto.SlimMetadataButton likeButton(String videoId, boolean like,
                                                                     long count, String label,
                                                                     boolean rated) {
        // Already rated: the count includes the account's own vote, so "off" is one lower.
        long off = rated ? Math.max(0, count - 1) : count;
        AndroidFullNextProto.ToggleButtonRenderer toggle = new AndroidFullNextProto.ToggleButtonRenderer()
                .setIsToggled(rated ? 1 : 0)
                .setDefaultIcon(new AndroidFullNextProto.IconContainer()
                        .setIconType(like ? ICON_LIKE : ICON_DISLIKE))
                // LIKE_SELECTED / DISLIKE_SELECTED: the blue thumbs (IconType 301/302, gsu).
                .setToggledIcon(new AndroidFullNextProto.IconContainer()
                        .setIconType(like ? ICON_LIKE_SELECTED : ICON_DISLIKE_SELECTED))
                .setDefaultText(nextRuns(off > 0 ? shortNumber(off) : label))
                .setToggledText(nextRuns(shortNumber(off + 1)))
                .setDefaultServiceEndpoint(new LikeProto.Endpoint(
                        like ? LikeProto.LIKE : LikeProto.DISLIKE, videoId))
                .setToggledServiceEndpoint(new LikeProto.Endpoint(LikeProto.INDIFFERENT, videoId));
        return new AndroidFullNextProto.SlimMetadataButton()
                .setToggleButton(new LikeProto.SlimToggleButton(videoId, like, toggle));
    }

    /** One of the buttons under the video of the 2017 apps' watch page. */
    private static AndroidFullNextProto.SlimMetadataButton slimButton(int icon, String text) {
        return slimButton(icon, text, null);
    }

    private static AndroidFullNextProto.SlimMetadataButton slimButton(int icon, String text,
                                                                     AndroidFullNextProto.ButtonEndpoint endpoint) {
        AndroidFullNextProto.ButtonRenderer button = new AndroidFullNextProto.ButtonRenderer()
                .setText(nextRuns(text))
                .setIcon(new AndroidFullNextProto.IconContainer().setIconType(icon));
        if (endpoint != null) {
            button.setServiceEndpoint(endpoint).setNavigationEndpoint(endpoint);
        }
        return new AndroidFullNextProto.SlimMetadataButton().addButton(
                new AndroidFullNextProto.SlimMetadataButton.SlimMetadataButtonRenderer().setButton(
                        new AndroidFullNextProto.SlimMetadataButton.SlimMetadataButtonRenderer
                                .ButtonContainer().setButton(button)));
    }

    /**
     * The watch metadata of the 2017 apps: title, the collapsed and the expanded subtitle, the
     * like and dislike buttons, the channel row and the description (the apps show it after the
     * panel is expanded).
     */
    /** Apps from this version on get "Up next" with the Autoplay switch over the related videos. */
    static final int AUTOPLAY_SWITCH_VERSION = 11;

    /** Apps from this version on draw a Download button in the row under a video. */
    private static final int DOWNLOAD_BUTTON_VERSION = 14;

    private static AndroidFullNextProto.SlimVideoMetadataRenderer slimMetadata(
            InnertubeEndpoint.WatchPage page,
            AndroidFullNextProto.AuthorRendererData.SubscribeButtonData.SubContents subscribe,
            long now, int clientMajorVersion) {
        InnertubeEndpoint.Video video = page.video;
        long views = parseCount(video.views);
        long likes = parseCount(video.likes);
        String published = publishedOn(video.published, page.dateText);
        String expanded = fullCount(views, "view", "views");
        if (!published.isEmpty()) expanded = expanded + " \u00b7 " + published;

        AndroidFullNextProto.SlimVideoMetadataRenderer slim =
                new AndroidFullNextProto.SlimVideoMetadataRenderer()
                        .setTitle(nextRuns(video.title))
                        .setCollapsedSubtitle(nextRuns(shortCount(views, "views")))
                        .setExpandedSubtitle(nextRuns(expanded))
                        .setVideoId(video.id);
        if (!video.description.isEmpty()) slim.setDescription(linkedRuns(video.description));
        long dislikes = parseCount(video.dislikes);
        slim.addButtons(likeButton(video.id, true, likes, tr("Like"), "LIKE".equals(page.likeStatus)));
        slim.addButtons(likeButton(video.id, false, dislikes, tr("Dislike"), "DISLIKE".equals(page.likeStatus)));
        slim.addButtons(slimButton(ICON_SHARE, tr("Share"), shareEndpoint(video.id, video.title)));
        if (clientMajorVersion >= DOWNLOAD_BUTTON_VERSION) {
            slim.addButtons(slimButton(ICON_DOWNLOAD, tr("Download")));
        }
        slim.addButtons(slimButton(ICON_ADDTO, "Save",
                new AndroidFullNextProto.ButtonEndpoint().setAddToPlaylist(video.id)));

        AndroidFullNextProto.SlimOwner.SlimOwnerRenderer owner =
                new AndroidFullNextProto.SlimOwner.SlimOwnerRenderer()
                        .setAvatar(nextThumbnails(avatarOrDefault(page.ownerAvatar), 88, 88))
                        .setTitle(nextRuns(video.channel));
        if (!video.channelId.isEmpty()) owner.setNavigation(nextChannelNavigation(video.channelId));
        owner.setSubscribeButton(new AndroidFullNextProto.SlimOwner.SlimOwnerRenderer
                .SubscribeContainer().addButton(subscribe));
        slim.setOwner(new AndroidFullNextProto.SlimOwner().addOwner(owner));
        return slim;
    }

    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
            .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer nextSection() {
        return new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer();
    }

    private static AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
            .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer
            .ItemSectionRendererContents nextSectionContents() {
        return new AndroidFullNextProto.Root.Contents.SingleColumnWatchNextResults
                .SingleColumnWatchNextResultsContents.Results.ResultsContents.ItemSectionRenderer
                .ItemSectionRendererContents();
    }

    // ------------------------------------------------------------------ player (android_player)

    static final class PlayerInfo {
        String videoId = "";
        String title = "";
        int lengthSeconds;
        String channelId = "";
        String author = "";
        String description = "";
        long viewCount;
        /** Set when there is nothing to play; the app shows it instead of starting the player. */
        String error = "";
        final java.util.List<StreamFormat> formats = new java.util.ArrayList<StreamFormat>();
        final java.util.List<CaptionTrack> captions = new java.util.ArrayList<CaptionTrack>();
    }

    /** One subtitle track of the player answer. */
    static final class CaptionTrack {
        final String url;
        final String name;
        final String languageCode;
        final String vssId;
        final boolean translatable;

        CaptionTrack(String url, String name, String languageCode, String vssId, boolean translatable) {
            this.url = url;
            this.name = name;
            this.languageCode = languageCode;
            this.vssId = vssId;
            this.translatable = translatable;
        }
    }

    static final class StreamFormat {
        final int itag;
        final String url;
        final int width;
        final int height;
        final String quality;
        final String label;
        final String mimeType;
        /** Set when the format belongs in the adaptive list of the answer instead of the plain one. */
        boolean adaptive;
        int bitrate;
        long contentLength;
        int[] initRange;
        int[] indexRange;

        StreamFormat(int itag, String url, int width, int height, String quality, String label,
                     String mimeType) {
            this.itag = itag;
            this.url = url;
            this.width = width;
            this.height = height;
            this.quality = quality;
            this.label = label;
            this.mimeType = mimeType;
        }
    }

    static StreamFormat progressive360p(String url) {
        return new StreamFormat(18, url, 640, 360, "medium", "360p",
                "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"");
    }

    /**
     * The audio-only file the apps ask for when they play a video in the background. Without one
     * they log "No streaming data available for background playback" and stop at the lock screen.
     */
    static StreamFormat audioOnly(String url) {
        return new StreamFormat(140, url, 0, 0, "tiny", "",
                "audio/mp4; codecs=\"mp4a.40.2\"");
    }

    /**
     * The sound of the video as an adaptive stream. The apps keep two lists of formats: playing
     * a video on screen reads the plain one, playing it in the background reads the adaptive one
     * and only accepts itag 139 or 140 in it ("Audio not supported/available" otherwise), so the
     * same file is offered in both.
     */
    static StreamFormat adaptiveAudio(int itag, String url, String mimeType, int bitrate,
                                      long contentLength, int[] initRange, int[] indexRange) {
        StreamFormat format = new StreamFormat(itag == 139 ? 139 : 140, url, 0, 0, "tiny", "",
                mimeType.isEmpty() ? "audio/mp4; codecs=\"mp4a.40.2\"" : mimeType);
        format.adaptive = true;
        format.bitrate = bitrate;
        format.contentLength = contentLength;
        format.initRange = initRange;
        format.indexRange = indexRange;
        return format;
    }

    static StreamFormat muxed720p(String url) {
        return muxedQuality(720, url);
    }

    /**
     * One quality of the stream this proxy muxes. The itag and the quality name are YouTube's
     * own for that height - 17, 36, 18, 59, 22, 37 - because that is the table these apps were
     * written against; a number they do not know is dropped from their quality menu.
     */
    static StreamFormat muxedQuality(int height, String url) {
        int itag;
        String quality;
        if (height >= 1080) {
            itag = 37;
            quality = "hd1080";
        } else if (height >= 720) {
            itag = 22;
            quality = "hd720";
        } else if (height >= 480) {
            itag = 59;
            quality = "large";
        } else if (height >= 360) {
            itag = 18;
            quality = "medium";
        } else if (height >= 240) {
            itag = 36;
            quality = "small";
        } else {
            itag = 17;
            quality = "tiny";
        }
        return new StreamFormat(itag, url, height * 16 / 9, height, quality, height + "p",
                "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"");
    }

    static byte[] player(PlayerInfo info) {
        AndroidPlayerProto.Root root = new AndroidPlayerProto.Root();
        AndroidPlayerProto.Root.PlayabilityStatus playability = new AndroidPlayerProto.Root.PlayabilityStatus();
        if (info.error.isEmpty() && !info.formats.isEmpty()) {
            playability.setStatus(0).setPlayableInEmbed(1).setContentParams("CAESAggB");
        } else {
            // Innertube status 2 is UNPLAYABLE; the reason is shown to the user.
            playability.setStatus(2).setError(info.error.isEmpty()
                    ? "This video is not available on this device" : info.error);
        }
        root.addPlayability(playability);

        if (!info.formats.isEmpty()) {
            AndroidPlayerProto.Root.PlayerFormats formats = new AndroidPlayerProto.Root.PlayerFormats()
                    .setExpiresInSeconds(21540);
            for (StreamFormat format : info.formats) {
                AndroidPlayerProto.Root.PlayerFormats.Format entry =
                        new AndroidPlayerProto.Root.PlayerFormats.Format()
                                .setFormatId(format.itag)
                                .setUrl(format.url)
                                .setMimeType(format.mimeType)
                                .setVideoWidth(format.width)
                                .setVideoHeight(format.height)
                                .setVideoQuality(format.quality)
                                .setQualityLabel(format.label)
                                .setSortKey(format.height);
                if (!format.adaptive) {
                    formats.addNondashFormat(entry);
                    continue;
                }
                entry.setTotalBitrate(format.bitrate).setContentLength(format.contentLength);
                if (format.initRange != null) {
                    entry.addInitRange(new AndroidPlayerProto.NumberRange()
                            .setStart(format.initRange[0]).setEnd(format.initRange[1]));
                }
                if (format.indexRange != null) {
                    entry.addIndexRange(new AndroidPlayerProto.NumberRange()
                            .setStart(format.indexRange[0]).setEnd(format.indexRange[1]));
                }
                formats.addDashFormat(entry);
            }
            root.addFormats(formats);
        }

        // The subtitle tracks. The app fetches the link of a track as it is, so it points back
        // at this proxy, which turns YouTube's own answer into the transcript these apps read.
        if (!info.captions.isEmpty()) {
            AndroidPlayerProto.Root.CaptionTracks.Contents tracks =
                    new AndroidPlayerProto.Root.CaptionTracks.Contents();
            for (CaptionTrack track : info.captions) {
                tracks.addTrack(new AndroidPlayerProto.Root.CaptionTracks.Contents.CaptionTrack()
                        .setUrl(track.url)
                        .addName(new AndroidPlayerProto.TextRuns().addRun(
                                new AndroidPlayerProto.TextRuns.TextRun().setText(track.name)))
                        .setVssId(track.vssId)
                        .setLanguageCode(track.languageCode)
                        .setIsTranslatable(track.translatable ? 1 : 0));
            }
            // The audio track the subtitles belong to. Without it the app has nothing to read
            // the list of tracks out of, and starting the video ends in a NullPointerException.
            AndroidPlayerProto.Root.CaptionTracks.Contents.AudioTracks audio =
                    new AndroidPlayerProto.Root.CaptionTracks.Contents.AudioTracks();
            for (int i = 0; i < info.captions.size(); i++) audio.addCaptionTrackIndices(i);
            tracks.addAudioTrack(audio.setDefaultCaptionTrackIndex(0)).setDefaultAudioTrackIndex(0);
            root.addCaptions(new AndroidPlayerProto.Root.CaptionTracks().addContent(tracks));
        }

        root.addVideoMetadata(new AndroidPlayerProto.Root.Metadata()
                .setId(info.videoId)
                .setTitle(info.title)
                .setVideoLength(info.lengthSeconds)
                .setChannelId(info.channelId)
                .setDescription(info.description)
                .addThumbnails(new AndroidPlayerProto.Root.Metadata.ThumbList().addThumbnail(
                        new AndroidPlayerProto.Root.Metadata.ThumbList.Thumb()
                                .setUrl(hqThumbnail(info.videoId)).setWidth(480).setHeight(360)))
                .setViewCount(Long.toString(info.viewCount))
                .setAuthorName(info.author)
                .setIsOwnerViewing(0)
                .setIsCrawlable(0)
                .setAllowRatings(1)
                .setIsPrivate(0)
                .setIsUnpluggedCorpus(0)
                .setIsLiveContent(0));
        root.setPbVarious69("CAA%3D");
        return root.toByteArray();
    }

    // ------------------------------------------------------------------ guide and resolve_url

    private static AndroidGuideProto.BrowseNavigation guideNavigation(String browseId) {
        return new AndroidGuideProto.BrowseNavigation().addData(
                new AndroidGuideProto.BrowseNavigation.BrowseData().setBrowseId(browseId));
    }

    private static AndroidGuideProto.TextRuns guideRuns(String text) {
        return new AndroidGuideProto.TextRuns().addRun(new AndroidGuideProto.TextRuns.TextRun().setText(text));
    }

    private static AndroidGuideProto.PivotBarItem pivotItem(String identifier, String title, int icon) {
        return new AndroidGuideProto.PivotBarItem().addItem(
                new AndroidGuideProto.PivotBarItem.PivotBarItemRenderer()
                        .setPivotIdentifier(identifier)
                        .addNavigation(guideNavigation(identifier))
                        .addTitle(guideRuns(title))
                        .addIcon(new AndroidGuideProto.PivotBarItem.PivotBarItemRenderer.IconContainer()
                                .setIconType(icon)));
    }

    /**
     * The classic bottom bar of the 2017 apps, as an item of the guide answer: Home, Trending,
     * Library and Account. The app reads the pivot bar out of the guide response it keeps on
     * disk, so it appears on the next start of the app.
     */
    private static AndroidGuideProto.Root.Items.PivotBarRenderer pivotBar() {
        return new AndroidGuideProto.Root.Items.PivotBarRenderer()
                .addItems(pivotItem("FEwhat_to_watch", "Home", ICON_PIVOT_HOME))
                .addItems(pivotItem("FEtrending", "Trending", ICON_PIVOT_TRENDING))
                .addItems(pivotItem("FEsubscriptions", "Subscriptions", ICON_PIVOT_SUBSCRIPTIONS))
                .addItems(pivotItem("FElibrary", "Library", ICON_PIVOT_LIBRARY));
    }

    static byte[] guide(int clientMajorVersion) {
        AndroidGuideProto.Root root = new AndroidGuideProto.Root();
        AndroidGuideProto.GuideEntry home = new AndroidGuideProto.GuideEntry()
                .setTitle("What to Watch")
                .addIcon(new AndroidGuideProto.GuideEntry.IconContainer().setIconType(5))
                .addNavigation(guideNavigation("FEwhat_to_watch"));
        root.addItem(new AndroidGuideProto.Root.Items().addGuideSection(
                new AndroidGuideProto.Root.Items.GuideSectionRenderer().addContent(
                        new AndroidGuideProto.Root.Items.GuideSectionRenderer.Contents().addEntry(home))));

        AndroidGuideProto.Root.Items.GuideSectionRenderer best = new AndroidGuideProto.Root.Items.GuideSectionRenderer()
                .setHeader("Best of YouTube");
        // The reference server linked Music to a browse id it did not serve; FEmusic is served here.
        String[][] entries = {{"Popular on YouTube", "FEtrending"}, {"Music", "FEmusic"}};
        for (String[] entry : entries) {
            best.addContent(new AndroidGuideProto.Root.Items.GuideSectionRenderer.Contents().addEntry(
                    new AndroidGuideProto.GuideEntry().setTitle(entry[0]).addNavigation(guideNavigation(entry[1]))));
        }
        root.addItem(new AndroidGuideProto.Root.Items().addGuideSection(best));
        if (clientMajorVersion >= PIVOT_BAR_VERSION) {
            root.addItem(new AndroidGuideProto.Root.Items().addPivotBar(pivotBar()));
        }
        return root.toByteArray();
    }

    static byte[] resolveVideo(String videoId) {
        return new AndroidResolveProto.Root().addContent(new AndroidResolveProto.Root.Contents()
                .addVideo(new AndroidResolveProto.VideoNavigation().setId(videoId))).toByteArray();
    }

    static byte[] resolveBrowse(String browseId) {
        return new AndroidResolveProto.Root().addContent(new AndroidResolveProto.Root.Contents()
                .addBrowse(new AndroidResolveProto.BrowseNavigation().setBrowseId(browseId))).toByteArray();
    }

    static byte[] resolveSearch(String query) {
        return new AndroidResolveProto.Root().addContent(new AndroidResolveProto.Root.Contents()
                .addSearch(new AndroidResolveProto.SearchNavigation().setQuery(query))).toByteArray();
    }
}
