package com.vwp.owmap;

import android.preference.PreferenceManager;
import android.view.*;
import android.widget.*;
import android.content.*;


public class WMapEntry {
    private String BSSID;
    private String SSID;
    private double firstLat;
    private double firstLon;
    private double lastLat = 0.0, lastLon = 0.0, avgLat = 0.0, avgLon = 0.0;
    private int avgCtr = 0;
    private long lastUpdate;
    private TableRow row;
    private TextView latView = null;
    private TextView lonView = null;
    private int listPos = 0;
    private int flags = 0;

    static final int FLAG_UI_USED = 0x0001;
    static final int FLAG_IS_VISIBLE = 0x0002;
    static final int FLAG_POS_CHANGED = 0x0004;
    static final int FLAG_IS_OPEN = 0x0008;
    static final int FLAG_IS_NOMAP = 0x0010;
    static final int FLAG_IS_FREIFUNK = 0x0020;
    static final int FLAG_IS_FREEHOTSPOT = 0x0040;
    static final int FLAG_IS_THECLOUD = 0x0080;

    public WMapEntry(String BSSID, String SSID, double lat, double lon, int listPos) {
        this.BSSID = BSSID;
        this.SSID = SSID;
        this.listPos = listPos;
        firstLat = lat;
        lastLat = lat;
        firstLon = lon;
        lastLon = lon;
        addAvgPos(lat, lon);
        lastUpdate = System.currentTimeMillis();
        flags |= FLAG_POS_CHANGED;
    }


    void createUIData(Context ctx) {
        String showType;

        row = new TableRow(ctx);
        row.setGravity(Gravity.LEFT);

        TableLayout.LayoutParams tableRowParams =
                new TableLayout.LayoutParams
                        (TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);

        tableRowParams.setMargins(2, 2, 18, 2);

        row.setLayoutParams(tableRowParams);

        ImageView img = new ImageView(ctx);
        if ((flags & FLAG_IS_FREIFUNK) != 0) img.setImageResource(R.drawable.wifi_frei);
        else if ((flags & FLAG_IS_FREEHOTSPOT) != 0)
            img.setImageResource(R.drawable.wifi_freehotspot);
        else if ((flags & FLAG_IS_THECLOUD) != 0) img.setImageResource(R.drawable.wifi_cloud);
        else if ((flags & FLAG_IS_OPEN) != 0) img.setImageResource(R.drawable.wifi_open);
        else img.setImageResource(R.drawable.wifi);
        row.addView(img);

        TextView cntText = new TextView(ctx);
        OWMapAtAndroid.setTextStyle(ctx, cntText);
        cntText.setText(listPos + ". ");
        row.addView(cntText);

        TextView bssidView = new TextView(ctx);
        OWMapAtAndroid.setTextStyle(ctx, bssidView);

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(ctx);
        showType = SP.getString("showType", "1");
        if (showType.equalsIgnoreCase("1")) bssidView.setText(BSSID + " ");
        else if (showType.equalsIgnoreCase("2")) bssidView.setText(SSID + " ");
        else if (showType.equalsIgnoreCase("3")) bssidView.setText(SSID + " / " + BSSID + " ");

        row.addView(bssidView);

        latView = new TextView(ctx);
        OWMapAtAndroid.setTextStyle(ctx, latView);
        row.addView(latView, new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT));

        lonView = new TextView(ctx);
        OWMapAtAndroid.setTextStyle(ctx, lonView);
        row.addView(lonView, new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT));

        if ((flags & FLAG_IS_NOMAP) != 0) {
            cntText.setTextColor(0xFFFFAAAA);
            bssidView.setTextColor(0xFFFFAAAA);
            latView.setTextColor(0xFFFFAAAA);
            lonView.setTextColor(0xFFFFAAAA);
        } else if ((flags & (FLAG_IS_FREIFUNK | FLAG_IS_FREEHOTSPOT | FLAG_IS_THECLOUD)) != 0) {
            cntText.setTextColor(0xFFAAFFAA);
            bssidView.setTextColor(0xFFAAFFAA);
            latView.setTextColor(0xFFAAFFAA);
            lonView.setTextColor(0xFFAAFFAA);
        } else if ((flags & FLAG_IS_OPEN) != 0) {
            cntText.setTextColor(0xFFAAAAFF);
            bssidView.setTextColor(0xFFAAAAFF);
            latView.setTextColor(0xFFAAAAFF);
            lonView.setTextColor(0xFFAAAAFF);
        }
        flags |= FLAG_UI_USED;
    }


    public double getLat() {
        double d;

        d = avgLat / avgCtr;
        return (firstLat + lastLat + d) / 3.0;
    }


    public double getLon() {
        double d;

        d = avgLon / avgCtr;
        return (firstLon + lastLon + d) / 3.0;
    }


    public boolean posIsValid() {
        return !((lastLat == 0.0) || (lastLon == 0.0))
                && Math.abs(lastLat - firstLat) <= 0.0035
                && Math.abs(lastLon - firstLon) <= 0.0035;
    }

    private void addAvgPos(double lat, double lon) {
        avgCtr++;
        avgLat += lat;
        avgLon += lon;
    }

    public void setPos(double lat, double lon) {
        lastLat = lat;
        lastLon = lon;
        addAvgPos(lat, lon);
        lastUpdate = System.currentTimeMillis();
        flags |= FLAG_POS_CHANGED;
    }

    public String getBSSID() {
        return BSSID;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public TableRow getRow() {
        return row;
    }

    public TextView getLatView() {
        return latView;
    }

    public TextView getLonView() {
        return lonView;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

}


