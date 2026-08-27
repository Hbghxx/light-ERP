package com.example.erp.mapper;

import com.example.erp.entity.SalesOutbound;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SalesOutboundMapper {

    @Insert("INSERT INTO sales_outbound (outbound_no, product_sku, quantity, customer_name, status) " +
            "VALUES (#{outboundNo}, #{productSku}, #{quantity}, #{customerName}, 1)")
    int insert(SalesOutbound outbound);

    // 显式为下划线字段加上 AS 别名
    @Select("SELECT id, " +
            "outbound_no AS outboundNo, " +
            "product_sku AS productSku, " +
            "quantity, " +
            "customer_name AS customerName, " +
            "status, " +
            "created_at AS createdAt " +
            "FROM sales_outbound ORDER BY id DESC")
    List<SalesOutbound> selectAll();
}