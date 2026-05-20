package com.ruoyi.postgrad.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 学校基础信息对象 school
 *
 * @author ruoyi
 */
public class School extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @Excel(name = "学校ID", cellType = ColumnType.NUMERIC)
    private Long id;

    /** 学校全称 */
    @Excel(name = "学校全称")
    private String name;

    /** 学校简称 */
    @Excel(name = "学校简称")
    private String shortName;

    /** 省份 */
    @Excel(name = "省份")
    private String province;

    /** 城市 */
    @Excel(name = "城市")
    private String city;

    /** 学校层次 */
    @Excel(name = "学校层次", readConverterExp = "985=985,211=211,DOUBLE_FIRST=双一流,PUBLIC_REGULAR=普通公办,PRIVATE=民办,INDEPENDENT=独立学院,RESEARCH_INSTITUTE=科研院所,OTHER=其他")
    private String tier;

    /** 是否985 */
    @Excel(name = "是否985", readConverterExp = "0=否,1=是")
    private Integer is985;

    /** 是否211 */
    @Excel(name = "是否211", readConverterExp = "0=否,1=是")
    private Integer is211;

    /** 是否双一流 */
    @Excel(name = "是否双一流", readConverterExp = "0=否,1=是")
    private Integer isDoubleFirst;

    /** 是否公办 */
    @Excel(name = "是否公办", readConverterExp = "0=否,1=是")
    private Integer isPublic;

    /** 学校官网或研招官网 */
    @Excel(name = "学校官网或研招官网")
    private String website;

    /** 状态 */
    @Excel(name = "状态", readConverterExp = "active=正常,inactive=停用")
    private String status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "创建时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "更新时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    @NotBlank(message = "学校全称不能为空")
    @Size(max = 100, message = "学校全称长度不能超过100个字符")
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Size(max = 30, message = "学校简称长度不能超过30个字符")
    public String getShortName()
    {
        return shortName;
    }

    public void setShortName(String shortName)
    {
        this.shortName = shortName;
    }

    @NotBlank(message = "省份不能为空")
    @Size(max = 30, message = "省份长度不能超过30个字符")
    public String getProvince()
    {
        return province;
    }

    public void setProvince(String province)
    {
        this.province = province;
    }

    @NotBlank(message = "城市不能为空")
    @Size(max = 30, message = "城市长度不能超过30个字符")
    public String getCity()
    {
        return city;
    }

    public void setCity(String city)
    {
        this.city = city;
    }

    public String getTier()
    {
        return tier;
    }

    public void setTier(String tier)
    {
        this.tier = tier;
    }

    public Integer getIs985()
    {
        return is985;
    }

    public void setIs985(Integer is985)
    {
        this.is985 = is985;
    }

    public Integer getIs211()
    {
        return is211;
    }

    public void setIs211(Integer is211)
    {
        this.is211 = is211;
    }

    public Integer getIsDoubleFirst()
    {
        return isDoubleFirst;
    }

    public void setIsDoubleFirst(Integer isDoubleFirst)
    {
        this.isDoubleFirst = isDoubleFirst;
    }

    public Integer getIsPublic()
    {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic)
    {
        this.isPublic = isPublic;
    }

    @Size(max = 255, message = "官网地址长度不能超过255个字符")
    public String getWebsite()
    {
        return website;
    }

    public void setWebsite(String website)
    {
        this.website = website;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt)
    {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("shortName", getShortName())
            .append("province", getProvince())
            .append("city", getCity())
            .append("tier", getTier())
            .append("is985", getIs985())
            .append("is211", getIs211())
            .append("isDoubleFirst", getIsDoubleFirst())
            .append("isPublic", getIsPublic())
            .append("website", getWebsite())
            .append("status", getStatus())
            .append("createdAt", getCreatedAt())
            .append("updatedAt", getUpdatedAt())
            .toString();
    }
}
