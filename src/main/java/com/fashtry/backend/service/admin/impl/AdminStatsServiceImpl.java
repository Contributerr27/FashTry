package com.fashtry.backend.service.admin.impl;

import com.fashtry.backend.bean.vo.AdminStatsDailyPointVO;
import com.fashtry.backend.bean.vo.AdminStatsOverviewVO;
import com.fashtry.backend.common.constants.GeminiImageTaskType;
import com.fashtry.backend.mapper.AdminStatsMapper;
import com.fashtry.backend.service.admin.AdminStatsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AdminStatsServiceImpl implements AdminStatsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter GENERATED_AT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long CACHE_MS = 30_000L;

    private final AdminStatsMapper statsMapper;

    private final Object cacheLock = new Object();
    private volatile AdminStatsOverviewVO cached;
    private volatile long cachedAt;

    public AdminStatsServiceImpl(AdminStatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    @Override
    public AdminStatsOverviewVO overview() {
        long now = System.currentTimeMillis();
        AdminStatsOverviewVO hit = cached;
        if (hit != null && now - cachedAt < CACHE_MS) {
            return hit;
        }
        synchronized (cacheLock) {
            if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
                return cached;
            }
            AdminStatsOverviewVO vo = loadOverview();
            cached = vo;
            cachedAt = System.currentTimeMillis();
            return vo;
        }
    }

    private AdminStatsOverviewVO loadOverview() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime start7 = today.minusDays(7).atStartOfDay();
        LocalDateTime start14 = today.minusDays(13).atStartOfDay();
        LocalDateTime start30 = today.minusDays(30).atStartOfDay();
        LocalDateTime endTomorrow = today.plusDays(1).atStartOfDay();

        var fUsers = CompletableFuture.supplyAsync(
                () -> statsMapper.userSummary(startToday, endTomorrow, start7, start30));
        var fTryon = CompletableFuture.supplyAsync(() -> statsMapper.tryonSummary(start7));
        var fGemini = CompletableFuture.supplyAsync(() -> statsMapper.geminiSummary(start7));
        var fUpload = CompletableFuture.supplyAsync(() -> statsMapper.uploadSummary(start7));
        var fBrands = CompletableFuture.supplyAsync(statsMapper::brandCount);
        var fOrders = CompletableFuture.supplyAsync(() -> statsMapper.paidOrderSummary(start7));
        var fWallet = CompletableFuture.supplyAsync(statsMapper::walletSummary);
        var fUserDaily = CompletableFuture.supplyAsync(() -> statsMapper.userDaily(start14, endTomorrow));
        var fTryonDaily = CompletableFuture.supplyAsync(() -> statsMapper.tryonDaily(start14, endTomorrow));
        var fGeminiDaily = CompletableFuture.supplyAsync(() -> statsMapper.geminiDaily(start14, endTomorrow));
        var fUploadDaily = CompletableFuture.supplyAsync(() -> statsMapper.uploadDaily(start14, endTomorrow));
        var fOrderDaily = CompletableFuture.supplyAsync(() -> statsMapper.paidOrderDaily(start14, endTomorrow));

        CompletableFuture.allOf(
                fUsers, fTryon, fGemini, fUpload, fBrands, fOrders, fWallet,
                fUserDaily, fTryonDaily, fGeminiDaily, fUploadDaily, fOrderDaily).join();

        Map<String, Object> users = fUsers.join();
        Map<String, Object> tryon = fTryon.join();
        Map<String, Map<String, Object>> gemini = indexBy(fGemini.join(), "taskType");
        Map<String, Object> upload = fUpload.join();
        Map<String, Object> orders = fOrders.join();
        Map<String, Object> wallet = fWallet.join();
        Map<String, Object> le = gemini.getOrDefault(GeminiImageTaskType.EDIT, Map.of());
        Map<String, Object> ci = gemini.getOrDefault(GeminiImageTaskType.CLOTHING_PRODUCT_SUITE, Map.of());
        Map<String, Object> mi = gemini.getOrDefault(GeminiImageTaskType.CLOTHING_PACKSHOT, Map.of());

        return AdminStatsOverviewVO.builder()
                .generatedAt(LocalDateTime.now(ZONE).format(GENERATED_AT_FMT))
                .dayCutTimezone("上海时区，按自然日 0 点切日")
                .trafficNote("独立页面访问量（PV）未接入日志时无法统计；下方「近7日/按日」为业务提交量（任务、上传、支付等），可反映使用强度。")
                .totalUsers(lng(users, "total"))
                .newUsersToday(lng(users, "today"))
                .newUsersLast7Days(lng(users, "last7"))
                .newUsersLast30Days(lng(users, "last30"))
                .totalTryonTasks(lng(tryon, "total"))
                .tryonStatusQueued(lng(tryon, "queued"))
                .tryonStatusProcessing(lng(tryon, "processing"))
                .tryonStatusDone(lng(tryon, "done"))
                .tryonStatusFailed(lng(tryon, "failed"))
                .tryonTasksCreatedLast7Days(lng(tryon, "last7"))
                .tryonCreditsConsumedDone(lng(tryon, "creditsDone"))
                .totalLocalEditTasks(lng(le, "total"))
                .localEditTasksCreatedLast7Days(lng(le, "last7"))
                .localEditCreditsConsumedSuccess(lng(le, "creditsSuccess"))
                .totalClothingImageTasks(lng(ci, "total"))
                .clothingImageTasksCreatedLast7Days(lng(ci, "last7"))
                .clothingImageCreditsConsumedSuccess(lng(ci, "creditsSuccess"))
                .totalModelImageTasks(lng(mi, "total"))
                .modelImageTasksCreatedLast7Days(lng(mi, "last7"))
                .modelImageCreditsConsumedSuccess(lng(mi, "creditsSuccess"))
                .totalUserUploads(lng(upload, "total"))
                .userUploadsLast7Days(lng(upload, "last7"))
                .totalBrands(fBrands.join())
                .paidOrdersTotal(lng(orders, "paidCount"))
                .paidOrdersAmountTotal(dec(orders, "paidSum"))
                .paidOrdersLast7Days(lng(orders, "paid7"))
                .walletTotalRechargedCredits(lng(wallet, "credits"))
                .walletTotalRechargedAmount(dec(wallet, "amount"))
                .dailyLast14Days(buildDailyLast14(
                        today,
                        indexDayCount(fUserDaily.join()),
                        indexDayCount(fTryonDaily.join()),
                        indexGeminiDaily(fGeminiDaily.join()),
                        indexDayCount(fUploadDaily.join()),
                        fOrderDaily.join()))
                .build();
    }

    private List<AdminStatsDailyPointVO> buildDailyLast14(
            LocalDate today,
            Map<String, Long> users,
            Map<String, Long> tryon,
            Map<String, Map<String, Long>> gemini,
            Map<String, Long> uploads,
            List<Map<String, Object>> orderRows) {
        Map<String, Long> paidCount = new HashMap<>();
        Map<String, BigDecimal> paidAmt = new HashMap<>();
        if (orderRows != null) {
            for (Map<String, Object> row : orderRows) {
                String d = str(val(row, "d"));
                if (d == null) {
                    continue;
                }
                paidCount.put(d, toLong(val(row, "c")));
                paidAmt.put(d, toDecimal(val(row, "amt")));
            }
        }
        List<AdminStatsDailyPointVO> list = new ArrayList<>(14);
        for (int ago = 13; ago >= 0; ago--) {
            String ds = today.minusDays(ago).format(DAY);
            Map<String, Long> g = gemini.getOrDefault(ds, Map.of());
            list.add(AdminStatsDailyPointVO.builder()
                    .date(ds)
                    .newUsers(users.getOrDefault(ds, 0L))
                    .tryonTasksCreated(tryon.getOrDefault(ds, 0L))
                    .localEditTasksCreated(g.getOrDefault(GeminiImageTaskType.EDIT, 0L))
                    .clothingImageTasksCreated(g.getOrDefault(GeminiImageTaskType.CLOTHING_PRODUCT_SUITE, 0L))
                    .modelImageTasksCreated(g.getOrDefault(GeminiImageTaskType.CLOTHING_PACKSHOT, 0L))
                    .userUploads(uploads.getOrDefault(ds, 0L))
                    .paidOrders(paidCount.getOrDefault(ds, 0L))
                    .paidAmount(paidAmt.getOrDefault(ds, BigDecimal.ZERO))
                    .build());
        }
        return list;
    }

    private static Map<String, Long> indexDayCount(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            String d = str(val(row, "d"));
            if (d != null) {
                map.put(d, toLong(val(row, "c")));
            }
        }
        return map;
    }

    private static Map<String, Map<String, Long>> indexGeminiDaily(List<Map<String, Object>> rows) {
        Map<String, Map<String, Long>> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            String d = str(val(row, "d"));
            String type = str(val(row, "taskType"));
            if (d == null || type == null) {
                continue;
            }
            map.computeIfAbsent(d, k -> new HashMap<>()).put(type, toLong(val(row, "c")));
        }
        return map;
    }

    private static Map<String, Map<String, Object>> indexBy(List<Map<String, Object>> rows, String key) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            String k = str(val(row, key));
            if (k != null) {
                map.put(k, row);
            }
        }
        return map;
    }

    private static Object val(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static long lng(Map<String, Object> map, String key) {
        return toLong(val(map, key));
    }

    private static BigDecimal dec(Map<String, Object> map, String key) {
        return toDecimal(val(map, key));
    }

    private static String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return new BigDecimal(v.toString()).longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
