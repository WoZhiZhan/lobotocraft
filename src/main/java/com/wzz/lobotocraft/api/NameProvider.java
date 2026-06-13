package com.wzz.lobotocraft.api;

public interface NameProvider {

    /**
     * 获取当前方法的类名
     * @return 当前方法的类名
     */
    default String getCurrentClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 2) {
            // stackTrace[2] 是调用当前方法的方法信息
            return stackTrace[2].getClassName();
        } else {
            return "Unknown class";
        }
    }

    /**
     * 获取当前方法的名称
     * @return 当前方法的名称
     */
    default String getCurrentMethodName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 2) {
            return stackTrace[2].getMethodName();
        } else {
            return "Unknown method";
        }
    }
}