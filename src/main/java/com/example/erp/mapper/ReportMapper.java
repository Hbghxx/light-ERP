package com.example.erp.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReportMapper {

    /** 近30天出入库日报:仅统计已通过单据 */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS `day`, 'IN' AS `type`, " +
            "SUM(quantity) AS qty, SUM(total_amount) AS amount " +
            "FROM purchase_inbound WHERE status = 2 AND created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
            "GROUP BY `day`, `type` " +
            "UNION ALL " +
            "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS `day`, 'OUT' AS `type`, " +
            "SUM(quantity) AS qty, SUM(total_amount) AS amount " +
            "FROM sales_outbound WHERE status = 2 AND created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
            "GROUP BY `day`, `type` " +
            "ORDER BY `day` DESC, `type`")
    List<Map<String, Object>> dailyFlows();

    /** 库存价值榜: 单价×库存 降序 */
    @Select("SELECT p.sku, p.name, p.price, s.stock_qty AS stockQty, " +
            "(p.price * s.stock_qty) AS stockValue " +
            "FROM product_info p JOIN inventory_stock s ON s.product_sku = p.sku " +
            "ORDER BY stockValue DESC")
    List<Map<String, Object>> stockValue();
}
