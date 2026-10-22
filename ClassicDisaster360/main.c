// Classic Disaster Survival: original block-world prototype for Xbox 360 homebrew.
// Procedural geometry only; not Roblox source code or extracted game assets.
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "classic_disaster_shaders.h"
#include "island_geometry.h"
#include "xsdk/gpu.h"
#include "xsdk/kernel.h"
#include "xsdk/log.h"
#include "xsdk/present.h"
#include "xsdk/shader.h"
#include "xsdk/splash.h"
#include "xsdk/text.h"

#define PI 3.14159265358979323846f
#define MAX_VERTICES 18000
#define ROUND_FRAMES 1800u
#define PREP_FRAMES 240u
#define VERTEX_BYTES ((uint32_t)sizeof(Vertex))
#define GPU_BYTES ((uint32_t)(MAX_VERTICES * sizeof(Vertex)))
#define SKY 0xFF78B8EAu

typedef struct { float x,y,z,r,g,b; } Vertex;
typedef struct { float m[16]; } Matrix;
static Vertex cpu_vertices[MAX_VERTICES];
static uint32_t vcount;
static volatile uint32_t *gpu_vertices;
static XsdkShader vs, ps;
static XsdkDepthStencilState depth_state, hud_depth_state;
static bool text_ready;
static uint32_t width, height;

static const float axes[6][3][3] = {
 {{1,0,0},{0,0,-1},{0,1,0}}, {{-1,0,0},{0,0,1},{0,1,0}},
 {{0,1,0},{1,0,0},{0,0,-1}}, {{0,-1,0},{1,0,0},{0,0,1}},
 {{0,0,1},{1,0,0},{0,1,0}}, {{0,0,-1},{-1,0,0},{0,1,0}}
};
static const uint8_t order[6] = {0,1,2,2,1,3};
static const char *const names[] = {
 "TSUNAMI","TORNADO","METEOR SHOWER","EARTHQUAKE","FIRE","VOLCANIC ERUPTION"
};
enum { TSUNAMI, TORNADO, METEORS, EARTHQUAKE, FIRE, VOLCANO, DISASTER_COUNT };

static float clampf(float x,float a,float b){ return x<a?a:(x>b?b:x); }
static float stick(int16_t x){ return (x>6000 || x<-6000) ? (float)x/32768.0f : 0.0f; }
static void vertex(float x,float y,float z,float r,float g,float b){
 if(vcount<MAX_VERTICES) cpu_vertices[vcount++]=(Vertex){x,y,z,r,g,b};
}

