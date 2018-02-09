// Vertex shader to generate the Depth Map
// Used for shadow mapping - generates depth map from the light's viewpoint
precision highp float;

// model-view projection matrix
uniform mat4 u_MVPMatrix;

// position of the vertices
attribute vec4 a_ShadowPosition;

void main() {
	gl_Position = u_MVPMatrix * a_ShadowPosition;
}
