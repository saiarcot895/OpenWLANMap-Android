package com.vwp.owmini;

import android.content.*;
import android.net.NetworkInfo;
import android.app.*;

import java.io.*;
import java.net.*;
import java.util.*;

import com.vwp.owmini.OWMiniAtAndroid.*;

class UploadThread extends Thread {
    private ScanData scanData;
    private boolean uploading = true, silent;
    private ScanService ctx;
    private SharedPreferences SP;
    private Notification notification;
    private NetworkInfo mWifi;

    private static final int version = 104;
    private static final String FILE_UPLOADSTORE = "uploadstore";


    UploadThread(ScanData scanData, ScanService ctx, SharedPreferences SP, boolean silent, Notification notification, NetworkInfo mWifi) {
        notification.icon = R.drawable.upload;
        notification.tickerText = ctx.getResources().getText(R.string.uploading_data);
        ctx.startForeground(1703, notification);

        this.scanData = scanData;
        this.ctx = ctx;
        this.SP = SP;
        this.silent = silent;
        this.notification = notification;
        this.mWifi = mWifi;
        start();
    }


    private void resetNotification() {
        notification.icon = R.drawable.icon;
        notification.tickerText = ctx.getResources().getText(R.string.app_name);
        ctx.startForeground(1703, notification);
        uploading = false;
    }


    public void run() {
        DataInputStream in, openIn;
        String bssid, outString;
        double lat, lon;
        HashMap<String, WMapSlimEntry> uploadMap;
        HashMap<String, String> openMap;
        Collection<WMapSlimEntry> uploadCollection;
        Iterator<WMapSlimEntry> uploadIt;
        Collection<String> openCollection;
        Iterator<String> openIt;
        WMapSlimEntry currEntry;
        byte data[] = new byte[12];
        boolean foundEntry, scanningEnabled;
        int mainFlags = 0, cnt;

        System.gc();
        String tagName = SP.getString("tag", "");
        String teamid = SP.getString("team", "");
        if (SP.getBoolean("publish", false)) mainFlags = 1;
        if (SP.getBoolean("pubmap", false)) mainFlags |= 2;

        try {
            in = new DataInputStream(ctx.openFileInput(FILE_UPLOADSTORE));
            in.readByte(); // version
            outString = in.readUTF();
            in.close();
            if (!uploadData(outString, silent)) {
                if (!silent)
                    OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_SIMPLE_ALERT, 0, 0, ctx.getResources().getText(R.string.upload_problem));
                resetNotification();
                return;
            }
            ctx.deleteFile(FILE_UPLOADSTORE);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }


