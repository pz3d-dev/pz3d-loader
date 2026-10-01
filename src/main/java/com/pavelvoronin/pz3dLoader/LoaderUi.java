package com.pavelvoronin.pz3dLoader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.util.*;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import static net.bytebuddy.matcher.ElementMatchers.*;

public final class LoaderUi {
    private static NativeCanvas canvas;
    private static boolean disabled;
    private static long nextMeasure;
    private static int buddyBottom;
    private static boolean right;
    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default().disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
            .type(named("zombie.core.Core"))
            .transform((b,t,l,m,d)->b.visit(Advice.to(Frame.class).on(named("EndFrameUI").and(takesArguments(0)))))
            .type(named("zombie.core.Translator"))
            .transform((b,t,l,m,d)->b.visit(Advice.to(Language.class).on(named("loadFiles").and(takesArguments(0)))))
            .installOn(instrumentation);
    }
    public static class Frame {
        @Advice.OnMethodEnter public static void enter(){LoaderUi.draw();}
    }
    public static class Language {
        @Advice.OnMethodExit public static void exit(){
            try{Translations.load();}catch(Exception error){System.err.println("[pz3d Loader] Translation resources: "+error);}
        }
    }
    public static void draw() {
        if(disabled)return;
        try {
            GameCompatibility.check();
            if(canvas==null)canvas=new NativeCanvas();
            if((boolean)canvas.ingame.invoke(null))return;
            Object core=canvas.coreInstance.invoke(null);
            if(canvas.frame!=null && !(boolean)canvas.frame.get(core))return;
            int width=(int)canvas.screenWidth.invoke(core);
            int height=(int)canvas.lineHeight.invoke(canvas.manager,canvas.font,"Pz3dLoader");
            if(height<=0)return;
            long now=System.nanoTime();
            if(now>=nextMeasure){measureBuddy(width,height);nextMeasure=now+250_000_000L;}
            String state=Agent.status;
            if(com.pavelvoronin.pz3dLoader.bootstrap.Main.state().equals("FAILED"))state="FAILED";
            String key="UI_Pz3dLoader_"+state;
            String message=canvas.translation(key);
            // Native catalogs may have been rebuilt before our hook was installed.
            if(message.equals(key)) { Translations.load(); message=canvas.translation(key); }
            if(!Agent.modVersion.isEmpty()) message="pz3d "+Agent.modVersion+" - "+message;
            if(!SelfUpdater.pendingVersion.isEmpty())message+=" | "+canvas.translation("UI_Pz3dLoader_UPDATE");
            Panel.draw(canvas,width,Math.max(8,buddyBottom+8),height,message,state,right);
        } catch(Throwable error) {
            disabled=true;System.err.println("[pz3d Loader] Indicator unavailable: "+error);
        }
    }
    private static void measureBuddy(int width,int lineHeight) throws Exception {
        buddyBottom=0;right=false;
        Class<?> loader;
        try{loader=Class.forName("me.zed_0xff.zombie_buddy.Loader",false,ClassLoader.getSystemClassLoader());}
        catch(ClassNotFoundException absent){return;}
        try {
            Class<?> watermark=Class.forName("me.zed_0xff.zombie_buddy.Watermark");
            Field mid=watermark.getDeclaredField("_midLine");mid.setAccessible(true);
            Field alpha=watermark.getDeclaredField("_alpha");alpha.setAccessible(true);
            if(alpha.getFloat(null)<=0)return;
            Method activeMods=loader.getDeclaredMethod("getActiveJavaMods");activeMods.setAccessible(true);
            List<?> states=(List<?>)activeMods.invoke(null);
            List<String> names=new ArrayList<>();
            for(Object state:states) {
                Method idMethod=state.getClass().getDeclaredMethod("id");idMethod.setAccessible(true);
                Method flagsMethod=state.getClass().getDeclaredMethod("flags");flagsMethod.setAccessible(true);
                String id=String.valueOf(idMethod.invoke(state));
                Object flags=flagsMethod.invoke(state);
                boolean preload=(boolean)flags.getClass().getMethod("has",int.class).invoke(flags,32);
                names.add(id+(preload?" (preload)":""));
            }
            Collections.sort(names);
            String middle=(String)mid.get(null);
            buddyBottom=Panel.buddyBottom(width,lineHeight,width>2000?128:64,System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("mac")?16:0,middle!=null&&!middle.isBlank(),names,canvas::width);
        } catch(ReflectiveOperationException unsupported) {
            // Unknown ZombieBuddy layout: use the other corner rather than cover its UI.
            right=true;
        }
    }
    private static final class NativeCanvas implements Panel.Canvas {
        final Object manager,font;
        final Method measure,draw,render,translate,ingame,coreInstance,screenWidth,lineHeight;
        final Field sprite,frame;
        NativeCanvas() throws Exception {
            Class<?> tm=Class.forName("zombie.ui.TextManager"),fonts=Class.forName("zombie.ui.UIFont");
            manager=tm.getField("instance").get(null);font=fonts.getField("Small").get(null);
            measure=tm.getMethod("MeasureStringX",fonts,String.class);lineHeight=tm.getMethod("MeasureStringY",fonts,String.class);
            draw=tm.getMethod("DrawString",fonts,double.class,double.class,String.class,double.class,double.class,double.class,double.class);
            translate=Class.forName("zombie.core.Translator").getMethod("getText",String.class,Object[].class);
            ingame=Class.forName("zombie.GameWindow").getMethod("isIngameState");
            Class<?> core=Class.forName("zombie.core.Core");coreInstance=core.getMethod("getInstance");screenWidth=core.getMethod("getScreenWidth");
            Field field=null;
            for(String name:new String[]{"uiRenderThisFrame","UIRenderThisFrame"})try{field=core.getDeclaredField(name);field.setAccessible(true);break;}catch(NoSuchFieldException ignored){}
            frame=field;
            Class<?> renderer=Class.forName("zombie.core.SpriteRenderer");sprite=renderer.getField("instance");
            render=Arrays.stream(renderer.getMethods()).filter(m->m.getName().equals("renderi")&&m.getParameterCount()==10).findFirst().orElseThrow();
        }
        public int width(String text){try{return (int)measure.invoke(manager,font,text);}catch(Exception e){throw new IllegalStateException(e);}}
        String translation(String key) throws Exception {return (String)translate.invoke(null,key,new Object[0]);}
        public void text(int x,int y,String text,float r,float g,float b){try{draw.invoke(manager,font,(double)x,(double)y,text,(double)r,(double)g,(double)b,1d);}catch(Exception e){throw new IllegalStateException(e);}}
        public void box(int x,int y,int w,int h,float r,float g,float b,float a){try{render.invoke(sprite.get(null),null,x,y,w,h,r,g,b,a,null);}catch(Exception e){throw new IllegalStateException(e);}}
    }
}
