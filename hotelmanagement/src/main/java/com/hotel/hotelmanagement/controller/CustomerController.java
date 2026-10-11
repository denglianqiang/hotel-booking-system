package com.hotel.hotelmanagement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hotel.hotelmanagement.annotation.RequirePermission;
import com.hotel.hotelmanagement.common.Result;
import com.hotel.hotelmanagement.dto.CustomerDTO;
import com.hotel.hotelmanagement.dto.CustomerQueryDTO;
import com.hotel.hotelmanagement.dto.CustomerResetPasswordDTO;
import com.hotel.hotelmanagement.entity.Customer;
import com.hotel.hotelmanagement.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户管理 Controller
 *
 * <p>权限码说明：
 * <ul>
 *   <li>读取客户列表、快速建档（"新增客户"）是下单流程的一部分，
 *       所以 orders / roomStatus 权限同样放行 —— 前台开单时经常要顺手建档</li>
 *   <li>编辑、删除客户属于客户资料维护，只对 customers 权限开放</li>
 * </ul>
 */
@RestController
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * POST /customer/list - 分页查询客户列表
     */
    @PostMapping("/list")
    @RequirePermission("customers")
    public Result<IPage<Customer>> list(@Valid @RequestBody CustomerQueryDTO query) {
        IPage<Customer> page = customerService.queryPage(query);
        return Result.success(page);
    }

    /**
     * GET /customer/all - 获取全部客户（预订下拉选择用）
     */
    @GetMapping("/all")
    @RequirePermission({"customers", "orders", "roomStatus"})
    public Result<List<Customer>> all() {
        return Result.success(customerService.listAll());
    }

    /**
     * POST /customer/save - 新增客户（订单页/房态图的"快速新建客户"会调用）
     */
    @PostMapping("/save")
    @RequirePermission({"customers", "orders", "roomStatus"})
    public Result<Void> save(@Valid @RequestBody CustomerDTO dto) {
        customerService.saveCustomer(dto);
        return Result.success("新增成功");
    }

    /**
     * PUT /customer/update - 编辑客户
     */
    @PutMapping("/update")
    @RequirePermission("customers")
    public Result<Void> update(@Valid @RequestBody CustomerDTO dto) {
        customerService.updateCustomer(dto);
        return Result.success("修改成功");
    }

    /**
     * PUT /customer/reset-password - 重置普通用户登录密码（前台代客开通或重置）
     */
    @PutMapping("/reset-password")
    @RequirePermission("customers")
    public Result<Void> resetPassword(@Valid @RequestBody CustomerResetPasswordDTO dto) {
        customerService.resetPassword(dto.getId(), dto.getPassword());
        return Result.success("密码已重置，该用户即可用手机号 + 新密码登录");
    }

    /**
     * DELETE /customer/delete/{id} - 删除客户
     */
    @DeleteMapping("/delete/{id}")
    @RequirePermission("customers")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return Result.success("删除成功");
    }
}
