package bertrandt.shadows.openGL.importer;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import bertrandt.shadows.R;
import bertrandt.shadows.openGL.common.TextureHelper;

/**
 * Created by buhrmanc on 05.02.2018.
 */

public class ImportObj {

    private static final String TAG = "ImportObj";
    private Context mContext;

    private String mFileName;

    private List<String> mFacesList;
    private List<String> mVerticesList;
    private List<String> mNormalsList;
    private List<String> mTexelsList;

    private FloatBuffer mVerticesBuffer;
    private FloatBuffer mNormalsBuffer;
    private FloatBuffer mTexelsBuffer;

    private int mBytesPerFloat = 4;
    private int mFacesCount = 3;
    private int mVertexSize = 3;
    private int mNormalSize = 3;
    private int mTexelSize = 2;

    private int mObjectTextureHandle;

    public ImportObj(Context context, String fileName) {
        this.mContext = context;
        this.mFileName = fileName;
        readRaw();
        allocateBuffer();
        populateBuffer();
    }

    private void readRaw() {

        mFacesList = new ArrayList<>();
        mVerticesList = new ArrayList<>();
        mNormalsList = new ArrayList<>();
        mTexelsList = new ArrayList<>();

        try {
            Scanner scanner = new Scanner(mContext.getAssets().open(mFileName));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("f ")) {
                    mFacesList.add(line.substring(2));
                } else if (line.startsWith("v ")) {
                    mVerticesList.add(line.substring(2));
                } else if (line.startsWith("vn ")) {
                    mNormalsList.add(line.substring(3));
                } else if (line.startsWith("vt ")) {
                    mTexelsList.add(line.substring(3));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "readRaw: object file could not be read");
        }
    }

    private void allocateBuffer() {
        mVerticesBuffer = ByteBuffer.allocateDirect(mFacesList.size() * mFacesCount * mVertexSize * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mNormalsBuffer = ByteBuffer.allocateDirect(mFacesList.size() * mFacesCount * mNormalSize * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexelsBuffer = ByteBuffer.allocateDirect(mFacesList.size() * mFacesCount * mTexelSize * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private void populateBuffer() {
        for (String faces : mFacesList) {
            String points[] = faces.split(" ");
            for (String singlePoint : points) {
                String element[] = singlePoint.split("/");
                String vertice[] = mVerticesList.get(Integer.valueOf(element[0]) - 1).split(" ");
                String normal[] = mNormalsList.get(Integer.valueOf(element[2]) - 1).split(" ");
                String texel[] = mTexelsList.get(Integer.valueOf(element[1]) - 1).split(" ");
                for (String value : vertice) {
                    mVerticesBuffer.put(Float.parseFloat(value));
                }
                for (String value : normal) {
                    mNormalsBuffer.put(Float.parseFloat(value));
                }
                for (String value : texel) {
                    mTexelsBuffer.put(Float.parseFloat(value));
                }
            }
        }
        mObjectTextureHandle = TextureHelper.loadTexture(mContext, R.drawable.android);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
    }

    public FloatBuffer getVerticesBuffer() {
        return mVerticesBuffer;
    }

    public FloatBuffer getNormalsBuffer() {
        return mNormalsBuffer;
    }

    public FloatBuffer getTexelsBuffer() {
        return mTexelsBuffer;
    }

    public int getObjectTextureHandle() {
        return mObjectTextureHandle;
    }

    public int getPositionSize(){
        return mFacesList.size()*3;
    }
}
