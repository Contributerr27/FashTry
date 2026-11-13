package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fashtry.backend.bean.db.UserUploadRecord;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserUploadVO extends UserUploadRecord {

    private String userNickname;
    private String userMobile;

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long getId() {
        return super.getId();
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long getUserId() {
        return super.getUserId();
    }
}
