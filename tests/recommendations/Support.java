package io.freemytv;
import java.util.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
// Peers used only to load the real recommendation cache/mixing code on a desktop JVM.
final class Config {
    static final String TMDB_KEY="", WATCHMODE_KEY="", HIDE_SERVICES="";
    static final boolean HIDE_ANIMATION=false;
}
final class Place { static String region(){return "IN";} }
final class Backdrop {
    static final int W=1920,H=1080;
    static Backdrop make(Bitmap b,boolean light){return null;}
    static Backdrop make(Bitmap b,boolean light,boolean own,boolean tall){return null;}
    boolean backgroundReady(){return true;}
    void prepareBackground(){}
    void releaseBackground(){}
}
final class HomeApps {
    static Intent launchIntent(PackageManager pm,String pkg){return null;}
    static List<String> shelfPackages(Context c){return Collections.emptyList();}
}
final class Prefs { static String homeDesign(){return "vibe";} }
final class AppLog { static void w(String text){} }
final class Ui {
    static String language="en";
    static String tr(String text){return text;}
    static String trf(String text,Object... args){return text;}
    static String plural(String text,int n){return text;}
    static Locale locale(){return Locale.US;}
}
