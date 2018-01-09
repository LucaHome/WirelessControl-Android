package guepardoapps.lucahome.common.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Collectors;

import guepardoapps.lucahome.basic.classes.SerializableDate;
import guepardoapps.lucahome.basic.classes.SerializableList;
import guepardoapps.lucahome.basic.controller.BroadcastController;
import guepardoapps.lucahome.basic.controller.NetworkController;
import guepardoapps.lucahome.basic.controller.ReceiverController;
import guepardoapps.lucahome.basic.utils.Logger;
import guepardoapps.lucahome.basic.utils.Tools;
import guepardoapps.lucahome.common.classes.LucaMenu;
import guepardoapps.lucahome.common.classes.LucaUser;
import guepardoapps.lucahome.common.constants.Constants;
import guepardoapps.lucahome.common.converter.JsonDataToMenuConverter;
import guepardoapps.lucahome.common.database.DatabaseMenuList;
import guepardoapps.lucahome.common.enums.LucaServerAction;
import guepardoapps.lucahome.common.controller.DownloadController;
import guepardoapps.lucahome.common.controller.SettingsController;
import guepardoapps.lucahome.common.interfaces.classes.ILucaClass;
import guepardoapps.lucahome.common.interfaces.services.IDataService;
import guepardoapps.lucahome.common.service.broadcasts.content.ObjectChangeFinishedContent;

@SuppressWarnings({"unused", "WeakerAccess"})
public class MenuService implements IDataService {
    public static class MenuDownloadFinishedContent extends ObjectChangeFinishedContent {
        public SerializableList<LucaMenu> MenuList;

        MenuDownloadFinishedContent(@NonNull SerializableList<LucaMenu> menuList, boolean succcess, @NonNull byte[] response) {
            super(succcess, response);
            MenuList = menuList;
        }
    }

    public static final String MenuIntent = "MenuIntent";

    public static final String MenuDownloadFinishedBroadcast = "guepardoapps.lucahome.data.service.menu.download.finished";
    public static final String MenuDownloadFinishedBundle = "MenuDownloadFinishedBundle";

    public static final String MenuUpdateFinishedBroadcast = "guepardoapps.lucahome.data.service.menu.update.finished";
    public static final String MenuUpdateFinishedBundle = "MenuUpdateFinishedBundle";

    public static final String MenuClearFinishedBroadcast = "guepardoapps.lucahome.data.service.menu.clear.finished";
    public static final String MenuClearFinishedBundle = "MenuClearFinishedBundle";

    private static final MenuService SINGLETON = new MenuService();
    private boolean _isInitialized;

    private static final String TAG = MenuService.class.getSimpleName();

    private boolean _loadDataEnabled;

    private Date _lastUpdate;

    private static final int MIN_TIMEOUT_MIN = 2 * 60;
    private static final int MAX_TIMEOUT_MIN = 24 * 60;

    private boolean _reloadEnabled;
    private int _reloadTimeout;
    private Handler _reloadHandler = new Handler();
    private Runnable _reloadListRunnable = new Runnable() {
        @Override
        public void run() {
            LoadData();
            if (_reloadEnabled && _networkController.IsHomeNetwork(SettingsController.getInstance().GetHomeSsid())) {
                _reloadHandler.postDelayed(_reloadListRunnable, _reloadTimeout);
            }
        }
    };

