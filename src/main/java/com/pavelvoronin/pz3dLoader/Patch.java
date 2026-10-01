package com.pavelvoronin.pz3dLoader;

import java.lang.annotation.*;

/** Metadata adapter for PZ3D's shared ZombieBuddy patch declarations. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Patch {
    String className();
    String methodName();
    boolean strictMatch() default false;
    boolean warmUp() default false;
    boolean isAdvice() default true;
}
