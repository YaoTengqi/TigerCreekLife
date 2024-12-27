package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 验证手机号
        if (!RegexUtils.isPhoneInvalid(phone)) {
            // 2. 不符合则报错
            return Result.fail("The phone number is invalid");
        }
        // 3. 符合则生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 验证码保存到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5. 发送验证码
        log.debug("send code: " + code + " successfully!");
        // 6. 返回状态
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (!RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("The phone number is invalid");
        }
        // 2. 校验验证码
        String code = loginForm.getCode();  // 用户提交的验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);    // 从redis中获取生成的验证码
        if (cacheCode == null || !cacheCode.equals(code)) {
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
        // 7. 保存用户到redis
        // 生成随机的token作为redis_hash的key
        String token = UUID.randomUUID().toString().replace("-", "");
        // 将user对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        // 将token-user存入redis
        userMap.forEach((k, v) -> {
            userMap.put(k, v.toString());   // 将所有的value类型转为String
        });
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        // 和session一样需要对存入redis的token设置一个有效期, 防止redis中存储太多信息
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);    // 返回token到前端
    }

    @Override
    public Result sign() {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 拼接key
        String month = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + month;
        // 判断今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
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
