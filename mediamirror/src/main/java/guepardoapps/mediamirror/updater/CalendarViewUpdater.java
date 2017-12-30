package guepardoapps.mediamirror.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.format.DateUtils;

import guepardoapps.lucahome.basic.classes.SerializableList;
import guepardoapps.lucahome.basic.controller.BroadcastController;
import guepardoapps.lucahome.basic.controller.CalendarController;
import guepardoapps.lucahome.basic.controller.ReceiverController;
import guepardoapps.lucahome.basic.dto.CalendarEntryDto;
import guepardoapps.lucahome.basic.utils.Logger;
import guepardoapps.mediamirror.common.constants.Broadcasts;
import guepardoapps.mediamirror.common.constants.Bundles;

public class CalendarViewUpdater {
    private static final String TAG = CalendarViewUpdater.class.getSimpleName();

    private Handler _updater;

    private BroadcastController _broadcastController;
    private CalendarController _calendarController;
    private ReceiverController _receiverController;

    private int _updateTime;
    private boolean _isRunning;

    private Runnable _updateRunnable = new Runnable() {
        public void run() {
            SerializableList<CalendarEntryDto> calendarList = _calendarController.ReadCalendar(DateUtils.YEAR_IN_MILLIS * 10000);
            _broadcastController.SendSerializableBroadcast(
                    Broadcasts.SHOW_CALENDAR_MODEL,
                    Bundles.CALENDAR_MODEL,
                    calendarList);
            _updater.postDelayed(_updateRunnable, _updateTime);
        }
    };

    private BroadcastReceiver _performUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SerializableList<CalendarEntryDto> calendarList = _calendarController.ReadCalendar(DateUtils.YEAR_IN_MILLIS * 10000);
            _broadcastController.SendSerializableBroadcast(
                    Broadcasts.SHOW_CALENDAR_MODEL,
                    Bundles.CALENDAR_MODEL,
                    calendarList);
        }
    };

    public CalendarViewUpdater(@NonNull Context context) {
        _updater = new Handler();
        _broadcastController = new BroadcastController(context);
        _calendarController = new CalendarController(context);
        _receiverController = new ReceiverController(context);
    }

    public void Start(int updateTime) {
        if (_isRunning) {
            Logger.getInstance().Warning(TAG, "Already running!");
            return;
        }
        _updateTime = updateTime;
        _receiverController.RegisterReceiver(_performUpdateReceiver, new String[]{Broadcasts.PERFORM_CALENDAR_UPDATE});
        _updateRunnable.run();
        _isRunning = true;
    }

    public void Dispose() {
        _updater.removeCallbacks(_updateRunnable);
        _receiverController.Dispose();
        _isRunning = false;
    }
}