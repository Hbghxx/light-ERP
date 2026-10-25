package com.example.erp.mapper;

import com.example.erp.entity.PurchaseInbound;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface PurchaseInboundMapper {

    @Insert("INSERT INTO purchase_inbound (inbound_no, product_sku, quantity, unit_price, total_amount, supplier_name, status, created_by, dept_id) " +
            "VALUES (#{inboundNo}, #{productSku}, #{quantity}, #{unitPrice}, #{totalAmount}, #{supplierName}, 1, #{createdBy}, #{deptId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PurchaseInbound inbound);

    /**
     * 数据权限过滤：SELF 仅本人创建；DEPT_AND_CHILD 本部门及子部门（物化路径前缀匹配）；ALL 不过滤
     */
    @Select("<script>" +
            "SELECT id, inbound_no AS inboundNo, product_sku AS productSku, " +
            "quantity, unit_price AS unitPrice, total_amount AS totalAmount, " +
            "supplier_name AS supplierName, status, created_by AS createdBy, dept_id AS deptId, created_at AS createdAt " +
            "FROM purchase_inbound" +
            "<where>" +
            "<if test=\"ds == 'SELF'\"> AND created_by = #{uid}</if>" +
            "<if test=\"ds == 'DEPT_AND_CHILD'\"> AND dept_id IN (SELECT id FROM sys_dept WHERE path LIKE CONCAT(#{dpath}, '%'))</if>" +
            "</where>" +
            " ORDER BY id DESC" +
            "</script>")
    List<PurchaseInbound> selectAll(@Param("ds") String ds, @Param("uid") Long uid, @Param("dpath") String dpath);

    @Select("SELECT id, inbound_no AS inboundNo, product_sku AS productSku, quantity, " +
            "unit_price AS unitPrice, total_amount AS totalAmount, supplier_name AS supplierName, " +
            "status, created_by AS createdBy, dept_id AS deptId, created_at AS createdAt " +
            "FROM purchase_inbound WHERE id = #{id}")
    PurchaseInbound findById(@Param("id") Long id);

    /** CAS 状态迁移：仅当当前状态等于期望值时更新（防并发重复审批/作废） */
    @Update("UPDATE purchase_inbound SET status = #{to} WHERE id = #{id} AND status = #{from}")
    int updateStatusCas(@Param("id") Long id, @Param("from") Integer from, @Param("to") Integer to);

    /** 待审批单据（供审批中心），带申请人姓名，数据权限过滤同列表 */
    @Select("<script>" +
            "SELECT i.id, i.inbound_no AS inboundNo, i.product_sku AS productSku, i.quantity, " +
            "i.unit_price AS unitPrice, i.total_amount AS totalAmount, i.supplier_name AS supplierName, " +
            "i.status, i.created_by AS createdBy, i.created_at AS createdAt, u.username AS applicantName " +
            "FROM purchase_inbound i LEFT JOIN sys_user u ON u.id = i.created_by" +
            "<where>" +
            " i.status = 1" +
            "<if test=\"ds == 'SELF'\"> AND i.created_by = #{uid}</if>" +
            "<if test=\"ds == 'DEPT_AND_CHILD'\"> AND i.dept_id IN (SELECT id FROM sys_dept WHERE path LIKE CONCAT(#{dpath}, '%'))</if>" +
            "</where>" +
            " ORDER BY i.id DESC" +
            "</script>")
    List<PurchaseInbound> listPending(@Param("ds") String ds, @Param("uid") Long uid, @Param("dpath") String dpath);


}