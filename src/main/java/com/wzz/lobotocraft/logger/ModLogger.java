package com.wzz.lobotocraft.logger;

import com.wzz.lobotocraft.ModMain;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModLogger {
    public static final Logger LOGGER = LogManager.getLogger(ModMain.MODID);

    public static Logger getLogger(Object object) {
        if (object instanceof String s)
            return LogManager.getLogger(s);
        return LogManager.getLogger(object.getClass());
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static void info(String str) {
        LOGGER.info(str);
    }

    public static void warn(String str) {
        LOGGER.warn(str);
    }

    public static void debug(String str) {
        LOGGER.debug(str);
    }

    public static void error(String str) {
        LOGGER.error(str);
    }
}
