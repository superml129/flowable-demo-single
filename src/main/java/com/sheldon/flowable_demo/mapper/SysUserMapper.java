package com.sheldon.flowable_demo.mapper;

import com.sheldon.flowable_demo.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper {
    @Select("select * from sys_user where username=#{username} and status=1")
    SysUser findUserByUsername(@Param("username") String username);
}