package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

public class UserHolder {
    //就是在里面new一个静态变量tl然后全局都可以存和取
    /*
    虽然大家调用的都是同一个 tl，但当你调用 tl.set(user) 时
    ，ThreadLocal 会自动找到当前正在执行代码的那个线程，
    把数据存进这个线程自己私有的小空间里

    并发访问时每个用户（每个线程）的数据完全独立、绝不打
     */
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
