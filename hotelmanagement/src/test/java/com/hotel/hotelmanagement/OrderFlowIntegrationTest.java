package com.hotel.hotelmanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 核心业务规则的集成测试。
 *
 * <p>覆盖的都是"改错了会出事故"的地方：超卖、订单状态机、退款规则、计划日期不被覆盖、页面级权限。
 *
 * <p>运行前提：本机 MySQL 已按 sql/hotel_db.sql + sql/answer_demo_data.sql 初始化好 hotel_db。
 * 不需要 Redis：会话与缓存通过 application-test.yml 退回内存实现。
 * 类上的 {@code @Transactional} 会让每个测试方法结束后回滚，因此不会污染演示数据。
 * 之所以用真实数据库而不是 Mock：这些规则本身就是"和数据库打交道"的（行锁、区间查询、唯一键），
 * 用 Mock 测不出真问题。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Transactional
class OrderFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private CacheManager cacheManager;

    /**
     * 缓存不参与 @Transactional 回滚：上个用例留下的缓存条目会污染下个用例的房态查询，
     * 每个用例前整体清空（缓存 key 全部由请求参数派生，清空无副作用）。
     */
    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    // ===================== 工具方法 =====================

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode postJson(String path, String token, String body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header("Authorization", token);
        }
        // body 为 null 表示无请求体（如 DELETE），不能调 content(null)，否则 NPE
        if (body != null) {
            request.content(body);
        }
        return json(mockMvc.perform(request).andReturn());
    }

    private JsonNode getJson(String path, String token) throws Exception {
        var request = get(path);
        if (token != null) {
            request.header("Authorization", token);
        }
        return json(mockMvc.perform(request).andReturn());
    }

    private JsonNode putJson(String path, String token, String body) throws Exception {
        var request = put(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header("Authorization", token);
        }
        return json(mockMvc.perform(request.content(body)).andReturn());
    }

    private String login(String username, String password) throws Exception {
        JsonNode res = postJson("/user/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertThat(res.path("code").asInt()).as("登录应成功: " + res.path("message").asText()).isEqualTo(200);
        return res.path("data").path("token").asText();
    }

    /** 取一间在指定区间内可订的房间（房态由后端按订单推导，这里直接问接口） */
    private long findAvailableRoomId(String token, LocalDate in, LocalDate out) throws Exception {
        JsonNode res = getJson("/room/status?checkInDate=" + in + "&checkOutDate=" + out, token);
        assertThat(res.path("code").asInt()).isEqualTo(200);
        for (JsonNode room : res.path("data")) {
            if ("available".equals(room.path("availability").asText())) {
                return room.path("id").asLong();
            }
        }
        throw new IllegalStateException("测试区间内没有可订房间，请检查演示数据: " + in + " ~ " + out);
    }

    private JsonNode createOrder(String token, long roomId, long customerId, LocalDate in, LocalDate out) throws Exception {
        return postJson("/order/create", token, String.format(
                "{\"roomId\":%d,\"customerId\":%d,\"checkInDate\":\"%s\",\"checkOutDate\":\"%s\"}",
                roomId, customerId, in, out));
    }

    private Order reload(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    // ===================== 测试 =====================

    @Test
    @DisplayName("未付款不锁房：同房间同区间允许多笔待支付订单，先付先得，后付被拒")
    void unpaidOrderDoesNotBlockRoomAndFirstPayWins() throws Exception {
        String token = login("admin", "admin123");
        LocalDate in = LocalDate.now().plusDays(30);
        LocalDate out = in.plusDays(2);
        long roomId = findAvailableRoomId(token, in, out);

        long firstId = createOrder(token, roomId, 2L, in, out).path("data").path("id").asLong();

        // 待支付订单不占用房间：另一客户在相同房间、相同区间的订单也可以创建
        JsonNode second = createOrder(token, roomId, 3L, in, out);
        assertThat(second.path("code").asInt())
                .as("待支付订单不应锁房: " + second.path("message").asText()).isEqualTo(200);
        long secondId = second.path("data").path("id").asLong();

        // 先支付者占用成功
        assertThat(postJson("/order/pay", token, "{\"orderId\":" + firstId + "}").path("code").asInt()).isEqualTo(200);

        // 后支付者被支付时的房态复核拒绝，房间不会被双卖
        JsonNode latePay = postJson("/order/pay", token, "{\"orderId\":" + secondId + "}");
        assertThat(latePay.path("code").asInt()).isNotEqualTo(200);
        assertThat(latePay.path("message").asText()).contains("已被其他订单占用");
    }

    @Test
    @DisplayName("在途订单上限：同一客户的 待支付/已支付/已入住 达到上限后不允许再订")
    void customerActiveOrderLimitIsEnforced() throws Exception {
        String token = login("admin", "admin123");

        // 新建一个无任何订单的客户（手机号从时间戳构造，保证唯一）
        String phone = "137" + String.format("%08d", System.currentTimeMillis() % 100000000L);
        JsonNode saved = postJson("/customer/save", token,
                "{\"name\":\"限额测试客户\",\"phone\":\"" + phone + "\"}");
        assertThat(saved.path("code").asInt())
                .as("建档应成功: " + saved.path("message").asText()).isEqualTo(200);
        JsonNode list = postJson("/customer/list", token,
                "{\"current\":1,\"size\":10,\"phone\":\"" + phone + "\"}");
        long customerId = list.path("data").path("records").path(0).path("id").asLong();

        LocalDate in = LocalDate.now().plusDays(80);
        LocalDate out = in.plusDays(2);

        // 连订 3 间不同房间（上限默认 3）：都应成功
        for (int i = 0; i < 3; i++) {
            long roomId = findAvailableRoomId(token, in, out);
            JsonNode order = createOrder(token, roomId, customerId, in, out);
            assertThat(order.path("code").asInt())
                    .as("第 " + (i + 1) + " 单应成功: " + order.path("message").asText()).isEqualTo(200);
        }

        // 第 4 单：超过上限，拒绝
        long roomId = findAvailableRoomId(token, in, out);
        JsonNode fourth = createOrder(token, roomId, customerId, in, out);
        assertThat(fourth.path("code").asInt()).isNotEqualTo(200);
        assertThat(fourth.path("message").asText()).contains("已达上限");
    }

    @Test
    @DisplayName("相邻区间不算冲突：上一单 5 号退房，下一单 5 号入住应可下单")
    void adjacentRangeIsNotAConflict() throws Exception {
        String token = login("admin", "admin123");
        LocalDate in = LocalDate.now().plusDays(40);
        LocalDate mid = in.plusDays(2);
        LocalDate out = mid.plusDays(2);
        long roomId = findAvailableRoomId(token, in, out);

        assertThat(createOrder(token, roomId, 2L, in, mid).path("code").asInt()).isEqualTo(200);
        JsonNode adjacent = createOrder(token, roomId, 2L, mid, out);
        assertThat(adjacent.path("code").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("状态机：待支付订单不能直接办理入住，也不能重复支付")
    void pendingOrderCannotCheckIn() throws Exception {
        String token = login("admin", "admin123");
        LocalDate in = LocalDate.now().plusDays(50);
        long roomId = findAvailableRoomId(token, in, in.plusDays(1));

        long orderId = createOrder(token, roomId, 2L, in, in.plusDays(1)).path("data").path("id").asLong();

        JsonNode checkIn = postJson("/order/checkin", token, "{\"orderId\":" + orderId + "}");
        assertThat(checkIn.path("code").asInt()).isNotEqualTo(200);
        assertThat(checkIn.path("message").asText()).contains("只有已支付的订单");

        assertThat(postJson("/order/pay", token, "{\"orderId\":" + orderId + "}").path("code").asInt()).isEqualTo(200);
        JsonNode payAgain = postJson("/order/pay", token, "{\"orderId\":" + orderId + "}");
        assertThat(payAgain.path("code").asInt()).isNotEqualTo(200);
        assertThat(payAgain.path("message").asText()).contains("只有待支付的订单");
    }

    @Test
    @DisplayName("退房不覆盖计划退房日：check_out_date 保持原值，实际时间记在 actual_check_out_time")
    void checkOutKeepsPlannedDate() throws Exception {
        String token = login("admin", "admin123");
        LocalDate in = LocalDate.now();
        LocalDate plannedOut = in.plusDays(1);
        long roomId = findAvailableRoomId(token, in, plannedOut);

        long orderId = createOrder(token, roomId, 2L, in, plannedOut).path("data").path("id").asLong();
        postJson("/order/pay", token, "{\"orderId\":" + orderId + "}");
        postJson("/order/checkin", token, "{\"orderId\":" + orderId + "}");
        postJson("/order/checkout", token, "{\"orderId\":" + orderId + "}");

        Order order = reload(orderId);
        assertThat(order.getStatus()).isEqualTo(3);
        assertThat(order.getCheckOutDate()).as("计划退房日不能被覆盖").isEqualTo(plannedOut);
        assertThat(order.getActualCheckOutTime()).as("实际退房时间应单独记录").isNotNull();
        assertThat(order.getFinalPrice()).as("退房时应写入结算金额").isNotNull();
        assertThat(order.getActualCheckInTime()).isNotNull();
    }

    @Test
    @DisplayName("退款规则：距入住日 >=1 天取消，全额退款")
    void cancelFarAheadRefundsInFull() throws Exception {
        String token = login("admin", "admin123");
        LocalDate in = LocalDate.now().plusDays(60);
        long roomId = findAvailableRoomId(token, in, in.plusDays(2));

        JsonNode created = createOrder(token, roomId, 2L, in, in.plusDays(2));
        long orderId = created.path("data").path("id").asLong();
        BigDecimal total = created.path("data").path("totalPrice").decimalValue();
        postJson("/order/pay", token, "{\"orderId\":" + orderId + "}");

        JsonNode cancelled = postJson("/order/cancel", token, "{\"orderId\":" + orderId + ",\"reason\":\"行程变更\"}");
        assertThat(cancelled.path("code").asInt()).isEqualTo(200);
        assertThat(cancelled.path("data").path("refundAmount").decimalValue())
                .as("提前 60 天取消应全额退款").isEqualByComparingTo(total);
    }

    @Test
    @DisplayName("退款规则：入住当天取消，扣除首晚房费后退余额")
    void cancelOnCheckInDayDeductsFirstNight() throws Exception {
        String token = login("admin", "admin123");
        LocalDate in = LocalDate.now();
        LocalDate out = in.plusDays(3);
        long roomId = findAvailableRoomId(token, in, out);

        JsonNode created = createOrder(token, roomId, 2L, in, out);
        long orderId = created.path("data").path("id").asLong();
        BigDecimal total = created.path("data").path("totalPrice").decimalValue();
        postJson("/order/pay", token, "{\"orderId\":" + orderId + "}");

        JsonNode cancelled = postJson("/order/cancel", token, "{\"orderId\":" + orderId + "}");
        BigDecimal firstNight = total.divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
        assertThat(cancelled.path("data").path("refundAmount").decimalValue())
                .as("入住当天取消应扣掉首晚").isEqualByComparingTo(total.subtract(firstNight));
    }

    @Test
    @DisplayName("引用完整性：有订单的客户与房间都不允许删除")
    void customerAndRoomWithOrdersCannotBeDeleted() throws Exception {
        String token = login("admin", "admin123");
        JsonNode customer = json(mockMvc.perform(delete("/customer/delete/1").header("Authorization", token)).andReturn());
        JsonNode room = json(mockMvc.perform(delete("/room/delete/1").header("Authorization", token)).andReturn());
        assertThat(customer.path("code").asInt()).isNotEqualTo(200);
        assertThat(customer.path("message").asText()).contains("订单");
        assertThat(room.path("code").asInt()).isNotEqualTo(200);
        assertThat(room.path("message").asText()).contains("订单");
    }

    @Test
    @DisplayName("页面级权限：只有 orders 权限的账号访问房间管理接口返回 403")
    void ordersOnlyAccountIsForbiddenOnRoomModule() throws Exception {
        String operatorToken = login("operator", "operator123");
        // RBAC：先建一个只含 orders 权限的角色，再把账号挂到该角色上
        postJson("/role/save", operatorToken,
                "{\"roleCode\":\"t_orders_only\",\"roleName\":\"仅订单角色\",\"permissionCodes\":[\"orders\"]}");
        long roleId = findRoleIdByCode(operatorToken, "t_orders_only");

        String username = "t_perm_" + System.nanoTime();
        JsonNode created = postJson("/user/save", operatorToken, String.format(
                "{\"username\":\"%s\",\"password\":\"test123456\",\"nickname\":\"权限测试\",\"roleIds\":[%d]}",
                username, roleId));
        assertThat(created.path("code").asInt()).as("创建账号应成功: " + created.path("message").asText()).isEqualTo(200);

        String token = login(username, "test123456");

        // 自己角色内的权限：放行
        assertThat(postJson("/order/list", token, "{\"current\":1,\"size\":5}").path("code").asInt()).isEqualTo(200);
        // 其他模块：403（HTTP 状态码，不是响应体 code）
        mockMvc.perform(post("/room/list").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"current\":1,\"size\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/statistics/dashboard").header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/checkin/list").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC：调整角色权限后，持有该角色的账号立即按新权限生效")
    void rolePermissionChangeAffectsMembersImmediately() throws Exception {
        String operatorToken = login("operator", "operator123");
        postJson("/role/save", operatorToken,
                "{\"roleCode\":\"t_dyn\",\"roleName\":\"动态权限角色\",\"permissionCodes\":[\"rooms\"]}");
        long roleId = findRoleIdByCode(operatorToken, "t_dyn");

        String username = "t_rbac_" + System.nanoTime();
        assertThat(postJson("/user/save", operatorToken, String.format(
                "{\"username\":\"%s\",\"password\":\"test123456\",\"nickname\":\"动态权限\",\"roleIds\":[%d]}",
                username, roleId)).path("code").asInt()).isEqualTo(200);
        String token = login(username, "test123456");

        // 初始只有 rooms：房间列表放行，订单列表拒绝
        assertThat(postJson("/room/list", token, "{\"current\":1,\"size\":5}").path("code").asInt()).isEqualTo(200);
        mockMvc.perform(post("/order/list").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"current\":1,\"size\":5}"))
                .andExpect(status().isForbidden());

        // 角色权限改为 orders：同一账号同一 token，接口边界立即变化（无需重新登录）
        JsonNode updated = putJson("/role/update", operatorToken, String.format(
                "{\"id\":%d,\"roleName\":\"动态权限角色\",\"permissionCodes\":[\"orders\"]}", roleId));
        assertThat(updated.path("code").asInt()).isEqualTo(200);

        mockMvc.perform(post("/room/list").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"current\":1,\"size\":5}"))
                .andExpect(status().isForbidden());
        assertThat(postJson("/order/list", token, "{\"current\":1,\"size\":5}").path("code").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("RBAC：角色编码唯一、内置角色与在用角色不允许删除")
    void roleLifecycleGuards() throws Exception {
        String operatorToken = login("operator", "operator123");

        // 编码重复：拒绝
        assertThat(postJson("/role/save", operatorToken,
                "{\"roleCode\":\"admin\",\"roleName\":\"重复编码角色\",\"permissionCodes\":[\"rooms\"]}")
                .path("code").asInt()).isNotEqualTo(200);

        // 内置角色：不允许删除（业务错误包在 HTTP 200 的响应体里）
        long builtInId = findRoleIdByCode(operatorToken, "admin");
        JsonNode builtInDelete = json(mockMvc
                .perform(delete("/role/delete/" + builtInId).header("Authorization", operatorToken)).andReturn());
        assertThat(builtInDelete.path("code").asInt()).isNotEqualTo(200);

        // 有账号持有的角色：不允许删除
        postJson("/role/save", operatorToken,
                "{\"roleCode\":\"t_attached\",\"roleName\":\"在用角色\",\"permissionCodes\":[\"rooms\"]}");
        long attachedId = findRoleIdByCode(operatorToken, "t_attached");
        assertThat(postJson("/user/save", operatorToken, String.format(
                "{\"username\":\"t_role_user_%d\",\"password\":\"test123456\",\"nickname\":\"角色在用\",\"roleIds\":[%d]}",
                System.nanoTime(), attachedId)).path("code").asInt()).isEqualTo(200);
        JsonNode attachedDelete = json(mockMvc
                .perform(delete("/role/delete/" + attachedId).header("Authorization", operatorToken)).andReturn());
        assertThat(attachedDelete.path("code").asInt()).isNotEqualTo(200);

        // 无账号持有的自定义角色：允许删除
        postJson("/role/save", operatorToken,
                "{\"roleCode\":\"t_free\",\"roleName\":\"空闲角色\",\"permissionCodes\":[\"rooms\"]}");
        long freeId = findRoleIdByCode(operatorToken, "t_free");
        JsonNode freeDelete = json(mockMvc
                .perform(delete("/role/delete/" + freeId).header("Authorization", operatorToken)).andReturn());
        assertThat(freeDelete.path("code").asInt()).isEqualTo(200);
    }

    /**
     * 从角色列表里按编码取角色ID（列表接口返回的角色编码唯一）
     */
    private long findRoleIdByCode(String token, String roleCode) throws Exception {
        JsonNode res = getJson("/role/list", token);
        assertThat(res.path("code").asInt()).isEqualTo(200);
        for (JsonNode role : res.path("data")) {
            if (roleCode.equals(role.path("roleCode").asText())) {
                return role.path("id").asLong();
            }
        }
        throw new IllegalStateException("角色不存在: " + roleCode);
    }

    @Test
    @DisplayName("未登录访问受保护接口返回 401；登录接口本身放行")
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/room/status")).andExpect(status().isUnauthorized());
        // 注意用 POST：/order/list 只有 POST 映射，GET 会落到前端 SPA 兜底返回 200（index.html），探测不到拦截器
        mockMvc.perform(post("/order/list").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        // 注销是幂等的：不带 token 也应当成功
        mockMvc.perform(post("/user/logout")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("参数校验：分页 size 超上限、房价为 0、超长字段都会被拒绝")
    void invalidInputIsRejected() throws Exception {
        String token = login("admin", "admin123");

        assertThat(postJson("/order/list", token, "{\"current\":1,\"size\":100000}").path("code").asInt()).isEqualTo(400);
        assertThat(postJson("/room/save", token,
                "{\"roomNumber\":\"T900\",\"type\":\"单人间\",\"price\":0,\"floor\":1}").path("code").asInt()).isEqualTo(400);
        assertThat(postJson("/room/save", token,
                "{\"roomNumber\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"type\":\"单人间\",\"price\":100,\"floor\":1}")
                .path("code").asInt()).isEqualTo(400);
        assertThat(postJson("/customer/save", token,
                "{\"name\":\"测试\",\"phone\":\"12345\"}").path("code").asInt()).isEqualTo(400);
    }

    @Test
    @DisplayName("普通用户个人中心：改姓名即时生效；改密码需原密码校验，旧密码立即失效")
    void memberProfileAndPasswordChange() throws Exception {
        // 自助注册一个普通用户（手机号须唯一，从时间戳构造 139xxxxxxxx）
        String phone = "139" + String.format("%08d", System.currentTimeMillis() % 100000000L);
        JsonNode registered = postJson("/client/register", null, String.format(
                "{\"name\":\"测试普通用户\",\"phone\":\"%s\",\"password\":\"old123456\"}", phone));
        assertThat(registered.path("code").asInt())
                .as("注册应成功: " + registered.path("message").asText()).isEqualTo(200);

        // 统一登录进入普通用户端
        String token = login(phone, "old123456");

        // 改姓名：返回最新资料，且密码摘要不下发前端
        JsonNode profile = putJson("/client/profile", token, "{\"name\":\"改名后的普通用户\"}");
        assertThat(profile.path("code").asInt()).isEqualTo(200);
        assertThat(profile.path("data").path("name").asText()).isEqualTo("改名后的普通用户");
        assertThat(profile.path("data").has("password") && !profile.path("data").path("password").isNull())
                .as("密码摘要不能出现在响应里").isFalse();

        // 原密码错误：拒绝改密
        JsonNode wrongOld = putJson("/client/password", token,
                "{\"oldPassword\":\"wrong-pass\",\"newPassword\":\"new123456\"}");
        assertThat(wrongOld.path("code").asInt()).isNotEqualTo(200);
        assertThat(wrongOld.path("message").asText()).contains("原密码不正确");

        // 原密码正确：改密成功，旧密码立即失效、新密码可登录
        assertThat(putJson("/client/password", token,
                "{\"oldPassword\":\"old123456\",\"newPassword\":\"new123456\"}").path("code").asInt()).isEqualTo(200);
        JsonNode oldLogin = postJson("/user/login", null,
                "{\"username\":\"" + phone + "\",\"password\":\"old123456\"}");
        assertThat(oldLogin.path("code").asInt()).as("旧密码应已失效").isNotEqualTo(200);
        assertThat(login(phone, "new123456")).isNotBlank();
    }

    @Test
    @DisplayName("注册认领：前台代录的无密码档案可被本人注册认领；管理端可重置登录密码")
    void registerClaimsFrontDeskProfileAndAdminCanResetPassword() throws Exception {
        // 前台代录一个无密码档案（手机号从时间戳构造，保证唯一）
        String token = login("admin", "admin123");
        String phone = "138" + String.format("%08d", System.currentTimeMillis() % 100000000L);
        assertThat(postJson("/customer/save", token,
                "{\"name\":\"代录客户\",\"phone\":\"" + phone + "\"}").path("code").asInt()).isEqualTo(200);

        // 代录档案挡不住自助注册：认领档案并设置密码，随后即可登录
        assertThat(postJson("/client/register", null, String.format(
                "{\"name\":\"代录客户\",\"phone\":\"%s\",\"password\":\"claim123456\"}", phone))
                .path("code").asInt()).isEqualTo(200);
        assertThat(login(phone, "claim123456")).isNotBlank();

        // 认领后再注册（已有密码）必须被拒绝
        JsonNode again = postJson("/client/register", null, String.format(
                "{\"name\":\"代录客户\",\"phone\":\"%s\",\"password\":\"claim123456\"}", phone));
        assertThat(again.path("code").asInt()).isNotEqualTo(200);
        assertThat(again.path("message").asText()).contains("已注册");

        // 管理端重置密码：旧密码立即失效，新密码可登录
        JsonNode list = postJson("/customer/list", token,
                "{\"current\":1,\"size\":10,\"phone\":\"" + phone + "\"}");
        long customerId = list.path("data").path("records").path(0).path("id").asLong();
        assertThat(putJson("/customer/reset-password", token,
                "{\"id\":" + customerId + ",\"password\":\"reset654321\"}").path("code").asInt()).isEqualTo(200);
        JsonNode oldLogin = postJson("/user/login", null,
                "{\"username\":\"" + phone + "\",\"password\":\"claim123456\"}");
        assertThat(oldLogin.path("code").asInt()).as("重置后旧密码应已失效").isNotEqualTo(200);
        assertThat(login(phone, "reset654321")).isNotBlank();
    }
}
