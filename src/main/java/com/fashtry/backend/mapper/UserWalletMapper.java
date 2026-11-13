package com.fashtry.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fashtry.backend.bean.db.UserWallet;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserWalletMapper extends BaseMapper<UserWallet> {
}
