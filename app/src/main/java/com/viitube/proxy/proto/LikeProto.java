package com.viitube.proxy.proto;

/**
 * The like and the dislike button of the 2017 apps' watch page, with the field numbers read from
 * YouTube 12.45.56 (protobuf-nano, so every number is in the smali):
 * <ul>
 * <li>slimMetadataToggleButtonRenderer (afjq, slot 124608045 of a slim metadata button):
 *     target = 17 {videoId = 1}, isLike = 20, isDislike = 21, button = 26 (a union holding
 *     toggleButtonRenderer at 79971800).</li>
 * <li>likeEndpoint (adqn, field 63158558 of a NavigationEndpoint): status = 1, target = 2. The
 *     app (dxm) sends status 0 as like/like, 1 as like/dislike and 2 as like/removelike.</li>
 * </ul>
 * Only written by the proxy, so reading them back is not needed.
 */
public final class LikeProto {

    public static final int LIKE = 0;
    public static final int DISLIKE = 1;
    public static final int INDIFFERENT = 2;

    private LikeProto() { }

    /** likeTarget: {videoId = 1}. */
    static final class Target extends ProtoMessage {
        private final String videoId;

        Target(String videoId) {
            this.videoId = videoId;
        }

        @Override
        public void writeTo(ProtoWriter writer) {
            writer.writeString(1, videoId);
        }

        @Override
        public void mergeFrom(ProtoReader reader) throws java.io.IOException {
            while (reader.hasMore()) reader.skip(reader.readTag());
        }
    }

    /** A NavigationEndpoint carrying only a likeEndpoint. */
    public static final class Endpoint extends ProtoMessage {
        private final int status;
        private final String videoId;

        public Endpoint(int status, String videoId) {
            this.status = status;
            this.videoId = videoId;
        }

        @Override
        public void writeTo(ProtoWriter writer) {
            ProtoWriter like = new ProtoWriter();
            like.writeInt32(1, status);
            like.writeBytes(2, new Target(videoId).toByteArray());
            writer.writeBytes(63158558, like.toByteArray());
        }

        @Override
        public void mergeFrom(ProtoReader reader) throws java.io.IOException {
            while (reader.hasMore()) reader.skip(reader.readTag());
        }
    }

    /** slimMetadataToggleButtonRenderer around a toggle button. */
    public static final class SlimToggleButton extends ProtoMessage {
        private final String videoId;
        private final boolean like;
        private final AndroidFullNextProto.ToggleButtonRenderer toggle;

        public SlimToggleButton(String videoId, boolean like,
                                AndroidFullNextProto.ToggleButtonRenderer toggle) {
            this.videoId = videoId;
            this.like = like;
            this.toggle = toggle;
        }

        @Override
        public void writeTo(ProtoWriter writer) {
            writer.writeMessage(17, new Target(videoId));
            writer.writeInt32(like ? 20 : 21, 1);
            ProtoWriter union = new ProtoWriter();
            union.writeMessage(79971800, toggle);
            writer.writeBytes(26, union.toByteArray());
        }

        @Override
        public void mergeFrom(ProtoReader reader) throws java.io.IOException {
            while (reader.hasMore()) reader.skip(reader.readTag());
        }
    }
}
