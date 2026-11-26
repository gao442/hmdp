package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/*
导入依赖
在 pom.xml 中引入 mybatis-plus-spring-boot3-starter（以及分页所需的 mybatis-plus-jsqlparser）。

启动类开启 Mapper 扫描
在启动类 HmDianPingApplication.java 上加上 @MapperScan("com.hmdp.mapper")，让 Spring 能扫描并创建所有 Mapper 代理对象。

Mapper 接口继承 BaseMapper<实体类>
比如 UserMapper extends BaseMapper<User>。真正负责生成并执行基础 SQL 的核心其实是 BaseMapper。
(同时实体类如 User.java 上会用 @TableName("tb_user") 告诉框架这个类对应数据库里的哪张表。)

Service 层继承 IService 和 ServiceImpl
接口：IUserService extends IService<User>
实现类：UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService
这样 Service 层就拥有了 query()、update()、save()、getById() 等全套链式方法（它的底层其实就是帮你调用了第 3 步里的 UserMapper）
 */
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class HmDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }

}
