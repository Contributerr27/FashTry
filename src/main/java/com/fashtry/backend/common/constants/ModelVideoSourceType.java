package com.fashtry.backend.common.constants;

/**
 * {@link com.fashtry.backend.bean.dto.ModelVideoSubmitRequest#getSourceType()} 取值（与前端一致，大写）。
 */
public final class ModelVideoSourceType {

    public static final String TRYON = "TRYON";
    public static final String IMAGE_EDIT = "IMAGE_EDIT";
    public static final String UPLOAD = "UPLOAD";

    private ModelVideoSourceType() {}

    public static boolean isKnown(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String s = raw.trim();
        return TRYON.equalsIgnoreCase(s)
                || IMAGE_EDIT.equalsIgnoreCase(s)
                || UPLOAD.equalsIgnoreCase(s);
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return TRYON;
        }
        String s = raw.trim();
        if (TRYON.equalsIgnoreCase(s)) {
            return TRYON;
        }
        if (IMAGE_EDIT.equalsIgnoreCase(s)) {
            return IMAGE_EDIT;
        }
        if (UPLOAD.equalsIgnoreCase(s)) {
            return UPLOAD;
        }
        return TRYON;
    }
}
