package com.sheldon.flowable_demo.entity;

import lombok.Data;

@Data
public class SysUser {
    private Integer id;
    private String username;
    private String password;
    private String realName;
    private Integer status;
}