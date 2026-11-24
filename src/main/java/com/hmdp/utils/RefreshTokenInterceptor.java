package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //1.获取token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true ;
        }
        String key =RedisConstants.LOGIN_USER_KEY + token;
        //2.通过token获取到redis中的用户数据
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //3.判断用户是否存在
        //redis中如果没有这个用户数据不会返回空，回返回一个空的map
        if (userMap.isEmpty()) {
            return true;
        }
        //5.存在保存在线程空间
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        //刷新过期时间
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清理 ThreadLocal 中的用户信息，
        /*
        tomcat 线程池会反复复用工作线程，线程用完不会销毁
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
