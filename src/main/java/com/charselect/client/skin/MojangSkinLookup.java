package com.charselect.client.skin;

import com.charselect.CharSelect;
import com.charselect.character.SkinRef;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Resolves a Minecraft username to that account's skin through Mojang's public API, then
 * caches the PNG locally via {@link SkinStorage}.
 *
 * <p>Three hops: name to UUID, UUID to a base64 texture property, texture URL to PNG bytes.
 * All of it runs off the render thread.
 */
public final class MojangSkinLookup {
    private static final Pattern VALID_USERNAME = Pattern.compile("^\\w{1,16}$");

    private static final String NAME_TO_UUID = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String UUID_TO_PROFILE =
            "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static HttpClient client;

    private MojangSkinLookup() {
    }

    /** A failed lookup, carrying a translation key suitable for showing in the GUI. */
    public static class SkinLookupException extends RuntimeException {
        private final String translationKey;

        public SkinLookupException(String translationKey) {
            super(translationKey);
            this.translationKey = translationKey;
        }

        public SkinLookupException(String translationKey, Throwable cause) {
            super(translationKey, cause);
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private static synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }

    public static boolean isPlausibleUsername(String username) {
        return VALID_USERNAME.matcher(username).matches();
    }

    /**
     * Looks up {@code username} and returns a ref pointing at the locally cached copy of
     * that account's skin. The returned future fails with {@link SkinLookupException}.
     */
    public static CompletableFuture<SkinRef> byUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isPlausibleUsername(username)) {
                throw new SkinLookupException("charselect.skin.error.bad_username");
            }
            String uuid = fetchUuid(username);
            TextureInfo texture = fetchTexture(uuid);
            byte[] png = download(texture.url());
            try {
                String hash = SkinStorage.store(png);
                return SkinRef.mojang(username, hash, texture.model());
            } catch (SkinStorage.InvalidSkinException e) {
                throw new SkinLookupException(e.getMessage(), e);
            } catch (IOException e) {
                throw new SkinLookupException("charselect.skin.error.save_failed", e);
            }
        }, Util.ioPool());
    }

    private record TextureInfo(String url, SkinRef.SkinModel model) {
    }

    private static String fetchUuid(String username) {
        HttpResponse<String> response = send(NAME_TO_UUID + username);
        if (response.statusCode() == 404 || response.body().isBlank()) {
            throw new SkinLookupException("charselect.skin.error.no_such_player");
        }
        if (response.statusCode() == 429) {
            throw new SkinLookupException("charselect.skin.error.rate_limited");
        }
        if (response.statusCode() != 200) {
            throw new SkinLookupException("charselect.skin.error.mojang_unavailable");
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("id")) {
            throw new SkinLookupException("charselect.skin.error.no_such_player");
        }
        return json.get("id").getAsString();
    }

    private static TextureInfo fetchTexture(String uuid) {
        HttpResponse<String> response = send(UUID_TO_PROFILE + uuid);
        if (response.statusCode() != 200) {
            throw new SkinLookupException("charselect.skin.error.mojang_unavailable");
        }

        JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
        String encoded = null;
        for (var element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if ("textures".equals(property.get("name").getAsString())) {
                encoded = property.get("value").getAsString();
                break;
            }
        }
        if (encoded == null) {
            throw new SkinLookupException("charselect.skin.error.no_skin");
        }

        JsonObject textures;
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
        } catch (RuntimeException e) {
            throw new SkinLookupException("charselect.skin.error.mojang_unavailable", e);
        }

        // An account that never set a skin has no SKIN entry and just renders as a default.
        if (textures == null || !textures.has("SKIN")) {
            throw new SkinLookupException("charselect.skin.error.no_skin");
        }

        JsonObject skin = textures.getAsJsonObject("SKIN");
        SkinRef.SkinModel model = SkinRef.SkinModel.WIDE;
        if (skin.has("metadata")) {
            JsonObject metadata = skin.getAsJsonObject("metadata");
            if (metadata.has("model")
                    && "slim".equals(metadata.get("model").getAsString().toLowerCase(Locale.ROOT))) {
                model = SkinRef.SkinModel.SLIM;
            }
        }
        return new TextureInfo(skin.get("url").getAsString(), model);
    }

    private static byte[] download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            HttpResponse<byte[]> response =
                    client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new SkinLookupException("charselect.skin.error.download_failed");
            }
            return response.body();
        } catch (IOException e) {
            throw new SkinLookupException("charselect.skin.error.download_failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinLookupException("charselect.skin.error.download_failed", e);
        }
    }

    private static HttpResponse<String> send(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            return client().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            CharSelect.LOGGER.warn("Mojang lookup failed for {}", url, e);
            throw new SkinLookupException("charselect.skin.error.offline", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinLookupException("charselect.skin.error.offline", e);
        }
    }
}
