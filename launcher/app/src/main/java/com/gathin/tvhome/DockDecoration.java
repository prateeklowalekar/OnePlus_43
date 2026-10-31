package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Adds a gap below the first (dock) row and, unless config.xml dock_band is false, draws the tvOS style
 * band behind it. Without the band the dock tiles sit straight on the wallpaper, like the grid rows.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final boolean drawBand;
    private final boolean isV2;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    DockDecoration(int dockSize, float density, boolean drawBand, boolean isV2) {
        this.dockSize = dockSize;
        this.density = density;
        this.drawBand = drawBand;
        this.isV2 = isV2;
        band.setColor(isV2 ? 0xB30A0E17 : 0xFF141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(isV2 ? 1.5f * density : density);
        edge.setColor(isV2 ? 0x38FFFFFF : 0x1FFFFFFF);
    }

    DockDecoration(int dockSize, float density, boolean drawBand) {
        this(dockSize, density, drawBand, false);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!drawBand || isV2) return;
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float padX = (isV2 ? 18f : 14f) * density;
        float padY = (isV2 ? 12f : 14f) * density;
        rect.left -= padX;
        rect.right += padX;
        rect.top -= padY;
        rect.bottom += padY;
        float radius = (isV2 ? 24f : 26f) * density;
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
