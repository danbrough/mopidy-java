package danbroid.mopidy.util;

import android.net.Uri;

/**
 * Created by dan on 26/12/17.
 */
public class MediaIds {
	//private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MediaIds.class);

	public static final String EMPTY_ROOT = "__EMPTY_ROOT__";
	public static final String ROOT = "__ROOT__";
	public static final String SERVER = "__SERVER__";
	public static final String MOPIDY_ROOT = "__MOPIDY__";
	public static final String TRACKLIST = "__TRACKLIST__";
	public static final String PLAYLISTS = "__PLAYLISTS__";

	public static final String TRACKLIST_CLEAR = "__TRACKLIST_CLEAR__";
	public static final String M3U = "m3u";


	public static String idServer(String host, int port) {
		return SERVER + ":" + Uri.encode(host + ":" + port);
	}

	public static String idTracklistItem(int tlid) {
		return TRACKLIST + "/" + tlid;
	}


}
