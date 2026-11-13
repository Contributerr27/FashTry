package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.dto.ModelVideoSubmitRequest;
import com.fashtry.backend.bean.vo.ModelVideoPricingVO;
import com.fashtry.backend.bean.vo.ModelVideoStatusVO;
import com.fashtry.backend.bean.db.ModelVideoTask;
import com.fashtry.backend.bean.vo.ModelVideoSubmitVO;
import com.fashtry.backend.bean.vo.PageResult;

public interface ModelVideoService {

    ModelVideoPricingVO getPricing();

    ModelVideoSubmitVO submit(ModelVideoSubmitRequest request);

    ModelVideoStatusVO getStatus(Long taskId);

    PageResult<ModelVideoStatusVO> listMyTasks(int page, int size);

    void deductCreditsOnSuccess(ModelVideoTask task);
}
