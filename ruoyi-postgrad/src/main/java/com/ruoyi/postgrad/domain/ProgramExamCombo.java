package com.ruoyi.postgrad.domain;

import java.util.List;

public enum ProgramExamCombo {
    MATH_2_ENGLISH_2("22408", "101,204,302,408", "政治 + 英语二 + 数学二 + 408"),
    MATH_1_ENGLISH_1("11408", "101,201,301,408", "政治 + 英语一 + 数学一 + 408");

    private final String code;
    private final String subjects;
    private final String label;

    ProgramExamCombo(String code, String subjects, String label) {
        this.code = code;
        this.subjects = subjects;
        this.label = label;
    }

    public String code() { return code; }
    public String label() { return label; }

    public static List<String> subjectsForPreference(String preference) {
        for (ProgramExamCombo combo : values()) {
            if (combo.code.equals(preference)) return List.of(combo.subjects);
        }
        return List.of(MATH_2_ENGLISH_2.subjects, MATH_1_ENGLISH_1.subjects);
    }

    public static ProgramExamCombo fromSubjects(String subjects) {
        for (ProgramExamCombo combo : values()) {
            if (combo.subjects.equals(subjects)) return combo;
        }
        return null;
    }
}
