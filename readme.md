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
2. **初始化数据库**：新建 `erp` 数据库，创建 `product_info`, `inventory_stock`, `purchase_inbound`, `sales_outbound` 等数据表。
3. **启动后端**：配置 `application.properties` 中的数据库账号密码后，启动 `ErpApplication.java`。
4. **运行前端**：直接用浏览器双击打开 `index.html` 即可使用系统，无须任何依赖安装与 npm 构建。

---

## 📈 优化方向与后续拓展建议

* **分页与服务端过滤**：当前前端做全内存过滤与排序，若数据量突破万条，建议在后端 Mapper 中增加 `LIMIT` 分页以及 MyBatis 动态 SQL（`<where>` / `<if>`）。
* **数据库索引**：针对高频查询与关联字段（如 `product_sku`、`inbound_no`、`outbound_no`）建立唯一索引或普通索引，提高联表速度。
* **权限控制**：结合 Spring Security / Sa-Token 以及 Vue Router 实现多角色登录与权限控制（采购员、销售员、仓库管理员）。
* **前端工程化**：数据规模扩大后，建议将单文件 HTML 迁移至 Vite + Vue 3 + TypeScript 的标准工程化架构。