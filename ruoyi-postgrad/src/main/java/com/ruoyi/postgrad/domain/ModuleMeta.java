package com.ruoyi.postgrad.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用CRUD模块元数据 —— 定义单个业务模块的表结构/JOIN/列/搜索配置/主键。
 * <p>白名单驱动：表名、列名、JOIN 全部硬编码在此，运行时不会拼接用户输入。</p>
 *
 * @author ruoyi
 */
public class ModuleMeta
{
    private final String tableName;
    private String keyAlias;
    private final String fromSql;
    private final String selectSql;
    private final String orderSql;
    private final String optionSql;
    private final List<ColumnMeta> columns = new ArrayList<>();
    private final List<ColumnMeta> keyColumns = new ArrayList<>();
    private final List<SearchMeta> searches = new ArrayList<>();
    private final List<String> exportProps = new ArrayList<>();

    private ModuleMeta(String tableName, String fromSql, String selectSql, String orderSql, String optionSql)
    {
        this.tableName = tableName;
        this.keyAlias = tableName;
        this.fromSql = fromSql;
        this.selectSql = selectSql;
        this.orderSql = orderSql;
        this.optionSql = optionSql;
    }

    public static ModuleMeta of(String tableName, String fromSql, String selectSql, String orderSql, String optionSql)
    {
        return new ModuleMeta(tableName, fromSql, selectSql, orderSql, optionSql);
    }

    // ── getters ──

    public String getTableName() { return tableName; }
    public String getKeyAlias() { return keyAlias; }
    public String getFromSql() { return fromSql; }
    public String getSelectSql() { return selectSql; }
    public String getOrderSql() { return orderSql; }
    public String getOptionSql() { return optionSql; }
    public List<ColumnMeta> getColumns() { return columns; }
    public List<ColumnMeta> getKeyColumns() { return keyColumns; }
    public List<SearchMeta> getSearches() { return searches; }
    public List<String> getExportProps() { return exportProps; }

    public String labelOf(String prop)
    {
        return columns.stream().filter(c -> c.getProp().equals(prop)).findFirst().map(ColumnMeta::getLabel).orElse(prop);
    }

    // ── builder methods ──

    public ModuleMeta keyAlias(String v) { this.keyAlias = v; return this; }

    public ModuleMeta key(String prop, String column, String label)
    {
        ColumnMeta k = new ColumnMeta(prop, column, label, false, true, false);
        columns.add(k); keyColumns.add(k); exportProps.add(prop);
        return this;
    }

    public ModuleMeta compositeKey(String p1, String c1, String l1, String p2, String c2, String l2)
    {
        assignedKey(p1, c1, l1); assignedKey(p2, c2, l2);
        return this;
    }

    public ModuleMeta assignedKey(String prop, String column, String label)
    {
        ColumnMeta k = new ColumnMeta(prop, column, label, true, true, false);
        columns.add(k); keyColumns.add(k); exportProps.add(prop);
        return this;
    }

    public ModuleMeta col(String prop, String column, String label) { return col(prop, column, label, false); }

    public ModuleMeta col(String prop, String column, String label, boolean required)
    {
        columns.add(new ColumnMeta(prop, column, label, required, false, false));
        exportProps.add(prop);
        return this;
    }

    public ModuleMeta view(String prop, String label)
    {
        columns.add(new ColumnMeta(prop, prop, label, false, false, true));
        exportProps.add(prop);
        return this;
    }

    public ModuleMeta time(String prop, String label)
    {
        columns.add(new ColumnMeta(prop, prop, label, false, false, true));
        exportProps.add(prop);
        return this;
    }

    public ModuleMeta searchLike(String prop, String expression)
    {
        searches.add(new SearchMeta(prop, expression, true));
        return this;
    }

    public ModuleMeta searchExact(String prop, String expression)
    {
        searches.add(new SearchMeta(prop, expression, false));
        return this;
    }

    public ModuleMeta searchExists(String prop, String expression)
    {
        searches.add(new SearchMeta(prop, expression, false, true));
        return this;
    }

    // ── public static helpers ──

    public static String option(String module, String id, String label, String from, String order)
    {
        return "select " + id + " id, " + label + " label from " + from + " order by " + order;
    }

    public static String prefix(String alias, String columns)
    {
        return Arrays.stream(columns.split(", ")).map(c -> alias + "." + c).collect(Collectors.joining(", "));
    }

    public static String camel(String value)
    {
        StringBuilder b = new StringBuilder();
        boolean upper = false;
        for (char ch : value.toCharArray())
        {
            if (ch == '_') { upper = true; }
            else if (upper) { b.append(Character.toUpperCase(ch)); upper = false; }
            else { b.append(ch); }
        }
        return b.toString();
    }

    public static String admissionLabel(String column)
    {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("score_line", "复试线"); labels.put("single_math", "数学线");
        labels.put("single_english", "英语线"); labels.put("single_politics", "政治线");
        labels.put("single_professional", "专业课线"); labels.put("total_plan", "总计划");
        labels.put("recommended_exemption_plan", "推免计划"); labels.put("unified_exam_quota", "统考名额");
        labels.put("retest_count", "复试人数"); labels.put("admitted_count", "录取人数");
        labels.put("first_choice_admitted_count", "一志愿录取"); labels.put("min_admitted_score", "最低分");
        labels.put("avg_admitted_score", "平均分"); labels.put("max_admitted_score", "最高分");
        labels.put("has_transfer", "有调剂"); labels.put("transfer_count", "调剂人数");
        return labels.getOrDefault(column, column);
    }
}
