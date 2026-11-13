package com.fashtry.backend.mapper;

import com.fashtry.backend.bean.vo.AdminUnifiedTaskListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminUnifiedTaskMapper {

    long countUnified(@Param("userId") Long userId);

    List<AdminUnifiedTaskListItemVO> selectUnifiedPage(
            @Param("userId") Long userId, @Param("offset") long offset, @Param("limit") int limit);
}
