package com.wzz.lobotocraft.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MethodHandleUtils {
    
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final Map<String, MethodHandle> METHOD_HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, MethodHandle> CONSTRUCTOR_HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, MethodHandle> FIELD_GETTER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, MethodHandle> FIELD_SETTER_CACHE = new ConcurrentHashMap<>();

    private static final AtomicLong cacheHitCount = new AtomicLong(0);
    private static final AtomicLong cacheMissCount = new AtomicLong(0);
    private static volatile boolean enableStatistics = false;
    
    /**
     * 调用实例方法
     * @param target 目标对象
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @param args 参数
     * @return 方法执行结果
     */
    public static Object invokeMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            String cacheKey = generateMethodKey(target.getClass(), methodName, parameterTypes);
            MethodHandle methodHandle = getOrCreateMethodHandle(cacheKey, () -> {
                try {
                    Method method = findMethod(target.getClass(), methodName, parameterTypes);
                    method.setAccessible(true);
                    return LOOKUP.unreflect(method);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get method handle", e);
                }
            });
            
            return methodHandle.invokeWithArguments(prependTarget(target, args));
        } catch (Throwable e) {
            throw new RuntimeException("Method invocation failed", e);
        }
    }
    
    /**
     * 调用实例方法（无参数版本）
     * @param target 目标对象
     * @param methodName 方法名
     * @return 方法执行结果
     */
    public static Object invokeMethod(Object target, String methodName) {
        return invokeMethod(target, methodName, new Class<?>[0]);
    }
    
    /**
     * 调用实例方法（自动推断参数类型）
     * @param target 目标对象
     * @param methodName 方法名
     * @param args 参数
     * @return 方法执行结果
     */
    public static Object invokeMethodAuto(Object target, String methodName, Object... args) {
        Class<?>[] parameterTypes = getParameterTypes(args);
        return invokeMethod(target, methodName, parameterTypes, args);
    }
    
    /**
     * 安全调用实例方法（返回Optional，不抛出异常）
     * @param target 目标对象
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @param args 参数
     * @return Optional包装的方法执行结果
     */
    public static Optional<Object> invokeMethodSafe(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            return Optional.ofNullable(invokeMethod(target, methodName, parameterTypes, args));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) throws NoSuchMethodException {
        while (clazz != null) {
            try {
                return clazz.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchMethodException("Method " + name + " not found in class hierarchy");
    }

    public static MethodHandle getFieldHandle(Class<?> clazz, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);  // 使私有字段可访问
        return MethodHandles.lookup().unreflectSetter(field);
    }

    // 获取方法的 MethodHandle
    public static MethodHandle getMethodHandle(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);  // 使私有方法可访问
        return MethodHandles.lookup().unreflect(method);
    }
    
    /**
     * 查找所有匹配名称的方法（包括重载方法）
     * @param clazz 目标类
     * @param methodName 方法名
     * @return 匹配的方法列表
     */
    public static List<Method> findAllMethods(Class<?> clazz, String methodName) {
        List<Method> methods = new ArrayList<>();
        while (clazz != null) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    methods.add(method);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return methods;
    }

    /**
     * 调用静态方法
     * @param clazz 目标类
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @param args 参数
     * @return 方法执行结果
     */
    public static Object invokeStaticMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            String cacheKey = generateMethodKey(clazz, methodName, parameterTypes);
            MethodHandle methodHandle = getOrCreateMethodHandle(cacheKey, () -> {
                try {
                    Method method = clazz.getMethod(methodName, parameterTypes);
                    method.setAccessible(true);
                    return LOOKUP.unreflect(method);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get method handle", e);
                }
            });
            
            return methodHandle.invokeWithArguments(args);
        } catch (Throwable e) {
            throw new RuntimeException("Static method invocation failed", e);
        }
    }
    
    /**
     * 调用静态方法（无参数版本）
     * @param clazz 目标类
     * @param methodName 方法名
     * @return 方法执行结果
     */
    public static Object invokeStaticMethod(Class<?> clazz, String methodName) {
        return invokeStaticMethod(clazz, methodName, new Class<?>[0]);
    }
    
    /**
     * 调用静态方法（自动推断参数类型）
     * @param clazz 目标类
     * @param methodName 方法名
     * @param args 参数
     * @return 方法执行结果
     */
    public static Object invokeStaticMethodAuto(Class<?> clazz, String methodName, Object... args) {
        Class<?>[] parameterTypes = getParameterTypes(args);
        return invokeStaticMethod(clazz, methodName, parameterTypes, args);
    }
    
    /**
     * 创建对象实例
     * @param clazz 目标类
     * @param parameterTypes 参数类型
     * @param args 构造参数
     * @return 对象实例
     */
    public static <T> T newInstance(Class<T> clazz, Class<?>[] parameterTypes, Object... args) {
        try {
            String cacheKey = generateConstructorKey(clazz, parameterTypes);
            MethodHandle constructorHandle = getOrCreateConstructorHandle(cacheKey, () -> {
                try {
                    Constructor<T> constructor = clazz.getDeclaredConstructor(parameterTypes);
                    constructor.setAccessible(true);
                    return LOOKUP.unreflectConstructor(constructor);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get constructor handle", e);
                }
            });
            
            return (T) constructorHandle.invokeWithArguments(args);
        } catch (Throwable e) {
            throw new RuntimeException("Constructor invocation failed", e);
        }
    }
    
    /**
     * 创建对象实例（无参构造器）
     * @param clazz 目标类
     * @return 对象实例
     */
    public static <T> T newInstance(Class<T> clazz) {
        return newInstance(clazz, new Class<?>[0]);
    }
    
    /**
     * 创建对象实例（自动推断参数类型）
     * @param clazz 目标类
     * @param args 构造参数
     * @return 对象实例
     */
    public static <T> T newInstanceAuto(Class<T> clazz, Object... args) {
        Class<?>[] parameterTypes = getParameterTypes(args);
        return newInstance(clazz, parameterTypes, args);
    }
    
    /**
     * 获取字段值
     * @param target 目标对象
     * @param fieldName 字段名
     * @return 字段值
     */
    public static Object getFieldValue(Object target, String fieldName) {
        try {
            String cacheKey = generateFieldKey(target.getClass(), fieldName);
            MethodHandle fieldGetter = getOrCreateFieldGetter(cacheKey, () -> {
                try {
                    Field field = findField(target.getClass(), fieldName);
                    field.setAccessible(true);
                    return LOOKUP.unreflectGetter(field);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get field getter handle", e);
                }
            });
            
            return fieldGetter.invoke(target);
        } catch (Throwable e) {
            throw new RuntimeException("Field get failed", e);
        }
    }
    
    /**
     * 获取字段值（指定类型）
     * @param target 目标对象
     * @param fieldName 字段名
     * @param type 字段类型
     * @return 字段值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object target, String fieldName, Class<T> type) {
        Object value = getFieldValue(target, fieldName);
        return type.cast(value);
    }
    
    /**
     * 安全获取字段值（返回Optional，不抛出异常）
     * @param target 目标对象
     * @param fieldName 字段名
     * @return Optional包装的字段值
     */
    public static Optional<Object> getFieldValueSafe(Object target, String fieldName) {
        try {
            return Optional.ofNullable(getFieldValue(target, fieldName));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * 批量获取字段值
     * @param target 目标对象
     * @param fieldNames 字段名数组
     * @return 字段名到字段值的映射
     */
    public static Map<String, Object> getFieldValues(Object target, String... fieldNames) {
        Map<String, Object> result = new HashMap<>();
        for (String fieldName : fieldNames) {
            try {
                result.put(fieldName, getFieldValue(target, fieldName));
            } catch (Exception e) {
                result.put(fieldName, null);
            }
        }
        return result;
    }
    
    /**
     * 获取所有字段值（包括继承的字段）
     * @param target 目标对象
     * @return 字段名到字段值的映射
     */
    public static Map<String, Object> getAllFieldValues(Object target) {
        Map<String, Object> result = new HashMap<>();
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    try {
                        result.putIfAbsent(field.getName(), getFieldValue(target, field.getName()));
                    } catch (Exception ignored) {
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return result;
    }
    
    /**
     * 设置字段值
     * @param target 目标对象
     * @param fieldName 字段名
     * @param value 字段值
     */
    public static void setFieldValue(Object target, String fieldName, Object value) {
        try {
            String cacheKey = generateFieldKey(target.getClass(), fieldName);
            MethodHandle fieldSetter = getOrCreateFieldSetter(cacheKey, () -> {
                try {
                    Field field = findField(target.getClass(), fieldName);
                    field.setAccessible(true);
                    return LOOKUP.unreflectSetter(field);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get field setter handle", e);
                }
            });
            
            fieldSetter.invoke(target, value);
        } catch (Throwable e) {
            throw new RuntimeException("Field set failed", e);
        }
    }

    /**
     * 设置字段值
     * @param target 目标对象
     * @param field 字
     * @param value 字段值
     */
    public static void setFieldValue(Object target, Field field, Object value) {
        try {
            String cacheKey = generateFieldKey(target.getClass(), field.getName());
            MethodHandle fieldSetter = getOrCreateFieldSetter(cacheKey, () -> {
                try {
                    return LOOKUP.unreflectSetter(field);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get field setter handle", e);
                }
            });

            fieldSetter.invoke(target, value);
        } catch (Throwable e) {
            throw new RuntimeException("Field set failed", e);
        }
    }
    
    /**
     * 安全设置字段值（不抛出异常）
     * @param target 目标对象
     * @param fieldName 字段名
     * @param value 字段值
     * @return 是否设置成功
     */
    public static boolean setFieldValueSafe(Object target, String fieldName, Object value) {
        try {
            setFieldValue(target, fieldName, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 批量设置字段值
     * @param target 目标对象
     * @param fieldValues 字段名到字段值的映射
     */
    public static void setFieldValues(Object target, Map<String, Object> fieldValues) {
        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
            setFieldValueSafe(target, entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * 在类层次结构中查找字段
     * @param clazz 起始类
     * @param fieldName 字段名
     * @return 字段对象
     * @throws NoSuchFieldException 如果找不到字段
     */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        throw new NoSuchFieldException(
                "Field '" + fieldName + "' not found in " + clazz + " or its superclasses"
        );
    }

    public static Field getField(Class<?> clazz, String name, String obfName) throws NoSuchFieldException {
        try {
            return getField(clazz, name);
        } catch (NoSuchFieldException e) {
            return getField(clazz, obfName);
        }
    }

    /**
     * 获取字段对象（公开方法）
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return 字段对象
     */
    public static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return findField(clazz, fieldName);
    }
    
    /**
     * 安全获取字段对象
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return Optional包装的字段对象
     */
    public static Optional<Field> getFieldSafe(Class<?> clazz, String fieldName) {
        try {
            return Optional.of(findField(clazz, fieldName));
        } catch (NoSuchFieldException e) {
            return Optional.empty();
        }
    }
    
    /**
     * 获取类的所有字段（包括私有字段，不包括继承）
     * @param clazz 目标类
     * @return 字段数组
     */
    public static Field[] getDeclaredFields(Class<?> clazz) {
        return clazz.getDeclaredFields();
    }
    
    /**
     * 获取类的所有字段（包括继承的字段）
     * @param clazz 目标类
     * @return 字段列表
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
    
    /**
     * 获取类的所有实例字段（非静态字段，包括继承）
     * @param clazz 目标类
     * @return 字段列表
     */
    public static List<Field> getAllInstanceFields(Class<?> clazz) {
        return getAllFields(clazz).stream()
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取类的所有静态字段（包括继承）
     * @param clazz 目标类
     * @return 字段列表
     */
    public static List<Field> getAllStaticFields(Class<?> clazz) {
        return getAllFields(clazz).stream()
            .filter(f -> Modifier.isStatic(f.getModifiers()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取类的所有公共字段
     * @param clazz 目标类
     * @return 字段列表
     */
    public static List<Field> getPublicFields(Class<?> clazz) {
        return getAllFields(clazz).stream()
            .filter(f -> Modifier.isPublic(f.getModifiers()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取类的所有私有字段
     * @param clazz 目标类
     * @return 字段列表
     */
    public static List<Field> getPrivateFields(Class<?> clazz) {
        return getAllFields(clazz).stream()
            .filter(f -> Modifier.isPrivate(f.getModifiers()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 按类型获取字段
     * @param clazz 目标类
     * @param fieldType 字段类型
     * @return 匹配类型的字段列表
     */
    public static List<Field> getFieldsByType(Class<?> clazz, Class<?> fieldType) {
        return getAllFields(clazz).stream()
            .filter(f -> f.getType().equals(fieldType))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 按注解获取字段
     * @param clazz 目标类
     * @param annotationClass 注解类
     * @return 带有指定注解的字段列表
     */
    public static List<Field> getFieldsByAnnotation(Class<?> clazz, Class<? extends java.lang.annotation.Annotation> annotationClass) {
        return getAllFields(clazz).stream()
            .filter(f -> f.isAnnotationPresent(annotationClass))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 按名称模式获取字段（支持正则表达式）
     * @param clazz 目标类
     * @param namePattern 名称模式（正则表达式）
     * @return 匹配模式的字段列表
     */
    public static List<Field> getFieldsByPattern(Class<?> clazz, String namePattern) {
        return getAllFields(clazz).stream()
            .filter(f -> f.getName().matches(namePattern))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取字段的类型
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return 字段类型
     */
    public static Class<?> getFieldType(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return getField(clazz, fieldName).getType();
    }
    
    /**
     * 获取字段的泛型类型
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return 泛型类型
     */
    public static java.lang.reflect.Type getFieldGenericType(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return getField(clazz, fieldName).getGenericType();
    }
    
    /**
     * 判断字段是否为final
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return 是否为final
     */
    public static boolean isFieldFinal(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return Modifier.isFinal(getField(clazz, fieldName).getModifiers());
    }
    
    /**
     * 判断字段是否为static
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return 是否为static
     */
    public static boolean isFieldStatic(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return Modifier.isStatic(getField(clazz, fieldName).getModifiers());
    }
    
    /**
     * 获取所有字段名
     * @param clazz 目标类
     * @return 字段名列表
     */
    public static List<String> getFieldNames(Class<?> clazz) {
        return getAllFields(clazz).stream()
            .map(Field::getName)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取字段信息映射（字段名 -> 字段对象）
     * @param clazz 目标类
     * @return 字段映射
     */
    public static Map<String, Field> getFieldMap(Class<?> clazz) {
        Map<String, Field> fieldMap = new HashMap<>();
        for (Field field : getAllFields(clazz)) {
            fieldMap.putIfAbsent(field.getName(), field);
        }
        return fieldMap;
    }
    
    /**
     * 获取静态字段值
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return 字段值
     */
    public static Object getStaticFieldValue(Class<?> clazz, String fieldName) {
        try {
            String cacheKey = generateFieldKey(clazz, fieldName);
            MethodHandle fieldGetter = getOrCreateFieldGetter(cacheKey, () -> {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return LOOKUP.unreflectGetter(field);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get static field getter handle", e);
                }
            });
            
            return fieldGetter.invoke();
        } catch (Throwable e) {
            throw new RuntimeException("Static field get failed", e);
        }
    }
    
    /**
     * 获取静态字段值（指定类型）
     * @param clazz 目标类
     * @param fieldName 字段名
     * @param type 字段类型
     * @return 字段值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getStaticFieldValue(Class<?> clazz, String fieldName, Class<T> type) {
        Object value = getStaticFieldValue(clazz, fieldName);
        return type.cast(value);
    }
    
    /**
     * 设置静态字段值
     * @param clazz 目标类
     * @param fieldName 字段名
     * @param value 字段值
     */
    public static void setStaticFieldValue(Class<?> clazz, String fieldName, Object value) {
        try {
            String cacheKey = generateFieldKey(clazz, fieldName);
            MethodHandle fieldSetter = getOrCreateFieldSetter(cacheKey, () -> {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return LOOKUP.unreflectSetter(field);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get static field setter handle", e);
                }
            });
            
            fieldSetter.invoke(value);
        } catch (Throwable e) {
            throw new RuntimeException("Static field set failed", e);
        }
    }
    
    /**
     * 直接使用MethodType创建方法句柄（更灵活的方式）
     * @param clazz 目标类
     * @param methodName 方法名
     * @param returnType 返回类型
     * @param parameterTypes 参数类型
     * @return MethodHandle
     */
    public static MethodHandle findVirtual(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        try {
            MethodType methodType = MethodType.methodType(returnType, parameterTypes);
            return LOOKUP.findVirtual(clazz, methodName, methodType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find virtual method", e);
        }
    }
    
    /**
     * 直接使用MethodType创建静态方法句柄
     * @param clazz 目标类
     * @param methodName 方法名
     * @param returnType 返回类型
     * @param parameterTypes 参数类型
     * @return MethodHandle
     */
    public static MethodHandle findStatic(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        try {
            MethodType methodType = MethodType.methodType(returnType, parameterTypes);
            return LOOKUP.findStatic(clazz, methodName, methodType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find static method", e);
        }
    }
    
    /**
     * 查找构造器句柄
     * @param clazz 目标类
     * @param parameterTypes 参数类型
     * @return MethodHandle
     */
    public static MethodHandle findConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            MethodType methodType = MethodType.methodType(void.class, parameterTypes);
            return LOOKUP.findConstructor(clazz, methodType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find constructor", e);
        }
    }
    
    /**
     * 复制对象字段值到另一个对象
     * @param source 源对象
     * @param target 目标对象
     * @param fieldNames 要复制的字段名
     */
    public static void copyFields(Object source, Object target, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Object value = getFieldValue(source, fieldName);
                setFieldValue(target, fieldName, value);
            } catch (Exception ignored) {
            }
        }
    }
    
    /**
     * 复制对象所有字段值到另一个对象
     * @param source 源对象
     * @param target 目标对象
     */
    public static void copyAllFields(Object source, Object target) {
        Map<String, Object> fieldValues = getAllFieldValues(source);
        setFieldValues(target, fieldValues);
    }
    
    /**
     * 判断类是否有指定的方法
     * @param clazz 目标类
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @return 是否存在该方法
     */
    public static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            findMethod(clazz, methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    /**
     * 判断类是否有指定的字段
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return 是否存在该字段
     */
    public static boolean hasField(Class<?> clazz, String fieldName) {
        try {
            findField(clazz, fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
    
    /**
     * 获取方法对象
     * @param clazz 目标类
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @return 方法对象
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return findMethod(clazz, methodName, parameterTypes);
    }
    
    /**
     * 安全获取方法对象
     * @param clazz 目标类
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @return Optional包装的方法对象
     */
    public static Optional<Method> getMethodSafe(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            return Optional.of(findMethod(clazz, methodName, parameterTypes));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }
    
    /**
     * 获取类的所有方法（不包括继承）
     * @param clazz 目标类
     * @return 方法数组
     */
    public static Method[] getDeclaredMethods(Class<?> clazz) {
        return clazz.getDeclaredMethods();
    }
    
    /**
     * 获取类的所有方法（包括继承的方法）
     * @param clazz 目标类
     * @return 方法列表
     */
    public static List<Method> getAllMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        while (clazz != null) {
            methods.addAll(Arrays.asList(clazz.getDeclaredMethods()));
            clazz = clazz.getSuperclass();
        }
        return methods;
    }
    
    /**
     * 获取类的所有公共方法
     * @param clazz 目标类
     * @return 方法列表
     */
    public static List<Method> getPublicMethods(Class<?> clazz) {
        return getAllMethods(clazz).stream()
            .filter(m -> Modifier.isPublic(m.getModifiers()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取类的所有静态方法
     * @param clazz 目标类
     * @return 方法列表
     */
    public static List<Method> getAllStaticMethods(Class<?> clazz) {
        return getAllMethods(clazz).stream()
            .filter(m -> Modifier.isStatic(m.getModifiers()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 按注解获取方法
     * @param clazz 目标类
     * @param annotationClass 注解类
     * @return 带有指定注解的方法列表
     */
    public static List<Method> getMethodsByAnnotation(Class<?> clazz, Class<? extends java.lang.annotation.Annotation> annotationClass) {
        return getAllMethods(clazz).stream()
            .filter(m -> m.isAnnotationPresent(annotationClass))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 按返回类型获取方法
     * @param clazz 目标类
     * @param returnType 返回类型
     * @return 匹配返回类型的方法列表
     */
    public static List<Method> getMethodsByReturnType(Class<?> clazz, Class<?> returnType) {
        return getAllMethods(clazz).stream()
            .filter(m -> m.getReturnType().equals(returnType))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 按名称模式获取方法（支持正则表达式）
     * @param clazz 目标类
     * @param namePattern 名称模式（正则表达式）
     * @return 匹配模式的方法列表
     */
    public static List<Method> getMethodsByPattern(Class<?> clazz, String namePattern) {
        return getAllMethods(clazz).stream()
            .filter(m -> m.getName().matches(namePattern))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取所有方法名
     * @param clazz 目标类
     * @return 方法名集合（去重）
     */
    public static Set<String> getMethodNames(Class<?> clazz) {
        return getAllMethods(clazz).stream()
            .map(Method::getName)
            .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * 获取所有构造器
     * @param clazz 目标类
     * @return 构造器数组
     */
    @SuppressWarnings("unchecked")
    public static <T> Constructor<T>[] getConstructors(Class<T> clazz) {
        return (Constructor<T>[]) clazz.getDeclaredConstructors();
    }
    
    /**
     * 获取指定参数的构造器
     * @param clazz 目标类
     * @param parameterTypes 参数类型
     * @return 构造器对象
     */
    public static <T> Constructor<T> getConstructor(Class<T> clazz, Class<?>... parameterTypes) {
        try {
            return clazz.getDeclaredConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Constructor not found", e);
        }
    }
    
    // 生成方法缓存键
    private static String generateMethodKey(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        StringBuilder key = new StringBuilder();
        key.append(clazz.getName()).append(".").append(methodName);
        if (parameterTypes != null) {
            for (Class<?> paramType : parameterTypes) {
                key.append("_").append(paramType.getName());
            }
        }
        return key.toString();
    }
    
    // 生成构造器缓存键
    private static String generateConstructorKey(Class<?> clazz, Class<?>[] parameterTypes) {
        StringBuilder key = new StringBuilder();
        key.append(clazz.getName()).append(".<init>");
        if (parameterTypes != null) {
            for (Class<?> paramType : parameterTypes) {
                key.append("_").append(paramType.getName());
            }
        }
        return key.toString();
    }
    
    // 生成字段缓存键
    private static String generateFieldKey(Class<?> clazz, String fieldName) {
        return clazz.getName() + "." + fieldName;
    }
    
    // 在参数数组前添加target对象
    private static Object[] prependTarget(Object target, Object[] args) {
        if (args == null || args.length == 0) {
            return new Object[]{target};
        }
        
        Object[] newArgs = new Object[args.length + 1];
        newArgs[0] = target;
        System.arraycopy(args, 0, newArgs, 1, args.length);
        return newArgs;
    }
    
    // 从参数中推断参数类型
    private static Class<?>[] getParameterTypes(Object... args) {
        if (args == null || args.length == 0) {
            return new Class<?>[0];
        }
        
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] != null ? args[i].getClass() : Object.class;
        }
        return types;
    }
    
    // 带统计的缓存获取方法
    private static MethodHandle getOrCreateMethodHandle(String key, HandleSupplier supplier) {
        MethodHandle handle = METHOD_HANDLE_CACHE.get(key);
        if (handle != null) {
            if (enableStatistics) cacheHitCount.incrementAndGet();
            return handle;
        }
        if (enableStatistics) cacheMissCount.incrementAndGet();
        return METHOD_HANDLE_CACHE.computeIfAbsent(key, k -> supplier.get());
    }
    
    private static MethodHandle getOrCreateConstructorHandle(String key, HandleSupplier supplier) {
        MethodHandle handle = CONSTRUCTOR_HANDLE_CACHE.get(key);
        if (handle != null) {
            if (enableStatistics) cacheHitCount.incrementAndGet();
            return handle;
        }
        if (enableStatistics) cacheMissCount.incrementAndGet();
        return CONSTRUCTOR_HANDLE_CACHE.computeIfAbsent(key, k -> supplier.get());
    }
    
    private static MethodHandle getOrCreateFieldGetter(String key, HandleSupplier supplier) {
        MethodHandle handle = FIELD_GETTER_CACHE.get(key);
        if (handle != null) {
            if (enableStatistics) cacheHitCount.incrementAndGet();
            return handle;
        }
        if (enableStatistics) cacheMissCount.incrementAndGet();
        return FIELD_GETTER_CACHE.computeIfAbsent(key, k -> supplier.get());
    }
    
    private static MethodHandle getOrCreateFieldSetter(String key, HandleSupplier supplier) {
        MethodHandle handle = FIELD_SETTER_CACHE.get(key);
        if (handle != null) {
            if (enableStatistics) cacheHitCount.incrementAndGet();
            return handle;
        }
        if (enableStatistics) cacheMissCount.incrementAndGet();
        return FIELD_SETTER_CACHE.computeIfAbsent(key, k -> supplier.get());
    }

    public static void clearCache() {
        METHOD_HANDLE_CACHE.clear();
        CONSTRUCTOR_HANDLE_CACHE.clear();
        FIELD_GETTER_CACHE.clear();
        FIELD_SETTER_CACHE.clear();
    }
    
    /**
     * 函数式接口用于延迟创建MethodHandle
     */
    @FunctionalInterface
    private interface HandleSupplier {
        MethodHandle get();
    }
}