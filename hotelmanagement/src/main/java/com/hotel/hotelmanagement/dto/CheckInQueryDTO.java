package com.hotel.hotelmanagement.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 入住登记记录查询参数
 */
@Data
public class CheckInQueryDTO {

    /** 当前页码（默认1） */
    private Integer current = 1;

    /** 每页条数（默认10，服务端会限制上限） */
    private Integer size = 10;

    /** 房间号（模糊匹配） */
    private String roomNumber;

    /** 客户姓名（模糊匹配） */
    private String customerName;

    /** 入住日期区间起（含） */
    private LocalDate startDate;

    /** 入住日期区间止（含） */
    private LocalDate endDate;

    public int resolveCurrent() {
        return current == null || current < 1 ? 1 : current;
    }

    /** 限制每页条数，避免被传入超大 size 拖库 */
    public int resolveSize() {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }
}
