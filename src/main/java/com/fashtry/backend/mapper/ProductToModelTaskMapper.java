package com.fashtry.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fashtry.backend.bean.db.ProductToModelTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品图转模特图任务 Mapper
 */
@Mapper
public interface ProductToModelTaskMapper extends BaseMapper<ProductToModelTask> {
}
