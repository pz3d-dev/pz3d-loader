package com.pavelvoronin.pz3dLoader;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Offline layout preview, not a game screenshot. Uses the production Panel geometry. */
public final class PanelPreview {
    public static void main(String[] args) throws Exception {
        BufferedImage image=new BufferedImage(900,420,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setColor(new Color(22,29,32));g.fillRect(0,0,900,420);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Segoe UI",Font.PLAIN,16));
        Panel.Canvas canvas=new Panel.Canvas() {
            public int width(String text){return g.getFontMetrics().stringWidth(text);}
            public void box(int x,int y,int w,int h,float r,float green,float b,float a){g.setColor(new Color(r,green,b,a));g.fillRect(x,y,w,h);}
            public void text(int x,int y,String text,float r,float green,float b){g.setColor(new Color(r,green,b));g.drawString(text,x,y+g.getFontMetrics().getAscent());}
        };
        int line=22;
        Panel.draw(canvas,900,16,line,"pz3d loaded","READY",false);
        canvas.text(10,115,"WITH ZOMBIEBUDDY",0.62f,0.67f,0.69f);
        // A labeled placeholder reserves the real loader's footprint; it is not a replacement UI.
        canvas.box(8,150,64,64,0.17f,0.23f,0.22f,1);
        canvas.text(80,150,"ZombieBuddy 2.3.2 loaded",0.5f,1,0.5f);
        canvas.text(80,172,"2 active Java mods: othermod, pz3d",0.5f,1,0.5f);
        int bottom=Panel.buddyBottom(900,line,64,0,false,List.of("othermod","pz3d"),canvas::width);
        Panel.draw(canvas,900,150+bottom+8,line,"pz3d found - waiting for the game","DETECTED",false);
        Panel.draw(canvas,900,325,line,"Couldn't load pz3d - check console.txt for details","FAILED",false);
        g.dispose();ImageIO.write(image,"png",Path.of(args[0]).toFile());
    }
}
