package cl.usach.developen.demorfid.demorfid;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.Collection;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void doActions(@SuppressWarnings("UnusedParameters") View view) {
    /*    Collection<UgiUiUtil.MenuTitleAndHandler> items = new ArrayList<>();
        items.add(new UgiUiUtil.MenuTitleAndHandler("audio reconfiguration", () -> Ugi.getSingleton().invokeAudioReconfiguration()));
        items.add(new UgiUiUtil.MenuTitleAndHandler("set audio jack location", () -> Ugi.getSingleton().invokeAudioJackLocation()));
        items.add(new UgiUiUtil.MenuTitleAndHandler("example: second page", () -> this.startActivityWithTransition(SecondPageActivity.class)));

        if (Ugi.getSingleton().getActiveInventory() == null) {
            items.add(new UgiUiUtil.MenuTitleAndHandler("example: find one tag", () -> this.startActivityWithTransition(FindOneTagActivity.class, null, result -> {
                if (result != null) {
                    UgiEpc epc = (UgiEpc) result;
                    this.handleTagFound(epc);
                }
            })));
        }
        UgiUiUtil.showMenu(this, null, null, items.toArray(new UgiUiUtil.MenuTitleAndHandler[items.size()]));
        */
    }
}
