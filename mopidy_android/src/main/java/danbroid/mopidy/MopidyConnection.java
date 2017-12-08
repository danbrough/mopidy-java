package danbroid.mopidy;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;

import danbroid.mopidy.interfaces.Constants;
import danbroid.mopidy.interfaces.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Wraps the websocket communication with the Mopidy server
 */

public class MopidyConnection {

	private static final org.slf4j.Logger
			log = org.slf4j.LoggerFactory.getLogger(MopidyConnection.class);

	private String url;

	private WebSocket socket;
	private JsonParser parser = new JsonParser();
	private EventListener eventListener = new EventListenerImpl();
	private String version;
	private Core methods = new Core();

	private CallContext callContext = new CallContext();

	public Core getMethods() {
		return methods;
	}

	public void setEventListener(EventListener eventListener) {
		this.eventListener = eventListener;
	}


	public void start(String host, int port) {
		start("ws://" + host + ":" + port + "/mopidy/ws");
	}

	public void start(String url) {
		this.url = url;
		OkHttpClient client = new OkHttpClient();
		log.trace("start(): connecting to: {}", url);
		Request request = new Request.Builder().url(url).build();

		this.socket = client.newWebSocket(request, new WebSocketListener() {
			@Override
			public void onMessage(WebSocket webSocket, String text) {
				MopidyConnection.this.onMessage(text);
			}
		});


		call(methods.getVersion().setHandler(new Call.ResponseHandler<String>() {
			@Override
			public void onResponse(Call<String> call) {
				log.trace("mopidy version : " + call.getResult());
				MopidyConnection.this.version = version;
			}
		}));

	}


	private int requestID = 1;
	private HashMap<Integer, Call> calls = new HashMap<>();

	/**
	 * Dispatches call to the web socket
	 */
	public final void call(Call call) {
		prepareCall(call);
		String request = call.toString();
		log.trace("call(): request<{}>", request);
		socket.send(request);
	}

	protected void prepareCall(Call call) {
		call.requestID = requestID++;
		calls.put(call.requestID, call);
	}


	/**
	 * Shutsdown the socket.
	 */
	public void stop() {
		log.debug("stop(): {}", url);
		if (socket != null) {
			socket.close(1000, "Finished");
			socket = null;
			calls.clear();
		}
	}

	/**
	 * A message has been received
	 *
	 * @param text The JSON message
	 */
	public void onMessage(String text) {
		log.trace("onMessage(): {}", text);
		try {
			JsonElement e = parser.parse(text);
			if (e.isJsonObject()) {
				JsonObject o = e.getAsJsonObject();

				if (o.has(Constants.Key.ERROR)) {
					o = o.getAsJsonObject(Constants.Key.ERROR);
					String message = o.get(Constants.Key.MESSAGE).getAsString();
					int code = o.get(Constants.Key.CODE).getAsInt();
					JsonElement data = o.get(Constants.Key.DATA);
					onError(message, code, data);
					return;
				}

				if (o.has(Constants.Key.EVENT)) {
					processEvent(o.get(Constants.Key.EVENT).getAsString(), o);
					return;
				}

				if (o.has(Constants.Key.JSONRPC)) {
					processResponse(o.get(Constants.Key.ID).getAsInt(), o.get(Constants.Key.RESULT));
					return;
				}
				log.error("unhandled data: {}", text);

			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	protected void onError(String message, int code, JsonElement data) {
		log.error("onError() code: " + code + " {} data: {}", message, data);
	}

	protected void processResponse(int id, JsonElement result) {
		log.trace("processResponse(): id:{} result: {}", id, result);
		Call call = popCall(id);

		if (call == null) {
			log.error("no call found for request: " + id);
			return;
		}


		call.processResult(callContext, result);

	}

	protected Call popCall(int id) {
		return calls.remove(id);
	}

	/**
	 * Parse and dispatch event to the {@link #eventListener}
	 *
	 * @param event The name of the event
	 * @param o     The event data
	 * @return true if the event was processed else false
	 */
	protected boolean processEvent(String event, JsonObject o) {
		switch (event) {
			case "track_playback_paused":
				eventListener.onTrackPlaybackPaused(
						o.get(Constants.Key.TL_TRACK).getAsJsonObject(),
						o.get(Constants.Key.TIME_POSITION).getAsLong());
				break;
			case "track_playback_resumed":
				eventListener.onTrackPlaybackResumed(
						o.get(Constants.Key.TL_TRACK).getAsJsonObject(),
						o.get(Constants.Key.TIME_POSITION).getAsLong());
				break;
			case "track_playback_started":
				eventListener.onTrackPlaybackStarted(o.get(Constants.Key.TL_TRACK).getAsJsonObject());
				break;
			case "track_playback_ended":
				eventListener.onTrackPlaybackEnded(
						o.get(Constants.Key.TL_TRACK).getAsJsonObject(),
						o.get(Constants.Key.TIME_POSITION).getAsLong());
				break;
			case "playback_state_changed":
				eventListener.onPlaybackStateChanged(
						o.get(Constants.Key.OLD_STATE).getAsString(),
						o.get(Constants.Key.NEW_STATE).getAsString());
				break;
			case "tracklist_changed":
				eventListener.onTracklistChanged();
				break;
			case "playlists_loaded":
				eventListener.onPlaylistsLoaded();
				break;
			case "playlist_changed":
				eventListener.onPlaylistChanged(o.getAsJsonObject(Constants.Key.PLAYLIST));
				break;
			case "playlist_deleted":
				eventListener.onPlaylistDeleted(o.get(Constants.Key.URI).getAsString());
				break;
			case "stream_title_changed":
				eventListener.onStreamTitleChanged(o.get(Constants.Key.TITLE).getAsString());
				break;
			case "seeked":
				eventListener.onSeeked(o.get(Constants.Key.TIME_POSITION).getAsLong());
				break;
			case "mute_changed":
				eventListener.onMuteChanged(o.get(Constants.Key.MUTE).getAsBoolean());
				break;
			case "volume_changed":
				eventListener.onVolumeChanged(o.get(Constants.Key.VOLUME).getAsInt());
				break;
			case "options_changed":
				eventListener.onOptionsChanged();
				break;
			default:
				log.error("unknown event: " + o);
				return false;
		}
		return true;
	}


}
