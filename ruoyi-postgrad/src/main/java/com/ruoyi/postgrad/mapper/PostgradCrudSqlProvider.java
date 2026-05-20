package com.ruoyi.postgrad.mapper;

import java.util.List;
import java.util.Map;

import com.ruoyi.postgrad.domain.ModuleMeta;

/**
 * MyBatis SqlProvider — 根据 ModuleMeta 白名单动态构建 SQL。
 * <p>表名/列名/JOIN 来自硬编码的 ModuleMeta 定义，不拼接用户输入。
 * 用户数据通过 #{whereArgs}, #{values} 等参数化占位符绑定，防止 SQL 注入。</p>
 *
 * @author ruoyi
 */
public class PostgradCrudSqlProvider
{
    /** SELECT ... FROM ... WHERE ... ORDER BY ... LIMIT ? OFFSET ? */
    public String selectList(Map<String, Object> param)
    {
        ModuleMeta meta = (ModuleMeta) param.get("meta");
        return meta.getSelectSql()
                + " " + param.get("whereSql")
                + " " + meta.getOrderSql()
                + " limit #{limit} offset #{offset}";
    }

    /** SELECT count(1) FROM ... WHERE ... */
    public String selectCount(Map<String, Object> param)
    {
        ModuleMeta meta = (ModuleMeta) param.get("meta");
        return "select count(1) from " + meta.getFromSql()
                + " " + param.get("whereSql");
    }

    /** SELECT ... FROM ... WHERE ... ORDER BY ... （不分页） */
    public String selectExport(Map<String, Object> param)
    {
        ModuleMeta meta = (ModuleMeta) param.get("meta");
        return meta.getSelectSql()
                + " " + param.get("whereSql")
                + " " + meta.getOrderSql();
    }

    /** SELECT ... FROM ... WHERE <key condition> */
    public String selectById(Map<String, Object> param)
    {
        ModuleMeta meta = (ModuleMeta) param.get("meta");
        return meta.getSelectSql() + " where " + param.get("keySql");
    }

    /** optionselect 的 SQL 完全由调用方提供（来自 ModuleMeta.optionSql 或动态构建） */
    public String selectOption(Map<String, Object> param)
    {
        return (String) param.get("sql");
    }

    /** INSERT INTO table (cols) VALUES (#{values[0]}, #{values[1]}, ...) */
    @SuppressWarnings("unchecked")
    public String insert(Map<String, Object> param)
    {
        ModuleMeta meta = (ModuleMeta) param.get("meta");
        List<String> columns = (List<String>) param.get("columns");
        StringBuilder fields = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < columns.size(); i++)
        {
            if (i > 0)
            {
                fields.append(", ");
                placeholders.append(", ");
            }
            fields.append(columns.get(i));
            placeholders.append("#{values[").append(i).append("]}");
        }
        return "insert into " + meta.getTableName() + " (" + fields + ") values (" + placeholders + ")";
    }

    /** UPDATE table SET col = #{setValues[0]}, ... WHERE <key condition> */
    @SuppressWarnings("unchecked")
    public String update(Map<String, Object> param)
    {
        ModuleMeta meta = (ModuleMeta) param.get("meta");
        List<String> setColumns = (List<String>) param.get("setColumns");
        StringBuilder sets = new StringBuilder();
        for (int i = 0; i < setColumns.size(); i++)
        {
            if (i > 0) sets.append(", ");
            sets.append(setColumns.get(i)).append(" = #{setValues[").append(i).append("]}");
        }
        return "update " + meta.getTableName() + " set " + sets + " where " + param.get("keySql");
    }

    /** DELETE FROM table WHERE <key condition> */
    public String delete(Map<String, Object> param)
    {
        ModuleMeta meta = (ModuleMeta) param.get("meta");
        return "delete from " + meta.getTableName() + " where " + param.get("keySql");
    }
}