static void box_oriented(float x,float y,float z,float sx,float sy,float sz,
                         float yaw,float roll,float r,float g,float b){
 const float size[3]={sx,sy,sz};
 const float shade[6]={0.80f,0.72f,1.0f,0.55f,0.83f,0.66f};
 float cy=cosf(yaw),syaw=sinf(yaw),cr=cosf(roll),sr=sinf(roll);
 for(uint32_t f=0;f<6;f++) for(uint32_t t=0;t<6;t++){
  uint32_t c=order[t];
  float across=(c&1u)?1.0f:-1.0f,up=(c&2u)?1.0f:-1.0f;
  float p[3];
  for(uint32_t a=0;a<3;a++)
   p[a]=size[a]*0.5f*(axes[f][0][a]+across*axes[f][1][a]+up*axes[f][2][a]);
  float rx=p[0]*cr-p[1]*sr;
  float ry=p[0]*sr+p[1]*cr;
  float wx=x+rx*cy+p[2]*syaw;
  float wz=z-rx*syaw+p[2]*cy;
  vertex(wx,y+ry,wz,r*shade[f],g*shade[f],b*shade[f]);
 }
}
static void box(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b){
 box_oriented(x,y,z,sx,sy,sz,0.0f,0.0f,r,g,b);
}
static void add_tree(float x,float z,float sway){
 box(x+sway,0.19f,z,0.105f,0.38f,0.105f,0.36f,0.21f,0.09f);
 box(x+sway,0.35f,z,0.48f,0.25f,0.48f,0.10f,0.43f,0.13f);
 box(x+sway,0.51f,z,0.36f,0.22f,0.36f,0.13f,0.50f,0.15f);
 box(x+sway,0.65f,z,0.23f,0.18f,0.23f,0.16f,0.56f,0.18f);
}
static void add_house(float x,float z,float sway,float wall_r,float wall_g,float wall_b){
 x+=sway;
 box(x,0.28f,z,0.86f,0.54f,0.76f,wall_r,wall_g,wall_b);
 box(x,0.57f,z,0.98f,0.12f,0.88f,0.62f,0.16f,0.12f);
 box(x,0.16f,z+0.391f,0.18f,0.30f,0.035f,0.31f,0.18f,0.10f);
 box(x-0.24f,0.34f,z+0.392f,0.18f,0.14f,0.028f,0.22f,0.70f,0.91f);
 box(x+0.24f,0.34f,z+0.392f,0.18f,0.14f,0.028f,0.22f,0.70f,0.91f);
}
static void island(float shake){
 (void)shake;
 /* Flat ocean sits just below the lowest rock tier of the imported island. */
 box(0.0f,-3.19f,0.0f,100.0f,0.08f,100.0f,0.035f,0.31f,0.59f);
 for(uint32_t i=0;i<ISLAND_PART_COUNT;i++){
  const IslandPart *p=&island_parts[i];
  float s=ISLAND_UNIT;
  if(p->material==1){
   box(p->x*s,p->y*s,p->z*s,p->sx*s,p->sy*s,p->sz*s,0.17f,0.53f,0.19f);
  }else{
   box(p->x*s,p->y*s,p->z*s,p->sx*s,p->sy*s,p->sz*s,0.40f,0.245f,0.15f);
  }
 }
 /* Simple classic park details on top of the exact island footprint. */
 box(0.0f,0.018f,0.0f,0.44f,0.036f,10.5f,0.78f,0.78f,0.72f);
 box(0.0f,0.019f,0.0f,9.6f,0.036f,0.42f,0.78f,0.78f,0.72f);
 add_house(-2.35f,1.6f,shake*0.6f,0.76f,0.68f,0.52f);
 add_house(2.45f,1.4f,-shake*0.6f,0.62f,0.64f,0.67f);
 add_tree(-5.0f,-3.5f,shake*0.4f);
 add_tree(-4.1f,1.0f,-shake*0.6f);
 add_tree(4.7f,-3.7f,shake*0.4f);
 add_tree(5.0f,3.8f,-shake*0.5f);
 add_tree(-0.6f,5.1f,shake*0.3f);
 /* A small stepped hill/volcano makes the silhouette readable from the camera. */
 box(4.8f,0.13f,4.65f,1.55f,0.26f,1.55f,0.34f,0.21f,0.13f);
 box(4.8f,0.32f,4.65f,1.13f,0.22f,1.13f,0.39f,0.24f,0.14f);
 box(4.8f,0.48f,4.65f,0.72f,0.14f,0.72f,0.42f,0.22f,0.12f);
 box(4.8f,0.57f,4.65f,0.35f,0.06f,0.35f,0.98f,0.24f,0.035f);
}
static void avatar_box(float px,float feet_y,float pz,float lx,float ly,float lz,
                       float sx,float sy,float sz,float yaw,float roll,
                       float r,float g,float b){
 float c=cosf(yaw),s=sinf(yaw);
 float wx=px+lx*c+lz*s;
 float wz=pz-lx*s+lz*c;
 box_oriented(wx,feet_y+ly,wz,sx,sy,sz,yaw,roll,r,g,b);
}
static void player(float x,float feet_y,float z,float facing,uint32_t frame,bool moving,bool airborne){
 float swing=moving?sinf((float)frame*0.22f)*0.48f:sinf((float)frame*0.065f)*0.035f;
 float left_arm=-swing,right_arm=swing,left_leg=swing*0.88f,right_leg=-swing*0.88f;
 if(airborne){left_arm=-0.72f;right_arm=0.72f;left_leg=0.24f;right_leg=-0.24f;}
 const float yellow_r=0.96f,yellow_g=0.80f,yellow_b=0.19f;
 const float green_r=0.10f,green_g=0.62f,green_b=0.15f;
 const float blue_r=0.035f,blue_g=0.28f,blue_b=0.92f;
 /* Classic R6 silhouette: two independently animated arms and legs, a 2x2 torso, and a 2x1 head. */
 avatar_box(x,feet_y,z,-0.04f,0.16f+(-0.08f*cosf(left_leg)),0.0f,
            0.08f,0.16f,0.08f,facing,left_leg,green_r,green_g,green_b);
 avatar_box(x,feet_y,z,0.04f,0.16f+(-0.08f*cosf(right_leg)),0.0f,
            0.08f,0.16f,0.08f,facing,right_leg,green_r,green_g,green_b);
 avatar_box(x,feet_y,z,0.0f,0.24f,0.0f,
            0.16f,0.16f,0.08f,facing,0.0f,blue_r,blue_g,blue_b);
 avatar_box(x,feet_y,z,-0.08f+0.08f*sinf(left_arm),0.32f-0.08f*cosf(left_arm),0.0f,
            0.08f,0.16f,0.08f,facing,left_arm,yellow_r,yellow_g,yellow_b);
 avatar_box(x,feet_y,z,0.08f+0.08f*sinf(right_arm),0.32f-0.08f*cosf(right_arm),0.0f,
            0.08f,0.16f,0.08f,facing,right_arm,yellow_r,yellow_g,yellow_b);
 avatar_box(x,feet_y,z,0.0f,0.36f,0.0f,
            0.16f,0.08f,0.08f,facing,0.0f,yellow_r,yellow_g,yellow_b);
 /* Minimal classic face, placed on the avatar's local forward (+Z) plane. */
 avatar_box(x,feet_y,z,-0.026f,0.369f,0.042f,0.011f,0.012f,0.004f,facing,0.0f,0.035f,0.025f,0.02f);
 avatar_box(x,feet_y,z, 0.026f,0.369f,0.042f,0.011f,0.012f,0.004f,facing,0.0f,0.035f,0.025f,0.02f);
 avatar_box(x,feet_y,z,0.0f,0.347f,0.042f,0.034f,0.005f,0.004f,facing,0.0f,0.035f,0.025f,0.02f);
}

