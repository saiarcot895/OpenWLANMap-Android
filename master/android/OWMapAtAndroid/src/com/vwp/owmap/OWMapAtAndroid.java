package com.vwp.owmap;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.net.wifi.*;
import android.os.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.preference.*;
import android.provider.Settings;

import com.vwp.libwlocate.*;
import com.vwp.libwlocate.map.*;


public class OWMapAtAndroid extends Activity implements OnClickListener, OnItemClickListener, Runnable //implements SensorEventListener
{
    public static final int MAX_RADIUS = 98;
    public static final int RECV_TIMEOUT = 75000;

    public static final int FLAG_NO_NET_ACCESS = 0x0001;

    static final int VIEW_MODE_MAIN = 1;
    static final int VIEW_MODE_MAP = 2;
    static final int VIEW_MODE_FF_LIST = 3;

    static final int THREAD_MODE_SCAN = 1;
    static final int THREAD_MODE_UPLOAD = 2;
    static final int THREAD_MODE_MAP = 3;

    public static final String MAP_FILE = "lastmap.png";
    public static final String WSCAN_FILE = "wscndata";
    public static final String WFREI_FILE = "wfreidata";
    public static final String MAP_DATA_FILE = "mapdata.png";
    public static final String MAP_MAX_FILE = "maxdata.png";

    public static final int ALERT_OK = 0x0001;
    public static final int ALERT_NO_EXIT = 0x0002;
    public static final int ALERT_SHOW_MAP = 0x0003;
    public static final int ALERT_GPSWARN = 0x0004;

    //   private SensorManager          mSensorManager=null;
//   private Sensor                 mAccelerometer=null;
//   private EditText               accXField,accYField,accZField,accuracyField;
    CheckBox noNetAccCB;
    private OWMapAtAndroid ctx;
    static boolean showMap = false, showTele = false, doTrack = true, hasPosLock = false;
    private TextView rankText;
    ScannerHandler scannerHandler = null;
    //private PowerManager.WakeLock wl = null;
    private Vector<WMapSlimEntry> freeHotspotList;
    private ListView ffLv;

    private static int textSizeVal = 1;

    void simpleAlert(String text, String title, int mode) {
        AlertDialog ad = null;

        try {
            ad = new AlertDialog.Builder(ctx).create();
            if (mode != ALERT_OK) {
                ad.setCancelable(false);
                ad.setCanceledOnTouchOutside(false);
            }
            if (text != null) ad.setMessage(text);
            else ad.setMessage("missing ressource!");
            if (title != null) ad.setTitle(title);
            if (mode == ALERT_OK) {
                ad.setButton(DialogInterface.BUTTON_POSITIVE, ctx.getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
            } else if (mode == ALERT_GPSWARN) {
                ad.setButton(DialogInterface.BUTTON_POSITIVE, ctx.getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Intent myIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                        ctx.startActivity(myIntent);
                    }
                });
            } else if (mode == ALERT_NO_EXIT) {
                ad.setButton(DialogInterface.BUTTON_POSITIVE, ctx.getResources().getText(R.string.exit), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ScanService.running = false;
                        stopService(new Intent(ctx, ScanService.class));
                        dialog.dismiss();
                        finish();
                        //                 System.exit(0);
                    }
                });
                ad.setButton(DialogInterface.BUTTON_NEGATIVE, ctx.getResources().getText(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
            } else if (mode == ALERT_SHOW_MAP) {
                ad.setButton(DialogInterface.BUTTON_POSITIVE, ctx.getResources().getText(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        OWMapAtAndroid.sendMessage(OWMapAtAndroid.ScannerHandler.MSG_OPEN_PRG_DLG, 0, 0, ctx.getResources().getText(R.string.loading_map).toString());
                        scannerHandler.sendEmptyMessage(ScannerHandler.MSG_SHOW_MAP);
                        dialog.dismiss();
                    }
                });
                ad.setButton(DialogInterface.BUTTON_NEGATIVE, ctx.getResources().getText(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
            }
            ad.show();
        } catch (Exception e) // to avoid strange exceptions when app is in background
        {
            if (ad != null) ad.dismiss();
        }
    }


