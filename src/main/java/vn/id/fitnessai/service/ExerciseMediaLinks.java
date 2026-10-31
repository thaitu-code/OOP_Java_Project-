package vn.id.fitnessai.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Validate references only. Never download a remote URL from the application server. */
public final class ExerciseMediaLinks {
    private ExerciseMediaLinks() { }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String link = value.strip();
        if (link.length() > 500) throw new IllegalArgumentException("Link media không được vượt quá 500 ký tự");
        if (link.matches("/exercise-media/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(gif|mp4|webm)")) return link;
        try {
            URI uri = new URI(link);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawPath() == null) {
                throw new IllegalArgumentException("Dùng link tệp trực tiếp GIF/PNG/JPG/WebP/MP4/WebM qua HTTP hoặc HTTPS");
            }
            // Link /blob/ là trang HTML xem file; raw mới trả binary dùng được cho img/video.
            if (uri.getHost().equalsIgnoreCase("github.com") && uri.getRawPath().matches("/[^/]+/[^/]+/(blob|raw)/.+")) {
                String path = uri.getRawPath().replaceFirst("^(/[^/]+/[^/]+)/(blob|raw)/", "$1/");
                link = "https://raw.githubusercontent.com" + path
                        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                        + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
                uri = new URI(link);
            }
            if (link.length() > 500) throw new IllegalArgumentException("Link media sau chuẩn hóa vượt quá 500 ký tự");
            if (!uri.getRawPath().toLowerCase(Locale.ROOT).matches(".*\\.(gif|png|jpe?g|webp|mp4|webm)$")) {
                throw new IllegalArgumentException("Dùng link tệp trực tiếp GIF/PNG/JPG/WebP/MP4/WebM qua HTTP hoặc HTTPS");
            }
            return link;
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Link ảnh/video không hợp lệ");
        }
    }
}
