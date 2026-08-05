package com.wzz.lobotocraft.event.listener;

/** Original-game spawn counts projected onto opened facility departments. */
public final class OrdealSpawnPolicy {
    public static final int AMBER_DAWNS_PER_DEPARTMENT = 5;
    public static final int INDIGO_NOON_PER_DEPARTMENT = 3;

    private OrdealSpawnPolicy() {
    }

    public static int dawnDepartmentCount(int openedDepartments) {
        return Math.max(0, openedDepartments);
    }

    public static int greenNoonDepartmentCount(int openedDepartments) {
        return Math.max(0, openedDepartments);
    }

    public static int crimsonNoonTotalCount(int openedDepartments) {
        return openedDepartments > 0 ? 1 : 0;
    }

    public static int violetNoonDepartmentCount(int openedDepartments) {
        return Math.min(5, Math.max(0, openedDepartments));
    }

    public static int indigoNoonDepartmentCount(int openedDepartments) {
        return Math.min(4, Math.max(0, openedDepartments));
    }
}
