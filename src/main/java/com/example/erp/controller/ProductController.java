package com.example.erp.controller;

import com.example.erp.common.RequiresPermission;
import com.example.erp.common.Result;
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
    @RequiresPermission("product:add")
    public Result<String> addProduct(@RequestParam String sku,
                                     @RequestParam String name,
                                     @RequestParam BigDecimal price) {
        productService.addProduct(sku, name, price);
        return Result.ok("商品新增成功");
    }

    /**
     * 2. 查询商品列表接口
     */
    @GetMapping("/list")
    public Result<List<ProductInfo>> listProducts() {
        return Result.ok(productService.listProducts());
    }

    /**
     * 3. 修改商品价格接口
     */
    @PostMapping("/updatePrice")
    @RequiresPermission("product:price:update")
    public Result<String> updatePrice(@RequestParam String sku,
                                      @RequestParam BigDecimal price) {
        productService.updatePrice(sku, price);
        return Result.ok("价格更新成功");
    }
}
