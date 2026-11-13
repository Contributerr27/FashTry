package com.fashtry.backend.utils;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.fashtry.backend.bean.db.TryonTask;
import org.springframework.stereotype.Component;

/**
 * 雪花 ID 生成器（分布式唯一 ID）
 * <p>
 * 使用 MyBatis-Plus 的 DefaultIdentifierGenerator 生成雪花 ID，
 * 保证多实例下 ID 不重复。用于 users、tryon_tasks 等使用雪花 ID 的表。
 * </p>
 */
@Component
public class SnowflakeIdGenerator {

    private final DefaultIdentifierGenerator generator = new DefaultIdentifierGenerator();

    /**
     * 生成下一个雪花 ID
     *
     * @return 雪花 ID（long）
     */
    public long nextId() {
        return generator.nextId(new TryonTask()).longValue();
    }
}
