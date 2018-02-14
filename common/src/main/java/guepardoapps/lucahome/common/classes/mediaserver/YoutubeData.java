package guepardoapps.lucahome.common.classes.mediaserver;

import android.support.annotation.NonNull;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Locale;

import guepardoapps.lucahome.common.utils.Logger;

@SuppressWarnings({"WeakerAccess"})
public class YoutubeData implements IMediaServerClass {
    private static final String Tag = YoutubeData.class.getSimpleName();

    private boolean _isYoutubePlaying;
    private String _currentYoutubeId;
    private int _currentYoutubeVideoPosition;
    private int _currentYoutubeVideoDuration;
    private ArrayList<PlayedYoutubeVideo> _playedYoutubeVideos;

    public YoutubeData(
            boolean isYoutubePlaying,
            @NonNull String currentYoutubeId,
            int currentYoutubeVideoPosition,
            int currentYoutubeVideoDuration,
            @NonNull ArrayList<PlayedYoutubeVideo> playedYoutubeVideos) {
        _isYoutubePlaying = isYoutubePlaying;
        _currentYoutubeId = currentYoutubeId;
        _currentYoutubeVideoPosition = currentYoutubeVideoPosition;
        _currentYoutubeVideoDuration = currentYoutubeVideoDuration;
        _playedYoutubeVideos = playedYoutubeVideos;
    }

    public YoutubeData() {
        this(false, "", 0, 0, new ArrayList<>());
    }

    public boolean IsYoutubePlaying() {
        return _isYoutubePlaying;
    }

    public String GetCurrentYoutubeId() {
        return _currentYoutubeId;
    }

    public int GetCurrentYoutubeVideoPosition() {
        return _currentYoutubeVideoPosition;
    }

    public int GetCurrentYoutubeVideoDuration() {
        return _currentYoutubeVideoDuration;
    }

    public ArrayList<PlayedYoutubeVideo> GetPlayedYoutubeVideos() {
        return _playedYoutubeVideos;
    }

    @Override
    public String GetCommunicationString() {
        return new Gson().toJson(this);
    }

    @Override
    public void ParseCommunicationString(@NonNull String communicationString) throws Exception {
        if (communicationString.length() == 0) {
            throw new NullPointerException("CommunicationString may not be of length 0!");
        }
        Logger.getInstance().Debug(Tag, String.format(Locale.getDefault(), "CommunicationString is %s", communicationString));

        YoutubeData tmpYoutubeData = new Gson().fromJson(communicationString, YoutubeData.class);
        _isYoutubePlaying = tmpYoutubeData.IsYoutubePlaying();
        _currentYoutubeId = tmpYoutubeData.GetCurrentYoutubeId();
        _currentYoutubeVideoPosition = tmpYoutubeData.GetCurrentYoutubeVideoPosition();
        _currentYoutubeVideoDuration = tmpYoutubeData.GetCurrentYoutubeVideoDuration();
        _playedYoutubeVideos = tmpYoutubeData.GetPlayedYoutubeVideos();
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "{\"Class\":\"%s\",\"IsYoutubePlaying\":\"%s\",\"CurrentYoutubeId\":\"%s\",\"CurrentYoutubeVideoPosition\":%d,\"CurrentYoutubeVideoDuration\":%d,\"PlayedYoutubeVideos\":\"%s\"}",
                Tag, _isYoutubePlaying, _currentYoutubeId, _currentYoutubeVideoPosition, _currentYoutubeVideoDuration, _playedYoutubeVideos);
    }
}
