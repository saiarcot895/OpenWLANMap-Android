package com.vwp.owmap;

import java.util.concurrent.locks.*;

import android.content.*;
import android.graphics.*;
import android.view.*;

import com.vwp.libwlocate.map.*;


public class LiveMapView extends View implements Runnable {
    private Bitmap wlanBitmap, openWlanBitmap, freifunkWlanBitmap, freeHotspotWlanBitmap, theCloudWlanBitmap;
    private double m_lat, m_lon;
    private Paint wlanColour, instColour, instInner, instInner2, teleBG, posColour;
    public final int m_zoom = 17;
    private boolean allowThread = false;
    private LiveMapData[] mapData = new LiveMapData[2];
    private int currMap = 0;
    private GeoUtils geoUtils = new GeoUtils(GeoUtils.MODE_GSMAP);
    private Lock lock = new ReentrantLock();
    private TelemetryData telemetryData = null;
    private int useHeight;

    public LiveMapView(Context ctx) {
        super(ctx);
        mapData[0] = new LiveMapData();
        mapData[1] = new LiveMapData();
        setWillNotDraw(false);
        this.setMinimumWidth(50);
        this.setMinimumHeight(50);

        posColour = new Paint();
        posColour.setARGB(255, 25, 0, 255);
        posColour.setStyle(Paint.Style.STROKE);
        posColour.setStrokeWidth(3);

        wlanColour = new Paint();
        wlanColour.setARGB(255, 200, 0, 0);
        wlanColour.setStyle(Paint.Style.STROKE);
        wlanColour.setStrokeWidth(2);

        wlanBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.wifi);

        openWlanBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.wifi_open);

        freifunkWlanBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.wifi_frei);
        freeHotspotWlanBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.wifi_freehotspot);
        theCloudWlanBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.wifi_cloud);

        instColour = new Paint();
        instColour.setARGB(200, 30, 30, 80);
        instColour.setStyle(Paint.Style.STROKE);
        instColour.setStrokeWidth(3);

        instInner = new Paint();
        instInner.setARGB(130, 0, 0, 255);
        instInner.setStyle(Paint.Style.FILL);

        instInner2 = new Paint();
        instInner2.setARGB(130, 255, 0, 0);
        instInner2.setStyle(Paint.Style.STROKE);
        instInner2.setStrokeWidth(7);

        teleBG = new Paint();
        teleBG.setARGB(255, 90, 90, 110);
        teleBG.setStyle(Paint.Style.FILL);

        updateScreenOrientation();
    }


    public void setTelemetryData(TelemetryData telemetry) {
        telemetryData = telemetry;
        this.postInvalidate();
    }


    void setMapMode(String mode) {
        if (mode.equalsIgnoreCase("1")) geoUtils.setMode(GeoUtils.MODE_OSM);
        else if (mode.equalsIgnoreCase("4")) geoUtils.setMode(GeoUtils.MODE_OSM_NIGHT);
        else if (mode.equalsIgnoreCase("2")) geoUtils.setMode(GeoUtils.MODE_GMAP);
        else geoUtils.setMode(GeoUtils.MODE_GSMAP);
    }

    private void updateScreenOrientation() {
        Display display;
        int width, newXOffs, newYOffs;

        display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
//      orientation=display.getOrientation();
        width = getWidth();
        newXOffs = (int) Math.ceil(width / 256.0);
        newYOffs = (int) Math.ceil(display.getHeight() / 256.0);
        useHeight = (display.getHeight() * 3) / 5;
        if ((mapData[currMap].xOffs != newXOffs) || (mapData[currMap].yOffs != newYOffs)) {
            allowThread = false;
            mapData[1 - currMap].locTile = null;
            setMinimumHeight(display.getHeight());
            setMinimumWidth(newXOffs * 256);
            mapData[1 - currMap].xOffs = newXOffs;
            mapData[1 - currMap].yOffs = newYOffs;
            if (mapData[1 - currMap].xOffs > 2)
                mapData[1 - currMap].shiftX = mapData[1 - currMap].xOffs / 3;
            else mapData[1 - currMap].shiftX = 0;
            if (mapData[1 - currMap].yOffs > 2)
                mapData[1 - currMap].shiftY = mapData[1 - currMap].yOffs / 2;
            else mapData[1 - currMap].shiftY = 0;
        }
    }

    /**
     * This method updates the internal locTile array that holds bitmaps of the tiles that have to be
     * displayed currently. To get the tile images it first tries to load a local PNG image. In case
     * that fails the TAH server is connected to download and save a tile image in PNG format. Then it
     * tries again to load the local PNG image - now it should be successful because it was downloaded
     * just one step before.
     *
     * @param[in] lat the latitude of the current position which has to be displayed in center tile
     * @param[in] lon the longitude of the current position which has to be displayed in center tile
     */
    public synchronized void updateViewTiles(double lat, double lon) {
        int nextTileX, nextTileY;

        m_lat = lat;
        m_lon = lon;
        if ((lat == 0.0) && (lon == 0.0))
            return;
        updateScreenOrientation();
        if (allowThread) return;
        nextTileX = GeoUtils.long2tilex(lon, m_zoom);
        nextTileY = GeoUtils.lat2tiley(lat, m_zoom);

        if ((nextTileX != mapData[1 - currMap].m_tileX) || (nextTileY != mapData[1 - currMap].m_tileY) ||
                (mapData[1 - currMap].locTile == null)) {
            if ((mapData[1 - currMap].xOffs <= 0) || (mapData[1 - currMap].yOffs <= 0)) {
                mapData[1 - currMap].shiftX = mapData[currMap].shiftX;
                mapData[1 - currMap].shiftY = mapData[currMap].shiftY;
                mapData[1 - currMap].xOffs = mapData[currMap].xOffs;
                mapData[1 - currMap].yOffs = mapData[currMap].yOffs;
            }
            mapData[1 - currMap].locTile = new Bitmap[mapData[1 - currMap].xOffs][mapData[1 - currMap].yOffs];
            mapData[1 - currMap].locTileX = new int[mapData[1 - currMap].xOffs][mapData[1 - currMap].yOffs];
            mapData[1 - currMap].locTileY = new int[mapData[1 - currMap].xOffs][mapData[1 - currMap].yOffs];
            System.gc();
            mapData[1 - currMap].m_tileX = nextTileX;
            mapData[1 - currMap].m_tileY = nextTileY;
            allowThread = true;
            Thread tilesThread = new Thread(this);
            tilesThread.start();
        }
    }


    public void run() {
        int x, y, x2, y2;
        boolean foundExisting;

        try {
            for (x = 0; x < mapData[1 - currMap].xOffs; x++)
                for (y = 0; y < mapData[1 - currMap].yOffs; y++) {
                    if (!allowThread) break;
                    if (mapData[1 - currMap].locTile[x][y] == null) {
                        mapData[1 - currMap].locTileX[x][y] = (mapData[1 - currMap].m_tileX + x - mapData[1 - currMap].shiftX);
                        mapData[1 - currMap].locTileY[x][y] = (mapData[1 - currMap].m_tileY + y - mapData[1 - currMap].shiftY);
                        foundExisting = false;
                        try {
                            for (x2 = 0; x2 < mapData[currMap].xOffs; x2++)
                                for (y2 = 0; y2 < mapData[currMap].yOffs; y2++) {
                                    if ((mapData[currMap].locTileX[x2][y2] == mapData[1 - currMap].locTileX[x][y]) &&
                                            (mapData[currMap].locTileY[x2][y2] == mapData[1 - currMap].locTileY[x][y]) &&
                                            (mapData[currMap].locTile[x2][y2].getWidth() > 100)) {
                                        mapData[1 - currMap].locTile[x][y] = mapData[currMap].locTile[x2][y2];
                                        foundExisting = true;
                                        break;
                                    }
                                }
                        } catch (NullPointerException npe) // on startup one of the two map object contains null
                        {
                        }

                        if (!foundExisting)
                            mapData[1 - currMap].locTile[x][y] = geoUtils.loadMapTile(getContext(), mapData[1 - currMap].locTileX[x][y], mapData[1 - currMap].locTileY[x][y], m_zoom, ((ScanService.getScanData().getFlags() & OWMapAtAndroid.FLAG_NO_NET_ACCESS) == 0));
                    }
                }
            lock.lock();
            currMap = 1 - currMap;
            lock.unlock();
        } catch (Exception e) // catch all problems that may happen asynchronously, the next thread start will fix it
        {

        }
        System.gc();
        allowThread = false;
    }


    public void onDraw(Canvas c) {
        WMapEntry entry;
        int x, y, i;
        float cx, cy, val, fac;
        double tileLat1, tileLon1, tileLat2, tileLon2, ang;

        super.onDraw(c);

        if (((m_lat != 0.0) && (m_lon != 0.0) && (OWMapAtAndroid.showMap)) ||
                (OWMapAtAndroid.showTele)) {
            c.drawRect(0, 0, this.getWidth(), this.getHeight(), teleBG);
        }

        if ((m_lat != 0.0) && (m_lon != 0.0) && (OWMapAtAndroid.showMap)) {
            lock.lock();
            try {
                for (x = 0; x < mapData[currMap].xOffs; x++)
                    for (y = 0; y < mapData[currMap].yOffs; y++) {
                        if (mapData[currMap].locTile[x][y] != null) {
                            c.drawBitmap(mapData[currMap].locTile[x][y], ((x) * 256), (y) * 256, null);
                        }
                    }
            } catch (Exception e) {

            }

            tileLat1 = GeoUtils.tiley2lat(mapData[currMap].m_tileY, m_zoom);
            tileLat2 = GeoUtils.tiley2lat(mapData[currMap].m_tileY + 1, m_zoom);
            tileLon1 = GeoUtils.tilex2long(mapData[currMap].m_tileX, m_zoom);
            tileLon2 = GeoUtils.tilex2long(mapData[currMap].m_tileX + 1, m_zoom);

            cy = (float) ((mapData[currMap].shiftY * 256 + 256.0 * (m_lat - tileLat1) / (tileLat2 - tileLat1)));
            cx = (float) ((mapData[currMap].shiftX * 256 + 256.0 * (m_lon - tileLon1) / (tileLon2 - tileLon1)));
            c.drawLine(cx - 20, cy, cx + 20, cy, posColour);
            c.drawLine(cx, cy - 20, cx, cy + 20, posColour);

            ScanService.getScanData().getLock().lock();
            for (i = 0; i < ScanService.getScanData().getWmapList().size(); i++) {
                entry = ScanService.getScanData().getWmapList().elementAt(i);
                cy = (float) ((mapData[currMap].shiftY * 256 + 256.0 * (entry.getLat() - tileLat1) / (tileLat2 - tileLat1)));
                cx = (float) ((mapData[currMap].shiftX * 256 + 256.0 * (entry.getLon() - tileLon1) / (tileLon2 - tileLon1)));
                if ((entry.getFlags() & WMapEntry.FLAG_IS_FREIFUNK) != 0) {
                    c.drawBitmap(freifunkWlanBitmap, cx - 7, cy - 7, null);
                } else if ((entry.getFlags() & WMapEntry.FLAG_IS_FREEHOTSPOT) != 0) {
                    c.drawBitmap(freeHotspotWlanBitmap, cx - 7, cy - 7, null);
                } else if ((entry.getFlags() & WMapEntry.FLAG_IS_THECLOUD) != 0) {
                    c.drawBitmap(theCloudWlanBitmap, cx - 7, cy - 7, null);
                } else if ((entry.getFlags() & WMapEntry.FLAG_IS_OPEN) == 0) {
                    if ((entry.getFlags() & WMapEntry.FLAG_IS_NOMAP) != 0)
                        c.drawCircle(cx - 0.5f, cy - 1, 10, wlanColour);
                    c.drawBitmap(wlanBitmap, cx - 7, cy - 7, null);
                } else {
                    c.drawBitmap(openWlanBitmap, cx - 7, cy - 7, null);
                }
            }
            ScanService.getScanData().getLock().unlock();
            lock.unlock();
        }

        if (OWMapAtAndroid.showTele) {
            cy = useHeight / 2;
            c.drawRect(10, 10, 40, useHeight, instColour);
            c.drawRect(50, 10, 80, useHeight, instColour);
            c.drawRect(90, 10, 120, useHeight, instColour);

            c.drawRect(10, useHeight + 20, 120, useHeight + 130, instColour);

            if (telemetryData != null) {
                fac = (useHeight * 1.25f) / telemetryData.getAccelMax();
                val = fac * telemetryData.getAccelX();
                if (val > cy) val = cy;
                else if (val < -cy) val = -cy;
                c.drawRect(12, 10 + cy + val, 39, 10 + cy, instInner);

                val = fac * telemetryData.getAccelY();
                if (val > cy) val = cy;
                else if (val < -cy) val = -cy;
                c.drawRect(52, 10 + cy + val, 79, 10 + cy, instInner);

                val = fac * telemetryData.getAccelZ();
                if (val > cy) val = cy;
                else if (val < -cy) val = -cy;
                c.drawRect(92, 10 + cy + val, 119, 10 + cy, instInner);

                double x1, y1;

                x = 66;
                y = useHeight + 75;
                ang = (float) ((telemetryData.CoG) * Math.PI / 180.0);
                x1 = x + 48.0 * Math.cos(ang);
                y1 = y + 48.0 * Math.sin(ang);
                c.drawLine(x, y, (float) x1, (float) y1, instInner2);
                c.drawCircle(x, y, 51, instInner2);

                fac = 110 / 50.0f;
                val = telemetryData.getOrientY() * fac;
                x = (int) (useHeight + 75 - val);
                if (x > useHeight + 112) x = useHeight + 112;
                else if (x < useHeight + 28) x = useHeight + 28;
                c.drawRect(12, x - 7, 119, x + 7, instInner);

                fac = 110 / 50.0f;
                val = telemetryData.getOrientZ() * fac;
                y = (int) (65 + val);
                if (y < 18) y = 18;
                else if (y > 112) y = 112;
                c.drawRect(y - 7, useHeight + 21, y + 7, useHeight + 129, instInner);
            }

            c.drawLine(5, 10 + cy, 125, 10 + cy, instColour);
            c.drawLine(12, useHeight + 75, 120, useHeight + 75, instColour);
            c.drawLine(65, useHeight + 21, 65, useHeight + 129, instColour);
        }
    }

    public TelemetryData getTelemetryData() {
        return telemetryData;
    }

    private class LiveMapData {
        Bitmap locTile[][];
        int locTileX[][], locTileY[][];
        int xOffs, yOffs, shiftX = 0, shiftY = 0;
        int m_tileX = 0, m_tileY = 0;
    }
}
