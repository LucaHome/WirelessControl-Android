package guepardoapps.lucahome.common.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import guepardoapps.lucahome.basic.classes.SerializableList;
import guepardoapps.lucahome.basic.controller.BroadcastController;
import guepardoapps.lucahome.basic.controller.NetworkController;
import guepardoapps.lucahome.basic.controller.ReceiverController;
import guepardoapps.lucahome.basic.utils.Logger;
import guepardoapps.lucahome.basic.utils.Tools;
import guepardoapps.lucahome.common.classes.LucaUser;
import guepardoapps.lucahome.common.classes.Movie;
import guepardoapps.lucahome.common.constants.Constants;
import guepardoapps.lucahome.common.converter.JsonDataToMovieConverter;
import guepardoapps.lucahome.common.enums.LucaServerAction;
import guepardoapps.lucahome.common.controller.DownloadController;
import guepardoapps.lucahome.common.controller.SettingsController;
import guepardoapps.lucahome.common.interfaces.services.IDataService;
import guepardoapps.lucahome.common.service.broadcasts.content.ObjectChangeFinishedContent;

@SuppressWarnings({"unused", "WeakerAccess"})
public class MovieService implements IDataService {
    public static class MovieListDownloadFinishedContent extends ObjectChangeFinishedContent {
        public SerializableList<Movie> MovieList;

        MovieListDownloadFinishedContent(@NonNull SerializableList<Movie> movieList, boolean succcess, @NonNull byte[] response) {
            super(succcess, response);
            MovieList = movieList;
        }
    }

    public static final String MovieIntent = "MovieIntent";

    public static final String MovieDownloadFinishedBroadcast = "guepardoapps.lucahome.data.service.movie.download.finished";
    public static final String MovieDownloadFinishedBundle = "MovieDownloadFinishedBundle";

    public static final String MovieUpdateFinishedBroadcast = "guepardoapps.lucahome.data.service.movie.update.finished";
    public static final String MovieUpdateFinishedBundle = "MovieUpdateFinishedBundle";

    private static final MovieService SINGLETON = new MovieService();
    private boolean _isInitialized;

    private static final String TAG = MovieService.class.getSimpleName();

    private static final int MIN_TIMEOUT_MIN = 60;
    private static final int MAX_TIMEOUT_MIN = 24 * 60;

    private Date _lastUpdate;

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
                SerializableList<Movie> movieList = JsonDataToMovieConverter.getInstance().GetList(contentResponse);
                if (movieList == null) {
                    Logger.getInstance().Error(TAG, "Converted movieList is null!");
                    sendFailedDownloadBroadcast("Converted movieList is null!");
                    return "";
                }

