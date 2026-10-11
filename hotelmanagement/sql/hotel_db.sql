-- ============================================================
-- 酒店客房预订管理系统 - MySQL 建表脚本
-- 数据库: hotel_db
-- 字符集: utf8mb4
-- 共 11 张表: t_room / t_customer / t_order / t_order_log / t_seq / t_check_in
--           / t_user / t_role / t_permission / t_user_role / t_role_permission
-- 权限模型: RBAC（用户-角色-权限三级），权限码与前端 ADMIN_PAGES 一一对应
-- 用法: mysql -u root -p --default-character-set=utf8mb4 < hotel_db.sql
-- ============================================================

-- 显式声明连接字符集：部分 Windows 版 mysql 客户端默认使用 gbk，
-- 若不声明会把脚本里的中文按 gbk 解析，导致数据/注释乱码甚至报 "Data too long"。
SET NAMES utf8mb4;

-- 如果数据库不存在则创建
CREATE DATABASE IF NOT EXISTS `hotel_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `hotel_db`;

-- ============================================================
-- 1. 房间表 t_room
-- ============================================================
DROP TABLE IF EXISTS `t_room`;
CREATE TABLE `t_room` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `room_number` VARCHAR(20)  NOT NULL                COMMENT '房间号',
    `type`        VARCHAR(20)  NOT NULL DEFAULT '单人间' COMMENT '房间类型: 单人间/双人间/套房',
    `price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00  COMMENT '价格（元/晚）',
    `status`      TINYINT      NOT NULL DEFAULT 0      COMMENT '可售性: 0正常销售 3停用维修（已订/在住由订单日期区间推导，不落库）',
    `floor`       INT          NOT NULL DEFAULT 1      COMMENT '楼层',
    `description` VARCHAR(255) NULL DEFAULT NULL       COMMENT '房间描述',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_number` (`room_number`),
    KEY `idx_type`   (`type`),
    KEY `idx_status`  (`status`),
    KEY `idx_floor`  (`floor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间表';

