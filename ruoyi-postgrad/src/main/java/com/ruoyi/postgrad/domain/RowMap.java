package com.ruoyi.postgrad.domain;

import java.util.LinkedHashMap;

/**
 * Dynamic SQL projection row.
 *
 * <p>Some application-facing queries intentionally return flexible columns for
 * dashboard and recommendation payloads. Keeping the mapper return type away
 * from raw Map avoids MyBatis treating mapper methods as Map-keyed collections.
 */
public class RowMap extends LinkedHashMap<String, Object>
{
    private static final long serialVersionUID = 1L;
}