                _lastUpdate = new Date();
                _movieList = sortListAlphabetically(movieList);
                _broadcastController.SendSerializableBroadcast(
                        MovieDownloadFinishedBroadcast,
                        MovieDownloadFinishedBundle,
                        new MovieListDownloadFinishedContent(_movieList, true, Tools.CompressStringToByteArray("Download finished")));
            }
            return "Success";
        }
    }

    private BroadcastController _broadcastController;
    private DownloadController _downloadController;
    private NetworkController _networkController;
    private ReceiverController _receiverController;

    private SerializableList<Movie> _movieList = new SerializableList<>();

    private BroadcastReceiver _movieDownloadFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DownloadController.DownloadFinishedBroadcastContent content = (DownloadController.DownloadFinishedBroadcastContent) intent.getSerializableExtra(DownloadController.DownloadFinishedBundle);

            if (content.CurrentDownloadType != DownloadController.DownloadType.Movie) {
                return;
            }

            String contentResponse = Tools.DecompressByteArrayToString(DownloadStorageService.getInstance().GetDownloadResult(content.CurrentDownloadType));

            if (contentResponse.contains("Error") || contentResponse.contains("ERROR")
                    || contentResponse.contains("Canceled") || contentResponse.contains("CANCELED")
                    || content.FinalDownloadState != DownloadController.DownloadState.Success) {
                Logger.getInstance().Error(TAG, contentResponse);
                sendFailedDownloadBroadcast(contentResponse);
                return;
            }

            if (!content.Success) {
                Logger.getInstance().Error(TAG, "Download was not successful!");
                sendFailedDownloadBroadcast("Download was not successful!");
                return;
            }

            new AsyncConverterTask().execute(contentResponse);
        }
    };

    private BroadcastReceiver _movieUpdateFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DownloadController.DownloadFinishedBroadcastContent content = (DownloadController.DownloadFinishedBroadcastContent) intent.getSerializableExtra(DownloadController.DownloadFinishedBundle);

            if (content.CurrentDownloadType != DownloadController.DownloadType.MovieUpdate) {
                return;
            }

            String contentResponse = Tools.DecompressByteArrayToString(DownloadStorageService.getInstance().GetDownloadResult(content.CurrentDownloadType));

            if (contentResponse.contains("Error") || contentResponse.contains("ERROR")
                    || contentResponse.contains("Canceled") || contentResponse.contains("CANCELED")
                    || content.FinalDownloadState != DownloadController.DownloadState.Success) {
                Logger.getInstance().Error(TAG, contentResponse);
                sendFailedUpdateBroadcast(contentResponse);
                return;
            }

            if (!content.Success) {
                Logger.getInstance().Error(TAG, "Download was not successful!");
                sendFailedUpdateBroadcast(contentResponse);
                return;
            }

            _lastUpdate = new Date();

            _broadcastController.SendSerializableBroadcast(
                    MovieUpdateFinishedBroadcast,
                    MovieUpdateFinishedBundle,
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

    private MovieService() {
    }

    public static MovieService getInstance() {
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

        _broadcastController = new BroadcastController(context);
        _downloadController = new DownloadController(context);
        _networkController = new NetworkController(context);
        _receiverController = new ReceiverController(context);

        _receiverController.RegisterReceiver(_movieDownloadFinishedReceiver, new String[]{DownloadController.DownloadFinishedBroadcast});
        _receiverController.RegisterReceiver(_movieUpdateFinishedReceiver, new String[]{DownloadController.DownloadFinishedBroadcast});

        _receiverController.RegisterReceiver(_homeNetworkAvailableReceiver, new String[]{NetworkController.WIFIReceiverInHomeNetworkBroadcast});
        _receiverController.RegisterReceiver(_homeNetworkNotAvailableReceiver, new String[]{NetworkController.WIFIReceiverNoHomeNetworkBroadcast});

        SetReloadTimeout(reloadTimeout);

        _isInitialized = true;
    }

    @Override
    public void Dispose() {
        _reloadHandler.removeCallbacks(_reloadListRunnable);
        _receiverController.Dispose();
        _isInitialized = false;
    }

    @Override
    public SerializableList<Movie> GetDataList() {
        return _movieList;
    }

    public ArrayList<String> GetTitleList() {
        ArrayList<String> titleList = new ArrayList<>();
        for (int index = 0; index < _movieList.getSize(); index++) {
            titleList.add(_movieList.getValue(index).GetTitle());
        }
        return new ArrayList<>(titleList.stream().distinct().collect(Collectors.toList()));
    }

    public ArrayList<String> GetGenreList() {
        ArrayList<String> genreList = new ArrayList<>();
        for (int index = 0; index < _movieList.getSize(); index++) {
            genreList.add(_movieList.getValue(index).GetGenre());
        }
        return new ArrayList<>(genreList.stream().distinct().collect(Collectors.toList()));
    }

    public ArrayList<String> GetDescriptionList() {
        ArrayList<String> descriptionList = new ArrayList<>();
        for (int index = 0; index < _movieList.getSize(); index++) {
            descriptionList.add(_movieList.getValue(index).GetDescription());
        }
        return new ArrayList<>(descriptionList.stream().distinct().collect(Collectors.toList()));
    }

    public Movie GetById(int id) {
        for (int index = 0; index < _movieList.getSize(); index++) {
            Movie entry = _movieList.getValue(index);
            if (entry.GetId() == id) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public int GetHighestId() {
        int highestId = -1;
        for (int index = 0; index < _movieList.getSize(); index++) {
            int id = _movieList.getValue(index).GetId();
            if (id > highestId) {
                highestId = id;
            }
        }
        return highestId;
    }

    @Override
    public SerializableList<Movie> SearchDataList(@NonNull String searchKey) {
        SerializableList<Movie> foundMovies = new SerializableList<>();
        for (int index = 0; index < _movieList.getSize(); index++) {
            Movie entry = _movieList.getValue(index);
            if (entry.toString().contains(searchKey)) {
                foundMovies.addValue(entry);
            }
        }
        return sortListAlphabetically(foundMovies);
    }

    @Override
    public void LoadData() {
        LucaUser user = SettingsController.getInstance().GetUser();
        if (user == null) {
            sendFailedDownloadBroadcast("No user");
            return;
        }

        String requestUrl = "http://"
                + SettingsController.getInstance().GetServerIp()
                + Constants.ACTION_PATH
                + user.GetName() + "&password=" + user.GetPassphrase()
                + "&action=" + LucaServerAction.GET_MOVIES.toString();

        _downloadController.SendCommandToWebsiteAsync(requestUrl, DownloadController.DownloadType.Movie, true);
    }

    public void UpdateMovie(@NonNull Movie entry) {
        LucaUser user = SettingsController.getInstance().GetUser();
        if (user == null) {
            sendFailedUpdateBroadcast("No user");
            return;
        }

        String requestUrl = String.format(Locale.getDefault(), "http://%s%s%s&password=%s&action=%s",
                SettingsController.getInstance().GetServerIp(), Constants.ACTION_PATH,
                user.GetName(), user.GetPassphrase(),
                entry.CommandUpdate());

        _downloadController.SendCommandToWebsiteAsync(requestUrl, DownloadController.DownloadType.MovieUpdate, true);
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

    public Date GetLastUpdate() {
        return _lastUpdate;
    }

    private void sendFailedDownloadBroadcast(@NonNull String response) {
        if (response.length() == 0) {
            response = "Download for movies failed!";
        }

        _broadcastController.SendSerializableBroadcast(
                MovieDownloadFinishedBroadcast,
                MovieDownloadFinishedBundle,
                new MovieListDownloadFinishedContent(_movieList, false, Tools.CompressStringToByteArray(response)));
    }

    private void sendFailedUpdateBroadcast(@NonNull String response) {
        if (response.length() == 0) {
            response = "Update of movie failed!";
        }

        _broadcastController.SendSerializableBroadcast(
                MovieUpdateFinishedBroadcast,
                MovieUpdateFinishedBundle,
                new ObjectChangeFinishedContent(false, Tools.CompressStringToByteArray(response)));
    }

    private SerializableList<Movie> sortListAlphabetically(@NonNull SerializableList<Movie> movieList) {
        List<Movie> tmpMovieList = new ArrayList<>();
        for (int index = 0; index < movieList.getSize(); index++) {
            tmpMovieList.add(movieList.getValue(index));
        }

        tmpMovieList.sort(Comparator.comparing(Movie::GetTitle));

        SerializableList<Movie> returnMovieList = new SerializableList<>();
        for (Movie returnMovie : tmpMovieList) {
            returnMovieList.addValue(returnMovie);
        }

        return returnMovieList;
    }
}
