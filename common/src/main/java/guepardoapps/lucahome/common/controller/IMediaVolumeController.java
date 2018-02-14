package guepardoapps.lucahome.common.controller;

import android.content.Context;
import android.support.annotation.NonNull;

public interface IMediaVolumeController {
    String CurrentVolumeBroadcast = "guepardoapps.lucahome.common.controller.mediavolume.currentvolume";
    String CurrentVolumeBundle = "guepardoapps.lucahome.common.controller.mediavolume.currentvolume";

    void Initialize(@NonNull Context context);

    boolean IsInitialized();

    boolean Dispose();

    boolean IncreaseVolume();

    boolean DecreaseVolume();

    boolean SetVolume(int volume);

    boolean MuteVolume();

    boolean UnMuteVolume();

    int GetMaxVolume();

    int GetVolume();
}
