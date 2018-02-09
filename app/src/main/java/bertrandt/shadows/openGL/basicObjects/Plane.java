package bertrandt.shadows.openGL.basicObjects;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import bertrandt.shadows.R;
import bertrandt.shadows.openGL.common.TextureHelper;

/**
 * Created by buhrmanc on 05.02.2018.
 */

public class Plane {

    private FloatBuffer mPlanePosition;
    private FloatBuffer mPlaneNormal;
    private FloatBuffer mTexelsBuffer;

    private int mBytesPerFloat = 4;

    private int mPlaneTextureHandle;

    float[] planePositionData = {
            // X, Y, Z,
            -30.0f, -1.5f, -20.0f,
            -30.0f, -1.5f, 7.0f,
            30.0f, -1.5f, -20.0f,
            -30.0f, -1.5f, 7.0f,
            30.0f, -1.5f, 7.0f,
            30.0f, -1.5f, -20.0f
    };

    float[] planeNormalData = {
            // nX, nY, nZ
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f
    };

    float[] planeTexelData = {
            // U, V
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };

    public Plane(Context context) {
        // Buffer initialization
        mPlanePosition = ByteBuffer.allocateDirect(planePositionData.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mPlanePosition.put(planePositionData).position(0);

        mPlaneNormal = ByteBuffer.allocateDirect(planeNormalData.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mPlaneNormal.put(planeNormalData).position(0);

        mTexelsBuffer = ByteBuffer.allocateDirect(planeTexelData.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexelsBuffer.put(planeTexelData).position(0);

        mPlaneTextureHandle = TextureHelper.loadTexture(context, R.drawable.ground);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
    }

    public FloatBuffer getPlanePosition() {
        return mPlanePosition;
    }

    public FloatBuffer getPlaneNormal() {
        return mPlaneNormal;
    }

    public FloatBuffer getTexelsBuffer() {
        return mTexelsBuffer;
    }

    public int getPlaneTextureHandle() {
        return mPlaneTextureHandle;
    }
}
