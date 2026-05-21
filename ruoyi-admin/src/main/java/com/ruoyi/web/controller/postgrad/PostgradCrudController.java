package com.ruoyi.web.controller.postgrad;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.postgrad.service.IPostgradCrudService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 考研择校后台通用CRUD控制器。
 * <p>Controller 仅处理 HTTP 请求/响应和权限校验，业务逻辑全部在
 * {@link IPostgradCrudService} 中，数据访问在
 * {@link com.ruoyi.postgrad.mapper.PostgradCrudMapper} 中。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/postgrad/{module}")
public class PostgradCrudController extends BaseController
{
    @Autowired
    private IPostgradCrudService crudService;

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':list')")
    @GetMapping("/list")
    public TableDataInfo list(@PathVariable String module, @RequestParam Map<String, String> params)
    {
        Map<String, Object> data = crudService.list(module, params);
        TableDataInfo rsp = new TableDataInfo();
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        rsp.setRows((List<?>) data.get("rows"));
        rsp.setTotal(((Number) data.get("total")).longValue());
        return rsp;
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':export')")
    @Log(title = "考研择校数据导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@PathVariable String module, @RequestParam Map<String, String> params,
            HttpServletResponse response) throws IOException
    {
        List<Map<String, Object>> rows = crudService.export(module, params);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + module + ".csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('﻿');
        if (!rows.isEmpty())
        {
            // Header row — use keys from first row
            writer.println(rows.get(0).keySet().stream()
                    .filter(k -> !"__key".equals(k))
                    .collect(Collectors.joining(",")));
            for (Map<String, Object> row : rows)
            {
                writer.println(row.entrySet().stream()
                        .filter(e -> !"__key".equals(e.getKey()))
                        .map(e -> csv(e.getValue()))
                        .collect(Collectors.joining(",")));
            }
        }
        writer.flush();
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable String module, @PathVariable String id)
    {
        return success(crudService.getInfo(module, id));
    }

    @GetMapping("/optionselect")
    public AjaxResult optionselect(@PathVariable String module, @RequestParam Map<String, String> params)
    {
        return success(crudService.optionselect(module, params));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':add')")
    @Log(title = "考研择校数据新增", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@PathVariable String module, @RequestBody Map<String, Object> body)
    {
        return toAjax(crudService.add(module, body));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':edit')")
    @Log(title = "考研择校数据修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@PathVariable String module, @RequestBody Map<String, Object> body)
    {
        return toAjax(crudService.edit(module, body));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':remove')")
    @Log(title = "考研择校数据删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable String module, @PathVariable String ids)
    {
        return toAjax(crudService.remove(module, ids));
    }

    private static String csv(Object value)
    {
        String text = value == null ? "" : Objects.toString(value, "");
        return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }
}
