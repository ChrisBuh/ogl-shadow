package bertrandt.shadows.openGL;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import bertrandt.shadows.R;
import bertrandt.shadows.openGL.common.RawResourceReader;
import bertrandt.shadows.openGL.common.ShaderHelper;
import bertrandt.shadows.openGL.common.ShapeBuilder;
import bertrandt.shadows.openGL.common.TextureHelper;
import bertrandt.shadows.openGL.draw.DrawObj;
import bertrandt.shadows.openGL.draw.DrawPlane;

/**
 * Created by buhrmanc on 05.02.2018.
 */

public class Renderer implements GLSurfaceView.Renderer {
    /**
     * Used for debug logs.
     */
    private static final String TAG = "Renderer";

    /**
     * Current X,Y axis rotation of center cube
     */
    private float mRotationX;
    private float mRotationY;

    private final Context mActivityContext;

    /**
     * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
     * of being located at the center of the universe) to world space.
     */
    private float[] mModelMatrix = new float[16];

    /**
     * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
     * it positions things relative to our eye.
     */
    private float[] mViewMatrix = new float[16];

    /**
     * Store the projection matrix. This is used to project the scene onto a 2D viewport.
     */
    private float[] mProjectionMatrix = new float[16];

    /**
     * Allocate storage for the final combined matrix. This will be passed into the shader program.
     */
    private float[] mMVPMatrix = new float[16];

    /**
     * Store the accumulated rotation.
     */
    private final float[] mAccumulatedRotation = new float[16];

    /**
     * Store the current rotation.
     */
    private final float[] mCurrentRotation = new float[16];

    /**
     * A temporary matrix.
     */
    private float[] mTemporaryMatrix = new float[16];

    /**
     * Stores a copy of the model matrix specifically for the light position.
     */
    private float[] mLightModelMatrix = new float[16];


    /**
     * This will be used to pass in the transformation matrix.
     */
    private int mMVPMatrixHandle;

    /**
     * This will be used to pass in the modelview matrix.
     */
    private int mMVMatrixHandle;

    /**
     * This will be used to pass in the light position.
     */
    private int mLightPosHandle;

    /**
     * This will be used to pass in the texture.
     */
    private int mTextureUniformHandle;

    /**
     * This will be used to pass in model position information.
     */
    private int mPositionHandle;

    /**
     * This will be used to pass in model normal information.
     */
    private int mNormalHandle;

    /**
     * This will be used to pass in model texture coordinate information.
     */
    private int mTextureCoordinateHandle;

    /**
     * How many bytes per float.
     */
    private final int mBytesPerFloat = 4;

    /**
     * Size of the position data in elements.
     */
    private final int mPositionDataSize = 3;

    /**
     * Size of the normal data in elements.
     */
    private final int mNormalDataSize = 3;

    /**
     * Size of the texture coordinate data in elements.
     */
    private final int mTextureCoordinateDataSize = 2;

    /**
     * Used to hold a light centered on the origin in model space. We need a 4th coordinate so we can get translations to work when
     * we multiply this by our transformation matrices.
     */
    private final float[] mLightPosInModelSpace = new float[]{3.0f, 5.0f, 0.0f, 1.0f};

    /**
     * Used to hold the current position of the light in world space (after transformation via model matrix).
     */
    private final float[] mLightPosInWorldSpace = new float[4];

    /**
     * Used to hold the transformed position of the light in eye space (after transformation via modelview matrix)
     */
    private final float[] mLightPosInEyeSpace = new float[4];

    /**
     * This is a handle to our cube shading program.
     */
    private int mProgramHandle;

    /**
     * This is a handle to our light point program.
     */
    private int mPointProgramHandle;

    /**
     * These are handles to our texture data.
     */
    private int mObjDataHandle;
    private int mPlaneDataHandle;

    /**
     * Temporary place to save the min and mag filter, in case the activity was restarted.
     */
    private int mQueuedMinFilter;
    private int mQueuedMagFilter;

    // These still work without volatile, but refreshes are not guaranteed to happen.
    public volatile float mDeltaX;
    public volatile float mDeltaY;

    //Objects
    private DrawPlane mDrawPlane;
    private DrawObj mDrawObj;

    /**
     * Lightning
     */
    private float[] mLightProjectionMatrix = new float[16];
    private final float[] mLightViewMatrix = new float[16];