-- ============================================================
-- 2. 客户表 t_customer
-- ============================================================
DROP TABLE IF EXISTS `t_customer`;
CREATE TABLE `t_customer` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `name`          VARCHAR(50)  NOT NULL                COMMENT '姓名',
    `phone`         VARCHAR(20)  NOT NULL                COMMENT '电话',
    `id_card`       VARCHAR(18)  NULL DEFAULT NULL       COMMENT '身份证号',
    `password`      VARCHAR(100) NULL DEFAULT NULL       COMMENT '密码（BCrypt 摘要；管理员代录的客户为空，客人自助注册时写入）',
    `gender`        VARCHAR(4)   NULL DEFAULT NULL       COMMENT '性别: 男/女',
    `register_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

-- ============================================================
-- 3. 订单表 t_order
-- ============================================================
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no`        VARCHAR(32)   NOT NULL                COMMENT '订单号',
    `room_id`         BIGINT        NOT NULL DEFAULT 0     COMMENT '关联房间ID（逻辑外键）',
    `customer_id`     BIGINT        NOT NULL DEFAULT 0     COMMENT '关联客户ID（逻辑外键）',
    `customer_name`   VARCHAR(50)   NOT NULL                COMMENT '客户姓名（冗余字段）',
    `check_in_date`   DATE          NULL DEFAULT NULL       COMMENT '计划入住日期',
    `check_out_date`  DATE          NULL DEFAULT NULL       COMMENT '计划退房日期（不再被实际退房日覆盖）',
    `total_price`     DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '预订总价',
    `status`          TINYINT       NOT NULL DEFAULT 0      COMMENT '状态: 0待支付 1已支付 2已入住 3已退房 4已取消',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `paid_time`            DATETIME      NULL DEFAULT NULL COMMENT '支付时间',
    `pay_deadline`         DATETIME      NULL DEFAULT NULL COMMENT '待支付截止时间，超过后不再占用房间',
    `cancel_time`          DATETIME      NULL DEFAULT NULL COMMENT '取消时间',
    `cancel_reason`        VARCHAR(255)  NULL DEFAULT NULL COMMENT '取消原因',
    `refund_amount`        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '退款金额（未支付取消为 0）',
    `actual_check_in_time` DATETIME      NULL DEFAULT NULL COMMENT '实际入住时间',
    `actual_check_out_time` DATETIME     NULL DEFAULT NULL COMMENT '实际退房时间（收入按此归属）',
    `final_price`          DECIMAL(12,2) NULL DEFAULT NULL COMMENT '最终结算金额（退房时写入）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_room_id`      (`room_id`),
    KEY `idx_customer_id`  (`customer_id`),
    KEY `idx_status`       (`status`),
    KEY `idx_check_dates`  (`check_in_date`, `check_out_date`),
    KEY `idx_actual_checkout` (`actual_check_out_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================================
-- 3-1. 订单操作日志表 t_order_log
-- 每一次订单状态流转都留痕，解决"支付/取消/入退房无法追溯"的问题
-- ============================================================
DROP TABLE IF EXISTS `t_order_log`;
CREATE TABLE `t_order_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id`    BIGINT       NOT NULL                COMMENT '关联订单ID（逻辑外键）',
    `from_status` TINYINT      NULL DEFAULT NULL       COMMENT '变更前状态（下单时为 NULL）',
    `to_status`   TINYINT      NOT NULL                COMMENT '变更后状态',
    `operator`    VARCHAR(50)  NULL DEFAULT NULL       COMMENT '操作人（管理端昵称/客人姓名/系统）',
    `remark`      VARCHAR(255) NULL DEFAULT NULL       COMMENT '备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单操作日志表';

-- ============================================================
-- 3-2. 序号表 t_seq
-- 用于生成订单号（ORD + yyyyMMdd + 3位当日序号）。
-- 靠"查 count 再 +1"会并发重复，靠时间戳会撞唯一键，
-- 因此用一张序号表 + 一条原子自增语句取值。
-- ============================================================
DROP TABLE IF EXISTS `t_seq`;
CREATE TABLE `t_seq` (
    `name`     VARCHAR(32) NOT NULL COMMENT '序列名，如 order_no_20261004',
    `next_val` BIGINT      NOT NULL DEFAULT 0 COMMENT '当前已分配到的值',
    PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='序号表';

-- ============================================================
-- 4. 入住记录表 t_check_in
-- ============================================================
DROP TABLE IF EXISTS `t_check_in`;
CREATE TABLE `t_check_in` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id`        BIGINT        NOT NULL DEFAULT 0     COMMENT '关联订单ID（逻辑外键）',
    `room_id`         BIGINT        NOT NULL DEFAULT 0     COMMENT '关联房间ID（逻辑外键）',
    `customer_id`     BIGINT        NOT NULL DEFAULT 0     COMMENT '关联客户ID（逻辑外键）',
    `check_in_time`   DATETIME      NULL DEFAULT NULL       COMMENT '实际入住时间',
    `check_out_time`  DATETIME      NULL DEFAULT NULL       COMMENT '实际退房时间',
    `actual_price`    DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '实际支付金额',
    `operator`        VARCHAR(50)   NULL DEFAULT NULL       COMMENT '操作人',
    PRIMARY KEY (`id`),
    KEY `idx_order_id`       (`order_id`),
    KEY `idx_room_id`        (`room_id`),
    KEY `idx_customer_id`    (`customer_id`),
    KEY `idx_check_in_time`  (`check_in_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入住记录表';

-- ============================================================
-- 测试数据: 10 条房间记录
-- status 只表示可售性: 0 正常销售 / 3 停用维修
-- （已订/在住由 t_order 的日期区间推导，见 RoomAvailabilityService）
-- ============================================================
INSERT INTO `t_room` (`room_number`, `type`, `price`, `status`, `floor`, `description`) VALUES
('101', '单人间', 168.00, 0, 1, '大床房1.8m，含早，免费WiFi'),
('102', '单人间', 168.00, 0, 1, '大床房1.8m，含早，免费WiFi'),
('201', '双人间', 268.00, 0, 2, '双床1.2m，含早，免费WiFi'),
('202', '双人间', 268.00, 0, 2, '双床1.2m，含早，免费WiFi'),
('203', '双人间', 288.00, 0, 2, '双床1.2m，含早，免费WiFi，窗景房'),
('301', '套房',   588.00, 0, 3, '豪华套房，客厅+卧室，含早，免费WiFi'),
('302', '套房',   588.00, 0, 3, '豪华套房，客厅+卧室，含早，免费WiFi'),
('303', '套房',   668.00, 3, 3, '行政套房，维修中，暂不开放预订'),
('401', '单人间', 188.00, 0, 4, '大床房1.8m，含早，免费WiFi，高层景观'),
('402', '双人间', 298.00, 0, 4, '双床1.2m，含早，免费WiFi，高层景观');

-- ============================================================
-- 测试数据: 8 条客户记录
-- ============================================================
INSERT INTO `t_customer` (`name`, `phone`, `id_card`, `gender`, `register_time`) VALUES
('张三', '13800138001', '110101199001011234', '男', '2025-01-15 10:30:00'),
('李四', '13900139002', '110101199205052345', '女', '2025-02-20 14:20:00'),
('王五', '15800158003', '310101198808083456', '男', '2025-03-10 09:15:00'),
('赵六', '13700137004', '440101199503064567', '女', '2025-04-05 16:45:00'),
('钱七', '13600136005', '500101199212125678', '男', '2025-05-12 11:30:00'),
('孙八', '18800188006', '330101198907076789', '女', '2025-06-18 08:20:00'),
('周九', '18600186007', '320101199302027890', '男', '2025-07-25 13:10:00'),
('吴十', '18500185008', '510101199109038901', '女', '2025-08-30 17:00:00');

-- ============================================================
-- 测试数据: 6 条订单记录（覆盖各种状态）
-- ============================================================
INSERT INTO `t_order` (`order_no`, `room_id`, `customer_id`, `customer_name`, `check_in_date`, `check_out_date`, `total_price`, `status`, `create_time`) VALUES
-- 已退房订单
('ORD20250901001', 1, 1, '张三', '2025-09-01', '2025-09-03', 336.00, 3, '2025-08-28 10:00:00'),
-- 已入住订单（房间202已入住）
('ORD20250902002', 4, 2, '李四', '2025-09-02', '2025-09-05', 804.00, 2, '2025-08-30 14:30:00'),
-- 已支付待入住订单
('ORD20250903003', 2, 3, '王五', '2025-09-03', '2025-09-05', 336.00, 1, '2025-09-01 09:20:00'),
-- 待支付订单
('ORD20250904004', 5, 4, '赵六', '2025-09-04', '2025-09-06', 576.00, 0, '2025-09-02 16:45:00'),
-- 已取消订单
('ORD20250905005', 7, 5, '钱七', '2025-09-05', '2025-09-07', 1176.00, 4, '2025-09-03 11:30:00'),
-- 另一个已退房订单
('ORD20250906006', 9, 6, '孙八', '2025-09-06', '2025-09-08', 376.00, 3, '2025-09-04 08:20:00');

-- ============================================================
-- 5. 系统用户表 t_user（管理员账号，登录用）
--    角色与权限不再落在用户行上，改由 t_user_role / t_role_permission 关联（RBAC）
-- ============================================================
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `username`    VARCHAR(50)  NOT NULL                COMMENT '用户名',
    `password`    VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 摘要）',
    `nickname`    VARCHAR(50)  NULL DEFAULT NULL       COMMENT '昵称（页面显示用）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 默认账号（密码为 BCrypt 摘要，已含随机盐；明文见下）：
-- 系统管理员: admin / admin123（业务菜单权限，含入住登记；统计看板归运营端专属）
-- 运营方:     operator / operator123（统计看板+普通用户管理+账号管理，监管视角）
INSERT INTO `t_user` (`username`, `password`, `nickname`) VALUES
('admin',    '$2a$10$.yQRFZg9NbGH99VShYyTP.bSf0hPm0H7zDuBaFzIT49ck7rx0wRV.', '系统管理员'),
('operator', '$2a$10$yHdLWdJR8eTZju8J.roZJeBizAYZbSyNItH5EybmxcNYRa/DhSksi', '运营方');

-- ============================================================
-- 6. 角色表 t_role
-- ============================================================
DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_code`   VARCHAR(32)  NOT NULL                COMMENT '角色编码（如 admin/operator），登录响应与展示用',
    `role_name`   VARCHAR(50)  NOT NULL                COMMENT '角色名称（页面显示）',
    `description` VARCHAR(255) NULL DEFAULT NULL       COMMENT '角色说明',
    `built_in`    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否内置角色: 1内置（不允许删除） 0自定义',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ============================================================
-- 7. 权限表 t_permission（权限码与前端 ADMIN_PAGES 一一对应）
-- ============================================================
DROP TABLE IF EXISTS `t_permission`;
CREATE TABLE `t_permission` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `perm_code`   VARCHAR(32) NOT NULL                COMMENT '权限码（如 rooms/orders）',
    `perm_name`   VARCHAR(50) NOT NULL                COMMENT '权限名称（页面显示）',
    `sort_order`  INT         NOT NULL DEFAULT 0      COMMENT '展示排序',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- ============================================================
-- 8. 用户-角色关联表 t_user_role
-- ============================================================
DROP TABLE IF EXISTS `t_user_role`;
CREATE TABLE `t_user_role` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID（t_user.id）',
    `role_id` BIGINT NOT NULL COMMENT '角色ID（t_role.id）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- ============================================================
-- 9. 角色-权限关联表 t_role_permission
-- ============================================================
DROP TABLE IF EXISTS `t_role_permission`;
CREATE TABLE `t_role_permission` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`       BIGINT NOT NULL COMMENT '角色ID（t_role.id）',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID（t_permission.id）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- 权限种子数据（7 个权限码，对应管理端全部页面）
INSERT INTO `t_permission` (`perm_code`, `perm_name`, `sort_order`) VALUES
('dashboard',  '统计看板',     1),
('roomStatus', '房态图',       2),
('rooms',      '房间管理',     3),
('orders',     '订单管理',     4),
('checkins',   '入住登记',     5),
('customers',  '普通用户管理', 6),
('users',      '账号管理',     7);

-- 内置角色种子数据
INSERT INTO `t_role` (`role_code`, `role_name`, `description`, `built_in`) VALUES
('admin',    '系统管理员', '业务侧角色：房态图/房间/订单/入住登记/普通用户管理', 1),
('operator', '运营方',     '监管侧角色：统计看板/普通用户管理/账号管理',         1);

-- 角色-权限授权
INSERT INTO `t_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `t_role` r CROSS JOIN `t_permission` p
WHERE r.role_code = 'admin'
  AND p.perm_code IN ('roomStatus', 'rooms', 'orders', 'checkins', 'customers');

INSERT INTO `t_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `t_role` r CROSS JOIN `t_permission` p
WHERE r.role_code = 'operator'
  AND p.perm_code IN ('dashboard', 'customers', 'users');

-- 默认账号的角色关联
INSERT INTO `t_user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `t_user` u JOIN `t_role` r ON r.role_code = u.username;
