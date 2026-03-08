# ETL Samples

目录中的样本用于 ETL 上传与转换测试，覆盖常见场景：

- 干净数据（可直接入仓）
- 脏数据（去重、缺失值、非法日期、类型错误）
- 维表/事实表关联（customers/orders/payments/products/region）
- 半结构化日志（JSONL 事件流）

## 文件说明

- `01_customers_clean.csv`: 客户主数据（干净）
- `02_orders_clean.csv`: 订单事实（干净）
- `03_payments_clean.csv`: 支付事实（干净）
- `04_customers_dirty.csv`: 客户脏数据（重复主键、非法邮箱、空值、异常状态）
- `05_orders_dirty.csv`: 订单脏数据（外键缺失、负数、非法日期、金额类型异常）
- `06_product_catalog.json`: 产品维表（JSON）
- `07_events.jsonl`: 行式 JSON 事件日志（含一条非法时间）
- `08_region_mapping.csv`: 城市-区域映射维表
- `09_orders_pipe.txt`: 管道分隔文本样本（TXT）
- `10_inventory_snapshot.xlsx`: 库存快照（XLSX）
- `11_inventory_snapshot_legacy.xls`: 库存快照（XLS，兼容旧系统）

## 推荐测试流程

1. 先上传 `01/02/03/06/08` 验证主流程。
2. 再上传 `04/05/07` 验证清洗规则、错误行分流与告警。
3. 做关联测试：
   - `orders.customer_id -> customers.customer_id`
   - `orders.product_code -> product_catalog.product_code`
   - `customers.city -> region_mapping.city`
4. 做派生字段：
   - `order_amount = quantity * unit_price`
   - 订单日期分桶（日/周/月）
   - 用户区域标签（region/tier）

## 文件类型覆盖

当前目录已覆盖上传支持的全部类型：

- `.csv`：`01~05`、`08`
- `.json`：`06_product_catalog.json`
- `.txt`：`09_orders_pipe.txt`
- `.xlsx`：`10_inventory_snapshot.xlsx`
- `.xls`：`11_inventory_snapshot_legacy.xls`