    static class ScannerHandler extends Handler {
        public static final int MSG_ADD_ENTRY = 1;
        public static final int MSG_REM_ENTRY = 2;
        public static final int MSG_UPD_POS = 3;
        public static final int MSG_OPEN_PRG_DLG = 4;
        public static final int MSG_CLOSE_PRG_DLG = 5;
        public static final int MSG_GET_FREEHOTSPOT_S_DL2 = 6;
        public static final int MSG_SHOW_MAP2 = 7;
        public static final int MSG_SHOW_MAP = 8;
        public static final int MSG_DL_FAILURE = 9;
        public static final int MSG_SIMPLE_ALERT = 10;
        public static final int MSG_UPD_LOC_STATE = 11;
        public static final int MSG_UPD_AP_COUNT = 12;
        public static final int MSG_UPD_LIVE_MAP = 14;
        public static final int MSG_TELEMETRY = 16;
        public static final int MSG_TOAST = 17;
        public static final int MSG_GET_FREEHOTSPOT_POS_DL = 18;
        public static final int MSG_GET_FREEHOTSPOT_POS_DL2 = 19;

        private Lock lock = new ReentrantLock();

        TableLayout parentTable, mapTable;
        TextView latTableText, lonTableText, locStateText, bigCntText, apCountText, bigOpenCntText;
        LiveMapView liveMapView;
        ProgressDialog progDlg;
        MapView mapView;
        boolean bigCounter = false;
        boolean lastShowMap = true;
        OWMapAtAndroid owmp;
        private TotalMap mapOverlay;
        FrameLayout rootLayout;


        private void dbgRemEntry(Message msg) {
            WMapEntry entry; // lh54uvd

            lock.lock();
            entry = (WMapEntry) msg.obj;
            parentTable.removeView(entry.getRow());
            entry.setFlags(entry.getFlags() & ~WMapEntry.FLAG_IS_VISIBLE);
            lock.unlock();
        }

        private void dbgUpdLocState(Message msg) {
            if (msg.arg1 < 0)
                locStateText.setText(ScanService.getScanData().getCtx().getResources().getText(R.string.waitGPS) + " ");
            else {
                String locText = "";
                loc_info locationInfo;

                if ((msg.arg1 > OWMapAtAndroid.MAX_RADIUS * 1000) || (msg.arg2 == loc_info.LOC_METHOD_NONE)) {
                    locText = ScanService.getScanData().getCtx().getResources().getText(R.string.waitGPS) + " ";
                    hasPosLock = false;
                } else hasPosLock = true;
                locationInfo = (loc_info) msg.obj;
                if (locationInfo.lastLocMethod == loc_info.LOC_METHOD_GPS) {
                    locText = "GPS";
                    if (bigCounter) bigCntText.setTextColor(0xFFD9D9FF);
                } else if (locationInfo.lastLocMethod == loc_info.LOC_METHOD_LIBWLOCATE) {
                    locText = "WLAN";
                    if (bigCounter) bigCntText.setTextColor(0xFFFFFF64);
                } else if (locationInfo.lastLocMethod == -1) {
                    locText = "Mix";
                    if (bigCounter) bigCntText.setTextColor(0xFFFF6464);
                }
                if ((msg.arg1 > 0) && (msg.arg2 != loc_info.LOC_METHOD_NONE))
                    locText = locText + " +/-" + (msg.arg1 / 1000.0) + " m";
                locStateText.setText(locText);
                if (bigCounter) bigCntText.invalidate();
            }
        }


        private void dbgUpdApCount(Message msg) {
            apCountText.setText(ScanService.getScanData().getCtx().getResources().getText(R.string.ap_count).toString() + ": " + msg.arg1); //pj   q6uikcde
            if (bigCounter) {
                bigCntText.setText("" + msg.arg1);
                bigOpenCntText.setText("" + msg.arg2);
            } else {
                bigCntText.setText("");
                bigOpenCntText.setText("");
            }
        }


        private void openPrgDlg(Message msg) {
            if (progDlg != null) return;
            progDlg = new ProgressDialog(owmp);
            if (msg.arg1 > 0) {
                progDlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progDlg.setMax(msg.arg1);
            }
            progDlg.setTitle((String) msg.obj);
            progDlg.setCanceledOnTouchOutside(false);
            progDlg.setCancelable(false);
            progDlg.show();
        }


        private class DownloadMapDataTask extends AsyncTask<Void, Void, Void> {
            protected Void doInBackground(Void... params) {
                mapOverlay = new TotalMap(owmp, ScanService.getScanData().getOwnBSSID());
                owmp.scannerHandler.sendEmptyMessage(ScannerHandler.MSG_SHOW_MAP2);
                return null;
            }
        }

