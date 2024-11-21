package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Objects;
import java.util.Random;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        if (!RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("The phone number is invalid");
        }
        // 2. 校验验证码
        String code = loginForm.getCode();  // 用户提交的验证码
        Object cacheCode = session.getAttribute("code");    // 生成的验证码
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            // 3. 不一致则报错
            return Result.fail("The code is wrong!");
        }
        // 4. 一致根据手机号查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        // 5. 判断用户是否存在
        if (user == null) {
            // 6. 不存在则创建新用户并保存信息
            user = createUserWithPhone(loginForm.getPhone());
        }
        // 7. 保存用户到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        // 设置用户信息
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));

        // 保存用户信息到数据库
        save(user);
        return user;
    }
}
