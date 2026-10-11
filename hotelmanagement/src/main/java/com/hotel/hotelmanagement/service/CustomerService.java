package com.hotel.hotelmanagement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hotel.hotelmanagement.dto.CustomerDTO;
import com.hotel.hotelmanagement.dto.CustomerQueryDTO;
import com.hotel.hotelmanagement.entity.Customer;

import java.util.List;

/**
 * 客户表 Service 接口
 */
public interface CustomerService extends IService<Customer> {

    /**
     * 分页查询客户列表
     */
    IPage<Customer> queryPage(CustomerQueryDTO query);

    /**
     * 获取全部客户（预订时下拉选择用）
     */
    List<Customer> listAll();

    /**
     * 新增客户
     */
    void saveCustomer(CustomerDTO dto);

    /**
     * 编辑客户
     */
    void updateCustomer(CustomerDTO dto);

    /**
     * 删除客户
     */
    void deleteCustomer(Long id);

    /**
     * 重置普通用户登录密码（前台代客开通或重置），新密码 BCrypt 存储
     */
    void resetPassword(Long id, String rawPassword);
}