        try {
            in = new DataInputStream(ctx.openFileInput(OWMiniAtAndroid.WSCAN_FILE));
            if (in.available() < 28 * 240) {
                if (!silent)
                    OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_SIMPLE_ALERT, 0, 0, ctx.getResources().getText(R.string.nothing_to_upload));
                in.close();
                resetNotification();
                return;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            if (!silent)
                OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_SIMPLE_ALERT, 0, 0, ctx.getResources().getText(R.string.nothing_to_upload));
            resetNotification();
            return;
        }
        try {
            openIn = new DataInputStream(ctx.openFileInput(OWMiniAtAndroid.WFREI_FILE));
        } catch (IOException ioe) {
            openIn = null;
        }
        uploadMap = new HashMap<String, WMapSlimEntry>();
        ScanService.getScanData().getLock().lock();
        try {
            scanningEnabled = ScanService.getScanData().isScanningEnabled();
            ScanService.getScanData().setScanningEnabled(false);
            {
                String txt;

                txt = ctx.getResources().getText(R.string.app_name) + ": " + ctx.getResources().getText(R.string.preparing_data);
                OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_TOAST, 0, 0, txt);
            }
            while (in.available() >= 28) {
                in.read(data, 0, 12);
                bssid = new String(data);
                lat = in.readDouble();
                lon = in.readDouble();
                if ((lat >= -90.0) && (lat <= 90.0) &&
                        (lon >= -180.0) && (lon <= 180.0)) {
                    foundEntry = false;
                    currEntry = uploadMap.get(bssid);
                    if (currEntry != null) {
                        currEntry.addPos(lat, lon);
                        foundEntry = true;
                    }
                    if (!foundEntry) {
                        currEntry = new WMapSlimEntry(bssid, lat, lon);
                        uploadMap.put(bssid, currEntry);
                    }
                }
            }
            in.close();
            ScanService.getScanData().setScanningEnabled(scanningEnabled);
        } catch (IOException ioe2) {
        }
        ScanService.getScanData().getLock().unlock();
        System.gc();
        outString = ScanService.getScanData().getOwnBSSID() + "\n";
        if (tagName.length() > 20) tagName = tagName.substring(0, 20);
        outString = outString + "T\t" + tagName + "\n";

        if (teamid.length() > 0) {
            StringBuilder s = new StringBuilder(teamid);
            s.reverse();
            outString = outString + "E\t" + s + "\n";
        }
        outString = outString + "F\t" + mainFlags + "\n";
        uploadCollection = uploadMap.values();
        if (uploadCollection.size() > 0) {
            uploadIt = uploadCollection.iterator();
            uploadCollection = null;
            while (uploadIt.hasNext()) {
                currEntry = uploadIt.next();
                cnt = currEntry.cnt;
                if (cnt > 5) cnt = 5;
                outString = outString + cnt + "\t" + currEntry.BSSID + "\t" + (currEntry.lat / currEntry.cnt) + "\t" + (currEntry.lon / currEntry.cnt) + "\n";
            }
        }
        uploadMap.clear();
        uploadMap = null;

        if (openIn != null) {
            ScanService.getScanData().getLock().lock();
            openMap = new HashMap<String, String>();
            try {
                while (openIn.available() >= 12) {
                    openIn.read(data, 0, 12);
                    bssid = new String(data);
                    openMap.put(bssid, bssid);
                }
                openIn.close();
            } catch (IOException ioe2) {
            }//_:Ä:_Ö:_;
            ScanService.getScanData().getLock().unlock();
            openCollection = openMap.values();
            if (openCollection.size() > 0) {
                openIt = openCollection.iterator();
                openCollection = null;
                while (openIt.hasNext()) {
                    outString = outString + "U\t" + openIt.next() + "\n";
                }
            }
            openMap.clear();
            openMap = null;
        }

        System.gc();
        uploadIt = null;
        {
            String txt;

            txt = ctx.getResources().getText(R.string.app_name) + ": " + ctx.getResources().getText(R.string.uploading_data);
            OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_TOAST, 0, 0, txt);
        }
        if (!uploadData(outString, silent)) {
            DataOutputStream out;

            try {
                out = new DataOutputStream(ctx.openFileOutput(FILE_UPLOADSTORE, Context.MODE_PRIVATE));
                out.writeByte(1); // version
                out.writeUTF(outString);
                out.close();
                ctx.deleteFile(OWMiniAtAndroid.WSCAN_FILE);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        resetNotification();
    }

    boolean isUploading() {
        return uploading;
    }


    private boolean uploadData(String outString, boolean silent) {
        HttpURLConnection c = null;
        BufferedOutputStream os = null;
        BufferedReader is = null;
        int rc, remoteVersion = 0;
        String inString;
        int newAPs = 0, updAPs = 0, delAPs = 0, newPoints = 0;
        boolean uploadSuccess = true;

        if ((silent) && (mWifi != null)) {
            if (!mWifi.isConnected()) return false;
        }

        try {
            URL connectURL = new URL("http://www.openwlanmap.org/android/upload.php");
            c = (HttpURLConnection) connectURL.openConnection();
            if (c == null) return false;

            c.setDoOutput(true); // enable POST
            c.setRequestMethod("POST");
            c.addRequestProperty("Content-Type", "application/x-www-form-urlencoded, *.*");
            c.addRequestProperty("Content-Length", "" + outString.length());
            os = new BufferedOutputStream(c.getOutputStream());
            os.write(outString.getBytes(), 0, outString.length());
            os.flush();
            os.close();
            outString = null;
            os = null;
            System.gc();

            rc = c.getResponseCode();
            if (rc != HttpURLConnection.HTTP_OK) {
                if (!silent)
                    OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_SIMPLE_ALERT, 0, 0, ctx.getResources().getString(R.string.http_error) + " " + rc);
                return false;
            }
            is = new BufferedReader(new InputStreamReader(c.getInputStream()));
            try {
                inString = is.readLine();
                remoteVersion = Integer.parseInt(inString);
                inString = is.readLine();
                scanData.setUploadedCount(Integer.parseInt(inString));
                inString = is.readLine();
                scanData.setUploadedRank(Integer.parseInt(inString));

                inString = is.readLine();
                newAPs = Integer.parseInt(inString);
                inString = is.readLine();
                updAPs = Integer.parseInt(inString);
                inString = is.readLine();
                delAPs = Integer.parseInt(inString);
                inString = is.readLine();
                newPoints = Integer.parseInt(inString);
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
                throw new IOException("upload.php failed");
            }

            is.close();
            is = null;
            ctx.deleteFile(OWMiniAtAndroid.WSCAN_FILE);
            ctx.deleteFile(OWMiniAtAndroid.WFREI_FILE);
            ScanService.getScanData().setStoredValues(0);
            ScanService.getScanData().setFreeHotspotWLANs(0);
            ctx.storeConfig();
            if (!silent) {
                if (remoteVersion > version)
                    OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_SIMPLE_ALERT, 0, 0, ctx.getResources().getText(R.string.new_version_available));
            }
        } catch (IOException ioe) {
            if (!silent)
                OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_SIMPLE_ALERT, 0, 0, ctx.getResources().getText(R.string.upload_problem));
            uploadSuccess = false;
        } finally {
            try {
                if (is != null) is.close();
                if (os != null) os.close();
                if (c != null) c.disconnect();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        if (uploadSuccess) {
            if (!silent)
                OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_SIMPLE_ALERT, 0, 0,
                        ctx.getResources().getText(R.string.your_new_rank).toString() + ": " + scanData.getUploadedRank() + " (" + scanData.getUploadedCount() + ctx.getResources().getText(R.string.points).toString() + ")\n\n" +
                                ctx.getResources().getText(R.string.stat_newAPs).toString() + ": " + newAPs + "\n" +
                                ctx.getResources().getText(R.string.stat_updAPs).toString() + ": " + updAPs + "\n" +
                                ctx.getResources().getText(R.string.stat_delAPs).toString() + ": " + delAPs + "\n" +
                                ctx.getResources().getText(R.string.stat_newPoints).toString() + ": " + newPoints);
            ctx.deleteFile(OWMiniAtAndroid.MAP_DATA_FILE);
            ctx.deleteFile(OWMiniAtAndroid.MAP_MAX_FILE);
        }
        return uploadSuccess;
    }

}