    /**
     * Initialize the model data.
     */
    public Renderer(final Context activityContext) {
        mActivityContext = activityContext;

    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        // Test OES_depth_texture extension
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);

        if (extensions.contains("OES_depth_texture"))
            mHasDepthTextureExtension = true;

        // Set the background clear color to black.
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        // Use culling to remove back faces.
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // The below glEnable() call is a holdover from OpenGL ES 1, and is not needed in OpenGL ES 2.
        // Enable texture mapping
        // GLES20.glEnable(GLES20.GL_TEXTURE_2D);

        // Position the eye in front of the origin.
        final float eyeX = 0.0f;
        final float eyeY = 3.0f;
        final float eyeZ = 5.0f;

        // We are looking toward the distance
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -5.0f;

        // Set our up vector. This is where our head would be pointing were we holding the camera.
        final float upX = 0.0f;
        final float upY = 1.0f;
        final float upZ = 0.0f;

        // Set the view matrix. This matrix can be said to represent the camera position.
        // NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
        // view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
        Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

    /*    final String vertexShader = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.per_pixel_vertex_shader_tex_and_light);
        final String fragmentShader = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.per_pixel_fragment_shader_tex_and_light);

        final int vertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        final int fragmentShaderHandle = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

        mProgramHandle = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
                new String[]{"a_Position", "a_Normal", "a_TexCoordinate"});*/

