package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取session
        HttpSession session = request.getSession();
        // 2. 获取session中的用户
        Object user = session.getAttribute("user");
        // 3. 判断用户是否存在
        if (user == null) {
            // 4. 不存在则拦截 返回401状态码
            response.setStatus(401);
            return false;
        }
        // 5. 存在则将信息保存到Threadlocal
        User u = (User) user;
        UserHolder.saveUser(new UserDTO(u.getId(), u.getNickName(), u.getIcon()));
        // 6. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();    // 防止内存泄漏
    }
}
