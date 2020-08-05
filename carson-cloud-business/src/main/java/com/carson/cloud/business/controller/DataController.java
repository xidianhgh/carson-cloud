package com.carson.cloud.business.controller;

import com.carson.cloud.business.common.Result;
import com.carson.cloud.business.viewmodel.GetDataOVO;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataController {
    @GetMapping(value = "/data/getData")
    Result<GetDataOVO> getData(@RequestHeader("userId") String UserId) {
        GetDataOVO ovo = new GetDataOVO();
        ovo.setList(CollectionUtils.arrayToList(new String[]{"张*三", "李*四", "王*五", "马*六"}));
        Result<GetDataOVO> result = new Result<>();
        result.setData(ovo);
        return result;
    }
}
