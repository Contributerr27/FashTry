package com.fashtry.backend.service.business.impl;

import com.fashtry.backend.bean.vo.GeminiImageEditHistoryItemVO;
import com.fashtry.backend.bean.vo.GeminiPackshotHistoryItemVO;
import com.fashtry.backend.bean.vo.MobileUnifiedHistoryItemVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.TryonStatusVO;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.service.ai.GeminiImageTaskService;
import com.fashtry.backend.service.business.MobileTaskHistoryService;
import com.fashtry.backend.service.business.TryonService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MobileTaskHistoryServiceImpl implements MobileTaskHistoryService {

    private static final int PER_SOURCE_CAP = 50;
    private static final int MAX_PAGE_SIZE = 50;

    private final TryonService tryonService;
    private final GeminiImageTaskService geminiImageTaskService;

    public MobileTaskHistoryServiceImpl(
            TryonService tryonService, GeminiImageTaskService geminiImageTaskService) {
        this.tryonService = tryonService;
        this.geminiImageTaskService = geminiImageTaskService;
    }

    @Override
    public PageResult<MobileUnifiedHistoryItemVO> listUnified(Long userId, String kind, int page, int pageSize) {
        if (userId == null || userId <= 0) {
            return PageResult.of(List.of(), 0, 1, 0);
        }
        String k = kind == null || kind.isBlank() ? "all" : kind.trim().toLowerCase();
        return switch (k) {
            case "all" -> mergeAll(userId);
            case "tryon" -> mapTryonPage(tryonService.listMyTasks(normPage(page), normSize(pageSize)));
            case "image_edit" ->
                    mapImageEditPage(
                            geminiImageTaskService.listMyLocalEditHistory(userId, normPage(page), normSize(pageSize)));
            case "packshot" ->
                    mapPackshotPage(
                            "packshot",
                            geminiImageTaskService.listMyPackshotHistory(userId, normPage(page), normSize(pageSize)));
            case "suite" ->
                    mapSuitePage(
                            geminiImageTaskService.listMyClothingSuiteHistory(
                                    userId, normPage(page), normSize(pageSize)));
            case "retouch" ->
                    mapGeminiPage(
                            "retouch",
                            geminiImageTaskService.listMyProductRetouchHistory(
                                    userId, normPage(page), normSize(pageSize)));
            case "portrait" ->
                    mapGeminiPage(
                            "portrait",
                            geminiImageTaskService.listMyPortraitStudioHistory(
                                    userId, normPage(page), normSize(pageSize)));
            default -> throw new BusinessException(ResultCode.PARAM_INVALID, "kind 无效");
        };
    }

    private static int normPage(int page) {
        return Math.max(1, page);
    }

    private static int normSize(int size) {
        return Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    private PageResult<MobileUnifiedHistoryItemVO> mergeAll(Long userId) {
        PageResult<TryonStatusVO> tr = tryonService.listMyTasks(1, PER_SOURCE_CAP);
        PageResult<GeminiImageEditHistoryItemVO> ie =
                geminiImageTaskService.listMyLocalEditHistory(userId, 1, PER_SOURCE_CAP);
        PageResult<GeminiPackshotHistoryItemVO> pk =
                geminiImageTaskService.listMyPackshotHistory(userId, 1, PER_SOURCE_CAP);
        PageResult<GeminiPackshotHistoryItemVO> su =
                geminiImageTaskService.listMyClothingSuiteHistory(userId, 1, PER_SOURCE_CAP);
        PageResult<GeminiPackshotHistoryItemVO> rt =
                geminiImageTaskService.listMyProductRetouchHistory(userId, 1, PER_SOURCE_CAP);
        PageResult<GeminiPackshotHistoryItemVO> pt =
                geminiImageTaskService.listMyPortraitStudioHistory(userId, 1, PER_SOURCE_CAP);

        List<MobileUnifiedHistoryItemVO> merged = new ArrayList<>();
        for (TryonStatusVO vo : tr.getRecords()) {
            long ms = tryonSortMs(vo);
            merged.add(
                    MobileUnifiedHistoryItemVO.builder().kind("tryon").sortAtMs(ms).tryon(vo).build());
        }
        for (GeminiImageEditHistoryItemVO vo : ie.getRecords()) {
            merged.add(
                    MobileUnifiedHistoryItemVO.builder()
                            .kind("image_edit")
                            .sortAtMs(localDateTimeToMs(vo.getCreatedAt()))
                            .imageEdit(vo)
                            .build());
        }
        for (GeminiPackshotHistoryItemVO vo : pk.getRecords()) {
            merged.add(
                    MobileUnifiedHistoryItemVO.builder()
                            .kind("packshot")
                            .sortAtMs(localDateTimeToMs(vo.getCreatedAt()))
                            .packshot(vo)
                            .build());
        }
        for (GeminiPackshotHistoryItemVO vo : su.getRecords()) {
            merged.add(
                    MobileUnifiedHistoryItemVO.builder()
                            .kind("suite")
                            .sortAtMs(localDateTimeToMs(vo.getCreatedAt()))
                            .suite(vo)
                            .build());
        }
        merged.addAll(mapGeminiPage("retouch", rt).getRecords());
        merged.addAll(mapGeminiPage("portrait", pt).getRecords());
        merged.sort(Comparator.comparingLong(MobileUnifiedHistoryItemVO::getSortAtMs).reversed());
        int n = merged.size();
        return PageResult.of(merged, n, 1, n);
    }

    private static long tryonSortMs(TryonStatusVO vo) {
        if (vo.getSubmittedAt() != null && vo.getSubmittedAt() > 0) {
            return vo.getSubmittedAt();
        }
        return System.currentTimeMillis();
    }

    private static long localDateTimeToMs(LocalDateTime t) {
        if (t == null) {
            return 0L;
        }
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static PageResult<MobileUnifiedHistoryItemVO> mapTryonPage(PageResult<TryonStatusVO> in) {
        List<MobileUnifiedHistoryItemVO> rows = new ArrayList<>();
        for (TryonStatusVO vo : in.getRecords()) {
            long ms = tryonSortMs(vo);
            rows.add(MobileUnifiedHistoryItemVO.builder().kind("tryon").sortAtMs(ms).tryon(vo).build());
        }
        return PageResult.of(rows, in.getTotal(), in.getPage(), in.getPageSize());
    }

    private static PageResult<MobileUnifiedHistoryItemVO> mapImageEditPage(
            PageResult<GeminiImageEditHistoryItemVO> in) {
        List<MobileUnifiedHistoryItemVO> rows = new ArrayList<>();
        for (GeminiImageEditHistoryItemVO vo : in.getRecords()) {
            long ms = localDateTimeToMs(vo.getCreatedAt());
            rows.add(
                    MobileUnifiedHistoryItemVO.builder()
                            .kind("image_edit")
                            .sortAtMs(ms)
                            .imageEdit(vo)
                            .build());
        }
        return PageResult.of(rows, in.getTotal(), in.getPage(), in.getPageSize());
    }

    private static PageResult<MobileUnifiedHistoryItemVO> mapPackshotPage(
            String kind, PageResult<GeminiPackshotHistoryItemVO> in) {
        List<MobileUnifiedHistoryItemVO> rows = new ArrayList<>();
        for (GeminiPackshotHistoryItemVO vo : in.getRecords()) {
            long ms = localDateTimeToMs(vo.getCreatedAt());
            rows.add(
                    MobileUnifiedHistoryItemVO.builder()
                            .kind(kind)
                            .sortAtMs(ms)
                            .packshot(vo)
                            .build());
        }
        return PageResult.of(rows, in.getTotal(), in.getPage(), in.getPageSize());
    }

    private static PageResult<MobileUnifiedHistoryItemVO> mapGeminiPage(
            String kind, PageResult<GeminiPackshotHistoryItemVO> in) {
        List<MobileUnifiedHistoryItemVO> rows = new ArrayList<>();
        for (GeminiPackshotHistoryItemVO vo : in.getRecords()) {
            MobileUnifiedHistoryItemVO.MobileUnifiedHistoryItemVOBuilder b =
                    MobileUnifiedHistoryItemVO.builder().kind(kind).sortAtMs(localDateTimeToMs(vo.getCreatedAt()));
            if ("retouch".equals(kind)) {
                b.retouch(vo);
            } else {
                b.portrait(vo);
            }
            rows.add(b.build());
        }
        return PageResult.of(rows, in.getTotal(), in.getPage(), in.getPageSize());
    }

    private static PageResult<MobileUnifiedHistoryItemVO> mapSuitePage(PageResult<GeminiPackshotHistoryItemVO> in) {
        List<MobileUnifiedHistoryItemVO> rows = new ArrayList<>();
        for (GeminiPackshotHistoryItemVO vo : in.getRecords()) {
            long ms = localDateTimeToMs(vo.getCreatedAt());
            rows.add(
                    MobileUnifiedHistoryItemVO.builder()
                            .kind("suite")
                            .sortAtMs(ms)
                            .suite(vo)
                            .build());
        }
        return PageResult.of(rows, in.getTotal(), in.getPage(), in.getPageSize());
    }
}
