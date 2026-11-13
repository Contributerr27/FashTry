package com.fashtry.backend.bean.vo;

import lombok.Data;

@Data
public class CaptchaVO {

    /** 验证码唯一 key，发短信时与用户输入一起提交 */
    private String captchaKey;
    /** 图片 base64，前端用 img src="data:image/png;base64," + captchaImage 显示 */
    private String captchaImage;
}
