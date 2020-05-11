package com.challenge.colorizer;

import android.graphics.Bitmap;

public class ColorizerManager {
    private static Bitmap target;
    private static Bitmap[] results;
    public static void setBitmap(Bitmap in) { target = in; }
    public static void setResults(Bitmap[] in) { results = in; }
    public static Bitmap getBitmap() { return target; }
    public static Bitmap[] getResults() { return results; }
}
