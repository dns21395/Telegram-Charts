package com.bitvale.chartview.widget.chart;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.bitvale.chartview.ChartSpinnerListener;
import com.bitvale.chartview.R;
import com.bitvale.chartview.Utils;
import com.bitvale.chartview.model.Chart;

import java.util.ArrayList;
import java.util.Collections;

import static com.bitvale.chartview.widget.chart.ChartView.ANIMATION_DURATION;
import static com.bitvale.chartview.widget.chart.ChartView.OPAQUE;
import static com.bitvale.chartview.widget.chart.ChartView.TRANSPARENT;

/**
 * Created by Alexander Kolpakov (jquickapp@gmail.com) on 16-Mar-19
 */
public class ChartSpinner extends View {

    private ChartSpinnerListener listener;

    private ArrayList<Long> xAxis = new ArrayList<>();
    private ArrayList<Chart.Column> yAxis = new ArrayList<Chart.Column>();

    private Paint chartPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @ColorInt
    private int foregroundColor = 0;
    @ColorInt
    private int frameColor = 0;

    private int spinnerHeight = 0;

    private Path chartPath = new Path();

    private float xMultiplier = 0f;
    private float yMultiplier = 0f;

    private float smallPadding = 0f;
    private int minFrameWidth = 0;
    private int frameSideSize = 0;
    private Rect frameOuterRect = new Rect();
    private Rect frameInnerRect = new Rect();
    private Region frameRegion = new Region();

    private int currentFrameWidth = 0;

    private float dX = 0f;
    private float deltaX;
    private  ChartSpinner.State state = State.IDLE;
    private int currentOuterLeft = 0;
    private int currentOuterRight = 0;

    private boolean moveLeftBorder = false;
    private boolean moveRightBorder = false;

    private ValueAnimator translateAnimator;
    private float linesOffset = 0f;

    private float chartAlpha = OPAQUE;
    private float chartTranslationOffset = 0f;

    public ChartSpinner(Context context) {
        super(context);
        init(context, null, 0);
    }