        private class DownloadFreeHotspotDataTask extends AsyncTask<Void, Void, Void> {
            protected Void doInBackground(Void... params) {
                String outString;
                HttpURLConnection c = null;
                DataOutputStream os = null;
                BufferedReader is = null;

                outString = ScanService.getScanData().getLat() + "\n" + ScanService.getScanData().getLon() + "\n";
                try {
                    URL connectURL = new URL("http://www.openwlanmap.org/android/freifunk.php");
                    c = (HttpURLConnection) connectURL.openConnection();
                    if (c == null) {
                        owmp.scannerHandler.sendEmptyMessage(OWMapAtAndroid.ScannerHandler.MSG_CLOSE_PRG_DLG);
                        return null;
                    }
                    c.setDoOutput(true); // enable POST
                    c.setRequestMethod("POST");
                    c.addRequestProperty("Content-Type", "application/x-www-form-urlencoded, *.*");
                    c.addRequestProperty("Content-Length", "" + outString.length());
                    os = new DataOutputStream(c.getOutputStream());
                    os.write(outString.getBytes());
                    os.flush();
                    c.getResponseCode();
                    os.close();
                    is = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    outString = is.readLine();
                    owmp.freeHotspotList = new Vector<WMapSlimEntry>();
                    if (outString.equalsIgnoreCase("0")) {
                        try {
                            while (is.ready()) {
                                WMapSlimEntry entry = new WMapSlimEntry(is.readLine(), is.readLine());
                                owmp.freeHotspotList.add(entry);
                            }
                        } catch (NumberFormatException nfe) {

                        }
                    }
                    c.disconnect();
                    c = null;
                } catch (IOException ioe) {
                } finally {
                    try {
                        if (os != null) os.close();
                        if (is != null) is.close();
                        if (c != null) c.disconnect();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
                owmp.scannerHandler.sendEmptyMessage(OWMapAtAndroid.ScannerHandler.MSG_GET_FREEHOTSPOT_POS_DL2);
                return null;
            }
        }


        public void handleMessage(Message msg) {

            super.handleMessage(msg);

            if (!ScanService.getScanData().isActive()) return;
            switch (msg.what) {
                case MSG_TOAST:
                    Toast.makeText(owmp, (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_TELEMETRY:
                    if (showTele) {
                        liveMapView.setTelemetryData((TelemetryData) msg.obj);
                        liveMapView.invalidate();
                    }
                    break;
                case MSG_GET_FREEHOTSPOT_POS_DL: {
                    msg.obj = owmp.getResources().getText(R.string.loading_data).toString();
                    openPrgDlg(msg);
                    new DownloadFreeHotspotDataTask().execute(null, null, null);
                    break;
                }
                case MSG_GET_FREEHOTSPOT_POS_DL2: {
                    if ((owmp.freeHotspotList != null) && (owmp.freeHotspotList.size() > 0)) {
                        owmp.ffLv = new ListView(owmp);
                        ArrayAdapter<String> adapter = new ArrayAdapter<String>(owmp, R.layout.listviewitem, R.id.listViewItemText);
                        for (int i = 0; i < owmp.freeHotspotList.size(); i++) {
                            WMapSlimEntry entry = owmp.freeHotspotList.elementAt(i);

                            String text = "" + GeoUtils.latlon2dist(ScanService.getScanData().getLat(), ScanService.getScanData().getLon(), entry.lat, entry.lon);
                            text = text.substring(0, 8);
                            text = text + " km";
                            adapter.add(text);
                        }
                        owmp.ffLv.setAdapter(adapter);
                        owmp.ffLv.setOnItemClickListener(owmp);
                        owmp.scannerHandler.rootLayout.addView(owmp.ffLv);
                        ScanService.getScanData().setViewMode(VIEW_MODE_FF_LIST);
                    }
                    OWMapAtAndroid.sendMessage(OWMapAtAndroid.ScannerHandler.MSG_CLOSE_PRG_DLG, 0, 0, null);
                    break;
                }
                case MSG_UPD_LIVE_MAP:
                    if (showMap) {
                        liveMapView.updateViewTiles(ScanService.getScanData().getLat(), ScanService.getScanData().getLon());
                        if (!showTele) liveMapView.invalidate();
                    }
                    liveMapView.setVisibility(showMap ? View.VISIBLE : View.INVISIBLE);
                    break;
                case MSG_ADD_ENTRY: {
                    WMapEntry entry;

                    lock.lock();
                    entry = (WMapEntry) msg.obj;
                    if ((entry.getFlags() & WMapEntry.FLAG_UI_USED) == 0) entry.createUIData(owmp);
                    if ((entry.getFlags() & WMapEntry.FLAG_IS_VISIBLE) == 0) {
                        parentTable.addView(entry.getRow(), 1, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        entry.setFlags(entry.getFlags() | WMapEntry.FLAG_IS_VISIBLE);
                    }
                    lock.unlock();
                    break;
                }
                case MSG_REM_ENTRY: {
                    dbgRemEntry(msg);
                    break;
                }
                case MSG_UPD_POS: {
                    try {
                        WMapEntry entry;

                        entry = (WMapEntry) msg.obj;
                        if (showMap) {
                            if (lastShowMap != showMap) {
                                latTableText.setText("");
                                lonTableText.setText("");
                                entry.getLatView().setText("");
                                entry.getLonView().setText("");
                                mapTable.setColumnStretchable(0, false);
                                lastShowMap = showMap;
//                        liveMapView.setBackgroundColor(0xFF555570);
                            }
                        } else {
                            entry.getLatView().setText("" + (float) entry.getLat());
                            entry.getLonView().setText("" + (float) entry.getLon());
                            if (lastShowMap != showMap) {
                                latTableText.setText(owmp.getResources().getText(R.string.lat));
                                lonTableText.setText(owmp.getResources().getText(R.string.lon));
                                mapTable.setColumnStretchable(0, true);
                                lastShowMap = showMap;
//                        liveMapView.setBackgroundColor(0x00000000);
                            }
                        }
                    } catch (Exception e) {
                        // just in case a non-existing entry is used
                    }
                    break;
                }
                case MSG_OPEN_PRG_DLG: {
                    openPrgDlg(msg);
                    break;
                }
/*            case MSG_UPD_PRG_DLG:
            {
               if (progDlg!=null)
                progDlg.setTitle((String)msg.obj);
               break;
            }
            case MSG_UPD_PRG_PRG:
            {
               if (progDlg!=null)
                progDlg.setProgress(msg.arg1);
               break;
            }*/
                case MSG_CLOSE_PRG_DLG: {
                    if (progDlg != null) try {
                        progDlg.dismiss();
                    } catch (Exception e) {
                        // in case it is called while the user has put the app into background
                    }
                    progDlg = null;
                    if (mapView != null) mapView.invalidate();
                    owmp.updateRank();
                    break;
                }
                case MSG_SIMPLE_ALERT: {
                    owmp.simpleAlert((String) msg.obj, null, ALERT_OK);
                    break;
                }
                case MSG_SHOW_MAP: {
                    int mode;

                    SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(owmp);
                    if (SP.getString("mapType", "2").equalsIgnoreCase("1"))
                        mode = GeoUtils.MODE_OSM;
                    else if (SP.getString("mapType", "4").equalsIgnoreCase("1"))
                        mode = GeoUtils.MODE_OSM_NIGHT;
                    else mode = GeoUtils.MODE_GMAP;

                    mapView = new MapView(owmp, ((ScanService.getScanData().getFlags() & OWMapAtAndroid.FLAG_NO_NET_ACCESS) == 0), mode);
                    new DownloadMapDataTask().execute(null, null, null);
                    break;
                }
                case MSG_SHOW_MAP2: {
                    mapView.setOverlay(mapOverlay);
                    rootLayout.addView(mapView);

                    ScanService.getScanData().setViewMode(VIEW_MODE_MAP);
                    ScanService.getScanData().setThreadMode(THREAD_MODE_MAP);
                    ScanService.getScanData().getWatchThread().interrupt();

                    owmp.scannerHandler.sendEmptyMessage(OWMapAtAndroid.ScannerHandler.MSG_CLOSE_PRG_DLG);
                    break;
                }
                case MSG_DL_FAILURE: {
                    owmp.onBackPressed();
                    owmp.simpleAlert(owmp.getResources().getText(R.string.map_dl_failure).toString(), null, ALERT_OK);
                    break;
                }
                case MSG_UPD_LOC_STATE: {
                    dbgUpdLocState(msg);
                    break;
                }
                case MSG_UPD_AP_COUNT: {
                    dbgUpdApCount(msg);
                    break;
                }
                default: //bussi
                    assert (false);
                    break;
            }
        }
    }


    static void setTextStyle(Context ctx, TextView text) {
        if (textSizeVal == 1) text.setTextAppearance(ctx, android.R.style.TextAppearance_Small);
        else if (textSizeVal == 2)
            text.setTextAppearance(ctx, android.R.style.TextAppearance_Medium);
        else text.setTextAppearance(ctx, android.R.style.TextAppearance_Large);
    }


    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("init", true);
    }


    private void createUI() {
        scannerHandler.rootLayout = (FrameLayout) findViewById(R.id.rootLayout);
        scannerHandler.parentTable = (TableLayout) findViewById(R.id.currListTableLayout);
        scannerHandler.mapTable = (TableLayout) findViewById(R.id.mapTableLayout);
        scannerHandler.latTableText = (TextView) findViewById(R.id.latTableText);
        scannerHandler.lonTableText = (TextView) findViewById(R.id.lonTableText);

        setTextStyle(ctx, (TextView) findViewById(R.id.textView1));
        setTextStyle(ctx, (TextView) findViewById(R.id.textView2));
        setTextStyle(ctx, (TextView) findViewById(R.id.textView3));
        setTextStyle(ctx, scannerHandler.latTableText);
        setTextStyle(ctx, scannerHandler.lonTableText);

        TableRow mapTableRow = (TableRow) findViewById(R.id.mapTableRow);
        scannerHandler.liveMapView = new LiveMapView(this);
        mapTableRow.addView(scannerHandler.liveMapView);//,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        noNetAccCB = (CheckBox) findViewById(R.id.noNetAccessBox);
        noNetAccCB.setOnClickListener(this);
        scannerHandler.locStateText = (TextView) findViewById(R.id.locStateText);
        setTextStyle(ctx, scannerHandler.locStateText);
        scannerHandler.locStateText.setText(ctx.getResources().getText(R.string.waitGPS) + " ");
        scannerHandler.apCountText = (TextView) findViewById(R.id.APCountText);
        setTextStyle(ctx, scannerHandler.apCountText);
        rankText = (TextView) findViewById(R.id.rankText);
        setTextStyle(ctx, rankText);
        scannerHandler.bigCntText = (TextView) findViewById(R.id.bigCntText);
        scannerHandler.bigOpenCntText = (TextView) findViewById(R.id.bigOpenCntText);
        scannerHandler.bigOpenCntText.setTextColor(0xFFAAFFAA);
    }


    private void createService(Bundle savedInstanceState) {
        if ((savedInstanceState == null) || (!savedInstanceState.getBoolean("init")) /*|| (ScanService.scanData==null)*/) {
            ScanService.getScanData().init(this);
            loadConfig();
            startService(new Intent(this, ScanService.class));
        }
        if (ScanService.getScanData().getWifiManager() == null) ScanService.getScanData().init(this);
        if ((ScanService.getScanData().getFlags() & FLAG_NO_NET_ACCESS) != 0) {
            noNetAccCB.setChecked(true);
            ScanService.getScanData().getWifiManager().createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "OpenWLANMap");
        } else {
            ScanService.getScanData().getWifiManager().createWifiLock(WifiManager.WIFI_MODE_FULL, "OpenWLANMap");
        }
    }


    private void setupInitial() {
        WifiInfo wifiInfo = ScanService.getScanData().getWifiManager().getConnectionInfo();
        if ((wifiInfo != null) && (wifiInfo.getMacAddress() != null))
            ScanService.getScanData().setOwnBSSID(wifiInfo.getMacAddress().replace(":", "").replace(".", "").toUpperCase(Locale.US));
        else ScanService.getScanData().setOwnBSSID("00DEADBEEF00");
        updateRank();
    }

    //LKlko9ugnko9
    //iiop,mk,ööäö

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        ctx = this;
        boolean sendToBack = false;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if (getIntent() != null) {
            if (getIntent().getBooleanExtra("autostarted", false)) {
                if (!SP.getBoolean("autoStart", true))
                    System.exit(0);
                else sendToBack = true;
            }
            getIntent().putExtra("autostarted", false);
        }

        textSizeVal = Integer.parseInt(SP.getString("textSize", "1"));

        super.onCreate(savedInstanceState);
        scannerHandler = new ScannerHandler();
        scannerHandler.owmp = this;
        setContentView(R.layout.main);


        //     mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        //     mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        createUI();

        createService(savedInstanceState);
        ScanService.getScanData().setHudCounter(SP.getBoolean("hudCounter", false));
        setupInitial();

        sendMessage(ScannerHandler.MSG_UPD_AP_COUNT, ScanService.getScanData().getStoredValues(), ScanService.getScanData().getFreeHotspotWLANs(), null);
        if (sendToBack) moveTaskToBack(true);
        showMap = SP.getBoolean("showMap", false);
        scannerHandler.lastShowMap = !showMap;
        getTelemetryConfig(SP);
    }


