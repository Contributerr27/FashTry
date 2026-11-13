package com.fashtry.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fashtry.backend.bean.db.RegisterAbusePolicy;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RegisterAbusePolicyMapper extends BaseMapper<RegisterAbusePolicy> {

    @Insert("INSERT IGNORE INTO register_abuse_policy ("
            + "id, ip_window_minutes, ip_deny_from, prefix_window_minutes, prefix_deny_from, "
            + "global_window_minutes, global_allow, sms_window_minutes, sms_max_per_ip"
            + ") VALUES (1, 10, 3, 60, 3, 10, 8, 60, 5)")
    int insertIgnore();
}