    private class AsyncConverterTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... strings) {
            for (String contentResponse : strings) {
                SerializableList<LucaMenu> menuList = JsonDataToMenuConverter.getInstance().GetList(contentResponse);
                if (menuList == null) {
                    Logger.getInstance().Error(TAG, "Converted menuList is null!");
                    _menuList = _databaseMenuList.GetMenuList();
                    sendFailedMenuDownloadBroadcast("Converted menuList is null!");
                    return "";
                }

                // Sort list
                int dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                int startIndex = -1;
                for (int index = 0; index < menuList.getSize(); index++) {
                    if (menuList.getValue(index).GetDate().DayOfMonth() == dayOfMonth) {
                        startIndex = index;
                        break;
                    }
                }
                if (startIndex != -1) {
                    SerializableList<LucaMenu> sortedList = new SerializableList<>();
                    int selectedIndex = startIndex;
                    for (int index = 0; index < menuList.getSize(); index++) {
                        if (selectedIndex >= menuList.getSize()) {
                            selectedIndex = selectedIndex - menuList.getSize();
                        }
                        sortedList.addValue(menuList.getValue(selectedIndex));
                        selectedIndex++;
                    }
                    menuList = sortedList;
                }
                _menuList = menuList;

                _lastUpdate = new Date();

                controlMenus();

                clearMenuListFromDatabase();
                saveMenuListToDatabase();

                _broadcastController.SendSerializableBroadcast(
                        MenuDownloadFinishedBroadcast,
                        MenuDownloadFinishedBundle,
                        new MenuDownloadFinishedContent(_menuList, true, Tools.CompressStringToByteArray("Download finished")));
            }
            return "Success";
        }
    }

    private Context _context;

    private BroadcastController _broadcastController;
    private DownloadController _downloadController;
    private NetworkController _networkController;
    private ReceiverController _receiverController;

    private DatabaseMenuList _databaseMenuList;

    private SerializableList<LucaMenu> _menuList = new SerializableList<>();

    private BroadcastReceiver _menuDownloadFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DownloadController.DownloadFinishedBroadcastContent content = (DownloadController.DownloadFinishedBroadcastContent) intent.getSerializableExtra(DownloadController.DownloadFinishedBundle);

            if (content.CurrentDownloadType != DownloadController.DownloadType.Menu) {
                return;
            }

            String contentResponse = Tools.DecompressByteArrayToString(DownloadStorageService.getInstance().GetDownloadResult(content.CurrentDownloadType));

            if (contentResponse.contains("Error") || contentResponse.contains("ERROR")
                    || contentResponse.contains("Canceled") || contentResponse.contains("CANCELED")
                    || content.FinalDownloadState != DownloadController.DownloadState.Success) {
                Logger.getInstance().Error(TAG, contentResponse);
                _menuList = _databaseMenuList.GetMenuList();
                sendFailedMenuDownloadBroadcast(contentResponse);
                return;
            }

            if (!content.Success) {
                Logger.getInstance().Error(TAG, "Download was not successful!");
                _menuList = _databaseMenuList.GetMenuList();
                sendFailedMenuDownloadBroadcast("Download was not successful!");
                return;
            }

            new AsyncConverterTask().execute(contentResponse);
        }
    };

    private BroadcastReceiver _menuUpdateFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DownloadController.DownloadFinishedBroadcastContent content = (DownloadController.DownloadFinishedBroadcastContent) intent.getSerializableExtra(DownloadController.DownloadFinishedBundle);

            if (content.CurrentDownloadType != DownloadController.DownloadType.MenuUpdate) {
                return;
            }

            String contentResponse = Tools.DecompressByteArrayToString(DownloadStorageService.getInstance().GetDownloadResult(content.CurrentDownloadType));

            if (contentResponse.contains("Error") || contentResponse.contains("ERROR")
                    || contentResponse.contains("Canceled") || contentResponse.contains("CANCELED")
                    || content.FinalDownloadState != DownloadController.DownloadState.Success) {
                Logger.getInstance().Error(TAG, contentResponse);
                sendFailedMenuUpdateBroadcast(contentResponse);
                return;
            }

            if (!content.Success) {
                Logger.getInstance().Error(TAG, "Download was not successful!");
                sendFailedMenuUpdateBroadcast(contentResponse);
                return;
            }

            _lastUpdate = new Date();

            _broadcastController.SendSerializableBroadcast(
                    MenuUpdateFinishedBroadcast,
                    MenuUpdateFinishedBundle,
                    new ObjectChangeFinishedContent(true, new byte[]{}));

            LoadData();
        }
    };

    private BroadcastReceiver _menuClearFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DownloadController.DownloadFinishedBroadcastContent content = (DownloadController.DownloadFinishedBroadcastContent) intent.getSerializableExtra(DownloadController.DownloadFinishedBundle);

            if (content.CurrentDownloadType != DownloadController.DownloadType.MenuClear) {
                return;
            }

            String contentResponse = Tools.DecompressByteArrayToString(DownloadStorageService.getInstance().GetDownloadResult(content.CurrentDownloadType));

            if (contentResponse.contains("Error") || contentResponse.contains("ERROR")
                    || contentResponse.contains("Canceled") || contentResponse.contains("CANCELED")
                    || content.FinalDownloadState != DownloadController.DownloadState.Success) {
                Logger.getInstance().Error(TAG, contentResponse);
                sendFailedMenuClearBroadcast(contentResponse);
                return;
            }

            if (!content.Success) {
                Logger.getInstance().Error(TAG, "Download was not successful!");
                sendFailedMenuClearBroadcast(contentResponse);
                return;
            }

            _lastUpdate = new Date();

            _broadcastController.SendSerializableBroadcast(
                    MenuClearFinishedBroadcast,
                    MenuClearFinishedBundle,
                    new ObjectChangeFinishedContent(true, new byte[]{}));

            LoadData();
        }
    };

    private BroadcastReceiver _homeNetworkAvailableReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            _reloadHandler.removeCallbacks(_reloadListRunnable);
            if (_reloadEnabled && _networkController.IsHomeNetwork(SettingsController.getInstance().GetHomeSsid())) {
                _reloadHandler.postDelayed(_reloadListRunnable, _reloadTimeout);
            }
        }
    };

    private BroadcastReceiver _homeNetworkNotAvailableReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            _reloadHandler.removeCallbacks(_reloadListRunnable);
        }
    };

    private MenuService() {
    }

    public static MenuService getInstance() {
        return SINGLETON;
    }

    @Override
    public void Initialize(@NonNull Context context, boolean reloadEnabled, int reloadTimeout) {
        if (_isInitialized) {
            Logger.getInstance().Warning(TAG, "Already initialized!");
            return;
        }

        _lastUpdate = new Date();

        _reloadEnabled = reloadEnabled;

        _loadDataEnabled = true;

        _context = context;

        _broadcastController = new BroadcastController(_context);
        _downloadController = new DownloadController(_context);
        _networkController = new NetworkController(_context);
        _receiverController = new ReceiverController(_context);

        _databaseMenuList = new DatabaseMenuList(_context);
        _databaseMenuList.Open();

        _receiverController.RegisterReceiver(_menuDownloadFinishedReceiver, new String[]{DownloadController.DownloadFinishedBroadcast});
        _receiverController.RegisterReceiver(_menuUpdateFinishedReceiver, new String[]{DownloadController.DownloadFinishedBroadcast});
        _receiverController.RegisterReceiver(_menuClearFinishedReceiver, new String[]{DownloadController.DownloadFinishedBroadcast});

        _receiverController.RegisterReceiver(_homeNetworkAvailableReceiver, new String[]{NetworkController.WIFIReceiverInHomeNetworkBroadcast});
        _receiverController.RegisterReceiver(_homeNetworkNotAvailableReceiver, new String[]{NetworkController.WIFIReceiverNoHomeNetworkBroadcast});

        SetReloadTimeout(reloadTimeout);

        _isInitialized = true;
    }

    @Override
    public void Dispose() {
        _reloadHandler.removeCallbacks(_reloadListRunnable);
        _receiverController.Dispose();
        _databaseMenuList.Close();
        _isInitialized = false;
    }

    @Override
    public SerializableList<LucaMenu> GetDataList() {
        return _menuList;
    }

    public ArrayList<String> GetMenuTitleList() {
        ArrayList<String> titleList = new ArrayList<>();
        for (int index = 0; index < _menuList.getSize(); index++) {
            titleList.add(_menuList.getValue(index).GetTitle());
        }
        return new ArrayList<>(titleList.stream().distinct().collect(Collectors.toList()));
    }

    public ArrayList<String> GetMenuDescriptionList() {
        ArrayList<String> descriptionList = new ArrayList<>();
        for (int index = 0; index < _menuList.getSize(); index++) {
            descriptionList.add(_menuList.getValue(index).GetDescription());
        }
        return new ArrayList<>(descriptionList.stream().distinct().collect(Collectors.toList()));
    }

    public LucaMenu GetMenuById(int id) {
        for (int index = 0; index < _menuList.getSize(); index++) {
            LucaMenu entry = _menuList.getValue(index);
            if (entry.GetId() == id) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public int GetHighestId() {
        int highestId = -1;
        for (int index = 0; index < _menuList.getSize(); index++) {
            int id = _menuList.getValue(index).GetId();
            if (id > highestId) {
                highestId = id;
            }
        }
        return highestId;
    }

    @Override
    public SerializableList<LucaMenu> SearchDataList(@NonNull String searchKey) {
        SerializableList<LucaMenu> foundMenus = new SerializableList<>();
        for (int index = 0; index < _menuList.getSize(); index++) {
            LucaMenu entry = _menuList.getValue(index);
            if (entry.toString().contains(searchKey)) {
                foundMenus.addValue(entry);
            }
        }
        return foundMenus;
    }

    @Override
    public void LoadData() {
        if (!_loadDataEnabled) {
            return;
        }

        if (!_networkController.IsHomeNetwork(SettingsController.getInstance().GetHomeSsid())) {
            _menuList = _databaseMenuList.GetMenuList();
            _broadcastController.SendSerializableBroadcast(
                    MenuDownloadFinishedBroadcast,
                    MenuDownloadFinishedBundle,
                    new MenuDownloadFinishedContent(_menuList, true, Tools.CompressStringToByteArray("Loaded from database")));
            return;
        }

        LucaUser user = SettingsController.getInstance().GetUser();
        if (user == null) {
            sendFailedMenuDownloadBroadcast("No user");
            return;
        }

        if (hasMenuEntryNotOnServer()) {
            _loadDataEnabled = false;

            for (int index = 0; index < notOnServerMenu().getSize(); index++) {
                LucaMenu lucaMenu = notOnServerMenu().getValue(index);

                switch (lucaMenu.GetServerDbAction()) {
                    case Update:
                        UpdateMenu(lucaMenu);
                        break;
                    case Delete:
                        ClearMenu(lucaMenu);
                        break;
                    case Add:
                    case Null:
                    default:
                        Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "Nothing todo with %s.", lucaMenu));
                        break;
                }
            }

            _loadDataEnabled = true;
        }

        String requestUrl = "http://"
                + SettingsController.getInstance().GetServerIp()
                + Constants.ACTION_PATH
                + user.GetName() + "&password=" + user.GetPassphrase()
                + "&action=" + LucaServerAction.GET_MENU.toString();

        _downloadController.SendCommandToWebsiteAsync(requestUrl, DownloadController.DownloadType.Menu, true);
    }

    public void UpdateMenu(@NonNull LucaMenu entry) {
        if (!_networkController.IsHomeNetwork(SettingsController.getInstance().GetHomeSsid())) {
            entry.SetIsOnServer(false);
            entry.SetServerDbAction(ILucaClass.LucaServerDbAction.Update);

            _databaseMenuList.Update(entry);

            LoadData();

            return;
        }

        LucaUser user = SettingsController.getInstance().GetUser();
        if (user == null) {
            sendFailedMenuUpdateBroadcast("No user");
            return;
        }

        String requestUrl = String.format(Locale.getDefault(), "http://%s%s%s&password=%s&action=%s",
                SettingsController.getInstance().GetServerIp(), Constants.ACTION_PATH,
                user.GetName(), user.GetPassphrase(),
                entry.CommandUpdate());

        _downloadController.SendCommandToWebsiteAsync(requestUrl, DownloadController.DownloadType.MenuUpdate, true);
    }

    public void ClearMenu(@NonNull LucaMenu entry) {
        if (!_networkController.IsHomeNetwork(SettingsController.getInstance().GetHomeSsid())) {
            entry.SetIsOnServer(false);
            entry.SetServerDbAction(ILucaClass.LucaServerDbAction.Delete);

            _databaseMenuList.Update(entry);

            LoadData();

            return;
        }

        LucaUser user = SettingsController.getInstance().GetUser();
        if (user == null) {
            sendFailedMenuClearBroadcast("No user");
            return;
        }

        String requestUrl = String.format(Locale.getDefault(), "http://%s%s%s&password=%s&action=%s",
                SettingsController.getInstance().GetServerIp(), Constants.ACTION_PATH,
                user.GetName(), user.GetPassphrase(),
                entry.CommandDelete());

        _downloadController.SendCommandToWebsiteAsync(requestUrl, DownloadController.DownloadType.MenuClear, true);
    }

    @Override
    public boolean GetReloadEnabled() {
        return _reloadEnabled;
    }

    @Override
    public void SetReloadEnabled(boolean reloadEnabled) {
        _reloadEnabled = reloadEnabled;
        if (_reloadEnabled) {
            _reloadHandler.removeCallbacks(_reloadListRunnable);
            _reloadHandler.postDelayed(_reloadListRunnable, _reloadTimeout);
        }
    }

    @Override
    public int GetReloadTimeout() {
        return _reloadTimeout;
    }

    @Override
    public void SetReloadTimeout(int reloadTimeout) {
        if (reloadTimeout < MIN_TIMEOUT_MIN) {
            reloadTimeout = MIN_TIMEOUT_MIN;
        }
        if (reloadTimeout > MAX_TIMEOUT_MIN) {
            reloadTimeout = MAX_TIMEOUT_MIN;
        }

        _reloadTimeout = reloadTimeout * 60 * 1000;
        if (_reloadEnabled) {
            _reloadHandler.removeCallbacks(_reloadListRunnable);
            _reloadHandler.postDelayed(_reloadListRunnable, _reloadTimeout);
        }
    }

    public void ShareMenuList() {
        StringBuilder shareText = new StringBuilder("Menu:\n");

        for (int index = 0; index < _menuList.getSize(); index++) {
            LucaMenu entry = _menuList.getValue(index);
            shareText.append(entry.GetDateString()).append("\n").append(entry.GetTitle()).append("\n").append(entry.GetDescription()).append("\n\n");
        }

        Intent sendIntent = new Intent();

        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        sendIntent.setType("text/plain");

        _context.startActivity(sendIntent);
    }

    public Date GetLastUpdate() {
        return _lastUpdate;
    }

    private void clearMenuListFromDatabase() {
        SerializableList<LucaMenu> menuList = _databaseMenuList.GetMenuList();
        for (int index = 0; index < menuList.getSize(); index++) {
            LucaMenu menu = menuList.getValue(index);
            _databaseMenuList.Delete(menu);
        }
    }

    private void saveMenuListToDatabase() {
        for (int index = 0; index < _menuList.getSize(); index++) {
            LucaMenu menu = _menuList.getValue(index);
            _databaseMenuList.CreateEntry(menu);
        }
    }

    private void sendFailedMenuDownloadBroadcast(@NonNull String response) {
        if (response.length() == 0) {
            response = "Download for menu failed!";
        }

        _broadcastController.SendSerializableBroadcast(
                MenuDownloadFinishedBroadcast,
                MenuDownloadFinishedBundle,
                new MenuDownloadFinishedContent(_menuList, false, Tools.CompressStringToByteArray(response)));
    }

    private void sendFailedMenuUpdateBroadcast(@NonNull String response) {
        if (response.length() == 0) {
            response = "Update for menu failed!";
        }

        _broadcastController.SendSerializableBroadcast(
                MenuUpdateFinishedBroadcast,
                MenuUpdateFinishedBundle,
                new ObjectChangeFinishedContent(false, Tools.CompressStringToByteArray(response)));
    }

    private void sendFailedMenuClearBroadcast(@NonNull String response) {
        if (response.length() == 0) {
            response = "Clear for menu failed!";
        }

        _broadcastController.SendSerializableBroadcast(
                MenuClearFinishedBroadcast,
                MenuClearFinishedBundle,
                new ObjectChangeFinishedContent(false, Tools.CompressStringToByteArray(response)));
    }

    private void controlMenus() {
        Calendar today = Calendar.getInstance();
        int year = today.get(Calendar.YEAR);
        int month = today.get(Calendar.MONTH) + 1;
        int dayOfMonth = today.get(Calendar.DAY_OF_MONTH);

        for (int index = 0; index < _menuList.getSize(); index++) {
            LucaMenu menu = _menuList.getValue(index);

            if (menu.GetDate().Year() < year) {
                Logger.getInstance().Debug(TAG, String.format("Year of menu %s is lower then this year! Updating...", menu.toString()));
                menu = resetMenu(menu);
                if (menu == null) {
                    continue;
                }
                UpdateMenu(menu);
                continue;
            }

            if (menu.GetDate().Month() < month) {
                Logger.getInstance().Debug(TAG, String.format("Month of menu %s is lower then this year! Updating...", menu.toString()));
                menu = resetMenu(menu);
                if (menu == null) {
                    continue;
                }
                UpdateMenu(menu);
                continue;
            }

            if (menu.GetDate().DayOfMonth() < dayOfMonth && menu.GetDate().Month() <= month) {
                Logger.getInstance().Debug(TAG, String.format(Locale.getDefault(), "Day of menu %s is lower then this day and month is lower then this month! Updating...", menu.toString()));
                menu = resetMenu(menu);
                if (menu == null) {
                    continue;
                }
                UpdateMenu(menu);
            }
        }
    }

    private LucaMenu resetMenu(@NonNull LucaMenu menu) {
        menu.SetTitle("-");
        menu.SetDescription("-");

        Calendar today = Calendar.getInstance();

        int year = today.get(Calendar.YEAR);
        int month = today.get(Calendar.MONTH) + 1;
        int dayOfMonth = today.get(Calendar.DAY_OF_MONTH);
        int dayOfWeek = today.get(Calendar.DAY_OF_WEEK);

        int menuDayOfWeek = menu.GetWeekday().GetInt();

        if (menuDayOfWeek < 0) {
            Logger.getInstance().Error(TAG, "Day of week was not found!");
            return null;
        }

        int dayOfWeekDifference = menuDayOfWeek - dayOfWeek + 1;
        if (dayOfWeekDifference < 0) {
            dayOfWeekDifference += 7;
        }

        if (menu.GetDate().Year() < year || menu.GetDate().Month() < month || menu.GetDate().DayOfMonth() < dayOfMonth) {
            return calculateDate(menu, year, month, dayOfMonth, dayOfWeekDifference);
        }

        return null;
    }

    private LucaMenu calculateDate(
            @NonNull LucaMenu menu,
            int year,
            int month,
            int dayOfMonth,
            int dayOfWeekDifference) {
        dayOfMonth += dayOfWeekDifference;

        switch (month - 1) {
            case Calendar.JANUARY:
            case Calendar.MARCH:
            case Calendar.MAY:
            case Calendar.JULY:
            case Calendar.AUGUST:
            case Calendar.OCTOBER:
                if (dayOfMonth > 31) {
                    dayOfMonth -= 31;
                    month++;
                }
                break;
            case Calendar.APRIL:
            case Calendar.JUNE:
            case Calendar.SEPTEMBER:
            case Calendar.NOVEMBER:
                if (dayOfMonth > 30) {
                    dayOfMonth -= 30;
                    month++;
                }
                break;
            case Calendar.FEBRUARY:
                if (year % 4 == 0) {
                    if (dayOfMonth > 29) {
                        dayOfMonth -= 29;
                        month++;
                    }
                } else {
                    if (dayOfMonth > 28) {
                        dayOfMonth -= 28;
                        month++;
                    }
                }
                break;
            case Calendar.DECEMBER:
                if (dayOfMonth > 31) {
                    dayOfMonth -= 31;
                    month = 1;
                    year++;
                }
                break;
            default:
                Logger.getInstance().Error(TAG, String.format(Locale.getDefault(), "Invalid month %d!", month));
                return null;
        }

        menu.SetDate(new SerializableDate(year, month, dayOfMonth));

        return menu;
    }

    private SerializableList<LucaMenu> notOnServerMenu() {
        SerializableList<LucaMenu> notOnServerMenuList = new SerializableList<>();

        for (int index = 0; index < _menuList.getSize(); index++) {
            if (!_menuList.getValue(index).GetIsOnServer()) {
                notOnServerMenuList.addValue(_menuList.getValue(index));
            }
        }

        return notOnServerMenuList;
    }

    private boolean hasMenuEntryNotOnServer() {
        return notOnServerMenu().getSize() > 0;
    }
}