    private void getTelemetryConfig(SharedPreferences SP) {
        String txt = SP.getString("telemetry", "1");
        showTele = (txt.equalsIgnoreCase("2")) || (txt.equalsIgnoreCase("4"));
        ScanService.getScanData().setStoreTele((txt.equalsIgnoreCase("3")) || (txt.equalsIgnoreCase("4")));
    }


    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        WMapSlimEntry entry;

        entry = freeHotspotList.elementAt(position);
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + entry.lat + "," + entry.lon));
        ctx.startActivity(i);
        onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        MenuItem prefsMenuItem;

        if (ScanService.getScanData().getViewMode() == VIEW_MODE_MAP) {
            prefsMenuItem = menu.findItem(R.id.show_map);
            prefsMenuItem.setIcon(android.R.drawable.ic_menu_save);
        }

        prefsMenuItem = menu.findItem(R.id.freehotspot);
        prefsMenuItem.setEnabled(hasPosLock & ((ScanService.getScanData().getFlags() & FLAG_NO_NET_ACCESS) == 0));

        if (scannerHandler.liveMapView.getTelemetryData() == null) {
            prefsMenuItem = menu.findItem(R.id.calib_tele);
            prefsMenuItem.setEnabled(false);
            prefsMenuItem = menu.findItem(R.id.calib_orient);
            prefsMenuItem.setEnabled(false);
        }

        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.exit:
                simpleAlert(getResources().getText(R.string.really_exit_app).toString(), null, ALERT_NO_EXIT);
                break;
            case R.id.upload_data:
                ScanService.getScanData().setThreadMode(THREAD_MODE_UPLOAD);
                break;
            case R.id.show_map:
                if (ScanService.getScanData().getViewMode() == VIEW_MODE_MAP) {
                    scannerHandler.mapOverlay.saveMap();
                    //onBackPressed();
                } else {
                    if ((ScanService.getScanData().getFlags() & FLAG_NO_NET_ACCESS) == 0) {
                        OWMapAtAndroid.sendMessage(OWMapAtAndroid.ScannerHandler.MSG_OPEN_PRG_DLG, 0, 0, ctx.getResources().getText(R.string.loading_map).toString());
                        scannerHandler.sendEmptyMessage(ScannerHandler.MSG_SHOW_MAP);
                    } else
                        simpleAlert(getResources().getText(R.string.really_show_map).toString(), null, ALERT_SHOW_MAP);
                }
                break;
            case R.id.freehotspot:
                scannerHandler.sendEmptyMessage(OWMapAtAndroid.ScannerHandler.MSG_GET_FREEHOTSPOT_POS_DL);
                break;
            case R.id.prefs:
                Intent intent = new Intent(this, com.vwp.owmap.OWLMapPrefs.class);
                startActivity(intent);
                break;
            case R.id.teamid:
                String text;

