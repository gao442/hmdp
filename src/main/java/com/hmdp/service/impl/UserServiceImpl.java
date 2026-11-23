package com.hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
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
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        //验证手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式不正确");
        }
        //生成随机验证码
        String code = RandomUtil.randomNumbers(6);
        //保存生成的验证码
        /*
        tomcat里面存储了非常多的sessionid，
        通过sessionid找出来对应的唯一session，
        然后再从session里找存储的code给后端

        session 里面是Map<String, Object>
        session 就当成是 Tomcat 专门为当前这个用户 new 出来的一个专属 HashMap
        ，只要服务不重启、会话不过期，你想往里面放 "code"、"user" 还是其他临时数据都可以

        Tomcat 内存里有一张总表（外层 Map），存着成千上万个 JSESSIONID -> HttpSession；
        收到请求时，先凭浏览器带来的那个 JSESSIONID，从总表里找到唯一属于这个用户的 HttpSession 对象。
        第二层（取物）：进入这个 HttpSession 自己的内部 Map，
        凭你在代码里写的 Key（比如 "code" 或 "user"），
        把当初存进去的验证码或用户信息取出来交给后端代码使用。
         */
        //验证码储存在session里面，
        //session储存在tomcat里面，通过sessionId找到对应的session
        //浏览器和tomcat通过sessionId一一对应
        //再通过取出code进行验证是否一致

        //在redis里面储存
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES);
        //发送短信
        log.debug("手机验证码"+code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();

        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号不正确");
        }
        //校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

        if (cacheCode == null ||
                !cacheCode.equals(loginForm.getCode())){
            return Result.fail("验证码不正确");
        }
        /*
        前提是你的 UserServiceImpl.java
        继承了 MyBatis-Plus 的 ServiceImpl<UserMapper, User>，它自带了这套方法
         */

        //在数据库中查找用户是否存在
        User user = lambdaQuery().eq(User::getPhone, phone).one();

        if(user == null){
            //不存在直接注册
            user = createUserWithPhone(phone);
        }

        //生成token
        // 去掉中划线的32位UUID，如 "a1b2c3d4..
        String token = UUID.randomUUID().toString(true);

        //将user封装成userDTO,并且转化成map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                                fieldValue == null ? null : fieldValue.toString()));

        //将userMap存储在redis中，并实现过期提出设置时间限制
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //给前端返回token，每次在请求头中带着token
        return Result.ok(token);
        /*
        不需要返回凭证
        拦截器去 session.getAttribute("user") 里查看有没有用户信息。

        如果没有（为 null）：
        说明没登录（或 Session 过期），
        直接返回 HTTP 状态码 401（未登录），
        请求根本进不到你的业务代码里。

        如果有（拿到了 UserDTO）：
        说明已经登录成功！拦截器会顺手把这个 UserDTO 存进当前请求线程的 ThreadLocal 里
        （也就是调用项目里已经写好的 UserHolder.java 的 UserHolder.saveUser(userDTO)），然后放行。
         */

    }
    private User createUserWithPhone(String phone){
        User user = User.builder()
                .phone(phone)
                .nickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(8))
                .build();
        save(user);
        return user;
    }


}