static uint32_t hash32(uint32_t x){ x^=x>>16;x*=0x7feb352du;x^=x>>15;x*=0x846ca68bu;x^=x>>16;return x; }
static bool near(float x,float z,float px,float pz,float r){float dx=x-px,dz=z-pz;return dx*dx+dz*dz<r*r;}
static bool blocked(float x,float z){
 if(x < -10.0f || x > 8.9f || z < -9.4f || z > 9.4f) return true;
 float edge=(x*x)/(10.2f*10.2f)+(z*z)/(10.0f*10.0f);
 if(edge>1.66f) return true;
 const float objects[][3]={
  {-2.35f,1.6f,0.55f},{2.45f,1.4f,0.55f},{-5.0f,-3.5f,0.24f},
  {-4.1f,1.0f,0.24f},{4.7f,-3.7f,0.24f},{5.0f,3.8f,0.24f},
  {-0.6f,5.1f,0.24f},{4.8f,4.65f,0.68f}
 };
 for(uint32_t i=0;i<sizeof(objects)/sizeof(objects[0]);i++)
  if(near(x,z,objects[i][0],objects[i][1],objects[i][2])) return true;
 return false;
}
static void disaster_mesh(uint32_t d,uint32_t t,uint32_t round,float px,float pz,float shake){
 (void)px;(void)pz;(void)shake;
 if(d==TSUNAMI){
  float z=12.0f-(float)t*0.019f;
  box(0.0f,0.49f,z,28.0f,0.98f,0.52f,0.08f,0.61f,0.88f);
  box(0.0f,1.01f,z,28.0f,0.065f,0.60f,0.76f,0.94f,1.0f);
 }else if(d==TORNADO){
  float x=-8.0f+(float)(t%1100u)/1100.0f*16.0f;
  float z=0.3f+sinf((float)t*0.011f)*1.2f;
  for(uint32_t i=0;i<21;i++){
   float a=(float)i*0.78f+(float)t*0.105f;
   float rad=0.18f+(float)i*0.026f;
   float w=0.56f-(float)i*0.018f;if(w<0.17f)w=0.17f;
   box(x+cosf(a)*rad,0.12f+(float)i*0.082f,z+sinf(a)*rad,w,0.09f,w,0.34f,0.36f,0.39f);
  }
 }else if(d==METEORS){
  for(uint32_t i=0;i<18;i++){
   uint32_t h=hash32(i+round*53u+0x3245u);
   float x=(float)(h%190u)/10.0f-9.5f;
   float z=(float)((h>>8)%190u)/10.0f-9.5f;
   float y=5.3f-fmodf((float)t*0.075f+(float)i*1.37f,5.65f);
   float s=0.12f+(float)(i%3u)*0.025f;
   box(x,y,z,s,s*1.45f,s,0.98f,0.23f,0.025f);
   box(x,y+0.08f,z,s*0.48f,s*0.48f,s*0.48f,1.0f,0.72f,0.04f);
  }
 }else if(d==EARTHQUAKE){
  for(uint32_t i=0;i<9;i++){
   uint32_t h=hash32(i+round*79u+t/18u);
   float x=(float)(h%170u)/10.0f-8.5f;
   float z=(float)((h>>9)%170u)/10.0f-8.5f;
   box(x,0.06f+(float)((h>>19)%3u)*0.045f,z,0.16f,0.12f,0.16f,0.48f,0.35f,0.23f);
  }
 }else if(d==FIRE){
  const float p[4][2]={{-4.3f,1.2f},{4.0f,0.3f},{0.6f,4.3f},{1.0f,-4.5f}};
  for(uint32_t i=0;i<4;i++){
   float f=0.055f*sinf((float)t*0.21f+(float)i);
   box(p[i][0],0.20f+f,p[i][1],0.17f,0.34f,0.17f,0.94f,0.12f,0.02f);
   box(p[i][0]+0.04f,0.37f+f,p[i][1],0.10f,0.22f,0.10f,1.0f,0.53f,0.025f);
   box(p[i][0]-0.025f,0.47f+f,p[i][1],0.055f,0.13f,0.055f,1.0f,0.78f,0.10f);
  }
 }else{
  for(uint32_t i=0;i<8;i++){
   float q=fmodf((float)t*0.045f+(float)i*1.13f,6.6f);
   float x=4.8f-q*0.88f;
   float z=4.65f-q*0.76f;
   float y=1.25f+sinf(q*0.45f)*0.8f-q*0.035f;
   if(y<0.08f)y=0.08f;
   box(x,y,z,0.16f,0.16f,0.16f,0.98f,0.18f,0.02f);
   box(x,y+0.075f,z,0.075f,0.075f,0.075f,1.0f,0.72f,0.04f);
  }
 }
}
static bool hit_by(uint32_t d,uint32_t t,uint32_t round,float x,float z){
 if(d==TSUNAMI){float wz=12.0f-(float)t*0.019f;return fabsf(z-wz)<0.43f&&fabsf(x)<13.5f;}
 if(d==TORNADO){
  float cx=-8.0f+(float)(t%1100u)/1100.0f*16.0f;
  float cz=0.3f+sinf((float)t*0.011f)*1.2f;
  return near(x,z,cx,cz,0.78f);
 }
 if(d==METEORS){
  for(uint32_t i=0;i<18;i++){
   uint32_t h=hash32(i+round*53u+0x3245u);
   float mx=(float)(h%190u)/10.0f-9.5f;
   float mz=(float)((h>>8)%190u)/10.0f-9.5f;
   float my=5.3f-fmodf((float)t*0.075f+(float)i*1.37f,5.65f);
   if(my<0.52f&&near(x,z,mx,mz,0.27f))return true;
  }
  return false;
 }
 if(d==EARTHQUAKE&&(t%48u)<3u){
  uint32_t h=hash32(round+t/18u);
  float rx=(float)(h%170u)/10.0f-8.5f;
  float rz=(float)((h>>9)%170u)/10.0f-8.5f;
  return near(x,z,rx,rz,0.28f);
 }
 if(d==FIRE){
  const float p[4][2]={{-4.3f,1.2f},{4.0f,0.3f},{0.6f,4.3f},{1.0f,-4.5f}};
  for(uint32_t i=0;i<4;i++)if(near(x,z,p[i][0],p[i][1],0.46f))return true;
  return false;
 }
 if(d==VOLCANO){
  for(uint32_t i=0;i<8;i++){
   float q=fmodf((float)t*0.045f+(float)i*1.13f,6.6f);
   float mx=4.8f-q*0.88f,mz=4.65f-q*0.76f;
   float my=1.25f+sinf(q*0.45f)*0.8f-q*0.035f;
   if(my<0.65f&&near(x,z,mx,mz,0.30f))return true;
  }
 }
 return false;
}
static Matrix mul(const Matrix*a,const Matrix*b){Matrix o;for(int c=0;c<4;c++)for(int r=0;r<4;r++){float s=0;for(int k=0;k<4;k++)s+=a->m[k*4+r]*b->m[c*4+k];o.m[c*4+r]=s;}return o;}
static Matrix camera(float px,float py,float pz,float yaw,float pitch,float shake_x,float shake_y){
 const float distance=1.42f;
 float horizontal=distance*cosf(pitch);
 float ex=px-sinf(yaw)*horizontal+shake_x;
 float ey=py+0.20f+distance*sinf(pitch)+shake_y;
 float ez=pz-cosf(yaw)*horizontal;
 float tx=px,ty=py+0.19f,tz=pz;
 float fx=tx-ex,fy=ty-ey,fz=tz-ez,fl=sqrtf(fx*fx+fy*fy+fz*fz);
 if(fl<0.001f)fl=1.0f;
 fx/=fl;fy/=fl;fz/=fl;
 float sx=fz,sy=0.0f,sz=-fx,sl=sqrtf(sx*sx+sz*sz);
 if(sl<0.001f)sl=1.0f;
 sx/=sl;sz/=sl;
 float ux=fy*sz-fz*sy,uy=fz*sx-fx*sz,uz=fx*sy-fy*sx;
 Matrix v={{sx,ux,-fx,0,sy,uy,-fy,0,sz,uz,-fz,0,
  -(sx*ex+sy*ey+sz*ez),-(ux*ex+uy*ey+uz*ez),fx*ex+fy*ey+fz*ez,1}};
 float f=1.0f/tanf(0.567f),aspect=(float)width/(float)height,n=0.025f,far=90.0f,zf=far/(n-far);
 Matrix p={{f/aspect,0,0,0,0,f,0,0,0,0,zf,-1,0,0,n*zf,0}};
 return mul(&p,&v);
}
static void upload_vertices(void){uint32_t bytes=vcount*VERTEX_BYTES;const uint8_t*s=(const uint8_t*)cpu_vertices;for(uint32_t i=0;i<bytes/4;i++){uint32_t w;memcpy(&w,s+i*4,4);gpu_vertices[i]=w;}if(bytes)xsdk_gpu_invalidate(xsdk_gpu_address(gpu_vertices),bytes);}
static void frame_draw(uint32_t address,uint32_t frame,bool started,
                       float x,float y,float z,float camera_yaw,float camera_pitch,float facing,
                       bool moving,bool airborne,bool alive,uint32_t d,uint32_t active,
                       uint32_t round,uint32_t survived,uint32_t dead_timer){
 vcount=0;
 float shake=(started&&d==EARTHQUAKE&&active>0)?sinf((float)frame*0.21f)*0.035f:0.0f;
 island(shake);
 if(started&&alive)player(x,y,z,facing,frame,moving,airborne);
 if(started&&active>0)disaster_mesh(d,active,round,x,z,shake);
 upload_vertices();
 Matrix m=camera(x,y,z,camera_yaw,camera_pitch,shake*0.8f,
   shake*0.5f+(started&&d==EARTHQUAKE?cosf((float)frame*0.16f)*0.025f:0.0f));
 xsdk_gpu_set_target(width,height);
 xsdk_gpu_set_depth_clear(1.0f,0);
 xsdk_gpu_set_depth_stencil(&depth_state);
 xsdk_gpu_set_shaders(&vs,&ps);
 xsdk_built_shader_set_literals(&world_vs_hlsl);
 xsdk_built_shader_set_literals(&world_ps_hlsl);
 xsdk_gpu_set_vertex_constants(0,m.m,4);
 xsdk_gpu_set_vertices(0,xsdk_gpu_address(gpu_vertices),vcount*VERTEX_BYTES);
 if(vcount)xsdk_gpu_draw(XSDK_PRIMITIVE_TRIANGLE_LIST,vcount);

 if(text_ready){
  xsdk_gpu_set_depth_stencil(&hud_depth_state);
  xsdk_text_draw(24,18,1,0xFFFFFFFFu,"NATURAL DISASTER SURVIVAL");
  xsdk_text_draw(24,40,1,0xFFFFD56Au,"LS MOVE   RS CAMERA   A JUMP / PLAY   LT RUN   BACK EXIT");
  if(!started){
   xsdk_text_draw(418,222,2,0xFFFFFFFFu,"NATURAL DISASTER");
   xsdk_text_draw(466,268,1,0xFFFFD56Au,"PRESS A TO PLAY");
   xsdk_text_draw(388,298,1,0xFFFFFFFFu,"SURVIVE THE NEXT DISASTER");
  }else{
   char s[128];
   if(active==0)xsdk_text_draw(24,72,1,0xFFFFE08Au,"GET TO SAFETY - DISASTER INCOMING");
   else{
    (void)snprintf(s,sizeof s,"DISASTER: %s",names[d%DISASTER_COUNT]);
    xsdk_text_draw(24,72,1,0xFFFFD56Au,s);
   }
   (void)snprintf(s,sizeof s,"ROUND %u   SURVIVED: %u",(unsigned)(round+1u),(unsigned)survived);
   xsdk_text_draw(24,94,1,0xFFFFFFFFu,s);
   if(!alive&&dead_timer>0)xsdk_text_draw(460,330,1,0xFFFF6969u,"ELIMINATED - RESPAWNING");
   else if(active>0&&alive)xsdk_text_draw(24,116,1,0xFFB9F7B9u,"FIND COVER AND STAY ALIVE");
  }
 }
 xsdk_gpu_resolve(address,width,height,SKY);
}
int main(void){
 xsdk_log_write("natural_disaster: boot");
 NTSTATUS st=xsdk_splash_show(600);
 if(!NT_SUCCESS(st))xsdk_log_write("natural_disaster: splash 0x%08X",(unsigned)st);
 st=xsdk_gpu_open();
 if(!NT_SUCCESS(st)){xsdk_log_write("natural_disaster: GPU open 0x%08X",(unsigned)st);return 1;}
 gpu_vertices=xsdk_gpu_alloc(GPU_BYTES);
 if(!gpu_vertices){xsdk_gpu_close();return 1;}
 st=xsdk_shader_load(&vs,world_vs_hlsl.container,world_vs_hlsl.container_bytes);
 if(NT_SUCCESS(st))st=xsdk_shader_load(&ps,world_ps_hlsl.container,world_ps_hlsl.container_bytes);
 const XsdkVertexElement layout[]={
  {.usage=XSDK_USAGE_POSITION,.format=XSDK_VERTEX_FLOAT3,.offset_bytes=0},
  {.usage=XSDK_USAGE_COLOR,.format=XSDK_VERTEX_FLOAT3,.offset_bytes=12}
 };
 if(NT_SUCCESS(st))st=xsdk_shader_set_vertex_layout(&vs,layout,2,(const uint32_t[]){sizeof(Vertex)});
 if(!NT_SUCCESS(st)){
  xsdk_log_write("natural_disaster: shader setup 0x%08X",(unsigned)st);
  xsdk_gpu_free(gpu_vertices);xsdk_gpu_close();return 1;
 }
 XsdkDepthStencil dc={.depth_test=true,.depth_write=true,.depth_compare=XSDK_COMPARE_LESS_EQUAL};
 XsdkDepthStencil hc={.depth_test=false,.depth_write=false};
 xsdk_gpu_depth_stencil_state(&depth_state,&dc);
 xsdk_gpu_depth_stencil_state(&hud_depth_state,&hc);
 text_ready=NT_SUCCESS(xsdk_text_open());
 st=xsdk_present_open(XSDK_DISPLAY_720P,2,1,&width,&height);
 if(!NT_SUCCESS(st)){
  xsdk_log_write("natural_disaster: display open 0x%08X",(unsigned)st);
  if(text_ready) xsdk_text_close();
  xsdk_shader_free(&ps);
  xsdk_shader_free(&vs);
  xsdk_gpu_free(gpu_vertices);xsdk_gpu_close();return 1;
 }
 float px=0.0f,py=0.0f,pz=0.0f,camera_yaw=0.0f,camera_pitch=0.32f,facing=0.0f,jv=0.0f;
 bool started=false,alive=true,died=false,moving=false,airborne=false;
 uint32_t dead_timer=0,game_frame=0,survived=0;
 uint16_t previous_buttons=0;
 int32_t last_round=-1;
 for(uint32_t render=0;;render++){
  INPUT_STATE in={0};
  if(XamInputGetState(0,0,&in)!=ERROR_SUCCESS)in=(INPUT_STATE){0};
  const INPUT_GAMEPAD*p=&in.gamepad;
  bool a_pressed=((p->buttons&INPUT_GAMEPAD_A)!=0)&&((previous_buttons&INPUT_GAMEPAD_A)==0);
  previous_buttons=p->buttons;
  if(p->buttons&INPUT_GAMEPAD_BACK)break;
  moving=false;
  if(!started){
   if(a_pressed){
    started=true;game_frame=0;last_round=-1;survived=0;died=false;alive=true;
    dead_timer=0;px=0.0f;py=0.0f;pz=0.0f;camera_yaw=0.0f;camera_pitch=0.32f;facing=0.0f;jv=0.0f;
   }
  }else{
   uint32_t round=game_frame/ROUND_FRAMES,rf=game_frame%ROUND_FRAMES;
   if((int32_t)round!=last_round){
    if(last_round>=0&&!died)survived++;
    last_round=(int32_t)round;died=false;
   }
   camera_yaw+=stick(p->thumb_rx)*0.040f;
   if(camera_yaw>PI)camera_yaw-=2.0f*PI;
   if(camera_yaw<-PI)camera_yaw+=2.0f*PI;
   camera_pitch=clampf(camera_pitch-stick(p->thumb_ry)*0.022f,-0.08f,0.78f);
   if(alive){
    float fx=stick(p->thumb_ly),sx=stick(p->thumb_lx);
    float dx=sinf(camera_yaw)*fx+cosf(camera_yaw)*sx;
    float dz=cosf(camera_yaw)*fx-sinf(camera_yaw)*sx;
    float mag=sqrtf(dx*dx+dz*dz);
    if(mag>1.0f){dx/=mag;dz/=mag;mag=1.0f;}
    moving=mag>0.16f;
    if(moving)facing=atan2f(dx,dz);
    float speed=(p->left_trigger>120)?0.055f:0.034f;
    float nx=px+dx*speed,nz=pz+dz*speed;
    if(!blocked(nx,pz))px=nx;
    if(!blocked(px,nz))pz=nz;
    airborne=py>0.001f;
    if(a_pressed&&py<=0.001f)jv=0.050f;
    py+=jv;jv-=0.0025f;
    if(py<0.0f){py=0.0f;jv=0.0f;}
    airborne=py>0.001f;
   }else{
    airborne=false;
    if(dead_timer>0){
     dead_timer--;
     if(dead_timer==0){alive=true;px=0.0f;py=0.0f;pz=0.0f;jv=0.0f;facing=camera_yaw;}
    }
   }
   if(rf>=PREP_FRAMES&&alive){
    uint32_t d=round%DISASTER_COUNT,active=rf-PREP_FRAMES;
    if(hit_by(d,active,round,px,pz)){alive=false;dead_timer=120;died=true;moving=false;airborne=false;}
   }
   game_frame++;
  }
  uint32_t round=started?game_frame/ROUND_FRAMES:0;
  uint32_t rf=started?game_frame%ROUND_FRAMES:0;
  uint32_t d=round%DISASTER_COUNT;
  uint32_t active=(started&&rf>PREP_FRAMES)?rf-PREP_FRAMES:0;
  uint32_t address;
  st=xsdk_present_begin(&address,5000);
  if(!NT_SUCCESS(st)){xsdk_log_write("natural_disaster: present 0x%08X",(unsigned)st);break;}
  frame_draw(address,render,started,px,py,pz,camera_yaw,camera_pitch,facing,moving,airborne,alive,d,active,round,survived,dead_timer);
  xsdk_present_end();
 }
 xsdk_present_close();
 if(text_ready)xsdk_text_close();
 xsdk_shader_free(&ps);xsdk_shader_free(&vs);xsdk_gpu_free(gpu_vertices);xsdk_gpu_close();
 xsdk_log_write("natural_disaster: exit");
 return 0;
}
