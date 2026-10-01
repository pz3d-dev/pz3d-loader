package com.pavelvoronin.pz3dLoader;

import java.util.*;
import java.util.function.ToIntFunction;

/** One layout for the native renderer and the visual test artifact. */
public final class Panel {
    public interface Canvas {
        int width(String text);
        void box(int x,int y,int w,int h,float r,float g,float b,float a);
        void text(int x,int y,String value,float r,float g,float b);
    }

    public static int buddyBottom(int screenWidth,int lineHeight,int iconSize,int offset,boolean midLine,List<String> mods,ToIntFunction<String> measure) {
        int lines=1;boolean hasMod=false;
        String line=(mods.isEmpty()?"No":mods.size())+" active Java mods: ";
        int maxWidth=Math.max(80,screenWidth-iconSize-8);
        for(String mod:mods) {
            String text=(hasMod?", ":"")+mod;
            if(hasMod && measure.applyAsInt(line+text)>maxWidth){lines++;line="";text=mod;}
            line+=text;hasMod=true;
        }
        return Math.max(iconSize,offset+(1+(midLine?1:0)+lines)*lineHeight);
    }

    public static void draw(Canvas c,int screenWidth,int y,int lineHeight,String message,String state,boolean right) {
        int padding=Math.max(8,lineHeight/2);
        String title="pz3d Loader";
        String version=" "+Version.CURRENT;
        if(!SelfUpdater.pendingVersion.isEmpty())version+=" -> "+SelfUpdater.pendingVersion;
        int available=Math.max(40,screenWidth-2*padding-16);
        List<String> lines=wrap(message,available,c::width);
        int textWidth=c.width(title)+c.width(version);
        for(String line:lines)textWidth=Math.max(textWidth,c.width(line));
        int width=Math.min(screenWidth-16,2*padding+textWidth);
        int height=(lines.size()+1)*lineHeight+2*padding;
        int x=right?Math.max(8,screenWidth-width-8):8;
        boolean error=state.equals("FAILED") || state.equals("UNSUPPORTED") || state.equals("INCOMPATIBLE"), ready=state.equals("READY");
        float r=error?0.85f:0.42f,g=error?0.45f:ready?0.68f:0.62f,b=error?0.40f:ready?0.48f:0.74f;
        c.box(x,y,width,height,0.025f,0.035f,0.04f,0.58f);
        c.box(x,y,2,height,r,g,b,0.55f);
        int top=y+padding;
        int tx=x+padding;
        c.text(tx,top,title,0.76f,0.79f,0.78f);
        c.text(tx+c.width(title),top,version,0.43f,0.61f,0.70f);
        for(int i=0;i<lines.size();i++)c.text(tx,top+(i+1)*lineHeight,lines.get(i),r,g,b);
    }

    public static List<String> wrap(String text,int width,ToIntFunction<String> measure) {
        List<String> result=new ArrayList<>();String line="";
        for(String word:text.split(" ")) {
            String next=line.isEmpty()?word:line+" "+word;
            if(!line.isEmpty() && measure.applyAsInt(next)>width){result.add(line);line=word;}else line=next;
        }
        result.add(line);return result;
    }
}
