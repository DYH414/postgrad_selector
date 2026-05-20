package com.ruoyi.postgrad.domain;

import java.util.List;

/**
 * 主键 WHERE 子句 —— 继承 WhereSql，语义上标识这是按主键定位。
 *
 * @author ruoyi
 */
public class KeySql extends WhereSql
{
    public KeySql(String sql, List<Object> args)
    {
        super(sql, args);
    }
}
