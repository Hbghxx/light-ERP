package com.example.erp.controller;

import com.example.erp.entity.ProductInfo;
import com.example.erp.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 1. 新增商品接口
     */
    @PostMapping("/add")
    public String addProduct(@RequestParam String sku,
                             @RequestParam String name,
                             @RequestParam BigDecimal price) {
        try {
            productService.addProduct(sku, name, price);
            return "商品新增成功！";
        } catch (Exception e) {
            return "新增失败：" + e.getMessage();
        }
    }

    /**
     * 2. 查询商品列表接口
     */
    @GetMapping("/list")
    public List<ProductInfo> listProducts() {
        return productService.listProducts();
    }

    /**
     * 3. 修改商品价格接口
     */
    @PostMapping("/updatePrice")
    public String updatePrice(@RequestParam String sku,
                              @RequestParam BigDecimal price) {
        try {
            productService.updatePrice(sku, price);
            return "价格更新成功！";
        } catch (Exception e) {
            return "更新失败：" + e.getMessage();
        }
    }
}