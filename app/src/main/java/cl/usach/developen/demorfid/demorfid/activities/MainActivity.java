package cl.usach.developen.demorfid.demorfid.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.ugrokit.api.Ugi;
import com.ugrokit.api.UgiEpc;
import com.ugrokit.api.UgiFooterView;
import com.ugrokit.api.UgiInventory;
import com.ugrokit.api.UgiInventoryDelegate;
import com.ugrokit.api.UgiRfMicron;
import com.ugrokit.api.UgiRfidConfiguration;
import com.ugrokit.api.UgiTag;
import com.ugrokit.api.UgiTagCell;
import com.ugrokit.api.UgiUiActivity;
import com.ugrokit.api.UgiUiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cl.usach.developen.demorfid.demorfid.DemoRFIDApplication;
import cl.usach.developen.demorfid.demorfid.R;
import cl.usach.developen.demorfid.demorfid.models.Tag;
import cl.usach.developen.demorfid.demorfid.models.Tag_;
import io.objectbox.Box;
import io.objectbox.BoxStore;

public class MainActivity extends UgiUiActivity implements
        UgiInventoryDelegate,
        UgiInventoryDelegate.InventoryHistoryIntervalListener,
        UgiInventoryDelegate.InventoryDidStopListener,
        UgiInventoryDelegate.InventoryTagFoundListener,
        UgiInventoryDelegate.InventoryTagSubsequentFindsListener{

    private static final int SPECIAL_FUNCTION_NONE = 0;
    private static final int SPECIAL_FUNCTION_READ_USER_MEMORY = 1;
    private static final int SPECIAL_FUNCTION_READ_TID_MEMORY = 2;
    private static final int SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_SENSOR_CODE = 3;
    private static final int SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_TEMPERATURE = 4;

    private int specialFunction = SPECIAL_FUNCTION_NONE;

    private static final UgiRfMicron.MagnusModels RF_MICRON_MAGNUS_MODEL = UgiRfMicron.MagnusModels.Model402;
    private static final UgiRfMicron.RssiLimitTypes RF_MICRON_MAGNUS_LIMIT_TYPE = UgiRfMicron.RssiLimitTypes.LessThanOrEqual;
    private static final int RF_MICRON_MAGNUS_LIMIT_THRESHOLD = 31;

    private UgiRfidConfiguration.InventoryTypes inventoryType;
    private OurListAdapter listAdapter;

    private final List<UgiTag> displayedTags = new ArrayList<>();
    private final Map<UgiTag, StringBuilder> detailedData = new HashMap<>();

    private Handler updateTimerHandler = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.setDisplayDialogIfDisconnected(true);
        this.configureTitleViewNavigation();
        getTitleView().setBatteryStatusIndicatorDisplayVersionInfoOnTouch(true);
        getTitleView().setUseBackgroundBasedOnUiColor(true);
        getTitleView().setDisplayWaveAnimationWhileScanning(true);

        inventoryType = UgiRfidConfiguration.InventoryTypes.LOCATE_DISTANCE;
        ListView tagListView = findViewById(R.id.tagList);
        listAdapter = new OurListAdapter();
        tagListView.setAdapter(listAdapter);
        tagListView.setOnItemClickListener((parent, view, position, id) -> {
            final UgiInventory inventory = Ugi.getSingleton().getActiveInventory();
            final UgiTag tag = displayedTags.get(position);
            if ((inventory != null)) {
                if (!inventory.isPaused()) inventory.pauseInventory();
                UgiUiUtil.showMenu(this, null,
                        inventory::resumeInventory,
                        new UgiUiUtil.MenuTitleAndHandler("leer memoria de usuario", () -> this.doReadUserMemory(tag)),
                        new UgiUiUtil.MenuTitleAndHandler("escribir memoria de usuario", () -> this.doWriteUserMemory(tag)),
                        new UgiUiUtil.MenuTitleAndHandler("buscar solo este tag", () -> this.doLocate(tag))
                );
            } else {
                String message = "Touch a tag while scanning (or paused) to act on the tag";
                UgiUiUtil.showOk(this, "not scanning", message);
            }
        });

        updateUI();
    }

    private void updateUI() {
        final UgiInventory inventory = Ugi.getSingleton().getActiveInventory();
        //findViewById(R.id.actions_button).setEnabled(inventory == null);

        UgiFooterView footer = getFooterView();
        if (inventory != null) {
            if (inventory.isPaused()) {
                footer.setLeft(getString(R.string.FooterResume), () -> {
                    inventory.resumeInventory();
                    this.updateUI();
                });
            } else {
                footer.setLeft(getString(R.string.FooterPause), () -> {
                    inventory.pauseInventory();
                    this.updateUI();
                });
            }
            footer.setCenter(getString(R.string.FooterStop), this::stopScanning);
            footer.setRight(null, null);
        } else {
            footer.setCenter(getString(R.string.FooterStart), this::startScanning);
        }
    }

    private void startScanning() {
        displayedTags.clear();
        detailedData.clear();
        updateTable();

        UgiRfidConfiguration config;
        switch (specialFunction) {
            case SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_SENSOR_CODE:
                config = UgiRfMicron.configToReadMagnusSensorValue(
                        UgiRfidConfiguration.forInventoryType(UgiRfidConfiguration.InventoryTypes.LOCATE_DISTANCE),
                        RF_MICRON_MAGNUS_MODEL,
                        RF_MICRON_MAGNUS_LIMIT_TYPE,
                        RF_MICRON_MAGNUS_LIMIT_THRESHOLD);
                break;
            case SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_TEMPERATURE:
                config = UgiRfMicron.configToReadMagnusTemperature(UgiRfidConfiguration.forInventoryType(UgiRfidConfiguration.InventoryTypes.LOCATE_DISTANCE));
                break;
            default:
                config = UgiRfidConfiguration.forInventoryType(inventoryType);
                if (specialFunction == SPECIAL_FUNCTION_READ_USER_MEMORY) {
                    config.minUserBytes = 4;
                    config.maxUserBytes = 128;
                } else if (specialFunction == SPECIAL_FUNCTION_READ_TID_MEMORY) {
                    config.minTidBytes = 4;
                    config.maxTidBytes = 128;
                }
                break;
        }
        Ugi.getSingleton().startInventory(this, config);
        updateUI();
        updateCountAndTime();
        if (!config.reportSubsequentFinds) {
            updateTimerHandler = new Handler();
            updateTimerHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (updateTimerHandler != null) {
                        updateCountAndTime();
                        updateTimerHandler.postDelayed(this, 1000);
                    }
                }
            }, 1000); // 1 second delay (takes millis)
        }
    }

    private void stopScanning() {
        updateTimerHandler = null;
        UgiUiUtil.stopInventoryWithCompletionShowWaiting(this, this::updateUI);
    }

    private void updateTable() {
        listAdapter.notifyDataSetChanged();
        updateCountAndTime();
    }

    @Override public void inventoryDidStop(int result) {
        if ((result != UGI_INVENTORY_COMPLETED_LOST_CONNECTION) && (result != UGI_INVENTORY_COMPLETED_OK)) {
            //
            // Inventory error
            //
            UgiUiUtil.showInventoryError(this, result);
        }
        updateTimerHandler = null;
        updateUI();
    }

    @Override public void inventoryHistoryInterval() {
        updateTable();
    }

    @Override public void inventoryTagFound(UgiTag tag, UgiInventory.DetailedPerReadData[] detailedPerReadData) {
        displayedTags.add(tag);
        detailedData.put(tag, new StringBuilder());
        handlePerReads(tag, detailedPerReadData);
        updateTable();
    }

    @Override public void inventoryTagSubsequentFinds(UgiTag tag, int count,
                                                      UgiInventory.DetailedPerReadData[] detailedPerReadData) {
        handlePerReads(tag, detailedPerReadData);
    }

    private void handlePerReads(UgiTag tag,
                                UgiInventory.DetailedPerReadData[] detailedPerReadData) {
        if (specialFunction == SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_SENSOR_CODE) {
            for (UgiInventory.DetailedPerReadData p : detailedPerReadData) {
                //
                // get sensor code and add it to the string we display
                //
                int sensorCode = UgiRfMicron.getMagnusSensorCode(p);
                StringBuilder s = detailedData.get(tag);
                if (s.length() > 0) s.append(" ");
                s.append(sensorCode);
                if (RF_MICRON_MAGNUS_LIMIT_TYPE != UgiRfMicron.RssiLimitTypes.None) {
                    //
                    // get on-chip RSSI and add it to the string we display
                    //
                    int onChipRssi = UgiRfMicron.getMagnusOnChipRssi(p);
                    s.append("/");
                    s.append(onChipRssi);
                }
            }
        } else if (specialFunction == SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_TEMPERATURE) {
            for (UgiInventory.DetailedPerReadData p : detailedPerReadData) {
                //
                // Get the temperature and add it to string we display
                //
                double temperatureC = UgiRfMicron.getMagnusTemperature(tag, p);
                StringBuilder s = detailedData.get(tag);
                if (s.length() > 0) s.append(" ");
                s.append(temperatureC);
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateCountAndTime() {
        TextView tv = findViewById(R.id.count_value);
        tv.setText(Integer.toString(displayedTags.size()));

        UgiInventory inventory = Ugi.getSingleton().getActiveInventory();
        if (inventory != null) {
            int seconds = (int) ((System.currentTimeMillis() - inventory.getStartTime().getTime()) / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            tv = findViewById(R.id.time_value);
            tv.setText(String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds));
        }
    }

    private class OurListAdapter extends BaseAdapter {

        @Override public boolean hasStableIds() {
            return true;
        }

        @Override public int getCount() {
            return displayedTags.size();
        }

        @Override public Object getItem(int position) {
            return position < displayedTags.size() ? displayedTags.get(position) : null;
        }

        @Override public long getItemId(int position) {
            return position < displayedTags.size() ? displayedTags.get(position).getEpc().hashCode() : 0;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            UgiTagCell listItem = convertView != null ? (UgiTagCell)convertView : new UgiTagCell(getContext(), null);
            if (position < displayedTags.size()) {
                UgiTag tag = displayedTags.get(position);
                listItem.setDisplayTag(tag);
                listItem.setThemeColor(getThemeColor());
                listItem.setTitle(tag.getEpc().toString());
                String detailText = null;
                switch (specialFunction) {
                    case SPECIAL_FUNCTION_READ_USER_MEMORY:
                        detailText = "user: " + byteArrayToString(tag.getUserBytes());
                        break;
                    case SPECIAL_FUNCTION_READ_TID_MEMORY:
                        detailText = "tid: " + byteArrayToString(tag.getTidBytes());
                        break;
                    default:
                        StringBuilder s = detailedData.get(tag);
                        if (s.length() > 0) {
                            detailText = s.toString();
                        }
                        break;
                }
                listItem.setDetail(detailText);
                listItem.updateHistoryView();
            }
            return listItem;
        }
    }

    // Metodos relacionados al Tag
    private void doReadUserMemory(UgiTag tag) {
        Ugi.getSingleton().getActiveInventory().resumeInventory();
        updateUI();
        UgiUiUtil.showWaiting(this, "Leyendo información asociada");
        System.out.println("doReadUserMemory");

        BoxStore boxStore = DemoRFIDApplication.getApp().getBoxStore();
        Box<Tag> tagBox = boxStore.boxFor(Tag.class);
        Tag tagObject = tagBox.query()
                            .equal(Tag_.EPC, tag.getEpc().toString())
                            .build().findUnique();
        String contentTag = null != tagObject ? tagObject.getContenido() : "";

        UgiUiUtil.hideWaiting();
        UgiUiUtil.showOk(this, "Información Asociada",
                "Contenido: " + contentTag,
                "", null);

        /*Ugi.getSingleton().getActiveInventory().readTag(
                tag.getEpc(),
                UgiRfidConfiguration.MemoryBank.User,
                0, 16, 64,
                UgiInventory.NO_PASSWORD,
                (tag1, data, result) -> {
                    System.out.println("doReadUserMemory CALLBACK: " + result);
                    UgiUiUtil.hideWaiting();
                    if (result == UgiInventory.TagAccessReturnValues.OK) {
                        UgiUiUtil.showOk(this, "read user memory",
                                "Read " + data.length + " bytes: " + byteArrayToString(data),
                                "", null);
                    } else {
                        UgiUiUtil.showOk(this, "read user memory",
                                "Error: " + UgiUiUtil.getTagAccessErrorMessage(result));
                    }
                }
        );*/
    }

    private void doWriteUserMemory(UgiTag tag) {

        UgiUiUtil.showTextInput(
                this,
                "escribir tag", "Información:", "escribir", "",
                UgiUiUtil.DEFAULT_INPUT_TYPE,
                null, false,
                (value, switchValue) -> {
                    //UgiEpc newEpc = new UgiEpc(value);
                    Ugi.getSingleton().getActiveInventory().resumeInventory();
                    this.updateUI();
                    UgiUiUtil.showWaiting(this, "guardando");
                    BoxStore boxStore = DemoRFIDApplication.getApp().getBoxStore();
                    Box<Tag> tagBox = boxStore.boxFor(Tag.class);
                    Tag tagObject = tagBox.query()
                            .equal(Tag_.EPC, tag.getEpc().toString())
                            .build().findUnique();

                    if (null == tagObject) {
                        tagObject = new Tag();
                    }

                    tagObject.setEPC(tag.getEpc().toString());
                    tagObject.setContenido(value);
                    tagBox.put(tagObject);
                    UgiUiUtil.hideWaiting();

                    /*final byte[] newData = value.getBytes();
                    Ugi.getSingleton().getActiveInventory().writeTag(tag.getEpc(),
                            UgiRfidConfiguration.MemoryBank.User, 0, newData, null,
                            UgiInventory.NO_PASSWORD, (tag1, result) -> {
                                UgiUiUtil.hideWaiting();
                                if (result == UgiInventory.TagAccessReturnValues.OK) {
                                    UgiUiUtil.showOk(this, "escribir memoria de usuario",
                                            "Escritos " + newData.length + " bytes: " + byteArrayToString(newData),
                                            "", null);
                                } else {
                                    UgiUiUtil.showOk(this, "escribir memoria de usuario",
                                            "Error al escribir tag: " + UgiUiUtil.getTagAccessErrorMessage(result));
                                }
                            });*/
                }, () -> {
                    Ugi.getSingleton().getActiveInventory().resumeInventory();
                    this.updateUI();
                }, value -> (value.length() > 0));
    }

    private void doLocate(final UgiTag tag) {
        updateTimerHandler = null;
        UgiUiUtil.stopInventoryWithCompletionShowWaiting(this, () -> {
            displayedTags.clear();
            detailedData.clear();
            this.updateTable();

            UgiRfidConfiguration config = UgiRfidConfiguration.forInventoryType(UgiRfidConfiguration.InventoryTypes.LOCATE_DISTANCE);
            config.selectBank = UgiRfidConfiguration.MemoryBank.Epc;
            config.selectMask = tag.getEpc().toBytes();
            config.selectOffset = 32;
            Ugi.getSingleton().startInventory(this, config);
            this.updateUI();
            this.updateCountAndTime();
            UgiUiUtil.showToast(this, "Reinicio de Inventario", "Buscando tag " + tag.getEpc().toString());
        });
    }

    private static String byteArrayToString(byte[] ba) {
        if (ba == null) return null;
        StringBuilder sb = new StringBuilder(ba.length*2);
        for (byte b : ba) {
            sb.append(NibbleToChar((b >> 4) & 0xf));
            sb.append(NibbleToChar(b & 0xf));
        }
        return sb.toString();
    }

    private static char NibbleToChar(int nibble) {
        return (char) (nibble + (nibble < 10 ? '0' : 'a'-10));
    }
}
