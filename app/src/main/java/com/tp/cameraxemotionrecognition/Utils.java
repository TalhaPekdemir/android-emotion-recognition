package com.tp.cameraxemotionrecognition;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

public class Utils {
    public static Bitmap cropBitmap(@NonNull Bitmap bitmap, @NonNull Rect rect) {
        int w = rect.right - rect.left;
        int h = rect.bottom - rect.top;
        Bitmap ret = Bitmap.createBitmap(w, h, bitmap.getConfig());
        Canvas canvas = new Canvas(ret);
        canvas.drawBitmap(bitmap, -rect.left, -rect.top, null);
        return ret;
    }

    public static String mostFrequentWord(ArrayList<String> arr)
    {
        HashMap<String, Integer> freq = new HashMap<>();
        HashMap<String, Integer> occurrence
                = new HashMap<>();
        int max = 0;
        String result = "";
        int k = 1;

        for (String s : arr) {
            if (occurrence.containsKey(s)) {
                continue;
            }

            occurrence.put(s, k);
            k++;
        }

        for (String s : arr) {
            if (freq.containsKey(s)) {
                freq.put(s, freq.get(s) + 1);
            } else
                freq.put(s, +1);

            if (max <= freq.get(s)) {
                if (max < freq.get(s)) {
                    max = freq.get(s);
                    result = s;
                } else {
                    if (occurrence.get(result)
                            < occurrence.get(s)) {
                        max = freq.get(s);
                        result = s;
                    }
                }
            }
        }

        return result;
    }
}
