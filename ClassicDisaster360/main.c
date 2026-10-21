// Classic Disaster Survival: original block-world prototype for Xbox 360 homebrew.
// Procedural geometry only; not Roblox source code or extracted game assets.
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "classic_disaster_shaders.h"
#include "xsdk/gpu.h"
#include "xsdk/kernel.h"
#include "xsdk/log.h"
#include "xsdk/present.h"
#include "xsdk/shader.h"
#include "xsdk/splash.h"
#include "xsdk/text.h"

#define PI 3.14159265358979323846f
#define MAX_VERTICES 7000
#define ROUND_FRAMES 1440u
#define PREP_FRAMES 180u
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
 "TSUNAMI","TORNADO","METEOR SHOWER","EARTHQUAKE","FIRE","VOLCANO"
};
enum { TSUNAMI, TORNADO, METEORS, EARTHQUAKE, FIRE, VOLCANO, DISASTER_COUNT };

static float clampf(float x,float a,float b){ return x<a?a:(x>b?b:x); }
static float stick(int16_t x){ return (x>6000 || x<-6000) ? (float)x/32768.0f : 0.0f; }
static void vertex(float x,float y,float z,float r,float g,float b){
 if(vcount<MAX_VERTICES) cpu_vertices[vcount++]=(Vertex){x,y,z,r,g,b};
}
static void box(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b){
 const float size[3]={sx,sy,sz}, center[3]={x,y,z};
 const float shade[6]={0.76f,0.70f,1.0f,0.52f,0.83f,0.65f};
 for(uint32_t f=0;f<6;f++) for(uint32_t t=0;t<6;t++){
  uint32_t c=order[t]; float across=(c&1u)?1.0f:-1.0f, up=(c&2u)?1.0f:-1.0f;
  float p[3];
  for(uint32_t a=0;a<3;a++) p[a]=center[a]+size[a]*0.5f*(axes[f][0][a]+across*axes[f][1][a]+up*axes[f][2][a]);
  vertex(p[0],p[1],p[2],r*shade[f],g*shade[f],b*shade[f]);
 }
}
static void house(float x,float z,float r,float g,float b,float rr,float rg,float rb,float shift){
 x+=shift; box(x,0.68f,z,1.55f,1.25f,1.50f,r,g,b); box(x,1.35f,z,1.8f,0.15f,1.72f,rr,rg,rb);
 box(x,0.37f,z+0.76f,0.35f,0.70f,0.05f,0.28f,0.16f,0.08f);
 box(x-0.46f,0.88f,z+0.77f,0.25f,0.23f,0.03f,0.2f,0.7f,0.9f);
 box(x+0.46f,0.88f,z+0.77f,0.25f,0.23f,0.03f,0.2f,0.7f,0.9f);
}
static void tree(float x,float z,float sway){
 box(x+sway,0.62f,z,0.25f,1.0f,0.25f,0.36f,0.2f,0.08f);
 box(x+sway,1.35f,z,0.95f,0.70f,0.95f,0.1f,0.43f,0.12f);
 box(x+sway,1.88f,z,0.72f,0.58f,0.72f,0.13f,0.5f,0.15f);
}
static void island(float shake){
 box(0,-1.50f,0,40,0.40f,40,0.04f,0.30f,0.64f);
 box(0,-0.53f,0,15.1f,1.08f,15.1f,0.43f,0.25f,0.13f);
 box(0,0.025f,0,13.9f,0.10f,13.9f,0.20f,0.62f,0.18f);
 box(0.1f,0.10f,-0.7f,2.4f,0.055f,0.62f,0.68f,0.68f,0.63f);
 for(int i=0;i<8;i++) box(-0.95f+i*0.27f,0.14f,-0.7f,0.08f,0.05f,0.08f,0.78f,0.78f,0.73f);
 house(-3.0f,2.0f,0.79f,0.65f,0.42f,0.70f,0.12f,0.10f,shake);
 house(2.4f,2.3f,0.62f,0.66f,0.69f,0.24f,0.25f,0.30f,-shake);
 for(int i=0;i<4;i++) box(3.7f+shake,0.25f+i*0.49f,-1.2f,1.0f-i*0.12f,0.40f,0.95f-i*0.10f,0.67f,0.59f,0.37f);
 for(int i=0;i<5;i++) box(5.1f,0.4f+i*0.36f,3.7f,0.72f,0.34f,0.72f,(i&1)?0.83f:0.92f,0.16f,0.13f);
 tree(-5.0f,-3.6f,shake*1.2f); tree(-4.2f,0.1f,-shake); tree(4.2f,-4.4f,shake); tree(-0.8f,5.0f,-shake);
 for(int i=0;i<4;i++) box(-4.8f,0.25f+i*0.25f,4.4f,2.1f-i*0.32f,0.30f,2.1f-i*0.32f,0.36f,0.22f,0.13f);
 box(-4.8f,1.05f,4.4f,0.6f,0.08f,0.6f,0.98f,0.20f,0.02f);
}
static void player(float x,float y,float z){
 box(x,y+0.30f,z-0.02f,0.30f,0.60f,0.32f,0.12f,0.38f,0.16f);
 box(x,y+0.30f,z+0.02f,0.30f,0.60f,0.32f,0.12f,0.38f,0.16f);
 box(x,y+0.91f,z,0.68f,0.62f,0.38f,0.08f,0.26f,0.76f);
 box(x-0.48f,y+0.90f,z,0.24f,0.62f,0.31f,0.96f,0.72f,0.20f);
 box(x+0.48f,y+0.90f,z,0.24f,0.62f,0.31f,0.96f,0.72f,0.20f);
 box(x,y+1.55f,z,0.50f,0.50f,0.50f,0.98f,0.77f,0.25f);
 box(x-0.105f,y+1.61f,z+0.255f,0.05f,0.07f,0.02f,0.04f,0.03f,0.02f);
 box(x+0.105f,y+1.61f,z+0.255f,0.05f,0.07f,0.02f,0.04f,0.03f,0.02f);
}
static uint32_t hash32(uint32_t x){ x^=x>>16;x*=0x7feb352du;x^=x>>15;x*=0x846ca68bu;x^=x>>16;return x; }
static bool near(float x,float z,float px,float pz,float r){float dx=x-px,dz=z-pz;return dx*dx+dz*dz<r*r;}
static bool blocked(float x,float z){
 if(x<-6.15f||x>6.15f||z<-6.15f||z>6.15f)return true;
 const float objects[][3]={{-3,2,1.0f},{2.4f,2.3f,1.0f},{3.7f,-1.2f,0.65f},{5.1f,3.7f,0.48f},{-5,-3.6f,0.42f},{-4.2f,0.1f,0.42f},{4.2f,-4.4f,0.42f},{-0.8f,5,0.42f},{-4.8f,4.4f,0.8f}};
 for(uint32_t i=0;i<sizeof(objects)/sizeof(objects[0]);i++) if(near(x,z,objects[i][0],objects[i][1],objects[i][2]))return true;
 return false;
}
static void disaster_mesh(uint32_t d,uint32_t t,uint32_t round,float px,float pz,float shake){
 if(d==TSUNAMI){
  float z=9.0f-(float)t*0.018f; box(0,1.75f,z,16.5f,3.3f,0.7f,0.10f,0.69f,0.93f); box(0,3.48f,z,16.5f,0.16f,0.86f,0.78f,0.95f,1.0f);
 }else if(d==TORNADO){
  float x=-5.2f+(float)(t%1000u)/1000.0f*10.4f, z=1.0f+sinf(t*0.011f)*1.1f;
  for(int i=0;i<17;i++){float a=i*0.78f+t*0.10f,rad=0.25f+i*0.023f,w=0.78f-i*0.022f;if(w<0.32f)w=0.32f;box(x+cosf(a)*rad,0.3f+i*0.22f,z+sinf(a)*rad,w,0.24f,w,0.35f,0.38f,0.42f);}
 }else if(d==METEORS){
  for(uint32_t i=0;i<10;i++){uint32_t h=hash32(i+round*53u+0x3245u);float x=(h%125u)/10.0f-6.2f,z=((h>>8)%125u)/10.0f-6.2f,y=6.9f-fmodf(t*0.065f+i*1.81f,7.2f);float s=0.18f+(i%3u)*0.055f;box(x,y,z,s,s*1.4f,s,0.98f,0.23f,0.025f);box(x,y+0.1f,z,s*0.48f,s*0.48f,s*0.48f,1,0.7f,0.05f);}
 }else if(d==EARTHQUAKE){
  for(uint32_t i=0;i<7;i++){uint32_t h=hash32(i+round*79u+t/18u);float x=(h%105u)/10.0f-5.2f,z=((h>>9)%105u)/10.0f-5.2f;box(x,0.18f+((h>>19)%4u)*0.1f,z,0.25f,0.2f,0.25f,0.48f,0.36f,0.24f);}
 }else if(d==FIRE){
  const float p[4][2]={{-4.2f,1.2f},{4,0.2f},{0.5f,4.5f},{1,-4.7f}};
  for(uint32_t i=0;i<4;i++){float f=sinf(t*0.2f+i)*0.1f;box(p[i][0],0.35f+f,p[i][1],0.36f,0.62f,0.36f,0.94f,0.12f,0.02f);box(p[i][0],0.67f+f,p[i][1],0.20f,0.35f,0.20f,1,0.53f,0.02f);}
 }else{
  for(uint32_t i=0;i<8;i++){float q=fmodf(t*0.06f+i*1.1f,8.0f),x=-4.8f+q*0.78f,z=4.4f-q*0.76f,y=1.4f+sinf(q*0.4f)*1.55f-q*0.07f;if(y<0.2f)y=0.2f;box(x,y,z,0.25f,0.25f,0.25f,0.98f,0.18f,0.02f);box(x,y+0.12f,z,0.12f,0.12f,0.12f,1,0.72f,0.04f);}
 }
 (void)px;(void)pz;(void)shake;
}
static bool hit_by(uint32_t d,uint32_t t,uint32_t round,float x,float z){
 if(d==TSUNAMI){float wz=9.0f-t*0.018f;return fabsf(z-wz)<0.68f&&fabsf(x)<7.7f;}
 if(d==TORNADO){float cx=-5.2f+(float)(t%1000u)/1000.0f*10.4f,cz=1.0f+sinf(t*0.011f)*1.1f;return near(x,z,cx,cz,1.0f);}
 if(d==METEORS){for(uint32_t i=0;i<10;i++){uint32_t h=hash32(i+round*53u+0x3245u);float mx=(h%125u)/10.0f-6.2f,mz=((h>>8)%125u)/10.0f-6.2f,my=6.9f-fmodf(t*0.065f+i*1.81f,7.2f);if(my<1.8f&&near(x,z,mx,mz,0.45f))return true;}return false;}
 if(d==FIRE){const float p[4][2]={{-4.2f,1.2f},{4,0.2f},{0.5f,4.5f},{1,-4.7f}};for(uint32_t i=0;i<4;i++)if(near(x,z,p[i][0],p[i][1],0.7f))return true;return false;}
 if(d==VOLCANO){for(uint32_t i=0;i<8;i++){float q=fmodf(t*0.06f+i*1.1f,8.0f),mx=-4.8f+q*0.78f,mz=4.4f-q*0.76f,my=1.4f+sinf(q*0.4f)*1.55f-q*0.07f;if(my<1.8f&&near(x,z,mx,mz,0.44f))return true;}return false;}
 if(d==EARTHQUAKE&&(t%48u)<3u){uint32_t h=hash32(round+t/18u);float rx=(h%105u)/10.0f-5.2f,rz=((h>>9)%105u)/10.0f-5.2f;return near(x,z,rx,rz,0.45f);}
 return false;
}
static Matrix mul(const Matrix*a,const Matrix*b){Matrix o;for(int c=0;c<4;c++)for(int r=0;r<4;r++){float s=0;for(int k=0;k<4;k++)s+=a->m[k*4+r]*b->m[c*4+k];o.m[c*4+r]=s;}return o;}
static Matrix camera(float px,float py,float pz,float yaw,float shake_x,float shake_y){
 float ex=px-sinf(yaw)*6.2f+shake_x,ey=py+4.1f+shake_y,ez=pz-cosf(yaw)*6.2f;
 float tx=px,ty=py+0.95f,tz=pz,fx=tx-ex,fy=ty-ey,fz=tz-ez,fl=sqrtf(fx*fx+fy*fy+fz*fz);if(fl<0.001f)fl=1;
 fx/=fl;fy/=fl;fz/=fl;float sx=fz,sy=0,sz=-fx,sl=sqrtf(sx*sx+sz*sz);if(sl<0.001f)sl=1;sx/=sl;sz/=sl;
 float ux=fy*sz-fz*sy,uy=fz*sx-fx*sz,uz=fx*sy-fy*sx;
 Matrix v={{sx,ux,-fx,0,sy,uy,-fy,0,sz,uz,-fz,0,-(sx*ex+sy*ey+sz*ez),-(ux*ex+uy*ey+uz*ez),fx*ex+fy*ey+fz*ez,1}};
 float f=1.0f/tanf(0.525f),aspect=(float)width/(float)height,n=0.12f,far=100.0f,zf=far/(n-far);
 Matrix p={{f/aspect,0,0,0,0,f,0,0,0,0,zf,-1,0,0,n*zf,0}};return mul(&p,&v);
}
static void upload_vertices(void){uint32_t bytes=vcount*VERTEX_BYTES;const uint8_t*s=(const uint8_t*)cpu_vertices;for(uint32_t i=0;i<bytes/4;i++){uint32_t w;memcpy(&w,s+i*4,4);gpu_vertices[i]=w;}if(bytes)xsdk_gpu_invalidate(xsdk_gpu_address(gpu_vertices),bytes);}
static void frame_draw(uint32_t address,uint32_t frame,bool started,float x,float y,float z,float yaw,bool alive,uint32_t d,uint32_t active,uint32_t round,uint32_t survived,uint32_t dead_timer){
 vcount=0;float shake=(started&&d==EARTHQUAKE&&active>0)?sinf(frame*0.21f)*0.07f:0;
 island(shake);if(started&&alive)player(x,y,z);if(started&&active>0)disaster_mesh(d,active,round,x,z,shake);upload_vertices();
 Matrix m=camera(x,y,z,yaw,shake*0.8f,shake*0.5f);xsdk_gpu_set_target(width,height);xsdk_gpu_set_depth_clear(1,0);xsdk_gpu_set_depth_stencil(&depth_state);
 xsdk_gpu_set_shaders(&vs,&ps);xsdk_built_shader_set_literals(&world_vs_hlsl);xsdk_built_shader_set_literals(&world_ps_hlsl);xsdk_gpu_set_vertex_constants(0,m.m,4);
 xsdk_gpu_set_vertices(0,xsdk_gpu_address(gpu_vertices),vcount*VERTEX_BYTES);if(vcount)xsdk_gpu_draw(XSDK_PRIMITIVE_TRIANGLE_LIST,vcount);
 if(text_ready){xsdk_gpu_set_depth_stencil(&hud_depth_state);xsdk_text_draw(20,16,1,0xFFFFFFFFu,"CLASSIC DISASTER SURVIVAL  |  XBOX 360");
  xsdk_text_draw(20,38,1,0xFFFFD56Au,"LEFT STICK: MOVE   RIGHT STICK: CAMERA   A: JUMP / START   BACK: EXIT");
  if(!started){xsdk_text_draw(410,240,2,0xFFFFFFFFu,"CLASSIC DISASTER SURVIVAL");xsdk_text_draw(485,284,1,0xFFFFD56Au,"PRESS A TO START");xsdk_text_draw(380,316,1,0xFFFFFFFFu,"ONE ISLAND - SIX DISASTERS - SURVIVE!");}
  else{char s[100];if(active==0)xsdk_text_draw(20,70,1,0xFFFFE08Au,"GET READY! DISASTER INCOMING");else{snprintf(s,sizeof s,"DISASTER: %s",names[d%DISASTER_COUNT]);xsdk_text_draw(20,70,1,0xFFFFD56Au,s);}
   snprintf(s,sizeof s,"ROUND %u   SURVIVED: %u",(unsigned)(round+1),(unsigned)survived);xsdk_text_draw(20,92,1,0xFFFFFFFFu,s);
   if(!alive&&dead_timer)xsdk_text_draw(420,326,1,0xFFFF6969u,"ELIMINATED - RESPAWNING...");
   else if(active>0&&alive)xsdk_text_draw(20,114,1,0xFFB9F7B9u,"FIND COVER AND SURVIVE!");}
 }
 xsdk_gpu_resolve(address,width,height,SKY);
}
int main(void){
 xsdk_log_write("classic_disaster: boot");
 NTSTATUS st=xsdk_splash_show(800);if(!NT_SUCCESS(st))xsdk_log_write("classic_disaster: splash 0x%08X",(unsigned)st);
 st=xsdk_gpu_open();if(!NT_SUCCESS(st)){xsdk_log_write("classic_disaster: GPU open 0x%08X",(unsigned)st);return 1;}
 gpu_vertices=xsdk_gpu_alloc(GPU_BYTES);if(!gpu_vertices){xsdk_gpu_close();return 1;}
 st=xsdk_shader_load(&vs,world_vs_hlsl.container,world_vs_hlsl.container_bytes);
 if(NT_SUCCESS(st))st=xsdk_shader_load(&ps,world_ps_hlsl.container,world_ps_hlsl.container_bytes);
 const XsdkVertexElement layout[]={{.usage=XSDK_USAGE_POSITION,.format=XSDK_VERTEX_FLOAT3,.offset_bytes=0},{.usage=XSDK_USAGE_COLOR,.format=XSDK_VERTEX_FLOAT3,.offset_bytes=12}};
 if(NT_SUCCESS(st))st=xsdk_shader_set_vertex_layout(&vs,layout,2,(const uint32_t[]){sizeof(Vertex)});
 if(!NT_SUCCESS(st)){xsdk_log_write("classic_disaster: shader load 0x%08X",(unsigned)st);xsdk_gpu_free(gpu_vertices);xsdk_gpu_close();return 1;}
 XsdkDepthStencil dc={.depth_test=true,.depth_write=true,.depth_compare=XSDK_COMPARE_LESS_EQUAL},hc={.depth_test=false,.depth_write=false};
 xsdk_gpu_depth_stencil_state(&depth_state,&dc);xsdk_gpu_depth_stencil_state(&hud_depth_state,&hc);text_ready=NT_SUCCESS(xsdk_text_open());
 st=xsdk_present_open(XSDK_DISPLAY_720P,2,1,&width,&height);if(!NT_SUCCESS(st)){xsdk_log_write("classic_disaster: display 0x%08X",(unsigned)st);return 1;}
 float px=0,py=0,pz=-4.4f,yaw=0,jv=0;bool started=false,alive=true,died=false;uint32_t dead_timer=0,game_frame=0,survived=0;int32_t last_round=-1;
 for(uint32_t render=0;;render++){
  INPUT_STATE in={0};if(XamInputGetState(0,0,&in)!=ERROR_SUCCESS)in=(INPUT_STATE){0};const INPUT_GAMEPAD*p=&in.gamepad;if(p->buttons&INPUT_GAMEPAD_BACK)break;
  if(!started){if(p->buttons&INPUT_GAMEPAD_A){started=true;game_frame=0;last_round=-1;survived=0;died=false;alive=true;dead_timer=0;px=0;py=0;pz=-4.4f;yaw=0;jv=0;}}
  else{uint32_t round=game_frame/ROUND_FRAMES,rf=game_frame%ROUND_FRAMES;
   if((int32_t)round!=last_round){if(last_round>=0&&!died)survived++;last_round=(int32_t)round;died=false;}
   yaw+=stick(p->thumb_rx)*0.045f;if(yaw>PI)yaw-=2*PI;if(yaw<-PI)yaw+=2*PI;
   if(alive){float speed=(p->left_trigger>120)?0.115f:0.075f,fx=stick(p->thumb_ly),sx=stick(p->thumb_lx);
    float nx=px+(sinf(yaw)*fx+cosf(yaw)*sx)*speed,nz=pz+(cosf(yaw)*fx-sinf(yaw)*sx)*speed;
    if(!blocked(nx,pz))px=clampf(nx,-6.1f,6.1f);if(!blocked(px,nz))pz=clampf(nz,-6.1f,6.1f);
    if((p->buttons&INPUT_GAMEPAD_A)&&py<=0.001f)jv=0.17f;py+=jv;jv-=0.008f;if(py<0){py=0;jv=0;}
   }else if(dead_timer){dead_timer--;if(!dead_timer){alive=true;px=0;py=0;pz=-4.4f;jv=0;}}
   if(rf>=PREP_FRAMES&&alive){uint32_t d=round%DISASTER_COUNT,active=rf-PREP_FRAMES;if(hit_by(d,active,round,px,pz)){alive=false;dead_timer=120;died=true;}}
   game_frame++;
  }
  uint32_t round=started?game_frame/ROUND_FRAMES:0,rf=started?game_frame%ROUND_FRAMES:0,d=round%DISASTER_COUNT,active=(started&&rf>PREP_FRAMES)?rf-PREP_FRAMES:0;
  uint32_t address;st=xsdk_present_begin(&address,5000);if(!NT_SUCCESS(st)){xsdk_log_write("classic_disaster: present 0x%08X",(unsigned)st);break;}
  frame_draw(address,render,started,px,py,pz,yaw,alive,d,active,round,survived,dead_timer);xsdk_present_end();
 }
 xsdk_present_close();if(text_ready)xsdk_text_close();xsdk_shader_free(&ps);xsdk_shader_free(&vs);xsdk_gpu_free(gpu_vertices);xsdk_gpu_close();xsdk_log_write("classic_disaster: exit");return 0;
}