                text = getResources().getText(R.string.teamtext).toString();
                text = text + "\n";

                StringBuilder s = new StringBuilder(ScanService.getScanData().getOwnBSSID());
                s.reverse();
                text = text + s;

                simpleAlert(text, null, ALERT_OK);
                break;
            case R.id.calib_tele:
                if ((scannerHandler.liveMapView != null) && (scannerHandler.liveMapView.getTelemetryData() != null)) {
                    ScanService.getScanData().getTelemetryData().corrAccel(scannerHandler.liveMapView.getTelemetryData().getAccelX(),
                            scannerHandler.liveMapView.getTelemetryData().getAccelY(),
                            scannerHandler.liveMapView.getTelemetryData().getAccelZ());
                    ScanService.getScanData().getTelemetryData().corrOrient(scannerHandler.liveMapView.getTelemetryData().getOrientY(),
                            scannerHandler.liveMapView.getTelemetryData().getOrientZ());
                    ScanService.getScanData().getService().storeConfig();
                }
                break;
            case R.id.calib_orient:
                if ((scannerHandler.liveMapView != null) && (scannerHandler.liveMapView.getTelemetryData() != null)) {
                    ScanService.getScanData().getTelemetryData().corrCoG(scannerHandler.liveMapView.getTelemetryData().CoG);
                    ScanService.getScanData().getService().storeConfig();
                }
                break;
            case R.id.help:
                simpleAlert(getResources().getText(R.string.help_txt).toString(), null, ALERT_OK);
                break;
            case R.id.credits:
                simpleAlert("Credits go to: XcinnaY, Tobias, Volker, Keith and Christian\n...for translations, help, ideas, testing and detailed feedback\nThe OpenStreetMap team for map data", null, ALERT_OK);
                break;
            default:
                break;
        }
        return super.onMenuItemSelected(featureId, item);
    }


    private void loadConfig() {
        DataInputStream in;

        try {
            in = new DataInputStream(ctx.openFileInput("wscnprefs"));
            in.readByte(); // version
            ScanService.getScanData().setFlags(in.readInt()); // operation flags;
            ScanService.getScanData().setStoredValues(in.readInt()); // number of currently stored values
            ScanService.getScanData().setUploadedCount(in.readInt());
            ScanService.getScanData().setUploadedRank(in.readInt());
            in.readInt(); // open WLANS, no longer used
            ScanService.getScanData().setFreeHotspotWLANs(in.readInt());
            ScanService.getScanData().getTelemetryData().setCorrAccelX(in.readFloat());
            ScanService.getScanData().getTelemetryData().setCorrAccelY(in.readFloat());
            ScanService.getScanData().getTelemetryData().setCorrAccelZ(in.readFloat());
            ScanService.getScanData().getTelemetryData().setCorrCoG(in.readFloat());
            ScanService.getScanData().getTelemetryData().setCorrOrientY(in.readFloat());
            ScanService.getScanData().getTelemetryData().setCorrOrientZ(in.readFloat());
            in.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        try {
            in = new DataInputStream(openFileInput(OWMapAtAndroid.WSCAN_FILE));
            int i = in.available();
            ScanService.getScanData().setStoredValues(i / 28);
            in.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        updateRank();
    }


    private void updateRank() {
        if (ScanService.getScanData().getUploadedRank() > 0) {
            rankText.setText(ctx.getResources().getText(R.string.rank) + ": " + ScanService.getScanData().getUploadedRank() + " (" + ScanService.getScanData().getUploadedCount() + " " + ctx.getResources().getText(R.string.points).toString() + ")");
//         ctx.mapButton.setEnabled(true);
        } else {
            rankText.setText(ctx.getResources().getText(R.string.rank) + ": --");
//         mapButton.setEnabled(false);
        }
    }


    static void sendMessage(int what, int arg1, int arg2, Object obj) {
        if (ScanService.getScanData().isAppVisible()) {
            Message msg = new Message();
            msg.what = what;
            msg.arg1 = arg1;
            msg.arg2 = arg2;
            msg.obj = obj;
            ScanService.getScanData().getCtx().scannerHandler.sendMessage(msg);
        }
    }
   
   
/*   private void initView()
   {
      WMapEntry currEntry;
      int       j;

      if (ScanService.scanData.wmapList.size()>0)
      {
         ScanService.scanData.lock.lock();
         for (j=0; j<ScanService.scanData.wmapList.size(); j++)
         {
            currEntry=ScanService.scanData.wmapList.elementAt(j);
            sendMessage(ScannerHandler.MSG_ADD_ENTRY,0,0,currEntry);
         }
         ScanService.scanData.lock.unlock();          
      }      
   }*/

    public void run() {
        WMapEntry currEntry;
        int j;
        boolean configChanged;

        do {
            configChanged = false;
            if (ScanService.getScanData().getThreadMode() == THREAD_MODE_SCAN) {
                ScanService.getScanData().getLock().lock();
                for (j = 0; j < ScanService.getScanData().getWmapList().size(); j++) {
                    currEntry = ScanService.getScanData().getWmapList().elementAt(j);
                    if ((currEntry.getFlags() & WMapEntry.FLAG_UI_USED) == 0) {
//                   currEntry.createUIData(this);
                        sendMessage(ScannerHandler.MSG_ADD_ENTRY, 0, 0, currEntry);
                        configChanged = true; // store-count has changed
                    }
                    if (currEntry.getLastUpdate() + RECV_TIMEOUT < System.currentTimeMillis())
                        sendMessage(ScannerHandler.MSG_REM_ENTRY, 0, 0, currEntry);
                    else if ((currEntry.getFlags() & WMapEntry.FLAG_POS_CHANGED) != 0) {
                        sendMessage(ScannerHandler.MSG_UPD_POS, 0, 0, currEntry);
                        currEntry.setFlags(currEntry.getFlags() & ~WMapEntry.FLAG_POS_CHANGED);
                    }
                }
                if (scannerHandler.liveMapView != null)
                    ScanService.getScanData().getCtx().scannerHandler.sendEmptyMessage(ScannerHandler.MSG_UPD_LIVE_MAP);

                ScanService.getScanData().getLock().unlock();
                if (configChanged) ScanService.getScanData().getService().storeConfig();
                try {
                    Thread.sleep(1100);
                } catch (InterruptedException ie) {

                }
            } else if (ScanService.getScanData().getThreadMode() == THREAD_MODE_UPLOAD) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {

                }
            } else if (ScanService.getScanData().getThreadMode() == THREAD_MODE_MAP) {
//            if (mapView!=null) mapView.loadMap(ScanService.scanData.ownBSSID);
                ScanService.getScanData().setThreadMode(THREAD_MODE_SCAN);
            }
        }
        while (ScanService.getScanData().isActive());
    }

    public boolean onSearchRequested() {
        return false;
    }

    public void onBackPressed() {
        if (ScanService.getScanData().getViewMode() == VIEW_MODE_MAP) {
            scannerHandler.rootLayout.removeView(scannerHandler.mapView);
            scannerHandler.mapView = null;
            try {
                scannerHandler.mapOverlay.close();
                scannerHandler.mapOverlay = null;
            } catch (NullPointerException npe) {
                // just a workaround: mapOverlay is sometimes not valid...
            }
            ScanService.getScanData().setViewMode(VIEW_MODE_MAIN);
            scannerHandler.rootLayout.invalidate();
        } else if (ScanService.getScanData().getViewMode() == VIEW_MODE_FF_LIST) {
            scannerHandler.rootLayout.removeView(ffLv);
            ScanService.getScanData().setViewMode(VIEW_MODE_MAIN);
            scannerHandler.rootLayout.invalidate();
        } else
            simpleAlert(getResources().getText(R.string.really_exit_app).toString(), null, ALERT_NO_EXIT);
    }


    public void onClick(View v) {
        if (v == noNetAccCB) {
            if (noNetAccCB.isChecked()) ScanService.getScanData().setFlags(FLAG_NO_NET_ACCESS);
            else ScanService.getScanData().setFlags(0);
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            showMap = SP.getBoolean("showMap", false);
            ScanService.getScanData().getService().storeConfig();
        }
    }


    protected void onResume() {
        ScanService.getScanData().setActive(true);
        super.onResume();
        ScanService.getScanData().setAppVisible(true);
        if (ScanService.getScanData().getmView() != null) ScanService.getScanData().getmView().postInvalidate();
        if (ScanService.getScanData().getWatchThread() != null) {
            try {
                ScanService.getScanData().getWatchThread().join(100); // wait a bit to check if it already has received a previous interruption
            } catch (InterruptedException ie) {
            }
        }
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        scannerHandler.bigCounter = SP.getBoolean("bigCounter", false);
        ScanService.getScanData().setHudCounter(SP.getBoolean("hudCounter", false));
        showMap = SP.getBoolean("showMap", false);
        try {
            ScanService.getScanData().setUploadThres(Integer.parseInt(SP.getString("autoUpload", "0")));
        } catch (NumberFormatException nfe) {
        }
        try {
            ScanService.getScanData().setNoGPSExitInterval(Integer.parseInt(SP.getString("noGPSExitInterval", "0")) * 60 * 1000);
        } catch (NumberFormatException nfe) {
        }

        getTelemetryConfig(SP);
        if ((!doTrack) && (SP.getBoolean("track", false))) {
            String aText;
            int val1 = 0, val2 = 0;

            try {
                val1 = Integer.parseInt(ScanService.getScanData().getOwnBSSID().substring(0, 6), 16);
                val2 = Integer.parseInt(ScanService.getScanData().getOwnBSSID().substring(6), 16);
            } catch (NumberFormatException nfe) {

            }

            aText = getResources().getText(R.string.track_info).toString() + ":\n\n" + val1 + " - " + val2 + "\n\n" + getResources().getText(R.string.track_info2).toString();
            simpleAlert(aText, null, ALERT_OK);
        }
        doTrack = SP.getBoolean("track", false);
        if (scannerHandler.liveMapView != null)
            scannerHandler.liveMapView.setMapMode(SP.getString("mapType", "2"));

        if ((ScanService.getScanData().getWatchThread() == null) || (!ScanService.getScanData().getWatchThread().isAlive())) {
            ScanService.getScanData().setActive(true);
            ScanService.getScanData().setWatchThread(new Thread(this));
            ScanService.getScanData().getWatchThread().start();
        }
//      else initView();

//       mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        //if (wl != null) wl.acquire();
    }


    protected void onPause() {
        WMapEntry currEntry;
        int j;

        //if (wl != null) wl.release();
        ScanService.getScanData().setActive(false); // try to stop the thread
        if (ScanService.getScanData().getViewMode() == VIEW_MODE_MAP) onBackPressed();
//       mSensorManager.unregisterListener(this);
        ScanService.getScanData().setAppVisible(false);
        if (ScanService.getScanData().getmView() != null) ScanService.getScanData().getmView().postInvalidate();
        ScanService.getScanData().getLock().lock();
        if (ScanService.getScanData().getWmapList().size() > 0)
            for (j = 0; j < ScanService.getScanData().getWmapList().size(); j++) {
                currEntry = ScanService.getScanData().getWmapList().elementAt(j);
                currEntry.setFlags(currEntry.getFlags() & ~WMapEntry.FLAG_UI_USED);
                currEntry.setFlags(currEntry.getFlags() & ~WMapEntry.FLAG_IS_VISIBLE);
                scannerHandler.parentTable.removeView(currEntry.getRow());
            }
        ScanService.getScanData().getLock().unlock();
        super.onPause();
    }

}
