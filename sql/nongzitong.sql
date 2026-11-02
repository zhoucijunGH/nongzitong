-- ============================================================
-- 农资通（农资店进销存系统）建库建表 + 演示种子数据
-- 可重复执行：内部先 DROP 再 CREATE
-- 执行：mysql -uroot -p123456 --default-character-set=utf8mb4 < sql/nongzitong.sql
-- ============================================================

DROP DATABASE IF EXISTS nongzitong;
CREATE DATABASE nongzitong DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE nongzitong;

-- ---------------- 账号（老板 ADMIN / 店员 STAFF） ----------------
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL COMMENT '登录名',
  password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
  nickname    VARCHAR(50)  DEFAULT NULL COMMENT '姓名',
  phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  role        VARCHAR(10)  NOT NULL DEFAULT 'STAFF' COMMENT 'ADMIN 老板 / STAFF 店员',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 停用',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='账号';

-- ---------------- 商品档案 ----------------
CREATE TABLE product (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  name           VARCHAR(50)   NOT NULL COMMENT '商品名称',
  spec           VARCHAR(50)   NOT NULL COMMENT '规格，如 50kg/袋',
  unit           VARCHAR(10)   NOT NULL DEFAULT '袋' COMMENT '计量单位',
  category       VARCHAR(10)   NOT NULL DEFAULT '其他' COMMENT '类别：化肥/农药/种子/其他',
  purchase_price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '进价（元）',
  sale_price     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '售价（元）',
  warning_stock  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '预警库存',
  opening_stock  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '期初库存',
  stock          DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '当前库存 = 期初 + 入库 - 销售出库',
  status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  remark         VARCHAR(255)  DEFAULT NULL,
  created_at     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_name (name),
  KEY idx_category (category)
) ENGINE=InnoDB COMMENT='商品档案';

