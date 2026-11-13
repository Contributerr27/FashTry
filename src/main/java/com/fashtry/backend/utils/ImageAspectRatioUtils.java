package com.fashtry.backend.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 从图片像素尺寸推断 Gemini 生图 API 支持的 {@code aspectRatio} 令牌（如 {@code 3:4}）。
 */
public final class ImageAspectRatioUtils {

    /** 与 ProductToModel / Gemini 生图常用画幅一致 */
    private static final String[] GEMINI_IMAGE_ASPECT_RATIOS = {
        "1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9"
    };

    private ImageAspectRatioUtils() {}

    /**
     * 在 Gemini 支持的画幅中，选取与 {@code width:height} 最接近的比例（对数距离，横竖对称）。
     */
    public static String closestGeminiAspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        double actual = width / (double) height;
        String best = null;
        double bestDiff = Double.MAX_VALUE;
        for (String token : GEMINI_IMAGE_ASPECT_RATIOS) {
            int colon = token.indexOf(':');
            if (colon <= 0 || colon >= token.length() - 1) {
                continue;
            }
            double candidate =
                    Double.parseDouble(token.substring(0, colon))
                            / Double.parseDouble(token.substring(colon + 1));
            double diff = Math.abs(Math.log(actual / candidate));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = token;
            }
        }
        return best;
    }

    /** 读取图片字节宽高并映射到最接近的 Gemini 画幅；无法解析时返回 {@code null}。 */
    public static String detectClosestGeminiAspectRatio(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) {
                return null;
            }
            return closestGeminiAspectRatio(img.getWidth(), img.getHeight());
        } catch (IOException e) {
            return null;
        }
    }
}