        // Define a simple shader program for our point.
        final String pointVertexShader = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.point_vertex_shader);
        final String pointFragmentShader = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.point_fragment_shader);

        final int pointVertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, pointVertexShader);
        final int pointFragmentShaderHandle = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, pointFragmentShader);
        mPointProgramHandle = ShaderHelper.createAndLinkProgram(pointVertexShaderHandle, pointFragmentShaderHandle,
                new String[]{"a_Position"});


        mDrawPlane = new DrawPlane(mActivityContext);

        mDrawObj = new DrawObj(mActivityContext, "android.obj");

        // Initialize the accumulated rotation matrix
        Matrix.setIdentityM(mAccumulatedRotation, 0);

        //Shadow Testing

        final String VertexShaderDepthMap = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.vertex_shader_depth_map);
        final String FragmentShaderDepthMap = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.fragment_shader_depth_map);

        final int VertexShaderHandleDepthMap = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, VertexShaderDepthMap);
        final int FragmentShaderHandleDepthMap = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderDepthMap);
        mDepthMapProgram = ShaderHelper.createAndLinkProgram(VertexShaderHandleDepthMap, FragmentShaderHandleDepthMap,
                new String[]{"a_ShadowPosition"});

        //Shadow Testing

        final String VertexShader = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.vertex_shader_shadow);
        final String FragmentShader = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.fragment_shader_shadow);

        final int VertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, VertexShaderDepthMap);
        final int FragmentShaderHande = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderDepthMap);
        mProgramHandle = ShaderHelper.createAndLinkProgram(VertexShaderHandleDepthMap, FragmentShaderHandleDepthMap,
                new String[]{"a_ShadowPosition"});

    }

    private int mDepthMapProgram;

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        mDisplayWidth = width;
        mDisplayHeight = height;

        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);

        //GenerateFrameBuffer
        generateFrameBuffer();

        // Create a new perspective projection matrix. The height will stay the same
        // while the width will vary as per aspect ratio.
        final float ratio = (float) width / height;
        final float left = -ratio;
        final float right = ratio;
        final float bottom = -1.0f;
        final float top = 1.0f;
        final float near = 1.0f;
        final float far = 1000.0f;

        Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);

        Matrix.frustumM(mLightProjectionMatrix, 0, 1.1f*left, 1.1f*right, 1.1f*bottom, 1.1f*top,near,far);

    }

    //Shadows
    private int mMVPMatrixHandleShadow;
    private int mPositionHandleShadow;
    private int mNormalMatrixHandle;
    private int mShadowProjHandle;
    private int mMapStepXHandle;
    private int mMapStepYHandle;
    private int mShadowTextureUniformHandle;

    private float[] mActualLightPosition = new float[4];
    private float[] mCubeRotation = new float[16];
    private float[] mLightMvpMatrix_staticShapes = new float[16];
    private float[] mLightMvpMatrix_dynamicShapes = new float[16];
    private float[] mMVMatrix = new float[16];
    private float[] mNormalMatrix = new float[16];

    @Override
    public void onDrawFrame(GL10 glUnused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Do a complete rotation every 10 seconds.
        long time = SystemClock.uptimeMillis() % 10000L;
        long slowTime = SystemClock.uptimeMillis() % 100000L;
        float angleInDegrees = (360.0f / 10000L) * ((int) time);
        float slowAngleInDegrees = (360.0f / 100000.0f) * ((int) slowTime);

        //Depthmap Handles
        mMVPMatrixHandleShadow = GLES20.glGetUniformLocation(mDepthMapProgram,"u_MVPMatrix");
        mPositionHandleShadow = GLES20.glGetAttribLocation(mDepthMapProgram,"a_ShadowPosition");

        // Set program handles for object drawing.
        //Uniform
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVPMatrix");
        mMVMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVMatrix");
        mLightPosHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_LightPos");
        mNormalMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_NormalMatrix");
        mShadowProjHandle = GLES20.glGetUniformLocation(mProgramHandle,"u_ShadowProjMatrix");
        mShadowTextureUniformHandle = GLES20.glGetUniformLocation(mProgramHandle,"u_ShadowTexture");
        mMapStepXHandle = GLES20.glGetUniformLocation(mProgramHandle,"u_xPixelOffset");
        mMapStepYHandle = GLES20.glGetUniformLocation(mProgramHandle,"u_yPixelOffset");
        //mTextureUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_Texture");

        //Attribute
        mPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
        mNormalHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Normal");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_TexCoordinate");

        //Calculate Values for all renderers
        long elapsedMilliSec = SystemClock.elapsedRealtime();
        long rotationCounter = elapsedMilliSec % 12000L;
        float lightRotationDegree = (360.0f / 12000.0f) * ((int)rotationCounter);
        float[] rotationMatrix = new float[16];
        Matrix.setIdentityM(rotationMatrix, 0);
        Matrix.rotateM(rotationMatrix, 0, lightRotationDegree, 0.0f, 1.0f, 0.0f);
        Matrix.multiplyMV(mActualLightPosition, 0, rotationMatrix, 0, mLightPosInModelSpace, 0);
        Matrix.setIdentityM(mModelMatrix, 0);
        //Set view matrix from light source position
        Matrix.setLookAtM(mLightViewMatrix, 0,
         					//lightX, lightY, lightZ,
         					mActualLightPosition[0], mActualLightPosition[1], mActualLightPosition[2],
         					//lookX, lookY, lookZ,
         					//look in direction -y
         					mActualLightPosition[0], -mActualLightPosition[1], mActualLightPosition[2],
         					//upX, upY, upZ
         					//up vector in the direction of axisY
         					-mActualLightPosition[0], 0, -mActualLightPosition[2]);
        //Cube rotation with touch events
        float[] cubeRotationX = new float[16];
        float[] cubeRotationY = new float[16];
        Matrix.setRotateM(cubeRotationX, 0, mRotationX, 0, 1.0f, 0);
        Matrix.setRotateM(cubeRotationY, 0, mRotationY, 1.0f, 0, 0);
        Matrix.multiplyMM(mCubeRotation, 0, cubeRotationX, 0, cubeRotationY, 0);


                 // Cull front faces for shadow generation to avoid self shadowing
              	GLES20.glCullFace(GLES20.GL_FRONT);

              	renderShadowMap();



        //////

