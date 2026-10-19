package com.sheldon.flowable_demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.sheldon.flowable_demo.entity.SysUser;
import com.sheldon.flowable_demo.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

@Slf4j
@Service
@DS("userdb")
public class SysUserService {

    @Resource
    private SysUserMapper sysUserMapper;

    public SysUser login(String username, String password){
        log.info("【登录校验】用户名:{}",username);
        SysUser user = sysUserMapper.findUserByUsername(username);
        if(user == null){
            log.warn("【登录校验】用户不存在，username:{}",username);
            return null;
        }
        // 密码校验（示例明文；正式项目建议加密BCrypt）
        if(!user.getPassword().equals(password)){
            log.warn("【登录校验】密码错误，username:{}",username);
            return null;
        }
        log.info("【登录校验】登录成功，用户id:{}",user.getId());
        return user;
    }
}