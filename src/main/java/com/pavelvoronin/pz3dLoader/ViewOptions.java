package com.pavelvoronin.pz3dLoader;

import java.util.*;

/** Register public native options before PZ reads/saves its INI, without starting the engine. */
public final class ViewOptions {
    @SuppressWarnings("unchecked")
    public static void register(Object owner) throws ReflectiveOperationException {
        ClassLoader loader=owner.getClass().getClassLoader();
        Class<?> options=Class.forName("zombie.network.ServerOptions",false,loader);
        var lookup=options.getMethod("getOptionByName",String.class);
        var constructor=Class.forName("zombie.network.ServerOptions$BooleanServerOption",false,loader)
            .getConstructor(options,String.class,boolean.class);
        var published=(List<String>)options.getMethod("getPublicOptions").invoke(owner);
        for(String name:List.of("pz3d.AllowFirstPerson","pz3d.AllowThirdPerson","pz3d.AllowTopDown")){
            if(lookup.invoke(owner,name)==null)constructor.newInstance(owner,name,true);
            if(!published.contains(name))published.add(name);
        }
        Collections.sort(published);
    }
    private ViewOptions(){}
}
