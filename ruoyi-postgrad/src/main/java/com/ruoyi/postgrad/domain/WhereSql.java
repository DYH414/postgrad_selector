package com.ruoyi.postgrad.domain;

import java.util.List;

/**
 * WHERE 子句及其绑定参数。
 *
 * @author ruoyi
 */
public class WhereSql
{
    private final String sql;
    private final List<Object> args;

    public WhereSql(String sql, List<Object> args)
    {
        this.sql = sql;
        this.args = args;
    }

    public String getSql() { return sql; }
    public List<Object> getArgs() { return args; }
}
