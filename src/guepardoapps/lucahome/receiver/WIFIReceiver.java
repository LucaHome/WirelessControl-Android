package guepardoapps.lucahome.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;

import guepardoapps.lucahome.R;
import guepardoapps.lucahome.common.classes.SerializableList;
import guepardoapps.lucahome.common.constants.Broadcasts;
import guepardoapps.lucahome.common.constants.Bundles;
import guepardoapps.lucahome.common.constants.Constants;
import guepardoapps.lucahome.common.constants.IDs;
import guepardoapps.lucahome.common.constants.SharedPrefConstants;
import guepardoapps.lucahome.common.controller.DatabaseController;
import guepardoapps.lucahome.common.controller.ServiceController;
import guepardoapps.lucahome.common.dto.ActionDto;
import guepardoapps.lucahome.common.enums.LucaObject;
import guepardoapps.lucahome.common.enums.MainServiceAction;
import guepardoapps.lucahome.common.enums.RaspberrySelection;
import guepardoapps.lucahome.common.tools.LucaHomeLogger;
import guepardoapps.lucahome.services.MainService;

import guepardoapps.toolset.controller.BroadcastController;
import guepardoapps.toolset.controller.DialogController;
import guepardoapps.toolset.controller.NetworkController;
import guepardoapps.toolset.controller.SharedPrefController;
import guepardoapps.toolset.services.AndroidSystemService;

public class WIFIReceiver extends BroadcastReceiver {

	private static final String TAG = WIFIReceiver.class.getName();
	private LucaHomeLogger _logger;

	private static final String WIFI = "Wifi:";

	private static final int CHECK_CONNETION_TIMEOUT = 10 * 1000;

	private Context _context;

	private AndroidSystemService _androidSystemService;
	private BroadcastController _broadcastController;
	private DatabaseController _databaseController;
	private DialogController _dialogController;
	private NetworkController _networkController;
	private ServiceController _serviceController;
	private SharedPrefController _sharedPrefController;

	private boolean _checkConnectionEnabled;
	private Runnable _checkConnectionRunnable = new Runnable() {
		@Override
		public void run() {
			_logger.Info("_checkConnectionRunnable run");
			_checkConnectionEnabled = false;
			checkConnection();
		}
	};

	@Override
	public void onReceive(Context context, Intent intent) {
		if (_logger == null) {
			_logger = new LucaHomeLogger(TAG);
		}
		_logger.Debug("WIFIReceiver onReceive");
		_logger.Info("Context is " + context.toString());

		String action = intent.getAction();
		if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
			SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
			if (!(SupplicantState.isValidState(state) && state == SupplicantState.COMPLETED)) {
				_logger.Warn("Not yet a valid connection!");
				return;
			}
		}

		_logger.Info("valid connection!");

		_context = context;
		_checkConnectionEnabled = true;

		if (_androidSystemService == null) {
			_androidSystemService = new AndroidSystemService(_context);
		}
		if (_broadcastController == null) {
			_broadcastController = new BroadcastController(_context);
		}
		if (_databaseController == null) {
			_databaseController = DatabaseController.getInstance();
			_databaseController.onCreate(_context);
		}
		if (_dialogController == null) {
			int textColor = ContextCompat.getColor(_context, R.color.TextIcon);
			int backgroundColor = ContextCompat.getColor(_context, R.color.Background);
			_dialogController = new DialogController(_context, textColor, backgroundColor);
		}
		if (_networkController == null) {
			_networkController = new NetworkController(_context, _dialogController);
		}
		if (_serviceController == null) {
			_serviceController = new ServiceController(_context);
		}
		if (_sharedPrefController == null) {
			_sharedPrefController = new SharedPrefController(_context, SharedPrefConstants.SHARED_PREF_NAME);
		}

		checkConnection();
	}

	private void checkConnection() {
		_logger.Debug("checkConnection");

		if (_networkController.IsHomeNetwork(Constants.LUCAHOME_SSID)) {
			_logger.Debug("We are in the homenetwork!");

			if (_androidSystemService.IsServiceRunning(MainService.class)) {
				_broadcastController.SendSerializableArrayBroadcast(Broadcasts.MAIN_SERVICE_COMMAND,
						new String[] { Bundles.MAIN_SERVICE_ACTION }, new Object[] { MainServiceAction.DOWNLOAD_ALL });
			} else {
				Intent startMainService = new Intent(_context, MainService.class);
				Bundle mainServiceBundle = new Bundle();
				mainServiceBundle.putSerializable(Bundles.MAIN_SERVICE_ACTION, MainServiceAction.BOOT);
				startMainService.putExtras(mainServiceBundle);
				_context.startService(startMainService);
			}

			_serviceController.SendMessageToWear(WIFI + "HOME");

			// check if actions are stored to perform after entering home wifi
			checkDatabase();

			// check if flag is enabled to display the sleep notification
			checkSleepNotificationFlag();
		} else {
			_logger.Warn("We are NOT in the homenetwork!");

			_serviceController.CloseNotification(IDs.NOTIFICATION_TEMPERATURE);
			_serviceController.CloseNotification(IDs.NOTIFICATION_WEAR);
			_serviceController.SendMessageToWear(WIFI + "NO");

			if (_checkConnectionEnabled) {
				Handler checkConnectionHandler = new Handler();
				checkConnectionHandler.postDelayed(_checkConnectionRunnable, CHECK_CONNETION_TIMEOUT);
			}
		}
	}

	private void checkDatabase() {
		_logger.Debug("checkDatabase");
		SerializableList<ActionDto> storedActions = _databaseController.GetActions();
		if (storedActions.getSize() > 0) {
			for (int index = 0; index < storedActions.getSize(); index++) {
				ActionDto entry = storedActions.getValue(index);
				_serviceController.StartRestService(entry.GetSocket(), entry.GetCommandSet(),
						entry.GetNotificationBroadcast(), LucaObject.WIRELESS_SOCKET, RaspberrySelection.BOTH);
				_databaseController.DeleteAction(entry);
			}
		} else {
			_logger.Debug("No actions stored!");
		}
	}

	private void checkSleepNotificationFlag() {
		if (_sharedPrefController
				.LoadBooleanValueFromSharedPreferences(SharedPrefConstants.DISPLAY_SLEEP_NOTIFICATION_ACTIVE)) {
			_serviceController.StartNotificationService("", "", -1, LucaObject.GO_TO_BED);
			_sharedPrefController.SaveBooleanValue(SharedPrefConstants.DISPLAY_SLEEP_NOTIFICATION_ACTIVE, false);
		}
	}
}