package cl.usach.developen.demorfid.demorfid;

import android.app.Application;

import com.ugrokit.api.*;

public class DemoRFIDApplication extends Application {
    private Ugi ugi;

    public Ugi getUgi() { return ugi; }

    @Override public void onCreate() {
        super.onCreate();
        ugi = Ugi.createSingleton(this);
        ugi.openConnection();
    }
}
