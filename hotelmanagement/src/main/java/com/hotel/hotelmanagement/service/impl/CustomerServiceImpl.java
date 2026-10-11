package com.hotel.hotelmanagement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hotel.hotelmanagement.common.BusinessException;
import com.hotel.hotelmanagement.dto.CustomerDTO;
import com.hotel.hotelmanagement.dto.CustomerQueryDTO;
import com.hotel.hotelmanagement.entity.Customer;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.mapper.CustomerMapper;
import com.hotel.hotelmanagement.mapper.OrderMapper;
import com.hotel.hotelmanagement.service.CustomerService;
import com.hotel.hotelmanagement.util.PasswordUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户表 Service 实现类
 */
@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    private final OrderMapper orderMapper;

    public CustomerServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 分页查询客户列表
     */
    @Override
    public IPage<Customer> queryPage(CustomerQueryDTO query) {
        Page<Customer> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getName()), Customer::getName, query.getName());
        wrapper.like(StringUtils.hasText(query.getPhone()), Customer::getPhone, query.getPhone());
        wrapper.orderByDesc(Customer::getRegisterTime);
        IPage<Customer> result = this.page(page, wrapper);
        fillRegistered(result.getRecords());
        return result;
    }

    /**
     * 获取全部客户（预订时下拉选择用）
     */
    @Override
    public List<Customer> listAll() {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Customer::getRegisterTime);
        List<Customer> list = this.list(wrapper);
        fillRegistered(list);
        return list;
    }

    /**
     * 重置普通用户登录密码（前台代客开通或重置）。
     * 先查完整实体再改密码字段，updateById 走全字段写；新密码一律 BCrypt。
     */
    @Override
    public void resetPassword(Long id, String rawPassword) {
        Customer customer = this.getById(id);
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }
        customer.setPassword(PasswordUtil.encode(rawPassword));
        this.updateById(customer);
    }

    /** 填充注册状态展示字段：有密码即视为已注册。password 是 @JsonIgnore 的，前端只能从这里拿到布尔标记 */
    private void fillRegistered(List<Customer> customers) {
        customers.forEach(c -> c.setRegistered(c.getPassword() != null && !c.getPassword().isEmpty()));
    }

    /**
     * 新增客户
     */
    @Override
    public void saveCustomer(CustomerDTO dto) {
        // 校验手机号唯一
        long count = this.count(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getPhone, dto.getPhone()));
        if (count > 0) {
            throw new BusinessException("该手机号已注册");
        }
        Customer customer = new Customer();
        customer.setName(dto.getName());
        customer.setPhone(dto.getPhone());
        customer.setIdCard(dto.getIdCard());
        customer.setGender(dto.getGender());
        customer.setRegisterTime(LocalDateTime.now());
        this.save(customer);
    }

    /**
     * 编辑客户
     */
    @Override
    public void updateCustomer(CustomerDTO dto) {
        Customer customer = this.getById(dto.getId());
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }
        // 校验手机号唯一（排除自身）
        long count = this.count(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getPhone, dto.getPhone())
                .ne(Customer::getId, dto.getId()));
        if (count > 0) {
            throw new BusinessException("该手机号已被其他客户使用");
        }
        customer.setName(dto.getName());
        customer.setPhone(dto.getPhone());
        customer.setIdCard(dto.getIdCard());
        customer.setGender(dto.getGender());
        this.updateById(customer);
    }

    /**
     * 删除客户。
     *
     * <p>只要该客户存在订单（含已退房的历史订单）就不允许物理删除：
     * t_order.customer_id 是逻辑外键，删掉客户会让历史订单失去关联，
     * 对账与统计都会失真。与"房间有订单不允许删除"保持同一套口径。
     */
    @Override
    public void deleteCustomer(Long id) {
        Customer customer = this.getById(id);
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }
        long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getCustomerId, id));
        if (orderCount > 0) {
            throw new BusinessException("该客户存在 " + orderCount + " 笔订单，删除会让订单失去关联，不能删除");
        }
        this.removeById(id);
    }
}
