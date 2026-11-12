package com.example.wspro;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.IOException;
import java.util.Objects;

public class LogoSvgView extends View {

    private static final String TAG = "LogoSvgView";
    private String loadedAsset;
    private SVG logo;
    private final RectF bounds = new RectF();

    public LogoSvgView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public LogoSvgView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LogoSvgView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        if (getContentDescription() == null && context != null) {
            setContentDescription(context.getString(R.string.logo_content_description));
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        String asset = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                ? "logo_lockup_dark.svg"
                : "logo_lockup_light.svg";

        if (!Objects.equals(asset, loadedAsset)) {
            try {
                logo = SVG.getFromAsset(getContext().getAssets(), asset);
                loadedAsset = asset;
            } catch (IOException | SVGParseException | RuntimeException e) {
                Log.w(TAG, "Failed to load SVG logo asset: " + asset, e);
                logo = null;
            }
        }

        if (logo != null) {
            logo.setDocumentWidth(getWidth());
            logo.setDocumentHeight(getHeight());
            bounds.set(0, 0, getWidth(), getHeight());
            logo.renderToCanvas(canvas, bounds);
        }
    }
}
