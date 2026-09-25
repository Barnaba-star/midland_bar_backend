package com.midland.bar.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class Util {

    public static <T> T entity(Class<T> tClass, String uid) {
        try {
            Constructor<T> constructor = tClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = constructor.newInstance();

            Field field = null;
            Class<?> current = tClass;
            while (current != null) {
                try {
                    field = current.getDeclaredField("uid");
                    break;
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }

            if (field == null) {
                throw new NoSuchFieldException("Field 'uid' not found in class hierarchy.");
            }

            field.setAccessible(true);
            field.set(instance, uid);

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create entity instance", e);
        }
    }

}
