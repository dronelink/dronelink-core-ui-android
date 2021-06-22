//  DronelinkUI.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 1/4/21.
//  Copyright © 2021 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.dronelink.core.Dronelink;
import com.squareup.picasso.Callback;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

public class DronelinkUI {
    private static DronelinkUI instance = null;
    public static synchronized DronelinkUI initialize(final Context context) {
        instance = new DronelinkUI(context);
        return instance;
    }

    public static synchronized DronelinkUI getInstance() {
        return instance;
    }

    private static final String TAG = Dronelink.class.getCanonicalName();

    final Context context;

    public DronelinkUI(final Context context) {
        this.context = context;

        //image caching
        final Picasso.Builder builder = new Picasso.Builder(context);
        builder.downloader(new OkHttp3Downloader(context, Integer.MAX_VALUE));
        final Picasso built = builder.build();
        try {
            Picasso.setSingletonInstance(built);
        }
        catch (final IllegalStateException e) {
            //this fires if a singleton has already been set
        }
    }

    public void cacheImages(final String[] urls) {
        for (int i = 0; i < urls.length; i++) {
            final String image = urls[i];
            Picasso.get().load(image).fetch(new Callback() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "DronelinkUI cached image: " + image);
                }

                @Override
                public void onError(final Exception e) {}
            });
        }
    }

    public static int parseHexColor(String hex, final int defaultColor) {
        return parseHexColor(hex, defaultColor, null);
    }

    public static int parseHexColor(String hex, final int defaultColor, final String defaultAlpha) {
        if (hex == null || hex.isEmpty() || !(hex.length() == 7 || hex.length() == 9) || !hex.startsWith("#")) {
            return defaultColor;
        }

        if (hex.length() == 7 && defaultAlpha != null) {
            hex = hex + defaultAlpha;
        }

        //android likes aarrggbb
        if (hex.length() == 9) {
            hex = hex.substring(0, 1) + hex.substring(7, 9) + hex.substring(1, 7);
        }

        return Color.parseColor(hex);
    }
}