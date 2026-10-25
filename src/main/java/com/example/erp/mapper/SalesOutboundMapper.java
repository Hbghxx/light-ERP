package com.example.erp.mapper;

import com.example.erp.entity.SalesOutbound;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface SalesOutboundMapper {

    @Insert("INSERT INTO sales_outbound (outbound_no, product_sku, quantity, unit_price, total_amount, customer_name, status, created_by, dept_id) " +
            "VALUES (#{outboundNo}, #{productSku}, #{quantity}, #{unitPrice}, #{totalAmount}, #{customerName}, 1, #{createdBy}, #{deptId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SalesOutbound outbound);

    /**
     * 数据权限过滤：规则同采购入库（SELF/DEPT_AND_CHILD/ALL）
     */
    @Select("<script>" +
            "SELECT id, " +
            "outbound_no AS outboundNo, " +
            "product_sku AS productSku, " +
            "quantity, " +
            "unit_price AS unitPrice, " +
            "total_amount AS totalAmount, " +
            "customer_name AS customerName, " +
            "status, " +
            "created_by AS createdBy, " +
            "dept_id AS deptId, " +
            "created_at AS createdAt " +
            "FROM sales_outbound" +
            "<where>" +
            "<if test=\"ds == 'SELF'\"> AND created_by = #{uid}</if>" +
            "<if test=\"ds == 'DEPT_AND_CHILD'\"> AND dept_id IN (SELECT id FROM sys_dept WHERE path LIKE CONCAT(#{dpath}, '%'))</if>" +
            "</where>" +
            " ORDER BY id DESC" +
            "</script>")
    List<SalesOutbound> selectAll(@Param("ds") String ds, @Param("uid") Long uid, @Param("dpath") String dpath);

    @Select("SELECT id, outbound_no AS outboundNo, product_sku AS productSku, quantity, " +
            "unit_price AS unitPrice, total_amount AS totalAmount, customer_name AS customerName, " +
            "status, created_by AS createdBy, dept_id AS deptId, created_at AS createdAt " +
            "FROM sales_outbound WHERE id = #{id}")
    SalesOutbound findById(@Param("id") Long id);

    /** CAS 状态迁移：仅当当前状态等于期望值时更新（防并发重复审批/作废） */
    @Update("UPDATE sales_outbound SET status = #{to} WHERE id = #{id} AND status = #{from}")
    int updateStatusCas(@Param("id") Long id, @Param("from") Integer from, @Param("to") Integer to);

    /** 待审批单据（供审批中心），带申请人姓名，数据权限过滤同列表 */
    @Select("<script>" +
            "SELECT o.id, o.outbound_no AS outboundNo, o.product_sku AS productSku, o.quantity, " +
            "o.unit_price AS unitPrice, o.total_amount AS totalAmount, o.customer_name AS customerName, " +
            "o.status, o.created_by AS createdBy, o.created_at AS createdAt, u.username AS applicantName " +
            "FROM sales_outbound o LEFT JOIN sys_user u ON u.id = o.created_by" +
            "<where>" +
            " o.status = 1" +
            "<if test=\"ds == 'SELF'\"> AND o.created_by = #{uid}</if>" +
            "<if test=\"ds == 'DEPT_AND_CHILD'\"> AND o.dept_id IN (SELECT id FROM sys_dept WHERE path LIKE CONCAT(#{dpath}, '%'))</if>" +
            "</where>" +
            " ORDER BY o.id DESC" +
            "</script>")
    List<SalesOutbound> listPending(@Param("ds") String ds, @Param("uid") Long uid, @Param("dpath") String dpath);
}