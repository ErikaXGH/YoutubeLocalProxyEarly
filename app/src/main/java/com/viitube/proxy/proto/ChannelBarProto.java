package com.viitube.proxy.proto;

import java.util.List;

/**
 * The bar of channel pictures over the Subscriptions feed, as YouTube 12.45.56 reads it (field
 * numbers from its smali): the section list's subMenu (sectionListRenderer afdh field 5, a union
 * afdi) holds channelListSubMenuRenderer (abza, 48479645), whose contents (field 1, union abzb)
 * are channelListSubMenuAvatarRenderer entries (abyy, 48474525): thumbnail = 1, navigation
 * endpoint = 3, title = 8. The app draws them with layout channel_list_sub_menu(_avatar).
 */
public final class ChannelBarProto extends ProtoMessage {

    /** One channel of the bar. */
    public static final class Channel {
        final String id;
        final String name;
        final String avatar;

        public Channel(String id, String name, String avatar) {
            this.id = id;
            this.name = name;
            this.avatar = avatar;
        }
    }

    private final List<Channel> channels;

    public ChannelBarProto(List<Channel> channels) {
        this.channels = channels;
    }

    @Override
    public void writeTo(ProtoWriter writer) {
        ProtoWriter menu = new ProtoWriter();                    // abza
        for (Channel channel : channels) {
            ProtoWriter avatar = new ProtoWriter();              // abyy
            ProtoWriter size = new ProtoWriter();
            size.writeString(1, channel.avatar);
            size.writeInt32(2, 88);
            size.writeInt32(3, 88);
            ProtoWriter thumbnails = new ProtoWriter();          // afov
            thumbnails.writeBytes(1, size.toByteArray());
            avatar.writeBytes(1, thumbnails.toByteArray());
            avatar.writeMessage(3, new VideoListProto.BrowseNavigation().setData(
                    new VideoListProto.BrowseNavigation.BrowseData()
                            .setBrowseId(channel.id)
                            .setCanonicalBaseUrl("/channel/" + channel.id)));
            ProtoWriter run = new ProtoWriter();
            run.writeString(1, channel.name);
            ProtoWriter title = new ProtoWriter();               // addp {runs = 1}
            title.writeBytes(1, run.toByteArray());
            avatar.writeBytes(8, title.toByteArray());
            ProtoWriter entry = new ProtoWriter();               // abzb
            entry.writeBytes(48474525, avatar.toByteArray());
            menu.writeBytes(1, entry.toByteArray());
        }
        writer.writeBytes(48479645, menu.toByteArray());         // afdi
    }

    @Override
    public void mergeFrom(ProtoReader reader) throws java.io.IOException {
        while (reader.hasMore()) reader.skip(reader.readTag());
    }
}
