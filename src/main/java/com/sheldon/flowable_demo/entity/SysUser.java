package com.sheldon.flowable_demo.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class SysUser implements Serializable {
    private Integer id;
    private String username;
    private String password;
    private String realName;
    private Integer status;
}