package com.fashtry.backend.common.constants;

/**
 * 返回给浏览器/客户端的文案：不含上游供应商名称与原始异常细节，仅作日志排查依据。
 */
public final class ClientSafeMessages {

    public static final String TRYON_GENERATION_FAILED = "生成未成功，请稍后重试或更换图片";
    public static final String IMAGE_ASYNC_TASK_FAILED = "生成未成功，请稍后重试";
    public static final String MODEL_VIDEO_GENERATION_FAILED = "视频生成未成功，请稍后重试或更换描述";
    /** 提交前校验：未配置多模态生图能力 */
    public static final String AI_IMAGE_NOT_CONFIGURED = "智能生图服务未启用，请稍后再试或联系管理员";

    private ClientSafeMessages() {}

    /** 对外隐藏内部 provider 枚举值（如 GEMINI） */
    public static String mapTryonProviderForApi(String dbProvider) {
        if (TryonTaskProvider.GEMINI.equals(dbProvider)) {
            return "AI";
        }
        return dbProvider != null ? dbProvider : TryonTaskProvider.FASHN;
    }
}
