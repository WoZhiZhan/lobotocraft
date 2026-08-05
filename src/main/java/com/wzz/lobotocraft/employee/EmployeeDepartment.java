package com.wzz.lobotocraft.employee;

public enum EmployeeDepartment {
    CONTROL("control", "控制部", 0xD7AF3E),
    INFORMATION("information", "情报部", 0x8E5FC6),
    TRAINING("training", "培训部", 0xD67500),
    SAFETY("safety", "安保部", 0x21C784),
    CENTRAL("central", "中央本部", 0xE27F02),
    WELFARE("welfare", "福利部", 0x4C63B0),
    DISCIPLINARY("disciplinary", "惩戒部", 0xE33B22),
    RECORD("record", "记录部", 0xC8CCD0),
    EXTRACTION("extraction", "研发部", 0xC99A55),
    ARCHITECTURE("architecture", "构筑部", 0xE8E8E4),
    UNASSIGNED("unassigned", "未分配", 0x66BFCD);

    private final String id;
    private final String displayName;
    private final int textColorRgb;

    EmployeeDepartment(String id, String displayName, int textColorRgb) {
        this.id = id;
        this.displayName = displayName;
        this.textColorRgb = textColorRgb;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int textColorRgb() {
        return textColorRgb;
    }

    public int capacity() {
        if (this == UNASSIGNED) return Integer.MAX_VALUE;
        return this == CENTRAL ? 10 : 5;
    }

    public static EmployeeDepartment byId(String id) {
        for (EmployeeDepartment department : values()) {
            if (department.id.equals(id)) return department;
        }
        return UNASSIGNED;
    }
}
