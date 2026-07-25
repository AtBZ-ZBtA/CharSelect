package com.charselect.character;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

/**
 * Where a character's skin comes from. Deliberately free of client-only types so the
 * profile can be read on the server side too.
 *
 * <p>Skins that came from Mojang are downloaded once and kept in {@code charselect/skins/},
 * exactly like uploaded ones, so a character still looks right with no network and no
 * Mojang account. {@link #origin()} only survives to show the player where it came from.
 *
 * @param source how the skin was obtained
 * @param origin the username it was fetched under, or the file it was imported from; display only
 * @param hash   content hash naming the PNG under {@code charselect/skins/}, empty for defaults
 * @param model  arm width, detected from the Mojang profile or chosen by the player
 */
public record SkinRef(Source source, String origin, String hash, SkinModel model) {

    public enum Source {
        /** Vanilla Steve/Alex, picked by {@link SkinModel}. */
        DEFAULT,
        /** Fetched from Mojang's public profile API by username, then cached locally. */
        MOJANG,
        /** A PNG the player supplied. */
        FILE,
        /**
         * The player's own Minecraft account skin, left to resolve normally rather than
         * being overridden. Used by the character made from an existing installation.
         */
        ACCOUNT;

        static Source byKey(String key) {
            for (Source s : values()) {
                if (s.key().equals(key)) {
                    return s;
                }
            }
            return DEFAULT;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum SkinModel {
        WIDE, SLIM;

        public boolean slim() {
            return this == SLIM;
        }

        public SkinModel flip() {
            return this == WIDE ? SLIM : WIDE;
        }

        public static SkinModel byKey(String key) {
            return "slim".equalsIgnoreCase(key) ? SLIM : WIDE;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final SkinRef STEVE = new SkinRef(Source.DEFAULT, "", "", SkinModel.WIDE);
    public static final SkinRef ALEX = new SkinRef(Source.DEFAULT, "", "", SkinModel.SLIM);
    public static final SkinRef ACCOUNT = new SkinRef(Source.ACCOUNT, "", "", SkinModel.WIDE);

    /** True when this character should simply look like the player's own account. */
    public boolean usesAccountSkin() {
        return source == Source.ACCOUNT;
    }

    public static SkinRef mojang(String username, String hash, SkinModel model) {
        return new SkinRef(Source.MOJANG, username, hash, model);
    }

    public static SkinRef file(String displayName, String hash, SkinModel model) {
        return new SkinRef(Source.FILE, displayName, hash, model);
    }

    /** True when this points at a PNG in the skins folder rather than a vanilla default. */
    public boolean hasCustomTexture() {
        return source != Source.DEFAULT && !hash.isEmpty();
    }

    public SkinRef withModel(SkinModel model) {
        return new SkinRef(source, origin, hash, model);
    }

    /** Matches how Minecraft picks between Steve and Alex for an account with no skin. */
    public static SkinRef defaultFor(UUID id) {
        return (id.hashCode() & 1) == 0 ? STEVE : ALEX;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Source", source.key());
        tag.putString("Origin", origin);
        tag.putString("Hash", hash);
        tag.putString("Model", model.key());
        return tag;
    }

    public static SkinRef load(CompoundTag tag) {
        return new SkinRef(
                Source.byKey(tag.getString("Source")),
                tag.getString("Origin"),
                tag.getString("Hash"),
                SkinModel.byKey(tag.getString("Model")));
    }
}