/**
        // Set our per-vertex lighting program.
        GLES20.glUseProgram(mProgramHandle);

        // Calculate position of the light. Rotate and then push into the distance.
        Matrix.setIdentityM(mLightModelMatrix, 0);
        //Matrix.translateM(mLightModelMatrix, 0, 0.0f, 5.0f, -2.0f);
        Matrix.rotateM(mLightModelMatrix, 0, angleInDegrees, 0.0f, 1.0f, 0.0f);
        //Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, 3.5f);

        Matrix.multiplyMV(mLightPosInWorldSpace, 0, mLightModelMatrix, 0, mLightPosInModelSpace, 0);
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mViewMatrix, 0, mLightPosInWorldSpace, 0);

        //Set view matrix from light source position
        Matrix.setLookAtM(mLightViewMatrix, 0,
                //lightX, lightY, lightZ,
                mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2],
                //lookX, lookY, lookZ,
                //look in direction -y
                mLightPosInEyeSpace[0], -mLightPosInEyeSpace[1], mLightPosInEyeSpace[2],
                //upX, upY, upZ
                //up vector in the direction of axisY
                -mLightPosInEyeSpace[0], 0, -mLightPosInEyeSpace[2]);

        // End Light

        //Begin objects


        // Draw a plane
        Matrix.setIdentityM(mModelMatrix, 0);
        renderScene();



        // Draw a point to indicate the light.
        GLES20.glUseProgram(mPointProgramHandle);
        drawLight();

        */
    }

    private void renderShadowMap() {
        // bind the generated framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);
        GLES20.glViewport(0, 0, mShadowMapWidth,
                mShadowMapHeight);
        // Clear color and buffers
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        // Start using the shader
        GLES20.glUseProgram(mDepthMapProgram);
        float[] tempResultMatrix = new float[16];
        // Calculate matrices for standing objects
        // View matrix * Model matrix value is stored
        Matrix.multiplyMM(mLightMvpMatrix_staticShapes, 0, mLightViewMatrix, 0, mModelMatrix, 0);
        // Model * view * projection matrix stored and copied for use at rendering from camera point of view
        Matrix.multiplyMM(tempResultMatrix, 0, mLightProjectionMatrix, 0, mLightMvpMatrix_staticShapes, 0);
        System.arraycopy(tempResultMatrix, 0, mLightMvpMatrix_staticShapes, 0, 16);
        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandleShadow, 1, false, mLightMvpMatrix_staticShapes, 0);
        // Render all stationary shapes on scene

        mDrawPlane.setDraw(mPositionHandleShadow, 0, 0, 0, true);
        mDrawPlane.draw();

        // Calculate matrices for moving objects
        // Rotate the model matrix with current rotation matrix
        Matrix.multiplyMM(tempResultMatrix, 0, mModelMatrix, 0, mCubeRotation, 0);
        // View matrix * Model matrix value is stored
        Matrix.multiplyMM(mLightMvpMatrix_dynamicShapes, 0, mLightViewMatrix, 0, tempResultMatrix, 0);
        // Model * view * projection matrix stored and copied for use at rendering from camera point of view
        Matrix.multiplyMM(tempResultMatrix, 0, mLightProjectionMatrix, 0, mLightMvpMatrix_dynamicShapes, 0);
        System.arraycopy(tempResultMatrix, 0, mLightMvpMatrix_dynamicShapes, 0, 16);
        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandleShadow, 1, false, mLightMvpMatrix_dynamicShapes, 0);
        // Render all moving shapes on scene
        mDrawObj.setDraw(mPositionHandleShadow,0,0,0,true);
    }

  private void renderScene() {
      // bind default framebuffer
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
      GLES20.glUseProgram(mProgramHandle);
      GLES20.glViewport(0, 0, mDisplayWidth, mDisplayHeight);
      //pass stepsize to map nearby points properly to depth map texture - used in PCF algorithm
      GLES20.glUniform1f(mMapStepXHandle, (float) (1.0 / mShadowMapWidth));
      GLES20.glUniform1f(mMapStepYHandle, (float) (1.0 / mShadowMapHeight));
      float[] tempResultMatrix = new float[16];
      float bias[] = new float[]{
              0.5f, 0.0f, 0.0f, 0.0f,
              0.0f, 0.5f, 0.0f, 0.0f,
              0.0f, 0.0f, 0.5f, 0.0f,
              0.5f, 0.5f, 0.5f, 1.0f};
      float[] depthBiasMVP = new float[16];
      //calculate MV matrix
      Matrix.multiplyMM(tempResultMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
      System.arraycopy(tempResultMatrix, 0, mMVMatrix, 0, 16);
      //pass in MV Matrix as uniform
      GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVMatrix, 0);
      //calculate Normal Matrix as uniform (invert transpose MV)
      Matrix.invertM(tempResultMatrix, 0, mMVMatrix, 0);
      Matrix.transposeM(mNormalMatrix, 0, tempResultMatrix, 0);
      //pass in Normal Matrix as uniform
      GLES20.glUniformMatrix4fv(mNormalMatrixHandle, 1, false, mNormalMatrix, 0);
      //calculate MVP matrix
      Matrix.multiplyMM(tempResultMatrix, 0, mProjectionMatrix, 0, mMVMatrix, 0);
      System.arraycopy(tempResultMatrix, 0, mMVPMatrix, 0, 16);
      //pass in MVP Matrix as uniform
      GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
      Matrix.multiplyMV(mLightPosInEyeSpace, 0, mViewMatrix, 0, mActualLightPosition, 0);
      //pass in light source position
      GLES20.glUniform3f(mLightPosHandle, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);
      if (mHasDepthTextureExtension) {
          Matrix.multiplyMM(depthBiasMVP, 0, bias, 0, mLightMvpMatrix_staticShapes, 0);
          System.arraycopy(depthBiasMVP, 0, mLightMvpMatrix_staticShapes, 0, 16);
      }
      //MVP matrix that was used during depth map render
      GLES20.glUniformMatrix4fv(mShadowProjHandle, 1, false, mLightMvpMatrix_staticShapes, 0);
      //pass in texture where depth map is stored
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorTextureId[0]);
      GLES20.glUniform1i(mShadowTextureUniformHandle, 0);
    if(mDrawPlane.getInitialised()) {
        mDrawPlane.setDraw(mPositionHandle, mNormalHandle, mTextureCoordinateHandle, mShadowTextureUniformHandle, false);
        mDrawPlane.draw();
    }
      // Pass uniforms for moving objects (center cube) which are different from previously used uniforms
      // - MV matrix
      // - MVP matrix
      // - Normal matrix
      // - Light MVP matrix for dynamic objects
      // Rotate the model matrix with current rotation matrix
      Matrix.multiplyMM(tempResultMatrix, 0, mModelMatrix, 0, mCubeRotation, 0);
      //calculate MV matrix
      Matrix.multiplyMM(tempResultMatrix, 0, mViewMatrix, 0, tempResultMatrix, 0);
      System.arraycopy(tempResultMatrix, 0, mMVMatrix, 0, 16);
      //pass in MV Matrix as uniform
      GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVMatrix, 0);
      //calculate Normal Matrix as uniform (invert transpose MV)
      Matrix.invertM(tempResultMatrix, 0, mMVMatrix, 0);
      Matrix.transposeM(mNormalMatrix, 0, tempResultMatrix, 0);
      //pass in Normal Matrix as uniform
      GLES20.glUniformMatrix4fv(mNormalMatrixHandle, 1, false, mNormalMatrix, 0);
      //calculate MVP matrix
      Matrix.multiplyMM(tempResultMatrix, 0, mProjectionMatrix, 0, mMVMatrix, 0);
      System.arraycopy(tempResultMatrix, 0, mMVPMatrix, 0, 16);
      //pass in MVP Matrix as uniform
      GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
      if (mHasDepthTextureExtension) {
          Matrix.multiplyMM(depthBiasMVP, 0, bias, 0, mLightMvpMatrix_dynamicShapes, 0);
          System.arraycopy(depthBiasMVP, 0, mLightMvpMatrix_dynamicShapes, 0, 16);
      }
      //MVP matrix that was used during depth map render
      GLES20.glUniformMatrix4fv(mShadowProjHandle, 1, false, mLightMvpMatrix_dynamicShapes, 0);
      if(mDrawObj.getInitialised()){
          mDrawObj.setDraw(mPositionHandle,mNormalHandle,mTextureCoordinateHandle,mShadowTextureUniformHandle,false);
          mDrawObj.draw();
      }


  }
    private void drawStatic() {
        //GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);

        // This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVPMatrix, 0);

        // This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mTemporaryMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
        System.arraycopy(mTemporaryMatrix, 0, mMVPMatrix, 0, 16);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        // Pass in the light position in eye space.
        GLES20.glUniform3f(mLightPosHandle, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);
    }

    private void drawDynamic() {
        //GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);

        // This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
        // (which currently contains model * view).
        //Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVPMatrix, 0);

        // This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mTemporaryMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
        System.arraycopy(mTemporaryMatrix, 0, mMVPMatrix, 0, 16);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        // Pass in the light position in eye space.
        GLES20.glUniform3f(mLightPosHandle, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);
    }


    /**
     * Draws a point representing the position of the light.
     */
    private void drawLight() {
        final int pointMVPMatrixHandle = GLES20.glGetUniformLocation(mPointProgramHandle, "u_MVPMatrix");
        final int pointPositionHandle = GLES20.glGetAttribLocation(mPointProgramHandle, "a_Position");

        // Pass in the position.
        GLES20.glVertexAttrib3f(pointPositionHandle, mLightPosInModelSpace[0], mLightPosInModelSpace[1], mLightPosInModelSpace[2]);

        // Since we are not using a buffer object, disable vertex arrays for this attribute.
        GLES20.glDisableVertexAttribArray(pointPositionHandle);

        // Pass in the transformation matrix.
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mLightModelMatrix, 0);
        Matrix.multiplyMM(mTemporaryMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
        System.arraycopy(mTemporaryMatrix, 0, mMVPMatrix, 0, 16);
        GLES20.glUniformMatrix4fv(pointMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        // Draw the point.
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);
    }

    /**
     * Returns the X rotation angle of the cube.
     *
     * @return - A float representing the rotation angle.
     */
    public float getRotationX() {
        return mRotationX;
    }

    /**
     * Sets the X rotation angle of the cube.
     */
    public void setRotationX(float rotationX) {
        mRotationX = rotationX;
    }

    /**
     * Returns the Y rotation angle of the cube.
     *
     * @return - A float representing the rotation angle.
     */
    public float getRotationY() {
        return mRotationY;
    }

    /**
     * Sets the Y rotation angle of the cube.
     */
    public void setRotationY(float rotationY) {
        mRotationY = rotationY;
    }

    private int[] fboId;
    private int[] depthTextureId;
    private int[] renderTextureId;
    private int[] colorTextureId;

    private int mDisplayWidth;
    private int mDisplayHeight;


    private int mShadowMapWidth;
    private int mShadowMapHeight;

    private boolean mHasDepthTextureExtension = false;
    private void generateFrameBuffer() {
        mShadowMapWidth = Math.round(mDisplayWidth);
        mShadowMapHeight = Math.round(mDisplayHeight);

        fboId = new int[1];
        depthTextureId = new int[1];
        renderTextureId = new int[1];
        colorTextureId = new int[1];

        // Create a frame buffer
        GLES20.glGenFramebuffers( 1, fboId, 0 );

        // Generate a texture to hold the colour buffer
        GLES20.glGenTextures(1, colorTextureId, 0 );
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorTextureId[0]);
        // Width and height do not have to be a power of two
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                mShadowMapWidth, mShadowMapHeight,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);

        // create render buffer and bind 16-bit depth buffer
        GLES20.glGenRenderbuffers(1, depthTextureId, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthTextureId[0]);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, mShadowMapWidth, mShadowMapHeight);

        // Try to use a texture depth component
        GLES20.glGenTextures(1, renderTextureId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTextureId[0]);

        // GL_LINEAR does not make sense for depth texture. However, next tutorial shows usage of GL_LINEAR and PCF. Using GL_NEAREST
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        // Remove artifact on the edges of the shadowmap
        GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE );
        GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE );


        //GLES20. glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);

        // Associate the textures with the FBO.

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, colorTextureId[0], 0);

