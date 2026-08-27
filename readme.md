# 轻量级 ERP 管理系统

基于 Spring Boot + MyBatis + MySQL 构建后端 API，配合 Vue 3 + Element Plus 单文件 (HTML) 构建极简无构建依赖前端界面的轻量级 ERP 进销存管理系统。

---

## 🛠️ 技术栈与架构

* **后端**：Java 17 / Spring Boot 3.x / MyBatis / MySQL 8.0
* **前端**：Vue 3 (CDN) / Element Plus (CDN) / Axios (CDN)
* **核心特性**：无须 Node.js 前端打包环境、无缝跨域支持、MyBatis 自动驼峰映射、前端全内存高阶过滤与多列排序。

---

## 📦 系统功能模块

### 1. 商品管理模块 (Product Management)

* **商品主数据创建与维护**：新增商品 SKU、名称、基础售价及修改售价。
* **实时库存联动**：自动关联展示实时库存与库存更新时间。
* **高阶筛选与排序**：支持“隐藏库存≤0商品”动态开关，支持根据 ID / SKU / 商品名称混合搜索，支持按 ID、名称、售价、库存数量、各种时间戳进行多字段动态排序。

### 2. 采购入库模块 (Purchase Inbound)

* **采购入库提交**：绑定 SKU、入库数量及可选供应商。
* **入库流水记录**：展示历史采购单号、SKU、采购数量、供应商名称及入库时间，支持单号/SKU/供应商多维度检索。

### 3. 销售出库模块 (Sales Outbound)

* **销售出库提交**：绑定 SKU、出库数量及可选客户名称。
* **出库流水记录**：展示历史出库单号、SKU、出库数量、客户名称及出库时间，支持单号/SKU/客户多维度检索。

### 4. 库存扣减模块 (Stock Control)

* 提供底层乐观锁直扣/扣减测试接口，防超卖与并发控制。

---

## 🗄️ 数据库结构设计

系统使用 MySQL 数据库，包含商品主表、实时库存表（乐观锁）、采购入库表及销售出库表：

```sql
CREATE DATABASE IF NOT EXISTS erp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE erp;

-- 1. 商品主数据表
CREATE TABLE IF NOT EXISTS product_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    sku VARCHAR(50) NOT NULL UNIQUE COMMENT '商品SKU编码',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    price DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '基础售价',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品主表';

-- 2. 实时库存表 (含乐观锁控制)
CREATE TABLE IF NOT EXISTS inventory_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    product_sku VARCHAR(50) NOT NULL UNIQUE COMMENT '关联商品SKU',
    stock_qty INT NOT NULL DEFAULT 0 COMMENT '当前可用库存量',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- 3. 采购入库流水表
CREATE TABLE IF NOT EXISTS purchase_inbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '入库单ID',
    inbound_no VARCHAR(50) NOT NULL UNIQUE COMMENT '采购入库单号',
    product_sku VARCHAR(50) NOT NULL COMMENT '商品SKU',
    quantity INT NOT NULL COMMENT '采购数量',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-已入库',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '入库时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购入库记录';

-- 4. 销售出库流水表
CREATE TABLE IF NOT EXISTS sales_outbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '出库单ID',
    outbound_no VARCHAR(50) NOT NULL UNIQUE COMMENT '销售出库单号',
    product_sku VARCHAR(50) NOT NULL COMMENT '商品SKU',
    quantity INT NOT NULL COMMENT '销售出库数量',
    customer_name VARCHAR(100) COMMENT '客户名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-已出库',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '出库时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售出库记录';

```

---

## 🔌 API 接口与函数调用关系

| 模块 | HTTP 请求 | 接口路径 | 函数名称 | 参数说明 | 描述 |
| --- | --- | --- | --- | --- | --- |
| **商品** | GET | `/product/list` | `loadProducts()` | 无 | 联表查询所有商品及实时库存 |
| **商品** | POST | `/product/add` | `handleAddProduct()` | `sku`, `name`, `price` | 新增商品主数据 |
| **商品** | POST | `/product/updatePrice` | `handleUpdatePrice()` | `sku`, `price` | 更新商品基础售价 |
| **入库** | GET | `/inbound/list` | `loadInboundList()` | 无 | 获取采购入库历史流水 |
| **入库** | POST | `/inbound/create` | `handleInbound()` | `sku`, `qty`, `supplierName` | 提交采购入库单并增加库存 |
| **出库** | GET | `/outbound/list` | `loadOutboundList()` | 无 | 获取销售出库历史流水 |
| **出库** | POST | `/outbound/create` | `handleOutbound()` | `sku`, `qty`, `customerName` | 提交销售出库单并扣减库存 |
| **库存** | POST | `/stock/deduct` | `handleDeduct()` | `sku`, `qty` | 直接扣减库存 (乐观锁校验) |

---

## 🔑 核心代码实现与关键说明

### 1. 数据库下划线与 Java 驼峰自动映射

在 `application.properties` 中开启全局驼峰转换，避免手动编写大量的 `AS` 别名映射：

```properties
# MyBatis 开启下划线转驼峰（例如：product_sku -> productSku）
mybatis.configuration.map-underscore-to-camel-case=true
# 开启 SQL 调试日志
logging.level.com.example.erp.mapper=debug

```

### 2. 商品与库存的 LEFT JOIN 关联查询 (`ProductInfoMapper.java`)

通过左外连接一次性全量获取商品信息及对应的库存量，空库存自动填补为 0：

```java
@Select("SELECT p.id, p.sku, p.name, p.price, p.created_at, " +
        "IFNULL(s.stock_qty, 0) AS stock_qty, s.updated_at AS stock_updated_at " +
        "FROM product_info p " +
        "LEFT JOIN inventory_stock s ON p.sku = s.product_sku " +
        "ORDER BY p.id DESC")
List<ProductInfo> selectAll();

```

### 3. 全局跨域配置 (`CorsConfig.java`)

允许前端应用跨域访问后端 REST API：

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}

```

### 4. 前端高阶响应式过滤 (`index.html`)

采用 Vue 3 `computed` 计算属性在客户端进行极速二次检索，减少后端查询压力：

```javascript
const filteredProducts = computed(() => {
    return products.value.filter(item => {
        // 1. 隐藏库存 <= 0 的商品
        if (hideZeroStock.value && (item.stockQty || 0) <= 0) return false;
        
        // 2. 跨字段模糊匹配 (ID / SKU / 名称)
        if (productSearchKeyword.value) {
            const kw = productSearchKeyword.value.toLowerCase().trim();
            return String(item.id || '').toLowerCase().includes(kw) ||
                   String(item.sku || '').toLowerCase().includes(kw) ||
                   String(item.name || '').toLowerCase().includes(kw);
        }
        return true;
    });
});

```

---

## 🚀 部署与使用指南

1. **环境准备**：安装 JDK 17+、MySQL 8.0。
2. **初始化数据库**：在 MySQL 中运行上述 **数据库结构设计** 章节中的 DDL 脚本，自动创建 `erp` 数据库及表结构。
3. **启动后端**：修改 `application.properties` 中的数据库账号与密码，启动 `ErpApplication.java`。
4. **运行前端**：无需任何 npm 或 Node.js 环境，直接使用浏览器打开 `index.html` 即可访问。

---
