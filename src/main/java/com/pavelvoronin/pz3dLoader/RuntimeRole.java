package com.pavelvoronin.pz3dLoader;

/** Native process roles are independent of the active multiplayer mod list. */
final class RuntimeRole {
    static boolean serverCommand(){return System.getProperty("sun.java.command","").split("\\s+",2)[0].equals("zombie.network.GameServer");}
    private static boolean flag(String owner,String field){
        try{return Class.forName(owner,false,RuntimeRole.class.getClassLoader()).getField(field).getBoolean(null);}
        catch(ReflectiveOperationException missing){return false;}
    }
    static boolean server(){return serverCommand()||flag("zombie.network.GameServer","server");}
    static boolean client(){return flag("zombie.network.GameClient","client");}
    static boolean optionalServer(boolean server){return server&&Boolean.getBoolean("pz3d.server.optimizations");}
    static boolean retainLocal(boolean server,boolean client,boolean local){return !server&&client&&local;}
    static String entry(boolean server){return server?"com.pavelvoronin.pz3d.ServerMain":"com.pavelvoronin.pz3d.Main";}
    static String patches(boolean server){return server?"com.pavelvoronin.pz3dserver.ServerPatches":"com.pavelvoronin.pz3d.Patches";}
    private RuntimeRole(){}
}