package com.ruoyi.postgrad.domain;

/**
 * 搜索条件元数据 —— 描述前端搜索字段如何映射到 SQL WHERE 子句。
 *
 * @author ruoyi
 */
public class SearchMeta
{
    private final String prop;       // 前端参数名
    private final String expression; // SQL 表达式（含表别名）
    private final boolean like;      // true=LIKE, false=精确匹配
    private final boolean exists;    // true=EXISTS 子查询（不拼接 = ?）

    public SearchMeta(String prop, String expression, boolean like)
    {
        this(prop, expression, like, false);
    }

    public SearchMeta(String prop, String expression, boolean like, boolean exists)
    {
        this.prop = prop;
        this.expression = expression;
        this.like = like;
        this.exists = exists;
    }

    public String getProp() { return prop; }
    public String getExpression() { return expression; }
    public boolean isLike() { return like; }
    public boolean isExists() { return exists; }
}
