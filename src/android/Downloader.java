package hr.integrator.cordova.plugins.downloader;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.database.Cursor;
import android.net.Uri;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.HashMap;
import java.util.Map;

import android.os.Environment;

public class Downloader extends CordovaPlugin {
	
  private static final String LOG_TAG = "Downloader";

  DownloadManager downloadManager;
  BroadcastReceiver receiver;

  private CallbackContext downloadReceiverCallbackContext = null;
  long downloadId = 0;

  @Override
    public void initialize(final CordovaInterface cordova, final CordovaWebView webView) {
        super.initialize(cordova, webView);

        downloadManager = (DownloadManager) cordova.getActivity()
                .getApplication()
                .getApplicationContext()
                .getSystemService(Context.DOWNLOAD_SERVICE);
    }

  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException  {
      if(action.equals("download")) return download(args.getJSONObject(0), callbackContext);

	  callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
      return false;
  }
  
  protected boolean download(JSONObject obj, CallbackContext callbackContext) throws JSONException {
    
    DownloadManager.Request request = deserialiseRequest(obj);
      
	  if (this.downloadReceiverCallbackContext != null) {
		  removeDownloadReceiver();
    }
    
    this.downloadReceiverCallbackContext = callbackContext;

    IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

    webView.getContext().registerReceiver(downloadReceiver, intentFilter);

    this.downloadId = downloadManager.enqueue(request);
      
    // Don't return any result now, since status results will be sent when events come in from broadcast receiver
    PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
    pluginResult.setKeepCallback(true);
    callbackContext.sendPluginResult(pluginResult);
    return true;
  }
    
  public void onDestroy() {
    removeDownloadReceiver();
  }

  private void removeDownloadReceiver(){
	  try {
		  webView.getContext().unregisterReceiver(downloadReceiver);
		} 
		catch (Exception e) {
			LOG.e(LOG_TAG, "Error unregistering download receiver: " + e.getMessage(), e);
		}
  }

  private void sendDownloadResult(long id, String locationUri) {
  
    try{
      JSONObject json = new JSONObject();
		  json.put("referenceId", Long.toString(id));
		  json.put("url", locationUri);

		  if (this.downloadReceiverCallbackContext != null) {
			  PluginResult result = new PluginResult(PluginResult.Status.OK, json);
			  result.setKeepCallback(true);
        this.downloadReceiverCallbackContext.sendPluginResult(result);
      }
    }
    catch(JSONException e){
      LOG.e(LOG_TAG, "Error preparing download result: " + e.getMessage(), e);
    }
  }

  private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
  
    @Override
    public void onReceive(Context context, Intent intent) {
      
      long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
      
      DownloadManager.Query query = new DownloadManager.Query();
      query.setFilterById(referenceId);
      Cursor cursor = downloadManager.query(query);
      
      if(cursor.moveToFirst()){
        String downloadedTo = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
        int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));

        switch(status){
          case DownloadManager.STATUS_SUCCESSFUL:
            sendDownloadResult(referenceId, downloadedTo);
            break;
          case DownloadManager.STATUS_FAILED:
            downloadReceiverCallbackContext.error(reason);
            break;
          case DownloadManager.STATUS_PAUSED:
          case DownloadManager.STATUS_PENDING:
          case DownloadManager.STATUS_RUNNING:
          default:
            break;
        }
      }
    }
  };

  protected DownloadManager.Request deserialiseRequest(JSONObject obj) throws JSONException {
    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(obj.getString("uri")));

    req.setTitle(obj.optString("title"));
    req.setDescription(obj.optString("description"));
    req.setMimeType(obj.optString("mimeType", null));

    if (obj.has("destinationInExternalFilesDir")) {
      Context context = cordova.getActivity()
                               .getApplication()
                               .getApplicationContext();
      
      JSONObject params = obj.getJSONObject("destinationInExternalFilesDir");
      req.setDestinationInExternalFilesDir(context, params.optString("dirType"), params.optString("subPath"));
    }
    else if (obj.has("destinationInExternalPublicDir")) {
      JSONObject params = obj.getJSONObject("destinationInExternalPublicDir");
      req.setDestinationInExternalPublicDir(params.optString("dirType"), params.optString("subPath"));
    }
    else if (obj.has("destinationUri")) {
      req.setDestinationUri(Uri.parse(obj.getString("destinationUri")));
    }
      
    req.setVisibleInDownloadsUi(obj.optBoolean("visibleInDownloadsUi", true));
    req.setNotificationVisibility(obj.optInt("notificationVisibility"));
    
    if (obj.has("headers")) {
      JSONArray arrHeaders = obj.optJSONArray("headers");
      for (int i = 0; i < arrHeaders.length(); i++) {
        JSONObject headerObj = arrHeaders.getJSONObject(i);
        req.addRequestHeader(headerObj.optString("header"), headerObj.optString("value"));
      }
    }
    
    return req;
  }
}