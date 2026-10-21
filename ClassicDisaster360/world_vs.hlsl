float4x4 MVP : register(c0);
struct VS_OUT { float4 pos : POSITION; float3 color : COLOR0; };
VS_OUT vs_main(float3 position : POSITION, float3 color : COLOR0) {
    VS_OUT o;
    o.pos = mul(MVP, float4(position, 1.0));
    o.color = color;
    return o;
}
