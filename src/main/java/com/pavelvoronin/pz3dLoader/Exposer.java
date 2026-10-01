package com.pavelvoronin.pz3dLoader;
import java.lang.annotation.*;
public final class Exposer {
    private Exposer() {}
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface LuaClass { String name() default ""; }
}
