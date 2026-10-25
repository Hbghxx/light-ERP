package com.example.erp.service;

import com.example.erp.common.BusinessException;
import com.example.erp.entity.ProductInfo;
import com.example.erp.mapper.InventoryStockMapper;
import com.example.erp.mapper.ProductInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductInfoMapper productInfoMapper;

    @Autowired
    private InventoryStockMapper inventoryStockMapper;

    /**
     * 新增商品（同时初始化库存为 0）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addProduct(String sku, String name, BigDecimal price) {
        // 1. 前置参数校验
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("商品 SKU 不能为空");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("基础售价不能为负数");
        }
        if (sku.length() > 50) {
            throw new IllegalArgumentException("商品 SKU 不能超过 50 个字符");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("商品名称不能超过 100 个字符");
        }

        // 2. 校验 SKU 是否已存在
        ProductInfo existProduct = productInfoMapper.selectBySku(sku);
        if (existProduct != null) {
            throw new BusinessException("商品 SKU [" + sku + "] 已存在");
        }

        // 3. 插入商品主数据
        ProductInfo product = new ProductInfo();
        product.setSku(sku);
        product.setName(name);
        product.setPrice(price);
        productInfoMapper.insert(product);

        // 4. 自动初始化库存记录（初始库存为 0）
        inventoryStockMapper.insertStock(sku, 0);
    }

    /**
     * 查询商品列表
     */
    public List<ProductInfo> listProducts() {
        return productInfoMapper.selectAll();
    }

    /**
     * 修改商品基础售价
     */
    public void updatePrice(String sku, BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("基础售价不能为负数");
        }
        ProductInfo product = productInfoMapper.selectBySku(sku);
        if (product == null) {
            throw new BusinessException("商品 SKU [" + sku + "] 不存在");
        }
        productInfoMapper.updatePrice(sku, price);
    }
}
