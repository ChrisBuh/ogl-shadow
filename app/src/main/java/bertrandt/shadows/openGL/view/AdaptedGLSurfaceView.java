package bertrandt.shadows.openGL.view;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import bertrandt.shadows.R;
import bertrandt.shadows.openGL.Renderer;

/**
 * Created by buhrmanc on 05.02.2018.
 */

public class AdaptedGLSurfaceView extends GLSurfaceView {
    private bertrandt.shadows.openGL.Renderer mRenderer;
    private final float TOUCH_SCALE_FACTOR = 180.0f / 320;
    private float mPreviousX;
    private float mPreviousY;

    public AdaptedGLSurfaceView(Context context) {
        super(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();

        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE:

                float dx = x - mPreviousX;
                float dy = y - mPreviousY;

                mRenderer.setRotationX(
                        mRenderer.getRotationX() +
                                (dx * TOUCH_SCALE_FACTOR));  // = 180.0f / 320

                mRenderer.setRotationY(
                        mRenderer.getRotationY() +
                                (dy * TOUCH_SCALE_FACTOR));  // = 180.0f / 320

                requestRender();
        }

        mPreviousX = x;
        mPreviousY = y;
        return true;

    }

    // Hides superclass method.
    public void setRenderer(bertrandt.shadows.openGL.Renderer renderer) {
        mRenderer = renderer;
        super.setRenderer(renderer);
    }
}