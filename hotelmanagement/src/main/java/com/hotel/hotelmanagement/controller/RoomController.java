package com.hotel.hotelmanagement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hotel.hotelmanagement.annotation.RequirePermission;
import com.hotel.hotelmanagement.common.Result;
import com.hotel.hotelmanagement.dto.RoomDTO;
import com.hotel.hotelmanagement.dto.RoomQueryDTO;
import com.hotel.hotelmanagement.entity.Room;
import com.hotel.hotelmanagement.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 房间管理 Controller
 *
 * <p>权限码说明：
 * <ul>
 *   <li>房态图（roomStatus）需要读取房态、下单预订、切换维修状态</li>
 *   <li>房间管理（rooms）负责房间本身的新增/修改/删除</li>
 * </ul>
 */
@RestController
@RequestMapping("/room")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * POST /room/list - 分页查询房间列表
     * 支持按房间类型、可售性、楼层筛选
     */
    @PostMapping("/list")
    @RequirePermission("rooms")
    public Result<IPage<Room>> list(@Valid @RequestBody RoomQueryDTO query) {
        IPage<Room> page = roomService.queryPage(query);
        return Result.success(page);
    }

    /**
     * GET /room/status - 获取所有房间的房态图数据
     * 可传 checkInDate / checkOutDate 指定日期区间（yyyy-MM-dd），
     * 不传则默认「今天 → 明天」；每个房间会带上派生占用状态 availability。
     */
    @GetMapping("/status")
    @RequirePermission({"roomStatus", "rooms", "orders"})
    public Result<List<Room>> status(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOutDate) {
        List<Room> list = roomService.listAll(checkInDate, checkOutDate);
        return Result.success(list);
    }

    /**
     * POST /room/save - 新增房间
     */
    @PostMapping("/save")
    @RequirePermission("rooms")
    public Result<Void> save(@Valid @RequestBody RoomDTO dto) {
        roomService.saveRoom(dto);
        return Result.success("新增成功");
    }

    /**
     * PUT /room/update - 编辑房间
     */
    @PutMapping("/update")
    @RequirePermission("rooms")
    public Result<Void> update(@Valid @RequestBody RoomDTO dto) {
        roomService.updateRoom(dto);
        return Result.success("修改成功");
    }

    /**
     * DELETE /room/delete/{id} - 删除房间
     */
    @DeleteMapping("/delete/{id}")
    @RequirePermission("rooms")
    public Result<Void> delete(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return Result.success("删除成功");
    }

    /**
     * POST /room/set-status - 手动设置房间可售性（正常销售/停用维修）。
     * 房态图上也能直接切换维修状态，所以 roomStatus 权限同样放行。
     */
    @PostMapping("/set-status")
    @RequirePermission({"rooms", "roomStatus"})
    public Result<Void> setStatus(@RequestBody Map<String, Long> body) {
        // status 用 Long 接收再转 Integer，避免前端传数字被 Jackson 处理出歧义
        Long id = body.get("id");
        Long statusValue = body.get("status");
        roomService.setStatus(id, statusValue == null ? null : statusValue.intValue());
        return Result.success(statusValue == 3 ? "已设为维修" : "已恢复空闲");
    }
}
