package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 验证手机号
        if (!RegexUtils.isPhoneInvalid(phone)) {
            // 2. 不符合则报错
            return Result.fail("The phone number is invalid");
        }
        // 3. 符合则生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 验证码保存到session
        session.setAttribute("code", code);
        // 5. 发送验证码
        log.debug("send code: " + code + " successfully!");
        // 6. 返回状态
        return Result.ok();
    }
}
