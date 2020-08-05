package com.carson.cloud.business.controller;

import com.carson.cloud.business.common.Result;
import com.carson.cloud.business.viewmodel.LoginByPasswordIVO;
import com.carson.cloud.business.viewmodel.LoginByPasswordOVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {
    @PostMapping(value = "/login/loginByPassword")
    Result<LoginByPasswordOVO> loginByPassword(@RequestBody LoginByPasswordIVO ivo) {
        LoginByPasswordOVO ovo = new LoginByPasswordOVO();
        ovo.setPassword(ivo.getPassword());
        ovo.setUserName(ivo.getUserName());
        ovo.setTelephone("180****0000");
        ovo.setUserId("1001");
        Result<LoginByPasswordOVO> result = new Result<>();
        result.setData(ovo);
        return result;
    }
}
