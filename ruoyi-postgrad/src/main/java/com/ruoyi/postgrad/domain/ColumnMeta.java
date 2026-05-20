package com.ruoyi.postgrad.domain;

/**
 * 列元数据 —— 描述业务模块中一个字段的属性。
 *
 * @author ruoyi
 */
public class ColumnMeta
{
    private final String prop;      // 驼峰属性名（前端使用）
    private final String column;    // 数据库列名（下划线）
    private final String label;     // 中文标签
    private final boolean required; // 是否必填
    private final boolean key;      // 是否主键
    private final boolean readonly; // 是否只读（JOIN 展示字段）

    public ColumnMeta(String prop, String column, String label, boolean required, boolean key, boolean readonly)
    {
        this.prop = prop;
        this.column = column;
        this.label = label;
        this.required = required;
        this.key = key;
        this.readonly = readonly;
    }

    public String getProp() { return prop; }
    public String getColumn() { return column; }
    public String getLabel() { return label; }
    public boolean isRequired() { return required; }
    public boolean isKey() { return key; }
    public boolean isReadonly() { return readonly; }
}
