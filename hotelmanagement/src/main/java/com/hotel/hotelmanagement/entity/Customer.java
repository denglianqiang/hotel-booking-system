package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户表实体
 */
@Data
@TableName("t_customer")
public class Customer {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 姓名 */
    private String name;

    /** 电话（客人自助登录账号） */
    private String phone;

    /** 身份证号 */
    private String idCard;

    /** 密码（BCrypt 摘要，60 位；客人自助注册时写入，管理员代录为空；任何接口不返回） */
    @JsonIgnore
    private String password;

    /** 性别: 男/女 */
    private String gender;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** 是否已设置登录密码（可登录普通用户端）。非表字段，服务层按 password 是否为空填充；密码摘要本身永不出库 */
    @TableField(exist = false)
    private Boolean registered;
}
