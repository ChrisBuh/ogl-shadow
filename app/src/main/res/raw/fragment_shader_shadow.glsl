// Based on http://blog.shayanjaved.com/2011/03/13/shaders-android/
// from Shayan Javed
// And dEngine source from Fabien Sanglard

precision mediump float;

// The position of the light in eye space.
uniform vec3 u_LightPos;
  
// Texture variables: depth texture
uniform sampler2D u_ShadowTexture;
//uniform sampler2D u_Texture;

// This define the value to move one pixel left or right
uniform float u_xPixelOffset;
// This define the value to move one pixel up or down
uniform float u_yPixelOffset;
  
// from vertex shader - values get interpolated
varying vec3 v_Position;
varying vec2 v_TextureCoordinate;
varying vec3 v_Normal;
  
// shadow coordinates
varying vec4 v_ShadowCoord;
  
//Calculate variable bias - from http://www.opengl-tutorial.org/intermediate-tutorials/tutorial-16-shadow-mapping
float calcBias()
{
	float bias;

	vec3 n = normalize( v_Normal );
	// Direction of the light (from the fragment to the light)
	vec3 l = normalize( u_LightPos );
	
	// Cosine of the angle between the normal and the light direction, 
	// clamped above 0
	//  - light is at the vertical of the triangle -> 1
	//  - light is perpendiular to the triangle -> 0
	//  - light is behind the triangle -> 0
	float cosTheta = clamp( dot( n,l ), 0.0, 1.0 );
 		
 	bias = 0.0001*tan(acos(cosTheta));
	bias = clamp(bias, 0.0, 0.01);
 	
 	return bias;
}

float lookup( vec2 offSet) 
{ 
	vec4 shadowMapPosition = v_ShadowCoord / v_ShadowCoord.w;

	float distanceFromLight = texture2D(u_ShadowTexture, (shadowMapPosition +
	                               vec4(offSet.x * u_xPixelOffset, offSet.y * u_yPixelOffset, 0.05, 0.0)).st ).z;
			
	//add bias to reduce shadow acne (error margin)
	float bias = calcBias();

	return float(distanceFromLight > shadowMapPosition.z - bias);
}

float shadowPCF()
{
	float shadow = 1.0;

	for (float y = -1.5; y <= 1.5; y = y + 1.0) {
		for (float x = -1.5; x <= 1.5; x = x + 1.0) {
			shadow += lookup(vec2(x,y));
		}
	}
		
	shadow /= 16.0;
	shadow += 0.2;
	
	return shadow;
}
  
void main()                    		
{        
	vec3 lightVec = u_LightPos - v_Position;
	lightVec = normalize(lightVec);
   	
   	// Phong shading with diffuse and ambient component
	float diffuseComponent = max(0.0,dot(lightVec, v_Normal) );
	float ambientComponent = 0.3;
 		
 	// Shadow
   	float shadow = 1.0;
	
	//if the fragment is not behind light view frustum
	if (v_ShadowCoord.w > 0.0) {
			
		shadow = shadowPCF();
		//scale 0.0-1.0 to 0.2-1.0
		//otherways everything in shadow would be black
		shadow = (shadow * 0.8) + 0.2;
	}

	// Final output color with shadow and lighting
    gl_FragColor = vec4(0.5f,0.5f,0.5f,1.0f) * (diffuseComponent + ambientComponent) * shadow;
}  