package com.vwp.owmap;

public class TelemetryData {
    private float accelX;
    private float accelY;
    private float accelZ;
    float CoG;
    private float orientY;
    private float orientZ;
    private float corrAccelX = 0.0f;
    private float corrAccelY = 0.0f;
    private float corrAccelZ = 0.0f;
    private float corrCoG = 0.0f;
    private float corrOrientY = 0.0f;
    private float corrOrientZ = 0.0f;
    private float accelMax = 9.81f;
    private int accelCnt;
    private int orientCnt;

    TelemetryData() {
        reset();
    }

    void setAccelMax(float max) {
        accelMax = max;
    }


    void addAccel(float x, float y, float z) {
        accelX += x;
        accelY += y;
        accelZ += z;
        accelCnt++;
    }


    void corrAccel(float x, float y, float z) {
        corrAccelX += x;
        corrAccelY += y;
        corrAccelZ += z;
    }


    void addOrient(float x, float y, float z) {
        CoG += x;
        orientY += y;
        orientZ += z;
        orientCnt++;
    }


    void corrOrient(float y, float z) {
        corrOrientY += y;
        corrOrientZ += z;
    }


    void corrCoG(float x) {
        corrCoG += (x + 90);
    }


    void set(TelemetryData data) {
        accelMax = data.accelMax;
        accelX = (data.accelX / data.accelCnt) - data.corrAccelX;
        accelY = (data.accelY / data.accelCnt) - data.corrAccelY;
        accelZ = (data.accelZ / data.accelCnt) - data.corrAccelZ;
        accelCnt = 1;
        CoG = (data.CoG / data.orientCnt) - data.corrCoG;
        orientY = (data.orientY / data.orientCnt) - data.corrOrientY;
        orientZ = (data.orientZ / data.orientCnt) - data.corrOrientZ;
        orientCnt = 1;
    }


    double getCoG() {
        return (CoG / orientCnt) - corrCoG;
    }


    void reset() {
        accelX = 0.0f;
        accelY = 0.0f;
        accelZ = 0.0f;
        accelCnt = 0;
        CoG = 0.0f;
        orientY = 0.0f;
        orientZ = 0.0f;
        orientCnt = 0;
    }

    public float getAccelX() {
        return accelX;
    }

    public float getAccelY() {
        return accelY;
    }

    public float getAccelZ() {
        return accelZ;
    }

    public float getOrientY() {
        return orientY;
    }

    public float getOrientZ() {
        return orientZ;
    }

    public float getCorrAccelX() {
        return corrAccelX;
    }

    public void setCorrAccelX(float corrAccelX) {
        this.corrAccelX = corrAccelX;
    }

    public float getCorrAccelY() {
        return corrAccelY;
    }

    public void setCorrAccelY(float corrAccelY) {
        this.corrAccelY = corrAccelY;
    }

    public float getCorrAccelZ() {
        return corrAccelZ;
    }

    public void setCorrAccelZ(float corrAccelZ) {
        this.corrAccelZ = corrAccelZ;
    }

    public float getCorrCoG() {
        return corrCoG;
    }

    public void setCorrCoG(float corrCoG) {
        this.corrCoG = corrCoG;
    }

    public float getCorrOrientY() {
        return corrOrientY;
    }

    public void setCorrOrientY(float corrOrientY) {
        this.corrOrientY = corrOrientY;
    }

    public float getCorrOrientZ() {
        return corrOrientZ;
    }

    public void setCorrOrientZ(float corrOrientZ) {
        this.corrOrientZ = corrOrientZ;
    }

    public float getAccelMax() {
        return accelMax;
    }

    public int getAccelCnt() {
        return accelCnt;
    }

    public void setAccelCnt(int accelCnt) {
        this.accelCnt = accelCnt;
    }

    public int getOrientCnt() {
        return orientCnt;
    }

    public void setOrientCnt(int orientCnt) {
        this.orientCnt = orientCnt;
    }
}