    public ChartSpinner(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public ChartSpinner(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        setId(generateViewId());
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        TypedArray a = context.obtainStyledAttributes(
                attrs,
                R.styleable.ChartSpinner,
                R.attr.chartSpinnerStyle,
                R.style.ChartSpinner
        );

        foregroundColor = a.getColor(R.styleable.ChartSpinner_foreground_color, 0);
        frameColor = a.getColor(R.styleable.ChartSpinner_frame_color, 0);

        spinnerHeight = a.getDimensionPixelOffset(R.styleable.ChartSpinner_spinner_height, 0);

        chartPaint.setStyle(Paint.Style.STROKE);

        smallPadding = context.getResources().getDisplayMetrics().density * 4;
        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(widthSize, spinnerHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (yAxis.isEmpty()) return;
        linesOffset = h / 4f;
        xMultiplier = ((float) getWidth()) / (xAxis.size() - 1);
        calculateMultipliers();
        calculateFrameSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (yAxis.isEmpty()) return;

        calculateMultipliers();
        for (int i = 0; i < yAxis.size(); i++) {
            Chart.Column column = yAxis.get(i);
            if (column.enabled || column.animation != Chart.ChartAnimation.NONE) drawChart(canvas, yAxis.get(i));
        }
        drawFrame(canvas);
        drawForeground(canvas);
    }

    private void drawChart(Canvas canvas, Chart.Column column) {
        chartPaint.setColor(Color.parseColor(column.color));
        chartPaint.setStrokeWidth(4f);
        chartPath.reset();

        for (int i = 0; i < column.values.size(); i++) {
            float x = i * xMultiplier;
            float y = getHeight() - column.values.get(i) * yCurrentMultiplier - smallPadding;
            if (column.animation != Chart.ChartAnimation.NONE) {
                y += chartTranslationOffset;
                chartPaint.setAlpha((int) chartAlpha);
            }
            if (i == 0) {
                chartPath.moveTo(x, y);
            } else {
                chartPath.lineTo(x, y);
            }
        }
        canvas.drawPath(chartPath, chartPaint);
    }

    public void animateInOut(boolean out) {
        if (translateAnimator != null) translateAnimator.cancel();

        float from = 0f;
        float to = 1f;
        if (out) to = -1f;

        translateAnimator = ValueAnimator.ofFloat(from, to);

        translateAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            if (out) {
                chartTranslationOffset = Utils.lerp(0f, linesOffset * 4, value);
                chartAlpha = Utils.lerp(OPAQUE, TRANSPARENT, Math.abs(value));
            } else {
                chartTranslationOffset = Utils.lerp(linesOffset * -4, 0f, value);
                chartAlpha = Utils.lerp(TRANSPARENT, OPAQUE, Math.abs(value));
            }
            invalidate();
        });
        translateAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                for (int i = 0; i < yAxis.size(); i++) {
                    yAxis.get(i).animation = Chart.ChartAnimation.NONE;
                }
            }
        });

        translateAnimator.setInterpolator(new FastOutSlowInInterpolator());
        translateAnimator.setDuration(ANIMATION_DURATION);

        translateAnimator.start();
    }

    private void drawFrame(Canvas canvas) {
        paint.setColor(frameColor);
        frameRegion.set(frameOuterRect);
        frameRegion.op(frameInnerRect, Region.Op.XOR);
        canvas.drawPath(frameRegion.getBoundaryPath(), paint);
    }

    private void calculateFrameSize() {
        minFrameWidth = 6 * (getWidth() / xAxis.size());
        frameSideSize = minFrameWidth / 4;
        if (currentFrameWidth == 0) currentFrameWidth = minFrameWidth;
        updateFrameSize();
    }

    private void updateFrameSize() {
        frameOuterRect.set(getWidth() - currentFrameWidth, 0, getWidth(), getHeight());
        frameInnerRect.set(
                (int) (frameOuterRect.left + (frameSideSize * 1.5)),
                (frameOuterRect.top + frameSideSize / 2),
                (int) (frameOuterRect.right - (frameSideSize * 1.5)),
                (frameOuterRect.bottom - frameSideSize / 2)
        );
        calculateChartData(moveRightBorder, moveLeftBorder);
    }

    private void calculateChartData(boolean moveRightBorder, boolean moveLeftBorder) {
        int frameStartPosition = frameOuterRect.left;
        int frameEndPosition = frameOuterRect.right;
        int frameWidth = frameEndPosition - frameStartPosition;
        float oneDayWidth = getWidth() / ((float) xAxis.size());
        int daysInFrame = (int) (Math.ceil(frameWidth / oneDayWidth));
        int daysAfterFrame = (int) (Math.floor((getWidth() - frameEndPosition) / oneDayWidth));
        int daysBeforeFrame = (int) (Math.floor((frameStartPosition / oneDayWidth)));

        if (moveLeftBorder) {
            if (daysBeforeFrame < xAxis.size() - daysAfterFrame - daysInFrame) daysBeforeFrame =
                    xAxis.size() - daysAfterFrame - daysInFrame;
        }
        if (moveRightBorder) {
            if (daysAfterFrame > xAxis.size() - daysBeforeFrame - daysInFrame) daysAfterFrame =
                    xAxis.size() - daysAfterFrame - daysInFrame;
        }
        listener.onRangeChanged(daysBeforeFrame, daysAfterFrame, daysInFrame, deltaX, state);
    }

    private void drawForeground(Canvas canvas) {
        paint.setColor(foregroundColor);
        canvas.drawRect(0f, 0f, frameOuterRect.left, spinnerHeight, paint);
        canvas.drawRect(frameOuterRect.right, 0f, getWidth(), spinnerHeight, paint);
    }

    private void calculateMultipliers() {
        int max = 0;
        for (int i = 0; i < yAxis.size(); i++) {
            Chart.Column column = yAxis.get(i);
            if (column.enabled || column.animation == Chart.ChartAnimation.DOWN) {
                long newMax = Collections.max(yAxis.get(i).values);
                if (newMax > max) max = (int) newMax;
            }
        }

        yMultiplier = ((float) spinnerHeight - smallPadding * 2) / max;

        if (yCurrentMultiplier == 0f) yCurrentMultiplier = yMultiplier;

        if (yMaxValue != 0) {
            if (yMaxValue > max) startYAxisAnimation();
            else if (yMaxValue < max) startYAxisAnimation();
        }
        yMaxValue = max;
    }

    private ValueAnimator yMultiplierAnimator;
    private float yCurrentMultiplier = 0f;
    private int yMaxValue = 0;

    private void startYAxisAnimation() {
        if (yMultiplierAnimator != null) yMultiplierAnimator.cancel();

        yMultiplierAnimator = ValueAnimator.ofFloat(yCurrentMultiplier, yMultiplier);

        yMultiplierAnimator.addUpdateListener(animation -> {
            yCurrentMultiplier = (float) animation.getAnimatedValue();
            invalidate();
        });

        yMultiplierAnimator.setDuration(ANIMATION_DURATION);
        yMultiplierAnimator.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        int action = event.getAction() & MotionEvent.ACTION_MASK;

        switch (action) {
            case MotionEvent.ACTION_UP:
                state = State.IDLE;
                break;
            case MotionEvent.ACTION_DOWN:
                dX = x;
                currentOuterLeft = frameOuterRect.left;
                currentOuterRight = frameOuterRect.right;

                moveLeftBorder = false;
                moveRightBorder = false;

                if (x < frameInnerRect.left + 20) {
                    moveLeftBorder = true;
                }
                if (x > frameInnerRect.right - 20) {
                    moveRightBorder = true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                deltaX = 0;
                state = State.DRAGGIN;
                int l = currentOuterLeft + (int) (x - dX);
                int r = currentOuterRight + (int) (x - dX);
                if (moveLeftBorder) {
                    frameOuterRect.left = l;
                    if (frameOuterRect.width() < minFrameWidth) {
                        frameOuterRect.left = frameOuterRect.right - minFrameWidth;
                    }
                    if (frameOuterRect.left < 0) {
                        state = State.IDLE;
                        frameOuterRect.left = 0;
                    }
                    frameInnerRect.left = frameOuterRect.left + (int) (frameSideSize * 1.5);
                } else {
                    if (moveRightBorder) {
                        frameOuterRect.right = r;
                        if (frameOuterRect.width() < minFrameWidth) {
                            frameOuterRect.right = frameOuterRect.left + minFrameWidth;
                        }
                        if (frameOuterRect.right > getWidth()) {
                            state = State.IDLE;
                            frameOuterRect.right = getWidth();
                        }
                        frameInnerRect.right = frameOuterRect.right - (int) (frameSideSize * 1.5);
                    } else {
                        deltaX = x - dX;
                        if (l < 0) {
                            state = State.IDLE;
                            r = frameOuterRect.right;
                            l = 0;
                        }
                        if (r > getWidth()) {
                            state = State.IDLE;
                            r = getWidth();
                            l = frameOuterRect.left;
                        }
                        frameOuterRect.left = l;
                        frameOuterRect.right = r;
                        frameInnerRect.left = l + ((int) (frameSideSize * 1.5));
                        frameInnerRect.right = r - ((int) (frameSideSize * 1.5));
                    }
                }
                break;
        }
        calculateChartData(moveRightBorder, moveLeftBorder);
        invalidate();
        return true;
    }

    public void setupData(ArrayList<Long> xAxis, ArrayList<Chart.Column> yAxis) {
        this.xAxis.clear();
        this.yAxis.clear();
        this.xAxis.addAll(xAxis);
        this.yAxis.addAll(yAxis);
        invalidate();
    }

    public void setChartListener(ChartSpinnerListener listener) {
        this.listener = listener;
    }


    public enum State {
        DRAGGIN,
        IDLE
    }
}
