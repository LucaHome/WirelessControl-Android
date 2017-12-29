package guepardoapps.lucahome.common.classes;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import java.io.Serializable;
import java.util.Locale;

import guepardoapps.lucahome.basic.classes.SerializableDate;
import guepardoapps.lucahome.common.enums.LucaServerAction;
import guepardoapps.lucahome.common.interfaces.classes.ILucaClass;

public class LucaBirthday implements Serializable, ILucaClass {
    private static final long serialVersionUID = 8796770534492842492L;
    private static final String TAG = LucaBirthday.class.getSimpleName();

    public enum BirthdayType {PREVIOUS, TODAY, UPCOMING}

    private int _id;

    private String _name;
    private SerializableDate _date;
    private String _group;

    private boolean _remindMe;
    private boolean _sentMail;

    private transient Bitmap _photo;
    private int _notificationId;

    private boolean _isOnServer;
    private LucaServerDbAction _serverDbAction;

    public LucaBirthday(
            int id,
            @NonNull String name,
            @NonNull SerializableDate date,
            @NonNull String group,
            boolean remindMe,
            boolean sentMail,
            Bitmap photo,
            boolean isOnServer,
            @NonNull LucaServerDbAction serverDbAction) {
        _id = id;

        _name = name;
        _date = date;
        _group = group;

        _remindMe = remindMe;
        _sentMail = sentMail;

        _photo = photo;
        _notificationId = _id * _date.DayOfMonth() + _date.Month() * _date.Year();

        _isOnServer = isOnServer;
        _serverDbAction = serverDbAction;
    }

    @Override
    public int GetId() {
        return _id;
    }

    public String GetName() {
        return _name;
    }

    public void SetName(@NonNull String name) {
        _name = name;
    }

    public SerializableDate GetDate() {
        return _date;
    }

    public void SetDate(@NonNull SerializableDate date) {
        _date = date;
    }

    public String GetGroup() {
        return _group;
    }

    public void SetGroup(@NonNull String group) {
        _group = group;
    }

    public boolean GetRemindMe() {
        return _remindMe;
    }

    public void SetRemindMe(boolean remindMe) {
        _remindMe = remindMe;
    }

    public boolean GetSentMail() {
        return _sentMail;
    }

    public void SetSentMail(boolean sentMail) {
        _sentMail = sentMail;
    }

    public Bitmap GetPhoto() {
        return _photo;
    }

    public void SetPhoto(@NonNull Bitmap photo) {
        _photo = photo;
    }

    public int GetNotificationId() {
        return _notificationId;
    }

    public String GetNotificationBody() {
        return "It is " + _name + "'s " + String.valueOf(GetAge()) + "th birthday!";
    }

    public int GetAge() {
        int age;
        SerializableDate today = new SerializableDate();

        if (today.Month() > _date.Month()) {
            age = today.Year() - _date.Year();
        } else if (today.Month() == _date.Month()) {
            if (today.DayOfMonth() >= _date.DayOfMonth()) {
                age = today.Year() - _date.Year();
            } else {
                age = today.Year() - _date.Year() - 1;
            }
        } else {
            age = today.Year() - _date.Year() - 1;
        }

        return age;
    }

    public boolean HasBirthday() {
        SerializableDate today = new SerializableDate();
        return today.DayOfMonth() == _date.DayOfMonth() && today.Month() == _date.Month();
    }

    public BirthdayType CurrentBirthdayType() {
        if (HasBirthday()) {
            return BirthdayType.TODAY;
        } else {
            SerializableDate today = new SerializableDate();
            if (today.Month() > _date.Month() || (today.Month() == _date.Month() && today.DayOfMonth() > _date.DayOfMonth())) {
                return BirthdayType.PREVIOUS;
            } else {
                return BirthdayType.UPCOMING;
            }
        }
    }

    @Override
    public void SetServerDbAction(@NonNull LucaServerDbAction serverDbAction) {
        _serverDbAction = serverDbAction;
    }

    @Override
    public LucaServerDbAction GetServerDbAction() {
        return _serverDbAction;
    }

    @Override
    public void SetIsOnServer(boolean isOnServer) {
        _isOnServer = isOnServer;
    }

    @Override
    public boolean GetIsOnServer() {
        return _isOnServer;
    }

    @Override
    public String CommandAdd() {
        return String.format(Locale.getDefault(), "%s%d&name=%s&day=%d&month=%d&year=%d&group=%s&remindme=%s", LucaServerAction.ADD_BIRTHDAY.toString(), _id, _name, _date.DayOfMonth(), _date.Month(), _date.Year(), _group, (_remindMe ? "1" : "0"));
    }

    @Override
    public String CommandUpdate() {
        return String.format(Locale.getDefault(), "%s%d&name=%s&day=%d&month=%d&year=%d&group=%s&remindme=%s", LucaServerAction.UPDATE_BIRTHDAY.toString(), _id, _name, _date.DayOfMonth(), _date.Month(), _date.Year(), _group, (_remindMe ? "1" : "0"));
    }

    @Override
    public String CommandDelete() {
        return String.format(Locale.getDefault(), "%s%d", LucaServerAction.DELETE_BIRTHDAY.toString(), _id);
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "( %s: (Name: %s );(Birthday: %s );(Age: %d );(Group: %s );(HasBirthday: %s ))", TAG, _name, _date, GetAge(), _group, HasBirthday());
    }
}
