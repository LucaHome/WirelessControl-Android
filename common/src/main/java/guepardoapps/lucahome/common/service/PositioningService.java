package guepardoapps.lucahome.common.service;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import es.dmoral.toasty.Toasty;

import guepardoapps.lucahome.basic.classes.SerializableList;
import guepardoapps.lucahome.basic.controller.BluetoothController;
import guepardoapps.lucahome.basic.controller.BroadcastController;
import guepardoapps.lucahome.basic.controller.NetworkController;
import guepardoapps.lucahome.basic.controller.ReceiverController;
import guepardoapps.lucahome.basic.utils.Logger;
import guepardoapps.lucahome.basic.utils.Tools;
import guepardoapps.lucahome.common.classes.Position;
import guepardoapps.lucahome.common.classes.PuckJs;
import guepardoapps.lucahome.common.controller.SettingsController;
import guepardoapps.lucahome.common.interfaces.classes.ILucaClass;
import guepardoapps.lucahome.common.service.broadcasts.content.ObjectChangeFinishedContent;

@SuppressWarnings("unused")
public class PositioningService extends Service implements BeaconConsumer, MonitorNotifier, RangeNotifier {
    private static final String TAG = PositioningService.class.getSimpleName();

    public class PositioningServiceBinder extends Binder {
        public PositioningService getService() {
            Logger.getInstance().Debug(TAG, "PositioningServiceBinder getService");
            return PositioningService.this;
        }
    }

    private final IBinder _positioningServiceBinder = new PositioningServiceBinder();

    public static class PositioningUpdateFinishedContent extends ObjectChangeFinishedContent {
        public Position LatestPosition;

        PositioningUpdateFinishedContent(@NonNull Position latestPosition, boolean succcess, @NonNull byte[] response) {
            super(succcess, response);
            LatestPosition = latestPosition;
        }
    }

    public static final String PositioningCalculationFinishedBroadcast = "guepardoapps.lucahome.common.service.positioning.calculation.finished";
    public static final String PositioningCalculationFinishedBundle = "PositioningCalculationFinishedBundle";

    private boolean _permissionGranted;

    private Context _context;
    private Context _activeActivityContext;

    private BluetoothController _bluetoothController;
    private BroadcastController _broadcastController;
    private ReceiverController _receiverController;

    private static final int MIN_BETWEEN_SCAN_SEC = 15;
    private static final int MAX_BETWEEN_SCAN_SEC = 60 * 60;

    private static final int MIN_SCAN_MSEC = 500;
    private static final int MAX_SCAN_MSEC = 5 * 60 * 1000;

    private boolean _bluetoothIsEnabled;
    private boolean _scanEnabled;
    private boolean _handleBluetoothAutomatically;

    private ArrayList<Region> _previousRegionList = new ArrayList<>();
    private ArrayList<Region> _activeRegionList = new ArrayList<>();
    private Collection<Beacon> _beaconList;

    private BeaconManager _beaconManager;

    private BroadcastReceiver _bluetoothChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.getInstance().Debug(TAG, "_bluetoothChangedReceiver");

