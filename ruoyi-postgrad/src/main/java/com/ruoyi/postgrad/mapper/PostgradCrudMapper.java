package com.ruoyi.postgrad.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

/**
 * 通用CRUD Mapper —— 元数据驱动的动态SQL。
 * <p>表名/列名/JOIN全部来自 ModuleMeta 白名单，用户输入通过 #{whereArgs} 参数化绑定。</p>
 *
 * @author ruoyi
 */
public interface PostgradCrudMapper
{
    /** 分页列表查询 */
    @SelectProvider(type = PostgradCrudSqlProvider.class, method = "selectList")
    List<Map<String, Object>> selectList(
            @Param("meta") Object meta,
            @Param("whereSql") String whereSql,
            @Param("whereArgs") List<Object> whereArgs,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /** 总数查询 */
    @SelectProvider(type = PostgradCrudSqlProvider.class, method = "selectCount")
    Long selectCount(
            @Param("meta") Object meta,
            @Param("whereSql") String whereSql,
            @Param("whereArgs") List<Object> whereArgs);

    /** 导出（不分页） */
    @SelectProvider(type = PostgradCrudSqlProvider.class, method = "selectExport")
    List<Map<String, Object>> selectExport(
            @Param("meta") Object meta,
            @Param("whereSql") String whereSql,
            @Param("whereArgs") List<Object> whereArgs);

    /** 按主键查找 */
    @SelectProvider(type = PostgradCrudSqlProvider.class, method = "selectById")
    List<Map<String, Object>> selectById(
            @Param("meta") Object meta,
            @Param("keySql") String keySql,
            @Param("keyArgs") List<Object> keyArgs);

    /** 选项列表 */
    @SelectProvider(type = PostgradCrudSqlProvider.class, method = "selectOption")
    List<Map<String, Object>> selectOption(
            @Param("sql") String sql,
            @Param("args") List<Object> args);

    /** 新增 */
    @InsertProvider(type = PostgradCrudSqlProvider.class, method = "insert")
    int insert(
            @Param("meta") Object meta,
            @Param("columns") List<String> columns,
            @Param("values") List<Object> values);

    /** 修改 */
    @UpdateProvider(type = PostgradCrudSqlProvider.class, method = "update")
    int update(
            @Param("meta") Object meta,
            @Param("setColumns") List<String> setColumns,
            @Param("setValues") List<Object> setValues,
            @Param("keySql") String keySql,
            @Param("keyArgs") List<Object> keyArgs);

    /** 删除 */
    @DeleteProvider(type = PostgradCrudSqlProvider.class, method = "delete")
    int delete(
            @Param("meta") Object meta,
            @Param("keySql") String keySql,
            @Param("keyArgs") List<Object> keyArgs);
}