-- ---------------- 供应商 ----------------
CREATE TABLE supplier (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(50) NOT NULL COMMENT '供应商名称',
  contact    VARCHAR(50) DEFAULT NULL COMMENT '联系人',
  phone      VARCHAR(20) DEFAULT NULL COMMENT '电话',
  remark     VARCHAR(255) DEFAULT NULL,
  created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='供应商简档';

-- ---------------- 客户（赊销挂账主体） ----------------
CREATE TABLE customer (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(50) NOT NULL COMMENT '姓名/名称',
  phone      VARCHAR(20) DEFAULT NULL COMMENT '电话',
  address    VARCHAR(100) DEFAULT NULL COMMENT '地址',
  remark     VARCHAR(255) DEFAULT NULL,
  status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='客户档案';

-- ---------------- 采购入库单 ----------------
CREATE TABLE purchase_in (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  order_no        VARCHAR(32)   NOT NULL COMMENT '单号 RK+yyyyMMdd+序号',
  order_date      DATE          NOT NULL COMMENT '入库日期',
  supplier_id     BIGINT        DEFAULT NULL,
  supplier_name   VARCHAR(50)   DEFAULT NULL COMMENT '供应商名称（快照）',
  total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '总金额',
  remark          VARCHAR(255)  DEFAULT NULL,
  status          VARCHAR(10)   NOT NULL DEFAULT 'STORED' COMMENT 'STORED 已入库 / VOID 已作废',
  void_reason     VARCHAR(255)  DEFAULT NULL,
  voided_by_name  VARCHAR(50)   DEFAULT NULL,
  voided_at       DATETIME      DEFAULT NULL,
  created_by      BIGINT        DEFAULT NULL,
  created_by_name VARCHAR(50)   DEFAULT NULL,
  created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_in_order_no (order_no),
  KEY idx_in_date (order_date)
) ENGINE=InnoDB COMMENT='采购入库单';

CREATE TABLE purchase_in_item (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  in_id        BIGINT        NOT NULL COMMENT '采购单 id',
  product_id   BIGINT        NOT NULL,
  product_name VARCHAR(50)   NOT NULL COMMENT '商品名称（快照）',
  spec         VARCHAR(50)   NOT NULL COMMENT '规格（快照）',
  qty          DECIMAL(12,2) NOT NULL COMMENT '数量',
  price        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '进价（单据为准）',
  amount       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '金额 = 数量 × 进价',
  PRIMARY KEY (id),
  KEY idx_in (in_id)
) ENGINE=InnoDB COMMENT='采购入库单明细';

-- ---------------- 销售单 ----------------
CREATE TABLE sale_order (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  order_no        VARCHAR(32)   NOT NULL COMMENT '单号 XS+yyyyMMdd+序号',
  order_date      DATE          NOT NULL COMMENT '销售日期',
  customer_id     BIGINT        DEFAULT NULL COMMENT '客户 id（挂账必填）',
  customer_name   VARCHAR(50)   DEFAULT NULL COMMENT '客户名称（快照）',
  pay_method      VARCHAR(10)   NOT NULL DEFAULT 'CASH' COMMENT 'CASH 现结 / CREDIT 挂账',
  total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '合计 = Σ(数量 × 售价)',
  received_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '实收（挂账为已收款累计）',
  remark          VARCHAR(255)  DEFAULT NULL,
  status          VARCHAR(10)   NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID 待收款 / PAID 已结清 / VOID 已作废',
  void_reason     VARCHAR(255)  DEFAULT NULL,
  voided_by_name  VARCHAR(50)   DEFAULT NULL,
  voided_at       DATETIME      DEFAULT NULL,
  created_by      BIGINT        DEFAULT NULL,
  created_by_name VARCHAR(50)   DEFAULT NULL,
  created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sale_order_no (order_no),
  KEY idx_sale_date (order_date),
  KEY idx_sale_customer (customer_id),
  KEY idx_sale_status (status)
) ENGINE=InnoDB COMMENT='销售单';

CREATE TABLE sale_order_item (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  sale_id        BIGINT        NOT NULL COMMENT '销售单 id',
  product_id     BIGINT        NOT NULL,
  product_name   VARCHAR(50)   NOT NULL COMMENT '商品名称（快照）',
  spec           VARCHAR(50)   NOT NULL COMMENT '规格（快照）',
  qty            DECIMAL(12,2) NOT NULL COMMENT '数量',
  price          DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '售价（单据为准）',
  amount         DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '小计 = 数量 × 售价',
  cost_price     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '单据进价快照（毛利计算）',
  origin_price   DECIMAL(10,2) DEFAULT NULL COMMENT '原售价（改价留痕）',
  price_operator VARCHAR(50)   DEFAULT NULL COMMENT '改价操作人',
  PRIMARY KEY (id),
  KEY idx_sale (sale_id)
) ENGINE=InnoDB COMMENT='销售单明细';

-- ---------------- 库存流水（只增不改） ----------------
CREATE TABLE stock_flow (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  product_id   BIGINT        NOT NULL,
  product_name VARCHAR(50)   NOT NULL,
  spec         VARCHAR(50)   NOT NULL,
  flow_type    VARCHAR(10)   NOT NULL COMMENT 'IN 入库 / OUT 销售出库 / VOID 作废回滚',
  delta        DECIMAL(12,2) NOT NULL COMMENT '库存变化量（正增负减）',
  balance      DECIMAL(12,2) DEFAULT NULL COMMENT '变化后库存快照',
  ref_no       VARCHAR(32)   DEFAULT NULL COMMENT '关联单号',
  operator     VARCHAR(50)   DEFAULT NULL COMMENT '操作人',
  created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_flow_product (product_id),
  KEY idx_flow_ref (ref_no)
) ENGINE=InnoDB COMMENT='库存流水';

-- ---------------- 收款记录（只可作废） ----------------
CREATE TABLE payment (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  customer_id     BIGINT        NOT NULL,
  customer_name   VARCHAR(50)   DEFAULT NULL,
  amount          DECIMAL(12,2) NOT NULL COMMENT '收款金额',
  method          VARCHAR(20)   NOT NULL DEFAULT '现金' COMMENT '现金/转账/其他',
  operator        VARCHAR(50)   DEFAULT NULL COMMENT '经办人',
  pay_date        DATE          NOT NULL COMMENT '收款日期',
  remark          VARCHAR(255)  DEFAULT NULL,
  status          VARCHAR(10)   NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED 已登记 / VOID 已作废',
  void_reason     VARCHAR(255)  DEFAULT NULL,
  voided_by_name  VARCHAR(50)   DEFAULT NULL,
  voided_at       DATETIME      DEFAULT NULL,
  created_by      BIGINT        DEFAULT NULL,
  created_by_name VARCHAR(50)   DEFAULT NULL,
  created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_pay_customer (customer_id),
  KEY idx_pay_date (pay_date)
) ENGINE=InnoDB COMMENT='收款记录';

CREATE TABLE payment_alloc (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  payment_id BIGINT        NOT NULL COMMENT '收款记录 id',
  sale_id    BIGINT        NOT NULL COMMENT '销售单 id',
  amount     DECIMAL(12,2) NOT NULL COMMENT '分摊金额',
  PRIMARY KEY (id),
  KEY idx_alloc_payment (payment_id),
  KEY idx_alloc_sale (sale_id)
) ENGINE=InnoDB COMMENT='收款分摊（作废回滚依据）';

-- ============================================================
-- 演示种子数据（数据脱敏：品类真实、价格虚构）
-- 账号密码均为 123456（BCrypt）
-- ============================================================
INSERT INTO sys_user (username, password, nickname, phone, role, status) VALUES
('admin',  '$2a$10$IG1XFb5h6MmvaVkFG/nRFOLqXEoM91.FcsBzjbttZOW2Znlcw3TrK', '黄老板', '13800000011', 'ADMIN', 1),
('dianyuan', '$2a$10$IG1XFb5h6MmvaVkFG/nRFOLqXEoM91.FcsBzjbttZOW2Znlcw3TrK', '小韦', '13800000012', 'STAFF', 1);

INSERT INTO product (name, spec, unit, category, purchase_price, sale_price, warning_stock, opening_stock, status, remark) VALUES
-- 化肥
('尿素（含氮46%）',   '50kg/袋', '袋', '化肥', 128.00, 145.00, 12, 40, 1, NULL),
('复合肥（15-15-15）','50kg/袋', '袋', '化肥', 170.00, 195.00, 10, 30, 1, NULL),
('磷酸二铵',          '50kg/袋', '袋', '化肥', 210.00, 238.00, 20, 15, 1, '低库存演示'),
('碳酸氢铵',          '40kg/袋', '袋', '化肥',  42.00,  52.00, 10, 25, 1, NULL),
('过磷酸钙',          '50kg/袋', '袋', '化肥',  38.00,  48.00, 10, 20, 1, NULL),
('氯化钾',            '50kg/袋', '袋', '化肥', 195.00, 225.00,  6, 12, 1, NULL),
('有机肥',            '40kg/袋', '袋', '化肥',  55.00,  68.00,  8, 18, 1, NULL),
-- 农药
('毒死蜱',            '500ml/瓶','瓶', '农药',  22.00,  28.00, 15, 36, 1, NULL),
('草甘膦',            '1L/瓶',   '瓶', '农药',  18.00,  25.00, 15, 40, 1, NULL),
('吡虫啉',            '10g/包',  '包', '农药',   3.50,   6.00, 30, 60, 1, NULL),
('阿维菌素',          '500ml/瓶','瓶', '农药',  26.00,  35.00, 10, 20, 1, NULL),
('多菌灵',            '500g/包', '包', '农药',  12.00,  18.00, 15, 30, 1, NULL),
('乙草胺',            '1L/瓶',   '瓶', '农药',  16.00,  23.00, 10, 22, 1, NULL),
('代森锰锌',          '1kg/包',  '包', '农药',  28.00,  38.00, 10, 16, 1, NULL),
('敌敌畏',            '500ml/瓶','瓶', '农药',  15.00,  22.00, 15, 18, 1, '低库存演示'),
-- 种子
('水稻种子（杂交稻）','5kg/包',  '包', '种子',  55.00,  75.00, 10, 24, 1, NULL),
('玉米种子（紧凑型）','5kg/包',  '包', '种子',  60.00,  85.00,  8, 16, 1, NULL),
('花生种子',          '10kg/包', '包', '种子',  48.00,  65.00, 20, 14, 1, '低库存演示'),
('黄豆种子',          '5kg/包',  '包', '种子',  25.00,  36.00,  8, 18, 1, NULL),
('蔬菜种子（小白菜）','10g/包',  '包', '种子',   4.00,   8.00, 20, 50, 1, NULL),
-- 其他
('农膜（地膜）',      '100m/卷', '卷', '其他',  75.00,  95.00, 12, 10, 1, '低库存演示'),
('磷酸二氢钾叶面肥',  '500g/包', '包', '其他',  14.00,  22.00, 10, 22, 1, NULL);

INSERT INTO supplier (name, contact, phone, remark) VALUES
('广西沃肥商贸有限公司', '陈经理', '13800000001', '化肥主供'),
('南宁农乐种业',        '李技术', '13800000002', '种子专供'),
('桂隆农药化工',        '周老板', '13800000003', '农药供货');

INSERT INTO customer (name, phone, address, remark, status) VALUES
('隆安·韦大伯', '13800000001', '隆安县那桐镇', '老客户，常赊肥', 1),
('潭下·覃叔',   '13800000002', '灵川县潭下镇', NULL, 1),
('农资店·蓝姐', '13800000003', '永福县县城',   '同行调货', 1),
('大圩·陆婶',   '13800000004', '灵川县大圩镇', '散户', 1),
('淡村·黄哥',   '13800000005', '南宁市淡村',   '已停用示例', 0);

-- 采购入库单（3 张演示）
INSERT INTO purchase_in (order_no, order_date, supplier_id, supplier_name, total_amount, remark, status, created_by, created_by_name) VALUES
('RK20261010001', '2026-10-10', 1, '广西沃肥商贸有限公司', 7514.00, '秋播备肥', 'STORED', 1, '黄老板'),
('RK20261015001', '2026-10-15', 2, '南宁农乐种业',        1460.00, NULL,       'STORED', 2, '小韦'),
('RK20261020001', '2026-10-20', 3, '桂隆农药化工',         4029.00, '补货',     'STORED', 2, '小韦');

INSERT INTO purchase_in_item (in_id, product_id, product_name, spec, qty, price, amount) VALUES
(1, 1,  '尿素（含氮46%）',   '50kg/袋',  20, 128.00, 2560.00),
(1, 2,  '复合肥（15-15-15）','50kg/袋',  15, 170.00, 2550.00),
(1, 8,  '毒死蜱',            '500ml/瓶', 12,  22.00,  264.00),
(1, 16, '水稻种子（杂交稻）','5kg/包',   10,  55.00,  550.00),
(1, 4,  '碳酸氢铵',          '40kg/袋',  15,  42.00,  630.00),
(1, 12, '多菌灵',            '500g/包',  80,  12.00,  960.00),
(2, 9,  '草甘膦',            '1L/瓶',    24,  18.00,  432.00),
(2, 11, '阿维菌素',          '500ml/瓶', 10,  26.00,  260.00),
(2, 17, '玉米种子（紧凑型）','5kg/包',    8,  60.00,  480.00),
(2, 18, '花生种子',          '10kg/包',   6,  48.00,  288.00),
(3, 1,  '尿素（含氮46%）',   '50kg/袋',  24, 130.00, 3120.00),
(3, 10, '吡虫啉',            '10g/包',   50,   3.50,  175.00),
(3, 19, '黄豆种子',          '5kg/包',   10,  25.00,  250.00),
(3, 13, '乙草胺',            '1L/瓶',    25,  16.00,  400.00),
(3, 14, '代森锰锌',          '1kg/包',    3,  28.00,   84.00);

-- 销售单（14 张演示，覆盖近 7 天；含现结/挂账/作废）
INSERT INTO sale_order (order_no, order_date, customer_id, customer_name, pay_method, total_amount, received_amount, remark, status, created_by, created_by_name) VALUES
('XS20261016001', '2026-10-16', NULL, NULL, 'CASH',   664.00, 664.00, NULL,                'PAID',   1, '黄老板'),
('XS20261016002', '2026-10-16', 1, '隆安·韦大伯', 'CREDIT', 440.00,   0.00, '先记账',        'UNPAID', 2, '小韦'),
('XS20261017001', '2026-10-17', NULL, NULL, 'CASH',   285.00, 285.00, NULL,                'PAID',   2, '小韦'),
('XS20261017002', '2026-10-17', 2, '潭下·覃叔',   'CREDIT', 870.00,   0.00, '月底结',        'UNPAID', 1, '黄老板'),
('XS20261018001', '2026-10-18', NULL, NULL, 'CASH',   197.00, 197.00, NULL,                'PAID',   2, '小韦'),
('XS20261018002', '2026-10-18', 3, '农资店·蓝姐', 'CREDIT',1061.00,   0.00, '调货月结',      'UNPAID', 1, '黄老板'),
('XS20261019001', '2026-10-19', NULL, NULL, 'CASH',   750.00, 750.00, NULL,                'PAID',   2, '小韦'),
('XS20261019002', '2026-10-19', NULL, NULL, 'CASH',   865.00,   0.00, '开错单',            'VOID',   2, '小韦'),
('XS20261019003', '2026-10-19', 1, '隆安·韦大伯', 'CREDIT', 760.00, 200.00, '已还 200',      'UNPAID', 1, '黄老板'),
('XS20261020001', '2026-10-20', NULL, NULL, 'CASH',   193.00, 193.00, NULL,                'PAID',   2, '小韦'),
('XS20261021001', '2026-10-21', 2, '潭下·覃叔',   'CREDIT', 436.00, 436.00, '已结清',       'PAID',   1, '黄老板'),
('XS20261021002', '2026-10-21', NULL, NULL, 'CASH',   298.00, 298.00, NULL,                'PAID',   2, '小韦'),
('XS20261022001', '2026-10-22', NULL, NULL, 'CASH',   665.00, 665.00, '尿素改价 140 出售', 'PAID',   2, '小韦'),
('XS20261022002', '2026-10-22', 4, '大圩·陆婶',   'CREDIT', 251.00,   0.00, '挂账',          'UNPAID', 2, '小韦');

INSERT INTO sale_order_item (sale_id, product_id, product_name, spec, qty, price, amount, cost_price, origin_price, price_operator) VALUES
(1, 1,  '尿素（含氮46%）',   '50kg/袋',   4, 145.00, 580.00, 128.00, 145.00, NULL),
(1, 8,  '毒死蜱',            '500ml/瓶',  3,  28.00,  84.00,  22.00,  28.00, NULL),
(2, 2,  '复合肥（15-15-15）','50kg/袋',   2, 195.00, 390.00, 170.00, 195.00, NULL),
(2, 9,  '草甘膦',            '1L/瓶',     2,  25.00,  50.00,  18.00,  25.00, NULL),
(3, 16, '水稻种子（杂交稻）','5kg/包',    3,  75.00, 225.00,  55.00,  75.00, NULL),
(3, 10, '吡虫啉',            '10g/包',   10,   6.00,  60.00,   3.50,   6.00, NULL),
(4, 1,  '尿素（含氮46%）',   '50kg/袋',   6, 145.00, 870.00, 128.00, 145.00, NULL),
(5, 9,  '草甘膦',            '1L/瓶',     5,  25.00, 125.00,  18.00,  25.00, NULL),
(5, 12, '多菌灵',            '500g/包',   4,  18.00,  72.00,  12.00,  18.00, NULL),
(6, 2,  '复合肥（15-15-15）','50kg/袋',   3, 195.00, 585.00, 170.00, 195.00, NULL),
(6, 3,  '磷酸二铵',          '50kg/袋',   2, 238.00, 476.00, 210.00, 238.00, NULL),
(7, 17, '玉米种子（紧凑型）','5kg/包',    2,  85.00, 170.00,  60.00,  85.00, NULL),
(7, 1,  '尿素（含氮46%）',   '50kg/袋',   4, 145.00, 580.00, 128.00, 145.00, NULL),
(8, 1,  '尿素（含氮46%）',   '50kg/袋',   5, 145.00, 725.00, 128.00, 145.00, NULL),
(8, 8,  '毒死蜱',            '500ml/瓶',  5,  28.00, 140.00,  22.00,  28.00, NULL),
(9, 4,  '碳酸氢铵',          '40kg/袋',  10,  52.00, 520.00,  42.00,  52.00, NULL),
(9, 5,  '过磷酸钙',          '50kg/袋',   5,  48.00, 240.00,  38.00,  48.00, NULL),
(10, 11, '阿维菌素',          '500ml/瓶',  3,  35.00, 105.00,  26.00,  35.00, NULL),
(10, 15, '敌敌畏',            '500ml/瓶',  4,  22.00,  88.00,  15.00,  22.00, NULL),
(11, 2,  '复合肥（15-15-15）','50kg/袋',   2, 195.00, 390.00, 170.00, 195.00, NULL),
(11, 13, '乙草胺',            '1L/瓶',     2,  23.00,  46.00,  16.00,  23.00, NULL),
(12, 8,  '毒死蜱',            '500ml/瓶',  6,  28.00, 168.00,  22.00,  28.00, NULL),
(12, 18, '花生种子',          '10kg/包',   2,  65.00, 130.00,  48.00,  65.00, NULL),
(13, 1,  '尿素（含氮46%）',   '50kg/袋',   4, 140.00, 560.00, 128.00, 145.00, '小韦'),
(13, 9,  '草甘膦',            '1L/瓶',     3,  25.00,  75.00,  18.00,  25.00, NULL),
(13, 10, '吡虫啉',            '10g/包',    5,   6.00,  30.00,   3.50,   6.00, NULL),
(14, 2,  '复合肥（15-15-15）','50kg/袋',   1, 195.00, 195.00, 170.00, 195.00, NULL),
(14, 8,  '毒死蜱',            '500ml/瓶',  2,  28.00,  56.00,  22.00,  28.00, NULL);

-- 收款记录演示：部分收款 / 全额收款 / 已作废
INSERT INTO payment (customer_id, customer_name, amount, method, operator, pay_date, remark, status, created_by, created_by_name) VALUES
(1, '隆安·韦大伯', 200.00, '现金', '黄老板', '2026-10-21', '先还 200', 'REGISTERED', 1, '黄老板'),
(2, '潭下·覃叔',   500.00, '转账', '黄老板', '2026-10-22', '结清 XS20261021001，多出 64 元预存', 'REGISTERED', 1, '黄老板'),
(4, '大圩·陆婶',   100.00, '现金', '黄老板', '2026-10-20', '重复登记示例', 'VOID', 1, '黄老板');

INSERT INTO payment_alloc (payment_id, sale_id, amount) VALUES
(1, 9, 200.00),
(2, 11, 436.00);

-- ---------------- 库存流水（由单据反推生成，只增不改） ----------------
-- 期初流水
INSERT INTO stock_flow (product_id, product_name, spec, flow_type, delta, balance, ref_no, operator, created_at)
SELECT id, name, spec, 'IN', opening_stock, opening_stock, CONCAT('QC', DATE_FORMAT(created_at, '%Y%m%d')), '黄老板', created_at
FROM product WHERE opening_stock > 0;

-- 采购入库流水
INSERT INTO stock_flow (product_id, product_name, spec, flow_type, delta, balance, ref_no, operator, created_at)
SELECT i.product_id, i.product_name, i.spec, 'IN', i.qty, NULL, m.order_no, m.created_by_name, m.created_at
FROM purchase_in_item i JOIN purchase_in m ON m.id = i.in_id WHERE m.status = 'STORED';

-- 销售出库流水（作废单不计）
INSERT INTO stock_flow (product_id, product_name, spec, flow_type, delta, balance, ref_no, operator, created_at)
SELECT i.product_id, i.product_name, i.spec, 'OUT', -i.qty, NULL, s.order_no, s.created_by_name, s.created_at
FROM sale_order_item i JOIN sale_order s ON s.id = i.sale_id WHERE s.status <> 'VOID';

-- 作废回滚流水
INSERT INTO stock_flow (product_id, product_name, spec, flow_type, delta, balance, ref_no, operator, created_at)
SELECT i.product_id, i.product_name, i.spec, 'VOID', i.qty, NULL, s.order_no, s.voided_by_name, s.voided_at
FROM sale_order_item i JOIN sale_order s ON s.id = i.sale_id WHERE s.status = 'VOID';

-- ---------------- 当前库存对账：期初 + 已入库 - 有效销售出库 ----------------
UPDATE product p SET stock = p.opening_stock
 + COALESCE((SELECT SUM(i.qty) FROM purchase_in_item i
             JOIN purchase_in m ON m.id = i.in_id
             WHERE i.product_id = p.id AND m.status = 'STORED'), 0)
 - COALESCE((SELECT SUM(i.qty) FROM sale_order_item i
             JOIN sale_order s ON s.id = i.sale_id
             WHERE i.product_id = p.id AND s.status <> 'VOID'), 0);
