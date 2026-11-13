package com.fashtry.backend.controller.publicapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.PublicModel;
import com.fashtry.backend.bean.vo.PublicModelVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.mapper.PublicModelMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 公共模特库（无需登录）
 */
@RestController
@RequestMapping("/api/public")
public class PublicModelController {

    private final PublicModelMapper publicModelMapper;

    public PublicModelController(PublicModelMapper publicModelMapper) {
        this.publicModelMapper = publicModelMapper;
    }

    /**
     * 获取模特列表，支持按性别、风格筛选
     */
    @GetMapping("/models")
    public Result<List<PublicModelVO>> listModels(
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String region) {
        LambdaQueryWrapper<PublicModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PublicModel::getIsActive, 1)
                .orderByAsc(PublicModel::getSortOrder);
        if (gender != null && !gender.isBlank()) {
            wrapper.eq(PublicModel::getGender, gender);
        }
        if (style != null && !style.isBlank()) {
            wrapper.eq(PublicModel::getStyle, style);
        }
        if (region != null && !region.isBlank()) {
            wrapper.eq(PublicModel::getRegion, region);
        }
        List<PublicModel> list = publicModelMapper.selectList(wrapper);
        List<PublicModelVO> voList = list.stream()
                .map(m -> PublicModelVO.builder()
                        .id(m.getId())
                        .name(m.getName())
                        .gender(m.getGender())
                        .region(m.getRegion())
                        .ageGroup(m.getAgeGroup())
                        .style(m.getStyle())
                        .imageUrl(m.getImageUrl())
                        .thumbUrl(m.getThumbUrl())
                        .build())
                .collect(Collectors.toList());
        return Result.success(voList);
    }
}