            final String action = intent.getAction();
            if (action == null) {
                Logger.getInstance().Error(TAG, "_bluetoothChangedReceiver action is null");
                return;
            }
            Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "Action is %s", action));

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "State is %d", state));

                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        _bluetoothIsEnabled = false;
                        if (_beaconManager.isBound(PositioningService.this)) {
                            _beaconManager.unbind(PositioningService.this);
                        }
                        break;

                    case BluetoothAdapter.STATE_ON:
                        _bluetoothIsEnabled = true;
                        SetScanEnabled(_scanEnabled);
                        break;

                    default:
                        break;
                }
            }

        }
    };

    private BroadcastReceiver _homeNetworkAvailableReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.getInstance().Debug(TAG, "_homeNetworkAvailableReceiver");
            SetScanEnabled(_scanEnabled);
        }
    };

    private BroadcastReceiver _homeNetworkNotAvailableReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.getInstance().Debug(TAG, "_homeNetworkNotAvailableReceiver");
            if (_beaconManager.isBound(PositioningService.this)) {
                _beaconManager.unbind(PositioningService.this);
            }
        }
    };

    private BroadcastReceiver _puckJsDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setActiveRegionList();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Logger.getInstance().Information(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Logger.getInstance().Information(TAG, "onBind");
        return _positioningServiceBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.getInstance().Information(TAG, "onCreate");

        _context = this;
        _beaconManager = BeaconManager.getInstanceForApplication(_context.getApplicationContext());

        BeaconParser beaconParser = new BeaconParser();
        _beaconManager.getBeaconParsers().add(beaconParser.setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT));
        _beaconManager.getBeaconParsers().add(beaconParser.setBeaconLayout(BeaconParser.EDDYSTONE_TLM_LAYOUT));
        _beaconManager.getBeaconParsers().add(beaconParser.setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT));
        _beaconManager.getBeaconParsers().add(beaconParser.setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT));
        _beaconManager.getBeaconParsers().add(beaconParser.setBeaconLayout(BeaconParser.URI_BEACON_LAYOUT));

        _bluetoothController = new BluetoothController();
        _broadcastController = new BroadcastController(_context);
        _receiverController = new ReceiverController(_context);

        _receiverController.RegisterReceiver(_bluetoothChangedReceiver, new String[]{BluetoothAdapter.ACTION_STATE_CHANGED});
        _receiverController.RegisterReceiver(_homeNetworkAvailableReceiver, new String[]{NetworkController.WIFIReceiverInHomeNetworkBroadcast});
        _receiverController.RegisterReceiver(_homeNetworkNotAvailableReceiver, new String[]{NetworkController.WIFIReceiverNoHomeNetworkBroadcast});
        _receiverController.RegisterReceiver(_puckJsDownloadReceiver, new String[]{PuckJsListService.PuckJsListDownloadFinishedBroadcast});

        _bluetoothIsEnabled = _bluetoothController.IsBluetoothEnabled();

        SetScanEnabled(SettingsController.getInstance().IsBeaconScanEnabled());
        SetHandleBluetoothAutomatically(SettingsController.getInstance().HandleBluetoothAutomatically());
        SetBetweenScanPeriod(SettingsController.getInstance().GetTimeBetweenBeaconScansSec());
        SetScanPeriod(SettingsController.getInstance().GetTimeBeaconScansMsec());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.getInstance().Debug(TAG, "onDestroy");
        _receiverController.Dispose();
        if (_beaconManager.isBound(PositioningService.this)) {
            _beaconManager.unbind(PositioningService.this);
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "onBeaconServiceConnect, _scanEnabled: %s", _scanEnabled));
        setActiveRegionList();
    }

    @Override
    public void didEnterRegion(Region region) {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "didEnterRegion Region: %s", region.toString()));
        //calculatePosition();
    }

    @Override
    public void didExitRegion(Region region) {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "didExitRegion Region: %s", region.toString()));
        //calculatePosition();
    }

    @Override
    public void didDetermineStateForRegion(int state, Region region) {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "didDetermineStateForRegion Region: %s, State: %d", region.toString(), state));
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beaconList, Region region) {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "RangeNotifier: BeaconList: %s | Region: %s", beaconList, region));

        _beaconList = beaconList;

        if (_beaconList.size() > 0) {
            for (int beaconIndex = 0; beaconIndex < _beaconList.size(); beaconIndex++) {
                Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "didRangeBeaconsInRegion: Found Beacon %s", _beaconList.toArray()[beaconIndex].toString()));
            }
        }

        calculatePosition();
    }

    public void SetActiveActivityContext(@NonNull Context activeActivityContext) {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "SetActiveActivityContext: %s", activeActivityContext));
        _activeActivityContext = activeActivityContext;
        askForPermission();
    }

    public Context GetActiveActivityContext() {
        return _activeActivityContext;
    }

    public void SetHandleBluetoothAutomatically(boolean handleBluetoothAutomatically) {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "SetHandleBluetoothAutomatically: %s", handleBluetoothAutomatically));
        _handleBluetoothAutomatically = handleBluetoothAutomatically;
        if (_activeActivityContext == null) {
            Logger.getInstance().Error(TAG, "_activeActivityContext is null!");
            return;
        }
        if (_handleBluetoothAutomatically) {
            askForPermission();
        }
    }

    public boolean GetHandleBluetoothAutomatically() {
        return _handleBluetoothAutomatically;
    }

    public void SetScanEnabled(boolean scanEnabled) {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "SetScanEnabled: %s", scanEnabled));
        _scanEnabled = scanEnabled;
        if (_scanEnabled && !_beaconManager.isBound(this)) {
            if (validateBluetooth()) {
                Logger.getInstance().Debug(TAG, "Binding beaconManager");
                _beaconManager.bind(this);
            } else {
                Logger.getInstance().Warning(TAG, "Validating bluetooth failed!");
            }
        } else if (!_scanEnabled && _beaconManager.isBound(this)) {
            Logger.getInstance().Debug(TAG, "Unbinding beaconManager");
            _beaconManager.unbind(this);
        }
    }

    public boolean GetScanEnabled() {
        return _scanEnabled;
    }

    public void SetBetweenScanPeriod(long betweenScanPeriod) {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "SetBetweenScanPeriod: %d", betweenScanPeriod));

        if (betweenScanPeriod < MIN_BETWEEN_SCAN_SEC * 1000) {
            betweenScanPeriod = MIN_BETWEEN_SCAN_SEC * 1000;
        }
        if (betweenScanPeriod > MAX_BETWEEN_SCAN_SEC * 1000) {
            betweenScanPeriod = MAX_BETWEEN_SCAN_SEC * 1000;
        }

        _beaconManager.setBackgroundBetweenScanPeriod(betweenScanPeriod);
        _beaconManager.setForegroundBetweenScanPeriod(betweenScanPeriod);
    }

    public long GetBackgroundBetweenScanPeriod() {
        return _beaconManager.getBackgroundBetweenScanPeriod();
    }

    public long GetForegroundBetweenScanPeriod() {
        return _beaconManager.getForegroundBetweenScanPeriod();
    }

    public void SetScanPeriod(long scanPeriod) {
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "SetScanPeriod: %s", scanPeriod));

        if (scanPeriod < MIN_SCAN_MSEC) {
            scanPeriod = MIN_SCAN_MSEC;
        }
        if (scanPeriod > MAX_SCAN_MSEC) {
            scanPeriod = MAX_SCAN_MSEC;
        }

        _beaconManager.setBackgroundScanPeriod(scanPeriod);
        _beaconManager.setForegroundScanPeriod(scanPeriod);
    }

    public long GetBackgroundScanPeriod() {
        return _beaconManager.getBackgroundScanPeriod();
    }

    public long GetForegroundScanPeriod() {
        return _beaconManager.getForegroundScanPeriod();
    }

    private boolean validateBluetooth() {
        Logger.getInstance().Debug(TAG, "validateBluetooth");
        if (_bluetoothIsEnabled) {
            return true;
        } else {
            if (_permissionGranted) {
                _bluetoothController.SetNewBluetoothState(true);
                return true;
            } else {
                return false;
            }
        }
    }

    private void calculatePosition() {
        Logger.getInstance().Debug(TAG, "calculatePosition");

        if (_beaconList == null) {
            Logger.getInstance().Error(TAG, "_beaconList is null!");
            return;
        }

        if (_beaconList.size() == 0) {
            Logger.getInstance().Error(TAG, "_beaconList has size 0!");
            return;
        }

        if (_beaconList == null || _beaconList.size() == 0) {
            Logger.getInstance().Error(TAG, "Invalid BeaconList! Cannot calculate position!");
            return;
        }

        SerializableList<PuckJs> puckJsList = PuckJsListService.getInstance().GetDataList();
        if (puckJsList == null || puckJsList.getSize() == 0) {
            Logger.getInstance().Error(TAG, "Invalid PuckJsList! Cannot calculate position!");
            return;
        }

        // TODO create calculation (ASYNC!?!?!)
        Logger.getInstance().Information(TAG, "Calculating... TODO");

        _broadcastController.SendSerializableBroadcast(
                PositioningCalculationFinishedBroadcast,
                PositioningCalculationFinishedBundle,
                new PositioningUpdateFinishedContent(
                        new Position(new PuckJs(-1, "", "", "", false, ILucaClass.LucaServerDbAction.Null), -1),
                        true,
                        Tools.CompressStringToByteArray("Positioning finished")));
    }

    private void askForPermission() {
        Logger.getInstance().Debug(TAG, "askForPermission");
        try {
            Dexter.withActivity((Activity) _activeActivityContext)
                    .withPermission(Manifest.permission.BLUETOOTH_ADMIN)
                    .withListener(new PermissionListener() {
                        @Override
                        public void onPermissionGranted(PermissionGrantedResponse response) {
                            _permissionGranted = true;
                        }

                        @Override
                        public void onPermissionDenied(PermissionDeniedResponse response) {
                            _permissionGranted = false;
                            Toasty.error(_activeActivityContext, "BluetoothPermission not granted! Beacon not working!", Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        }
                    });
        } catch (Exception exception) {
            Logger.getInstance().Error(TAG, exception.toString());
            Toasty.error(_context, exception.toString(), Toast.LENGTH_LONG).show();
        } finally {
            SetScanEnabled(_scanEnabled);
        }
    }

    private void setActiveRegionList() {
        Logger.getInstance().Debug(TAG, "setActiveRegionList");

        _previousRegionList = _activeRegionList;
        _activeRegionList.clear();

        SerializableList<PuckJs> puckJsList = PuckJsListService.getInstance().GetDataList();
        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "setActiveRegionList for %d PuckJss", puckJsList.getSize()));

        for (int index = 0; index < puckJsList.getSize(); index++) {
            PuckJs puckJs = puckJsList.getValue(index);
            Region region = new Region(puckJs.GetArea(), puckJs.GetMac());
            _activeRegionList.add(region);
        }

        setMonitorNotifiers();
        setRangeNotifiers();
    }

    private void setMonitorNotifiers() {
        Logger.getInstance().Debug(TAG, "setMonitorNotifiers");

        // Remove previous notifier
        try {
            for (Region region : _previousRegionList) {
                Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "stopMonitoringBeaconsInRegion for region %s", region));
                _beaconManager.stopMonitoringBeaconsInRegion(region);
            }
        } catch (RemoteException remoteException) {
            Logger.getInstance().Error(TAG, remoteException.toString());
        }
        _beaconManager.removeAllMonitorNotifiers();

        // Add new notifier
        _beaconManager.addMonitorNotifier(this);
        try {
            for (Region region : _activeRegionList) {
                Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "startMonitoringBeaconsInRegion for region %s", region));
                _beaconManager.startMonitoringBeaconsInRegion(region);
            }
        } catch (RemoteException remoteException) {
            Logger.getInstance().Error(TAG, remoteException.toString());
        }
    }

    private void setRangeNotifiers() {
        Logger.getInstance().Debug(TAG, "setRangeNotifiers");

        // Remove previous notifier
        try {
            for (Region region : _previousRegionList) {
                Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "stopRangingBeaconsInRegion for region %s", region));
                _beaconManager.stopRangingBeaconsInRegion(region);
            }
        } catch (RemoteException remoteException) {
            Logger.getInstance().Error(TAG, remoteException.toString());
        }
        _beaconManager.removeAllRangeNotifiers();

        // Add new notifier
        _beaconManager.addRangeNotifier(this);
        try {
            for (Region region : _activeRegionList) {
                Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "startRangingBeaconsInRegion for region %s", region));
                _beaconManager.startRangingBeaconsInRegion(region);
            }
        } catch (RemoteException remoteException) {
            Logger.getInstance().Error(TAG, remoteException.toString());
        }
    }
}
