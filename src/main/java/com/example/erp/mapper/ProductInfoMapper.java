package com.example.erp.mapper;

import com.example.erp.entity.ProductInfo;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductInfoMapper {

    @Select("SELECT * FROM product_info WHERE sku = #{sku}")
    ProductInfo selectBySku(String sku);

    @Insert("INSERT INTO product_info (sku, name, price) VALUES (#{sku}, #{name}, #{price})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductInfo productInfo);

    /**
     * 左联接查询商品及其实时库存
     */
    @Select("SELECT p.id, p.sku, p.name, p.price, p.created_at, " +
            "IFNULL(s.stock_qty, 0) AS stock_qty, s.updated_at AS stock_updated_at " +
            "FROM product_info p " +
            "LEFT JOIN inventory_stock s ON p.sku = s.product_sku " +
            "ORDER BY p.id DESC")
    List<ProductInfo> selectAll();

    @Update("UPDATE product_info SET price = #{price} WHERE sku = #{sku}")
    int updatePrice(@Param("sku") String sku, @Param("price") BigDecimal price);
}