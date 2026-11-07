package vn.id.fitnessai.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

final class NutritionValidation {
    private NutritionValidation() { }

    static String name(String value, int maxLength) {
        if (value == null) throw new IllegalArgumentException("Tên không được để trống");

        String normalized = value.replaceAll("(?U)\\s+", " ").strip();

        if (normalized.isEmpty()) throw new IllegalArgumentException("Tên không được để trống");
        if (normalized.length() > maxLength) throw new IllegalArgumentException("Tên vượt giới hạn " + maxLength + " ký tự");

        return normalized;
    }

    static String nameKey(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    static String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;

        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Nội dung vượt giới hạn " + maxLength + " ký tự");
        }
        return normalized;
    }

    static BigDecimal number(BigDecimal value, int precision, boolean positive) {
        if (value == null || value.signum() < 0 || (positive && value.signum() == 0)) {
            throw new IllegalArgumentException(positive ? "Khối lượng phải lớn hơn 0" : "Chỉ số dinh dưỡng phải không âm");
        }

        final BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("Chỉ chấp nhận tối đa hai chữ số thập phân", failure);
        }

        if (normalized.precision() > precision) throw new IllegalArgumentException("Giá trị vượt giới hạn lưu trữ");
        return normalized;
    }

    static String imageUrl(String value) {
        String normalized = optionalText(value, 500);

        if (normalized == null) return null;

        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();

            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawPath() == null
                    || !uri.getRawPath().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp|gif)$")) {

                throw new IllegalArgumentException("Dùng URL ảnh PNG/JPG/WebP/GIF trực tiếp qua HTTP hoặc HTTPS");
            }
            return normalized;

        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("URL ảnh không hợp lệ", failure);
        }
    }

    static OffsetDateTime time(OffsetDateTime now, OffsetDateTime previous) {
        if (now == null) throw new IllegalArgumentException("Thiếu thời điểm xử lý từ server");

        OffsetDateTime normalized = now.truncatedTo(ChronoUnit.MICROS);

        if (previous != null && normalized.isBefore(previous)) {
            throw new IllegalArgumentException("Thời điểm cập nhật không được đi lùi");
        }
        return normalized;
    }
}
