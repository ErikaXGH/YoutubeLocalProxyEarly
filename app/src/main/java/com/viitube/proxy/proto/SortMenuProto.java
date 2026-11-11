package com.viitube.proxy.proto;

/**
 * The "Sort by" menu over a channel's Videos tab, as YouTube 12.45.56 reads it: the section list's
 * subMenu (union afdi) holds sortFilterSubMenuRenderer (afkc, 76818770): items = 1, title = 2,
 * icon = 3. An item (afkb) is {title = 1 (string), selected = 2, continuation = 3, serviceEndpoint
 * = 5}. With a continuation the app reloads the list in place (dbb) - but this app then shows
 * nothing - so each item carries a browse endpoint instead (dbb runs it when there is no
 * continuation), which opens the channel again with the Videos tab sorted.
 */
public final class SortMenuProto extends ProtoMessage {
    private static final int ICON_SORT = 192;

    private final String title;
    private final String[] labels;
    private final ProtoMessage[] endpoints;
    private final int selected;

    public SortMenuProto(String title, String[] labels, ProtoMessage[] endpoints, int selected) {
        this.title = title;
        this.labels = labels;
        this.endpoints = endpoints;
        this.selected = selected;
    }

    /**
     * A plain section heading for an item section's header (union adpa): itemSectionHeaderRenderer
     * adoz (61393757) {title = 1}. The app draws only the title (hqz).
     */
    public static final class TitleHeader extends ProtoMessage {
        private final String title;

        public TitleHeader(String title) {
            this.title = title;
        }

        @Override
        public void writeTo(ProtoWriter writer) {
            ProtoWriter run = new ProtoWriter();
            run.writeString(1, title);
            ProtoWriter text = new ProtoWriter();
            text.writeBytes(1, run.toByteArray());
            ProtoWriter renderer = new ProtoWriter();         // adoz
            renderer.writeBytes(1, text.toByteArray());
            writer.writeBytes(61393757, renderer.toByteArray());
        }

        @Override
        public void mergeFrom(ProtoReader reader) throws java.io.IOException {
            while (reader.hasMore()) reader.skip(reader.readTag());
        }
    }


    private boolean showIcon = true;

    /** The menu without the sort icon: its title and the drop-down arrow ("Videos only"). */
    public SortMenuProto withoutIcon() {
        showIcon = false;
        return this;
    }

    @Override
    public void writeTo(ProtoWriter writer) {
        ProtoWriter menu = new ProtoWriter();                    // afkc
        for (int i = 0; i < labels.length; i++) {
            ProtoWriter item = new ProtoWriter();                // afkb
            item.writeString(1, labels[i]);
            if (i == selected) item.writeBool(2, true);
            item.writeMessage(5, endpoints[i]);
            menu.writeBytes(1, item.toByteArray());
        }
        menu.writeString(2, title);
        if (showIcon) {
            ProtoWriter icon = new ProtoWriter();
            icon.writeInt32(1, ICON_SORT);
            menu.writeBytes(3, icon.toByteArray());
        }
        writer.writeBytes(76818770, menu.toByteArray());         // afdi
    }

    /**
     * The menu as an item section's header (itemSectionRenderer adpe field 5): union adpa holding
     * the header renderer adpb (88941025) {title = 3, menu = 4 (union adpf, the menu itself)}.
     * The app draws the menu here; as a section list subMenu it is ignored on a channel tab.
     */
    public ProtoMessage header(final String heading) {
        final SortMenuProto menu = this;
        return new ProtoMessage() {
            @Override
            public void writeTo(ProtoWriter writer) {
                ProtoWriter run = new ProtoWriter();
                run.writeString(1, heading);
                ProtoWriter text = new ProtoWriter();
                text.writeBytes(1, run.toByteArray());
                ProtoWriter renderer = new ProtoWriter();     // adpb
                renderer.writeBytes(3, text.toByteArray());
                renderer.writeMessage(4, menu);
                writer.writeBytes(88941025, renderer.toByteArray());
            }

            @Override
            public void mergeFrom(ProtoReader reader) throws java.io.IOException {
                while (reader.hasMore()) reader.skip(reader.readTag());
            }
        };
    }

    @Override
    public void mergeFrom(ProtoReader reader) throws java.io.IOException {
        while (reader.hasMore()) reader.skip(reader.readTag());
    }
}
