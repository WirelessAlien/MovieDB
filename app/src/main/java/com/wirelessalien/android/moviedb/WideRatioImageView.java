package com.wirelessalien.android.moviedb;

import android.content.Context;
import android.util.AttributeSet;

public class WideRatioImageView extends androidx.appcompat.widget.AppCompatImageView {

    public WideRatioImageView(Context context) {
        super(context);
    }

    public WideRatioImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WideRatioImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = width * 9 / 16; // Height will be calculated for 16:9 ratio
        setMeasuredDimension(width, height);
    }
}