// Use a depth texture
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_DEPTH_COMPONENT, mShadowMapWidth, mShadowMapHeight, 0, GLES20.GL_DEPTH_COMPONENT, GLES20.GL_UNSIGNED_INT, null);
        // Attach the depth texture to FBO depth attachment point
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_TEXTURE_2D, renderTextureId[0], 0);



        // check FBO status
        int FBOstatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if(FBOstatus != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "GL_FRAMEBUFFER_COMPLETE failed, CANNOT use FBO");
            throw new RuntimeException("GL_FRAMEBUFFER_COMPLETE failed, CANNOT use FBO");
        }

    }

    /**
     * private void renderShadowMap() {
     * // bind the generated framebuffer
     * GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);
     * <p>
     * GLES20.glViewport(0, 0, mShadowMapWidth,
     * mShadowMapHeight);
     * <p>
     * // Clear color and buffers
     * GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
     * GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
     * <p>
     * // Start using the shader
     * GLES20.glUseProgram(mDepthMapProgram);
     * <p>
     * float[] tempResultMatrix = new float[16];
     * <p>
     * // Calculate matrices for standing objects
     * <p>
     * // View matrix * Model matrix value is stored
     * Matrix.multiplyMM(mLightMvpMatrix_staticShapes, 0, mLightViewMatrix, 0, mModelMatrix, 0);
     * <p>
     * // Model * view * projection matrix stored and copied for use at rendering from camera point of view
     * Matrix.multiplyMM(tempResultMatrix, 0, mLightProjectionMatrix, 0, mLightMvpMatrix_staticShapes, 0);
     * System.arraycopy(tempResultMatrix, 0, mLightMvpMatrix_staticShapes, 0, 16);
     * <p>
     * // Pass in the combined matrix.
     * GLES20.glUniformMatrix4fv(shadow_mvpMatrixUniform, 1, false, mLightMvpMatrix_staticShapes, 0);
     * <p>
     * // Render all stationary shapes on scene
     * if(mDrawPlane.getInitialised()){
     * mDrawPlane.setDraw(mPositionHandle, 0, 0, 0, true);
     * draw();
     * mDrawPlane.draw();
     * }
     * <p>
     * <p>
     * // Calculate matrices for moving objects
     * <p>
     * // Rotate the model matrix with current rotation matrix
     * Matrix.multiplyMM(tempResultMatrix, 0, mModelMatrix, 0, mCubeRotation, 0);
     * <p>
     * // View matrix * Model matrix value is stored
     * Matrix.multiplyMM(mLightMvpMatrix_dynamicShapes, 0, mLightViewMatrix, 0, tempResultMatrix, 0);
     * <p>
     * // Model * view * projection matrix stored and copied for use at rendering from camera point of view
     * Matrix.multiplyMM(tempResultMatrix, 0, mLightProjectionMatrix, 0, mLightMvpMatrix_dynamicShapes, 0);
     * System.arraycopy(tempResultMatrix, 0, mLightMvpMatrix_dynamicShapes, 0, 16);
     * <p>
     * // Pass in the combined matrix.
     * GLES20.glUniformMatrix4fv(shadow_mvpMatrixUniform, 1, false, mLightMvpMatrix_dynamicShapes, 0);
     * <p>
     * // Render all moving shapes on scene
     * mCube.render(shadow_positionAttribute, 0, 0, true);
     * }
     */

    private void renderScene1() {

        if (mDrawPlane.getInitialised()) {
            mDrawPlane.setDraw(mPositionHandle, mNormalHandle, mTextureCoordinateHandle, mTextureUniformHandle, false);
            drawStatic();
            mDrawPlane.draw();
        }


        Matrix.setIdentityM(mModelMatrix, 0);
        //Cube rotation with touch events
         float[] cubeRotationX = new float[16];
         float[] cubeRotationY = new float[16];

         Matrix.setRotateM(cubeRotationX, 0, mRotationX, 0, 1.0f, 0);
         Matrix.setRotateM(cubeRotationY, 0, mRotationY, 1.0f, 0, 0);

         Matrix.multiplyMM(mCurrentRotation, 0, cubeRotationX, 0, cubeRotationY, 0);

        // Rotate the model matrix with current rotation matrix
        Matrix.multiplyMM(mTemporaryMatrix, 0, mModelMatrix, 0, mCurrentRotation, 0);

        //calculate MV matrix
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mTemporaryMatrix, 0);
        //System.arraycopy(mMVPMatrix, 0, mMVMatrix, 0, 16);

        // Draw a cube.
        // Translate the cube into the screen.
        //Matrix.setIdentityM(mModelMatrix, 0);
        //Matrix.translateM(mModelMatrix, 0, 0.0f, 0.8f, -3.5f);
/**
        // Set a matrix that contains the current rotation.
        Matrix.setIdentityM(mCurrentRotation, 0);
        Matrix.rotateM(mModelMatrix, 0, mRotationX, 0.0f, 1.0f, 0.0f);
        Matrix.rotateM(mModelMatrix, 0, mRotationY, 1.0f, 0.0f, 0.0f);

        // Multiply the current rotation by the accumulated rotation, and then set the accumulated rotation to the result.
        Matrix.multiplyMM(mTemporaryMatrix, 0, mCurrentRotation, 0, mAccumulatedRotation, 0);
        System.arraycopy(mTemporaryMatrix, 0, mAccumulatedRotation, 0, 16);

        // Rotate the cube taking the overall rotation into account.
        Matrix.multiplyMM(mTemporaryMatrix, 0, mModelMatrix, 0, mAccumulatedRotation, 0);
        System.arraycopy(mTemporaryMatrix, 0, mModelMatrix, 0, 16);*/

        if (mDrawObj.getInitialised()) {
            mDrawObj.setDraw(mPositionHandle, mNormalHandle, mTextureCoordinateHandle, mTextureUniformHandle, false);
            drawDynamic();
            mDrawObj.draw();
        }

    }


}
