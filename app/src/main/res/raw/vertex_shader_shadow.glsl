// Based on http://blog.shayanjaved.com/2011/03/13/shaders-android/
// from Shayan Javed

uniform mat4 u_MVPMatrix;
uniform mat4 u_MVMatrix;
uniform mat4 u_NormalMatrix;

// the shadow projection matrix
uniform mat4 u_ShadowProjMatrix;

// position and normal of the vertices
attribute vec4 a_Position;
attribute vec3 a_Normal;
attribute vec2 a_TextureCoordinate;

// to pass on
varying vec3 v_Position;
varying vec3 v_Normal;
varying vec4 v_ShadowCoord;
varying vec2 v_TextureCoordinate;


void main() {
	// the vertex position in camera space
	v_Position = vec3(u_MVMatrix * a_Position);

	// the vertex color
	v_TextureCoordinate = a_TextureCoordinate;

	// the vertex normal coordinate in camera space
	v_Normal = vec3(u_NormalMatrix * vec4(a_Normal, 0.0));

	v_ShadowCoord = u_ShadowProjMatrix * a_Position;

	gl_Position = uMVPMatrix * aPosition;
}
