package com.fashtry.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fashtry.backend.bean.db.TryonTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试穿任务 Mapper
 * <p>
 * 对应 tryon_tasks 表，提供 CRUD 及条件查询
 * </p>
 */
@Mapper
public interface TryonTaskMapper extends BaseMapper<TryonTask> {
}
