package com.fashtry.backend.service.business.impl;

import com.fashtry.backend.service.business.CaptchaService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 图片验证码：生成随机字符图，答案存 Redis，用于短信发码前校验防刷
 */
@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final String REDIS_KEY_PREFIX = "captcha:player:";
    private static final int EXPIRE_MINUTES = 5;
    /** 宽度需容纳 4 个粗体字符 + 左右边距，避免末字符被裁切 */
    private static final int WIDTH = 148;
    private static final int HEIGHT = 40;
    private static final int CODE_LEN = 4;
    /** 排除易混淆字符：0O 1lI */
    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";

    private final StringRedisTemplate redisTemplate;

    public CaptchaServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String[] generate() {
        String code = randomCode(CODE_LEN);
        String captchaKey = java.util.UUID.randomUUID().toString().replace("-", "");
        String redisKey = REDIS_KEY_PREFIX + captchaKey;
        redisTemplate.opsForValue().set(redisKey, code.toLowerCase(), EXPIRE_MINUTES, TimeUnit.MINUTES);

        String base64Image = drawImage(code);
        return new String[]{captchaKey, base64Image};
    }

    @Override
    public boolean validate(String captchaKey, String userInput) {
        if (!StringUtils.hasText(captchaKey) || !StringUtils.hasText(userInput)) {
            return false;
        }
        String redisKey = REDIS_KEY_PREFIX + captchaKey;
        String stored = redisTemplate.opsForValue().get(redisKey);
        if (stored == null) {
            return false;
        }
        boolean ok = stored.equalsIgnoreCase(userInput.trim());
        redisTemplate.delete(redisKey);
        return ok;
    }

    private String randomCode(int len) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_CHARS.charAt(r.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String drawImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(240, 240, 240));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        Random r = new Random();
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(r.nextInt(180), r.nextInt(180), r.nextInt(180)));
            g.drawLine(r.nextInt(WIDTH), r.nextInt(HEIGHT), r.nextInt(WIDTH), r.nextInt(HEIGHT));
        }

        g.setFont(new Font("Arial", Font.BOLD, 26));
        int padX = 10;
        int inner = WIDTH - 2 * padX;
        int slot = inner / CODE_LEN;
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(r.nextInt(80), r.nextInt(80), r.nextInt(80)));
            int x = padX + i * slot + r.nextInt(3) - 1;
            g.drawString(String.valueOf(code.charAt(i)), x, 28);
        }

        g.dispose();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("生成验证码图片失败", e);
        }
    }
}
