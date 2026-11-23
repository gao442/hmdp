package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //1.获取session
        HttpSession session = request.getSession();
        //2.获取session中的user信息
        Object userDTO = session.getAttribute("userDTO");
        //3.判断用户是否存在
        if (userDTO == null) {
            //4.不存在直接拦截
            //401未授权
            response.setStatus(401);
            return false;
        }
        //5.存在保存在线程空间
        UserHolder.saveUser((UserDTO) userDTO );
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清理 ThreadLocal 中的用户信息，
        /*
        omcat 线程池会反复复用工作线程，线程用完不会销毁
        ；如果不在这里调用 UserHolder.removeUser()
        ，当前用户的 UserDTO 就会一直挂在线程上无法被 GC 回收
        ，导致内存泄漏（或数据残留
         */
        /*
            HTTP 请求进来
        │
        ▼
        1. preHandle()        ◀── 在进入 Controller 方法【之前】执行（返回 true 放行，false 拦截）
        │
        ▼
        执行你的 Controller → Service → Mapper 业务代码
        │
        ▼
        2. postHandle()       ◀── 在 Controller 执行【之后】、视图/响应渲染【之前】执行（一般用不到）
        │
        ▼
        渲染视图 / 写出 JSON 响应给前端
        │
        ▼
        3. afterCompletion()  ◀── 在整个请求【彻底完成】、响应发给前端【之后】执行（最后一站）
         */
        UserHolder.removeUser();
    }
}
