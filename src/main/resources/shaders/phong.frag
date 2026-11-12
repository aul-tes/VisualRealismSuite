#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 FragPosLightSpace;

uniform vec3 lightPos;
uniform vec3 viewPos;
uniform vec3 lightColor;
uniform vec3 objectColor;
uniform sampler2D textureSampler;   // texture unit 0
uniform sampler2D shadowMap;        // texture unit 1
uniform bool useTexture;
uniform bool useBlinnPhong;
uniform bool useShadow;
uniform float biasSlope;            // koefisien bias berbasis kemiringan (default 0.01 x pengali)
uniform float biasMin;              // bias minimum (default 0.001 x pengali)

// Shadow Factor: 0.0 = terang, 1.0 = sepenuhnya bayangan (PCF 3x3)
float calcShadow(vec4 fragPosLS, vec3 N, vec3 L) {
    vec3 p = fragPosLS.xyz / fragPosLS.w;   // perspective divide
    p = p * 0.5 + 0.5;                      // NDC [-1,1] -> [0,1]
    if (p.z > 1.0) return 0.0;              // di luar far plane cahaya

    float bias = max(biasSlope * (1.0 - dot(N, L)), biasMin);
    float current = p.z;                    // d_current
    float shadow = 0.0;
    vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            float closest = texture(shadowMap, p.xy + vec2(x, y) * texel).r; // d_shadow
            shadow += (current - bias > closest) ? 1.0 : 0.0;
        }
    }
    return shadow / 9.0;
}

void main() {
    // 1. Ambient Lighting
    float ambientStrength = 0.2;
    vec3 ambient = ambientStrength * lightColor;

    // 2. Diffuse Lighting (Lambertian)
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Specular Lighting (Phong vs Blinn-Phong)
    float specularStrength = 0.8;
    vec3 viewDir = normalize(viewPos - FragPos);
    float spec = 0.0;
    if (useBlinnPhong) {
        vec3 halfwayDir = normalize(lightDir + viewDir);
        spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);   // Blinn-Phong
    } else {
        vec3 reflectDir = reflect(-lightDir, norm);
        spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0); // Standard Phong
    }
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Shadow (Shadow Mapping - Pass 2)
    float shadow = useShadow ? calcShadow(FragPosLightSpace, norm, lightDir) : 0.0;

    // Combine Texture & Lighting (ambient tetap ada walau berada dalam bayangan)
    vec3 baseColor = useTexture ? texture(textureSampler, TexCoords).rgb : objectColor;
    vec3 result = (ambient + (1.0 - shadow) * (diffuse + specular)) * baseColor;
    FragColor = vec4(result, 1.0);
}
