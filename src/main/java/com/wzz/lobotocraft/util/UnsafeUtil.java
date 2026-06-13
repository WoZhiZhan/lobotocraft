package com.wzz.lobotocraft.util;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class UnsafeUtil {
    public static Unsafe unsafe;

    static {
        try {
            Unsafe found = null;
            Field[] declaredFields = Unsafe.class.getDeclaredFields();
            for (Field field : declaredFields) {
                if (field.getType() == Unsafe.class) {
                    field.setAccessible(true);
                    found = (Unsafe) field.get(null);
                    break;
                }
            }
            if (found == null) {
                try {
                    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafe.setAccessible(true);
                    unsafe = (Unsafe) theUnsafe.get(null);
                } catch (Exception e) {
                    try {
                        Constructor<Unsafe> c = Unsafe.class.getDeclaredConstructor();
                        c.setAccessible(true);
                        unsafe = c.newInstance();
                    } catch (Exception ignored) {
                    }
                }
            } else {
                unsafe = found;
            }
        } catch (Throwable var5) {
            throw new ExceptionInInitializerError(var5);
        }
    }
}
