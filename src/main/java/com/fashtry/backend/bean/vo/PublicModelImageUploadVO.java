package com.fashtry.backend.bean.vo;

/**
 * 后台上传公共模特原图后返回 COS 地址（缩略图由服务端生成并上传）
 */
public record PublicModelImageUploadVO(String imageUrl, String thumbUrl) {}
