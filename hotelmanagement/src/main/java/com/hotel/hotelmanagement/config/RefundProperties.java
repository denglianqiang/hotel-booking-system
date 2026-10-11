package com.hotel.hotelmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 退款规则配置（application.yml 中 hotel.refund 节点）
 */
@Component
@ConfigurationProperties(prefix = "hotel.refund")
public class RefundProperties {

    /**
     * 距入住日的天数 >= 该值时取消，全额退款。
     * 例：默认 1，表示入住日前一天及以上取消不扣费，入住当天取消扣首晚。
     */
    private int freeCancelDays = 1;

    /** 未达到 freeCancelDays 时是否扣除首晚房费（false 则仍然全额退） */
    private boolean chargeFirstNight = true;

    public int getFreeCancelDays() {
        return freeCancelDays;
    }

    public void setFreeCancelDays(int freeCancelDays) {
        this.freeCancelDays = freeCancelDays;
    }

    public boolean isChargeFirstNight() {
        return chargeFirstNight;
    }

    public void setChargeFirstNight(boolean chargeFirstNight) {
        this.chargeFirstNight = chargeFirstNight;
    }
}
