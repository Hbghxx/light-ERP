package com.example.erp.mapper;

import com.example.erp.entity.PurchaseInbound;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface PurchaseInboundMapper {

    @Insert("INSERT INTO purchase_inbound (inbound_no, product_sku, quantity, supplier_name, status) " +
            "VALUES (#{inboundNo}, #{productSku}, #{quantity}, #{supplierName}, 1)")
    int insert(PurchaseInbound inbound);

    // 给数据库下划线字段加上 AS 别名映射到 Java 驼峰属性
    @Select("SELECT id, inbound_no AS inboundNo, product_sku AS productSku, " +
            "quantity, supplier_name AS supplierName, status, created_at AS createdAt " +
            "FROM purchase_inbound ORDER BY id DESC")
    List<PurchaseInbound> selectAll();


}