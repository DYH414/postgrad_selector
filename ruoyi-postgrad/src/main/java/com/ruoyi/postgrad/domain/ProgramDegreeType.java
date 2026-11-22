package com.ruoyi.postgrad.domain;

import java.util.regex.Pattern;

public final class ProgramDegreeType
{
    private static final Pattern PROFESSIONAL_CODE = Pattern.compile("0854[0-9]{2}|08[0-9]{2}51");

    private ProgramDegreeType()
    {
    }

    public static String resolve(String programCode, String storedType)
    {
        return programCode != null && PROFESSIONAL_CODE.matcher(programCode).matches()
            ? "professional" : storedType;
    }
}
