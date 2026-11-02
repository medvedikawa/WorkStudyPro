package com.example.wspro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class CropImageView extends View {

    private Bitmap mOriginalBitmap;
    private Bitmap mRotatedBitmap;
    private int mRotationDegrees = 0;

    private final RectF mImageRect = new RectF();
    private final RectF mCropRect = new RectF();

    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mScrimPaint = new Paint();
    private final Paint mFramePaint = new Paint();
    private final Paint mGuidePaint = new Paint();
    private final Paint mHandlePaint = new Paint();
    private final Paint mHandleInnerPaint = new Paint();

    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_TOP_LEFT = 1;
    private static final int TOUCH_TOP_RIGHT = 2;
    private static final int TOUCH_BOTTOM_LEFT = 3;
    private static final int TOUCH_BOTTOM_RIGHT = 4;
    private static final int TOUCH_MOVE = 5;

    private int mTouchMode = TOUCH_NONE;
    private float mLastTouchX = 0f;
    private float mLastTouchY = 0f;

    private float mHandleRadius = 24f;
    private float mTouchRadius = 48f;
    private float mMinCropSize = 80f;

    private boolean mIsCircular = false;
    private ScaleGestureDetector mScaleDetector;

    public void setCircular(boolean circular) {
        this.mIsCircular = circular;
        if (getWidth() > 0 && getHeight() > 0) {
            recalculateBounds(getWidth(), getHeight(), true);
        }
        invalidate();
    }

    public CropImageView(Context context) {
        super(context);
        init();
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        mHandleRadius = 10f * density;
        mTouchRadius = 32f * density;
        mMinCropSize = 50f * density;

        // Semi-transparent darkened background outside the crop box
        mScrimPaint.setColor(ContextCompat.getColor(getContext(), R.color.crop_scrim));
        mScrimPaint.setStyle(Paint.Style.FILL);

        // Vibrant border around crop window
        mFramePaint.setColor(ContextCompat.getColor(getContext(), R.color.ws_accent));
        mFramePaint.setStyle(Paint.Style.STROKE);
        mFramePaint.setStrokeWidth(2.5f * density);
        mFramePaint.setAntiAlias(true);

        // Rule of thirds subtle grid lines
        mGuidePaint.setColor(ContextCompat.getColor(getContext(), R.color.crop_grid));
        mGuidePaint.setStyle(Paint.Style.STROKE);
        mGuidePaint.setStrokeWidth(density);
        mGuidePaint.setAntiAlias(true);

        // Corner handles
        mHandlePaint.setColor(ContextCompat.getColor(getContext(), R.color.ws_accent));
        mHandlePaint.setStyle(Paint.Style.FILL);
        mHandlePaint.setAntiAlias(true);

        mHandleInnerPaint.setColor(ContextCompat.getColor(getContext(), R.color.ws_on_brand));
        mHandleInnerPaint.setStyle(Paint.Style.FILL);
        mHandleInnerPaint.setAntiAlias(true);

        mScaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                if (scaleFactor == 1.0f) return true;

                float cx = mCropRect.centerX();
                float cy = mCropRect.centerY();
                float currentW = mCropRect.width();
                float currentH = mCropRect.height();

                if (mIsCircular) {
                    float maxSize = Math.min(mImageRect.width(), mImageRect.height());
                    float newSize = Math.max(mMinCropSize, Math.min(maxSize, currentW * scaleFactor));
                    float half = newSize / 2f;

                    float newL = cx - half;
                    float newT = cy - half;
                    float newR = cx + half;
                    float newB = cy + half;

                    if (newL < mImageRect.left) {
                        newR += (mImageRect.left - newL);
                        newL = mImageRect.left;
                    }
                    if (newR > mImageRect.right) {
                        newL -= (newR - mImageRect.right);
                        newR = mImageRect.right;
                    }
                    if (newT < mImageRect.top) {
                        newB += (mImageRect.top - newT);
                        newT = mImageRect.top;
                    }
                    if (newB > mImageRect.bottom) {
                        newT -= (newB - mImageRect.bottom);
                        newB = mImageRect.bottom;
                    }

                    mCropRect.set(
                            Math.max(mImageRect.left, newL),
                            Math.max(mImageRect.top, newT),
                            Math.min(mImageRect.right, newR),
                            Math.min(mImageRect.bottom, newB)
                    );
                } else {
                    float newW = Math.max(mMinCropSize, Math.min(mImageRect.width(), currentW * scaleFactor));
                    float newH = Math.max(mMinCropSize, Math.min(mImageRect.height(), currentH * scaleFactor));
                    float halfW = newW / 2f;
                    float halfH = newH / 2f;
                    float left = Math.max(mImageRect.left, Math.min(mImageRect.right - newW, cx - halfW));
                    float top = Math.max(mImageRect.top, Math.min(mImageRect.bottom - newH, cy - halfH));
                    mCropRect.set(left, top, left + newW, top + newH);
                }

                invalidate();
                return true;
            }
        });
    }

    public void setImageBitmap(Bitmap bitmap) {
        this.mOriginalBitmap = bitmap;
        this.mRotationDegrees = 0;
        updateRotatedBitmap();
        if (getWidth() > 0 && getHeight() > 0) {
            recalculateBounds(getWidth(), getHeight(), true);
        }
        requestLayout();
        invalidate();
    }

    public void rotateClockwise() {
        if (mOriginalBitmap == null) return;
        mRotationDegrees = (mRotationDegrees + 90) % 360;
        updateRotatedBitmap();
        recalculateBounds(getWidth(), getHeight(), true);
        invalidate();
    }

    public void rotateCounterClockwise() {
        if (mOriginalBitmap == null) return;
        mRotationDegrees = (mRotationDegrees + 270) % 360;
        updateRotatedBitmap();
        recalculateBounds(getWidth(), getHeight(), true);
        invalidate();
    }

    public void resetCrop() {
        if (mImageRect.width() > 0 && mImageRect.height() > 0) {
            if (mIsCircular) {
                float size = Math.min(mImageRect.width(), mImageRect.height());
                float cx = mImageRect.centerX();
                float cy = mImageRect.centerY();
                mCropRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            } else {
                mCropRect.set(mImageRect);
            }
            invalidate();
        }
    }

    public CropData getCropData() {
        if (mRotatedBitmap == null) {
            return new CropData(null, 0, 0, 0, 0);
        }
        if (mImageRect.width() <= 0 || mImageRect.height() <= 0) {
            return new CropData(
                    mRotatedBitmap,
                    0,
                    0,
                    mRotatedBitmap.getWidth(),
                    mRotatedBitmap.getHeight()
            );
        }

        float scaleX = (float) mRotatedBitmap.getWidth() / mImageRect.width();
        float scaleY = (float) mRotatedBitmap.getHeight() / mImageRect.height();

        int cropX = Math.max(
                0,
                Math.min(
                        Math.round((mCropRect.left - mImageRect.left) * scaleX),
                        mRotatedBitmap.getWidth() - 1
                )
        );
        int cropY = Math.max(
                0,
                Math.min(
                        Math.round((mCropRect.top - mImageRect.top) * scaleY),
                        mRotatedBitmap.getHeight() - 1
                )
        );
        int cropWidth = Math.max(
                1,
                Math.min(Math.round(mCropRect.width() * scaleX), mRotatedBitmap.getWidth() - cropX)
        );
        int cropHeight = Math.max(
                1,
                Math.min(Math.round(mCropRect.height() * scaleY), mRotatedBitmap.getHeight() - cropY)
        );

        return new CropData(mRotatedBitmap, cropX, cropY, cropWidth, cropHeight);
    }

    public static Bitmap createCroppedBitmap(CropData cropData) {
        if (cropData.bitmap == null) {
            return null;
        }
        return Bitmap.createBitmap(
                cropData.bitmap,
                cropData.x,
                cropData.y,
                cropData.width,
                cropData.height
        );
    }

    public static Bitmap createCircularCroppedBitmap(CropData cropData) {
        Bitmap cropped = createCroppedBitmap(cropData);
        if (cropped == null) return null;
        return getCircularBitmap(cropped);
    }

    public static Bitmap getCircularBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        android.graphics.Rect srcRect = new android.graphics.Rect(
                (bitmap.getWidth() - size) / 2,
                (bitmap.getHeight() - size) / 2,
                (bitmap.getWidth() + size) / 2,
                (bitmap.getHeight() + size) / 2
        );
        android.graphics.Rect dstRect = new android.graphics.Rect(0, 0, size, size);

        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);
        return output;
    }

    public static final class CropData {
        private final Bitmap bitmap;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private CropData(Bitmap bitmap, int x, int y, int width, int height) {
            this.bitmap = bitmap;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private void updateRotatedBitmap() {
        if (mOriginalBitmap == null) {
            mRotatedBitmap = null;
            return;
        }
        if (mRotationDegrees == 0) {
            mRotatedBitmap = mOriginalBitmap;
        } else {
            Matrix matrix = new Matrix();
            matrix.postRotate(mRotationDegrees);
            mRotatedBitmap = Bitmap.createBitmap(
                    mOriginalBitmap, 0, 0,
                    mOriginalBitmap.getWidth(), mOriginalBitmap.getHeight(),
                    matrix, true
            );
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            recalculateBounds(w, h, mCropRect.isEmpty());
        }
    }

    private void recalculateBounds(int viewW, int viewH, boolean resetCropRect) {
        if (mRotatedBitmap == null || viewW <= 0 || viewH <= 0) return;

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        int availW = Math.max(10, viewW - (pad * 2));
        int availH = Math.max(10, viewH - (pad * 2));

        float bitmapW = mRotatedBitmap.getWidth();
        float bitmapH = mRotatedBitmap.getHeight();

        float scale = Math.min((float) availW / bitmapW, (float) availH / bitmapH);

        float drawW = bitmapW * scale;
        float drawH = bitmapH * scale;

        float left = (viewW - drawW) / 2f;
        float top = (viewH - drawH) / 2f;

        mImageRect.set(left, top, left + drawW, top + drawH);

        if (mIsCircular || resetCropRect || mCropRect.isEmpty()) {
            if (mIsCircular) {
                float size = Math.min(mImageRect.width(), mImageRect.height());
                float cx = mImageRect.centerX();
                float cy = mImageRect.centerY();
                mCropRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            } else {
                mCropRect.set(mImageRect);
            }
        } else {
            // Constrain existing crop rect to new image bounds
            mCropRect.left = Math.max(mImageRect.left, Math.min(mCropRect.left, mImageRect.right - mMinCropSize));
            mCropRect.top = Math.max(mImageRect.top, Math.min(mCropRect.top, mImageRect.bottom - mMinCropSize));
            mCropRect.right = Math.min(mImageRect.right, Math.max(mCropRect.right, mCropRect.left + mMinCropSize));
            mCropRect.bottom = Math.min(mImageRect.bottom, Math.max(mCropRect.bottom, mCropRect.top + mMinCropSize));
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mRotatedBitmap == null || mRotatedBitmap.isRecycled()) return;

        // 1. Draw base bitmap
        canvas.drawBitmap(mRotatedBitmap, null, mImageRect, mBitmapPaint);

        if (mIsCircular) {
            android.graphics.Path path = new android.graphics.Path();
            path.addRect(0, 0, getWidth(), getHeight(), android.graphics.Path.Direction.CW);
            float radius = Math.min(mCropRect.width(), mCropRect.height()) / 2f;
            path.addCircle(mCropRect.centerX(), mCropRect.centerY(), radius, android.graphics.Path.Direction.CCW);
            canvas.drawPath(path, mScrimPaint);

            canvas.drawCircle(mCropRect.centerX(), mCropRect.centerY(), radius, mFramePaint);
        } else {
            // 2. Draw darkened scrims outside mCropRect
            canvas.drawRect(0, 0, getWidth(), mCropRect.top, mScrimPaint);
            canvas.drawRect(0, mCropRect.bottom, getWidth(), getHeight(), mScrimPaint);
            canvas.drawRect(0, mCropRect.top, mCropRect.left, mCropRect.bottom, mScrimPaint);
            canvas.drawRect(mCropRect.right, mCropRect.top, getWidth(), mCropRect.bottom, mScrimPaint);

            // 3. Draw Rule of Thirds Guide Lines inside crop rect
            float thirdW = mCropRect.width() / 3f;
            float thirdH = mCropRect.height() / 3f;
            canvas.drawLine(mCropRect.left + thirdW, mCropRect.top, mCropRect.left + thirdW, mCropRect.bottom, mGuidePaint);
            canvas.drawLine(mCropRect.left + (thirdW * 2f), mCropRect.top, mCropRect.left + (thirdW * 2f), mCropRect.bottom, mGuidePaint);
            canvas.drawLine(mCropRect.left, mCropRect.top + thirdH, mCropRect.right, mCropRect.top + thirdH, mGuidePaint);
            canvas.drawLine(mCropRect.left, mCropRect.top + (thirdH * 2f), mCropRect.right, mCropRect.top + (thirdH * 2f), mGuidePaint);

            // 4. Draw Crop Rectangle Frame
            canvas.drawRect(mCropRect, mFramePaint);

            // 5. Draw 4 Corner Handles
            drawCornerHandle(canvas, mCropRect.left, mCropRect.top);
            drawCornerHandle(canvas, mCropRect.right, mCropRect.top);
            drawCornerHandle(canvas, mCropRect.left, mCropRect.bottom);
            drawCornerHandle(canvas, mCropRect.right, mCropRect.bottom);
        }
    }

    private void drawCornerHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, mHandleRadius, mHandlePaint);
        canvas.drawCircle(x, y, mHandleRadius * 0.5f, mHandleInnerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mScaleDetector != null) {
            mScaleDetector.onTouchEvent(event);
        }

        if (mScaleDetector != null && mScaleDetector.isInProgress()) {
            mTouchMode = TOUCH_NONE;
            return true;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mTouchMode = getTouchHandle(x, y);
                mLastTouchX = x;
                mLastTouchY = y;
                return mTouchMode != TOUCH_NONE || super.onTouchEvent(event);

            case MotionEvent.ACTION_POINTER_DOWN:
                mTouchMode = TOUCH_NONE;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mTouchMode == TOUCH_NONE) return false;

                float dx = x - mLastTouchX;
                float dy = y - mLastTouchY;

                if (mIsCircular) {
                    if (mTouchMode == TOUCH_MOVE) {
                        float w = mCropRect.width();
                        float h = mCropRect.height();
                        float newL = Math.max(mImageRect.left, Math.min(mImageRect.right - w, mCropRect.left + dx));
                        float newT = Math.max(mImageRect.top, Math.min(mImageRect.bottom - h, mCropRect.top + dy));
                        mCropRect.set(newL, newT, newL + w, newT + h);
                    } else if (mTouchMode != TOUCH_NONE) {
                        float delta = Math.abs(dx) > Math.abs(dy) ? dx : dy;
                        if (mTouchMode == TOUCH_TOP_LEFT || mTouchMode == TOUCH_BOTTOM_LEFT) {
                            delta = -delta;
                        }
                        float maxSize = Math.min(mImageRect.width(), mImageRect.height());
                        float newSize = Math.max(mMinCropSize, Math.min(maxSize, mCropRect.width() + delta));
                        float cx = mCropRect.centerX();
                        float cy = mCropRect.centerY();
                        float half = newSize / 2f;

                        float newL = Math.max(mImageRect.left, Math.min(mImageRect.right - newSize, cx - half));
                        float newT = Math.max(mImageRect.top, Math.min(mImageRect.bottom - newSize, cy - half));
                        mCropRect.set(newL, newT, newL + newSize, newT + newSize);
                    }
                } else {
                    if (mTouchMode == TOUCH_TOP_LEFT) {
                        mCropRect.left = Math.min(mCropRect.right - mMinCropSize, Math.max(mImageRect.left, mCropRect.left + dx));
                        mCropRect.top = Math.min(mCropRect.bottom - mMinCropSize, Math.max(mImageRect.top, mCropRect.top + dy));
                    } else if (mTouchMode == TOUCH_TOP_RIGHT) {
                        mCropRect.right = Math.max(mCropRect.left + mMinCropSize, Math.min(mImageRect.right, mCropRect.right + dx));
                        mCropRect.top = Math.min(mCropRect.bottom - mMinCropSize, Math.max(mImageRect.top, mCropRect.top + dy));
                    } else if (mTouchMode == TOUCH_BOTTOM_LEFT) {
                        mCropRect.left = Math.min(mCropRect.right - mMinCropSize, Math.max(mImageRect.left, mCropRect.left + dx));
                        mCropRect.bottom = Math.max(mCropRect.top + mMinCropSize, Math.min(mImageRect.bottom, mCropRect.bottom + dy));
                    } else if (mTouchMode == TOUCH_BOTTOM_RIGHT) {
                        mCropRect.right = Math.max(mCropRect.left + mMinCropSize, Math.min(mImageRect.right, mCropRect.right + dx));
                        mCropRect.bottom = Math.max(mCropRect.top + mMinCropSize, Math.min(mImageRect.bottom, mCropRect.bottom + dy));
                    } else if (mTouchMode == TOUCH_MOVE) {
                        float w = mCropRect.width();
                        float h = mCropRect.height();
                        float newL = Math.max(mImageRect.left, Math.min(mImageRect.right - w, mCropRect.left + dx));
                        float newT = Math.max(mImageRect.top, Math.min(mImageRect.bottom - h, mCropRect.top + dy));
                        mCropRect.set(newL, newT, newL + w, newT + h);
                    }
                }

                mLastTouchX = x;
                mLastTouchY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchMode = TOUCH_NONE;
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private int getTouchHandle(float x, float y) {
        if (isNear(x, y, mCropRect.left, mCropRect.top)) return TOUCH_TOP_LEFT;
        if (isNear(x, y, mCropRect.right, mCropRect.top)) return TOUCH_TOP_RIGHT;
        if (isNear(x, y, mCropRect.left, mCropRect.bottom)) return TOUCH_BOTTOM_LEFT;
        if (isNear(x, y, mCropRect.right, mCropRect.bottom)) return TOUCH_BOTTOM_RIGHT;
        if (mCropRect.contains(x, y)) return TOUCH_MOVE;
        return TOUCH_NONE;
    }

    private boolean isNear(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (dx * dx + dy * dy) <= (mTouchRadius * mTouchRadius);
    }
}
