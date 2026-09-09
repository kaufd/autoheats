package com.wt.airconditioner;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Трек слайдера: толстая полоса со скруглением, пройденная часть залита
 * акцентом, остальная — полупрозрачная белая, а по делениям идут точки.
 *
 * Стандартный SeekBar рисует тонкую линию без делений, поэтому трек свой:
 * пользователь ставит минуты пальцем, и точки показывают, куда он попадёт.
 */
final class SliderTrack extends Drawable {

    /** Уровень SeekBar приходит в диапазоне 0…10000. */
    private static final float LEVEL_MAX = 10000f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Переиспользуется: draw() зовётся на каждый кадр перетаскивания. */
    private final RectF bar = new RectF();
    private final int activeColor;
    private final int inactiveColor;
    private final int divisions;
    private final float thickness;
    private final float tickRadius;

    SliderTrack(int activeColor, int divisions, float thicknessPx, float tickRadiusPx) {
        this.activeColor = activeColor;
        /**
         * Непройденная часть трека: белый с прозрачностью.
         */
        this.inactiveColor = Color.argb(153, 255, 255, 255);
        this.divisions = divisions;
        this.thickness = thicknessPx;
        this.tickRadius = tickRadiusPx;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float centerY = bounds.exactCenterY();
        float top = centerY - thickness / 2f;
        float bottom = centerY + thickness / 2f;
        float radius = thickness / 2f;
        float progressX = bounds.left + bounds.width() * (getLevel() / LEVEL_MAX);

        paint.setColor(inactiveColor);
        bar.set(bounds.left, top, bounds.right, bottom);
        canvas.drawRoundRect(bar, radius, radius, paint);

        paint.setColor(activeColor);
        bar.set(bounds.left, top, progressX, bottom);
        canvas.drawRoundRect(bar, radius, radius, paint);

        if (divisions > 0) {
            for (int index = 0; index <= divisions; index++) {
                float x = bounds.left + bounds.width() * index / (float) divisions;
                /**
                 * Точка на пройденной части должна быть видна поверх заливки.
                 */
                paint.setColor(x <= progressX ? inactiveColor : Color.argb(90, 0, 0, 0));
                canvas.drawCircle(x, centerY, tickRadius, paint);
            }
        }
    }

    @Override
    protected boolean onLevelChange(int level) {
        invalidateSelf();
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(thickness);
    }
}
