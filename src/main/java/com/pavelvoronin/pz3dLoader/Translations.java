package com.pavelvoronin.pz3dLoader;

import java.nio.file.*;
import java.util.Map;
import java.util.function.*;

/** Feed ordinary UI.json resources into PZ's parser and native language/fallback stack. */
public final class Translations {
    private static Path root;
    public static void load() throws Exception {
        if(root==null) {
            root=Files.createTempDirectory("pz3dLoader-translations-");
            for(String language:new String[]{"EN"}) {
                String resource="media/lua/shared/Translate/"+language+"/UI.json";
                Path output=root.resolve(resource);Files.createDirectories(output.getParent());
                try(var stream=Translations.class.getResourceAsStream("/pz3dLoader/"+resource)){Files.copy(stream,output);}
            }
        }
        Class<?> translator=Class.forName("zombie.core.Translator");
        Class<?> language=Class.forName("zombie.core.Language");
        var read=translator.getDeclaredMethod("tryFillMapFromFile",String.class,String.class,Map.class,language,Function.class);
        read.setAccessible(true);
        Map<?,?> catalogs=(Map<?,?>)translator.getField("BY_NAME").get(null);
        Object ui=catalogs.get("UI");
        if(ui==null)throw new IllegalStateException("Native UI translation catalog missing");
        Consumer<Object> load=selected->{
            try {read.invoke(null,root.toString(),"UI",ui,selected,Function.identity());}
            catch(Exception error){throw new IllegalStateException("Native translation parsing failed",error);}
        };
        translator.getMethod("forLanguageStack",Consumer.class).invoke(null,load);
    }
}
