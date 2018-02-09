package bertrandt.shadows.openGL.draw;

import android.content.Context;
import android.opengl.GLES20;

import bertrandt.shadows.openGL.basicObjects.Plane;
import bertrandt.shadows.openGL.importer.ImportObj;

/**
 * Created by buhrmanc on 05.02.2018.
 */

public class DrawPlane {

    private Context mContext;

    private Plane mPlane;

    private boolean initialised=false;

    public DrawPlane(Context context) {
        this.mContext = context;

        mPlane = new Plane(mContext);
        initialised = true;

    }

    public void setDraw(int positionAttribute, int normalAttribute,
                          int mTexelCoordinateHandle, int mTextureUniformHandle, boolean onlyPosition) {

        // Pass position information to shader
        mPlane.getPlanePosition().rewind();
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false,
                0, mPlane.getPlanePosition());

        GLES20.glEnableVertexAttribArray(positionAttribute);

        if (!onlyPosition)
        {
            // Pass normal information to shader
            mPlane.getPlaneNormal().rewind();
            GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false,
                    0, mPlane.getPlaneNormal());

            GLES20.glEnableVertexAttribArray(normalAttribute);


            // Pass in the texel information
            mPlane.getTexelsBuffer().rewind();
            GLES20.glVertexAttribPointer(mTexelCoordinateHandle, 2, GLES20.GL_FLOAT, false,
                    0, mPlane.getTexelsBuffer());
            GLES20.glEnableVertexAttribArray(mTexelCoordinateHandle);

            // Set the active texture unit to texture unit 0.
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            // Bind the texture to this unit.
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mPlane.getPlaneTextureHandle());

            GLES20.glUniform1i(mTextureUniformHandle, 0);

        }
    }

    public void draw(){
        // Draw the plane
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }

    public boolean getInitialised(){
        return initialised;
    }
}

