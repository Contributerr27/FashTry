package com.fashtry.backend.common.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 腾讯云 COS 客户端配置
 */
@Configuration
public class CosConfig {

    private static final Logger log = LoggerFactory.getLogger(CosConfig.class);

    /** 控制台「全球加速」公网域名后缀，对应 {@code {bucket}.cos.accelerate.myqcloud.com} */
    private static final String ACCELERATE_PUBLIC_ENDPOINT_SUFFIX = "cos.accelerate.myqcloud.com";
    /** 内网全球加速后缀，仅腾讯云 VPC 内；对应 {@code {bucket}.cos-internal.accelerate.tencentcos.cn} */
    private static final String ACCELERATE_INTERNAL_ENDPOINT_SUFFIX = "cos-internal.accelerate.tencentcos.cn";

    @Bean
    public COSClient cosClient(CosProperties cosProperties) {
        COSCredentials cred = new BasicCOSCredentials(cosProperties.getSecretId(), cosProperties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setRegion(new Region(cosProperties.getRegion()));
        clientConfig.setConnectionTimeout(cosProperties.getConnectionTimeoutMs());
        clientConfig.setSocketTimeout(cosProperties.getSocketTimeoutMs());
        applyAccelerateEndpoint(clientConfig, cosProperties.getAccelerateMode());
        return new COSClient(cred, clientConfig);
    }

    private void applyAccelerateEndpoint(ClientConfig clientConfig, String mode) {
        if (!StringUtils.hasText(mode)) {
            return;
        }
        String m = mode.trim();
        if ("off".equalsIgnoreCase(m)) {
            return;
        }
        if ("public".equalsIgnoreCase(m)) {
            clientConfig.setEndPointSuffix(ACCELERATE_PUBLIC_ENDPOINT_SUFFIX);
            log.info("COS ClientConfig 全球加速: public ({})", ACCELERATE_PUBLIC_ENDPOINT_SUFFIX);
            return;
        }
        if ("internal".equalsIgnoreCase(m)) {
            clientConfig.setEndPointSuffix(ACCELERATE_INTERNAL_ENDPOINT_SUFFIX);
            log.info("COS ClientConfig 全球加速: internal ({})", ACCELERATE_INTERNAL_ENDPOINT_SUFFIX);
            return;
        }
        log.warn("COS app.cos.accelerate-mode 无效: {}，将使用默认地域域名", m);
    }
}
