package cl.usach.developen.demorfid.demorfid;

import android.app.Application;

import com.ugrokit.api.*;

import cl.usach.developen.demorfid.demorfid.models.MyObjectBox;
import io.objectbox.BoxStore;
import io.objectbox.android.AndroidObjectBrowser;

public class DemoRFIDApplication extends Application {
    private static DemoRFIDApplication sApp;
    private BoxStore mBoxStore;
    private Ugi ugi;

    public Ugi getUgi() { return ugi; }

    public static DemoRFIDApplication getApp() {
        return sApp;
    }

    public BoxStore getBoxStore() {
        return mBoxStore;
    }

    @Override public void onCreate() {
        super.onCreate();
        sApp = this;

        ugi = Ugi.createSingleton(this);
        ugi.openConnection();

        mBoxStore = MyObjectBox.builder().androidContext(DemoRFIDApplication.this).build();
        new AndroidObjectBrowser(mBoxStore).start(this);
    }
}
