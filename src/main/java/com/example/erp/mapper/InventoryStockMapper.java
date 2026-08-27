package com.example.erp.mapper;

import com.example.erp.entity.InventoryStock;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InventoryStockMapper {

    @Select("SELECT * FROM inventory_stock WHERE product_sku = #{productSku}")
    InventoryStock selectBySku(String productSku);

    /**
     * 乐观锁扣减库存：仅当版本号 match 且库存充足时更新（销售出库）
     */
    @Update("UPDATE inventory_stock " +
            "SET stock_qty = stock_qty - #{qty}, version = version + 1 " +
            "WHERE product_sku = #{productSku} AND version = #{version} AND stock_qty >= #{qty}")
    int reduceStockWithLock(@Param("productSku") String productSku,
                            @Param("qty") Integer qty,
                            @Param("version") Integer version);

    /**
     * 新增：采购入库增加库存
     */
    @Update("UPDATE inventory_stock " +
            "SET stock_qty = stock_qty + #{qty} " +
            "WHERE product_sku = #{productSku}")
    int increaseStock(@Param("productSku") String productSku, @Param("qty") Integer qty);

    /**
     * 新增：初次采购入库时初始化插入库存记录
     */
    @Insert("INSERT INTO inventory_stock (product_sku, stock_qty, version) " +
            "VALUES (#{productSku}, #{qty}, 0)")
    int insertStock(@Param("productSku") String productSku, @Param("qty") Integer qty);
}