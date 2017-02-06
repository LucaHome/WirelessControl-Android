package guepardoapps.mediamirror.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

import android.content.Context;
import android.os.AsyncTask;
import guepardoapps.lucahome.common.LucaHomeLogger;
import guepardoapps.lucahome.common.constants.Constants;
import guepardoapps.mediamirror.common.dto.PlayedYoutubeVideoDto;
import guepardoapps.mediamirror.common.enums.ServerAction;
import guepardoapps.toolset.controller.BroadcastController;

public class ClientTask extends AsyncTask<Void, Void, Void> {

	private static final String TAG = ClientTask.class.getName();
	private LucaHomeLogger _logger;

	private String _address;
	private int _port;

	private String _communication;
	private boolean _setNewCommunication;

	private String _response = "";

	private Context _context;
	private BroadcastController _broadcastController;

	public ClientTask(Context context, String address, int port) {
		_logger = new LucaHomeLogger(TAG);

		_address = address;
		_port = port;

		_logger.Info("Address is " + _address + " with port " + String.valueOf(_port));

		_context = context;
		_broadcastController = new BroadcastController(_context);
	}

	public void SetCommunication(String communication) {
		_communication = communication + "\n";
		_logger.Info("Communication is " + _communication);
		_setNewCommunication = true;
	}

	@Override
	protected Void doInBackground(Void... arg0) {
		_logger.Debug("executing Task");
		Socket socket = null;
		try {
			if (_setNewCommunication) {
				_logger.Debug("New communication is set");
				socket = new Socket(_address, _port);

				OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());
				_logger.Info("outputStreamWriter is " + outputStreamWriter.toString());

				BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
				_logger.Info("bufferedWriter is " + bufferedWriter.toString());

				PrintWriter printWriter = new PrintWriter(bufferedWriter, true);
				_logger.Info("printWriter is " + printWriter.toString());

				printWriter.println(_communication);
				_logger.Info("printWriter println");
				printWriter.flush();
				_logger.Info("printWriter flush");

				InputStreamReader inputStreamReader = new InputStreamReader(socket.getInputStream());
				_logger.Debug("inputStreamReader: " + inputStreamReader.toString());
				BufferedReader inputReader = new BufferedReader(inputStreamReader);
				_logger.Debug("inputReader: " + inputReader.toString());

				_response = inputReader.readLine();

				inputReader.close();
				inputStreamReader.close();
				printWriter.close();
				bufferedWriter.close();
				outputStreamWriter.close();
			} else {
				_logger.Warn("Communication not set!");
				_response += "Communication not set!";
			}
		} catch (UnknownHostException e) {
			_logger.Error(e.toString());
			_response = "UnknownHostException: " + e.toString();
		} catch (IOException e) {
			_logger.Error(e.toString());
			_response = "IOException: " + e.toString();
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					_logger.Error(e.toString());
				}
			}
			_communication = "";
			_setNewCommunication = false;
		}
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
		_logger.Info("Response: " + _response);

		if (_response == null) {
			_logger.Error("Response is null!");
			return;
		}

		String[] responseData = _response.split("\\:");
		if (responseData != null) {
			if (responseData.length > 1) {
				ServerAction responseAction = ServerAction.GetByString(responseData[0]);
				if (responseAction != null) {
					_logger.Info("ResponseAction: " + responseAction.toString());

					switch (responseAction) {
					case INCREASE_VOLUME:
					case DECREASE_VOLUME:
					case GET_CURRENT_VOLUME:
						String currentVolume = responseData[responseData.length - 1];
						_broadcastController.SendStringBroadcast(Constants.BROADCAST_MEDIAMIRROR_VOLUME,
								Constants.BUNDLE_CURRENT_RECEIVED_VOLUME, currentVolume);
						break;
					case GET_SAVED_YOUTUBE_IDS:
						String playedYoutubeData = responseData[responseData.length - 1];
						String[] rawYoutubeIds = playedYoutubeData.split("\\;");

						ArrayList<PlayedYoutubeVideoDto> playedYoutubeVideos = new ArrayList<PlayedYoutubeVideoDto>();
						for (String entry : rawYoutubeIds) {
							String[] data = entry.split("\\.");
							if (data.length == 3) {
								PlayedYoutubeVideoDto newData = new PlayedYoutubeVideoDto(Integer.parseInt(data[0]),
										data[1], Integer.parseInt(data[2]));
								_logger.Debug("Created new entry: " + newData.toString());
								playedYoutubeVideos.add(newData);
							} else {
								_logger.Warn("Wrong Size (" + data.length + ") for " + entry);
							}
						}

						_broadcastController.SendSerializableBroadcast(Constants.BROADCAST_PLAYED_YOUTUBE_VIDEOS,
								Constants.BUNDLE_PLAYED_YOUTUBE_VIDEOS, playedYoutubeVideos);
						break;
					default:
						break;
					}
				} else {
					_logger.Warn("responseAction is null!");
				}
			}
		}

		super.onPostExecute(result);
	}
}