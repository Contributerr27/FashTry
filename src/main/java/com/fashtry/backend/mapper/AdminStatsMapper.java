package com.fashtry.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminStatsMapper {

    Map<String, Object> userSummary(
            @Param("startToday") LocalDateTime startToday,
            @Param("endTomorrow") LocalDateTime endTomorrow,
            @Param("start7") LocalDateTime start7,
            @Param("start30") LocalDateTime start30);

    Map<String, Object> tryonSummary(@Param("start7") LocalDateTime start7);

    List<Map<String, Object>> geminiSummary(@Param("start7") LocalDateTime start7);

    Map<String, Object> uploadSummary(@Param("start7") LocalDateTime start7);

    long brandCount();

    Map<String, Object> paidOrderSummary(@Param("start7") LocalDateTime start7);

    Map<String, Object> walletSummary();

    List<Map<String, Object>> userDaily(
            @Param("start14") LocalDateTime start14,
            @Param("endTomorrow") LocalDateTime endTomorrow);

    List<Map<String, Object>> tryonDaily(
            @Param("start14") LocalDateTime start14,
            @Param("endTomorrow") LocalDateTime endTomorrow);

    List<Map<String, Object>> geminiDaily(
            @Param("start14") LocalDateTime start14,
            @Param("endTomorrow") LocalDateTime endTomorrow);

    List<Map<String, Object>> uploadDaily(
            @Param("start14") LocalDateTime start14,
            @Param("endTomorrow") LocalDateTime endTomorrow);

    List<Map<String, Object>> paidOrderDaily(
            @Param("start14") LocalDateTime start14,
            @Param("endTomorrow") LocalDateTime endTomorrow);
}
