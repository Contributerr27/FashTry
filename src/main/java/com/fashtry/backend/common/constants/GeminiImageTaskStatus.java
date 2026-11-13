package com.fashtry.backend.common.constants;

/**
 * {@link com.fashtry.backend.bean.db.GeminiImageTask#status}
 */
public final class GeminiImageTaskStatus {

    public static final int PENDING = 0;
    public static final int PROCESSING = 1;
    public static final int COMPLETED = 2;
    public static final int FAILED = 3;

    private GeminiImageTaskStatus() {}
}
