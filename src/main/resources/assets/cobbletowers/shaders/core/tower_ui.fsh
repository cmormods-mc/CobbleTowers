#version 150
in vec2 uv;
uniform sampler2D Scene;
uniform float UiTime;
uniform vec2 UiSize;
uniform vec2 FrameSize;
uniform vec3 UiAccent;
uniform float UiMode;
uniform float UiRadius;
uniform float UiBorder;
uniform float UiHover;
uniform float UiBlur;
uniform float UiOpacity;
out vec4 fragColor;
void main(){
    vec2 pixel=floor(uv*UiSize);
    if(UiMode<0.0){
        vec3 base=vec3(13.,14.,18.)/255.;
        float seam=step(47.,mod(pixel.x,48.))+step(47.,mod(pixel.y,48.));
        float lamp=exp(-abs(uv.y-.07)*36.)*.025;
        base+=vec3(.012,.019,.025)*min(seam,1.)+vec3(.65,.86,.92)*lamp;
        float vignette=clamp(1.-length((uv-.5)*.9),.45,1.);
        fragColor=vec4(base*vignette,1.);return;
    }
    if(UiMode>1.5){fragColor=vec4(0.);return;}
    vec3 metal=mix(vec3(24.,27.,34.),vec3(13.,14.,18.),uv.y)/255.;
    float scan=mod(pixel.y,3.)<1.? .006:0.;
    metal-=scan*UiBlur;
    metal*=mix(.8,1.15,clamp((UiOpacity-.65)/.33,0.,1.));
    float lip=1.-step(2.,pixel.y);
    metal+=vec3(.035,.045,.052)*lip;
    float edge=1.-step(2.,min(pixel.x,UiSize.x-1.-pixel.x));
    // A restrained, split-color terminal edge. Text is rendered afterwards.
    metal+=UiAccent*UiHover*(.025+edge*.18);
    metal+=vec3(.0,.018,.024)*UiHover*(1.-step(3.,pixel.x));
    fragColor=vec4(metal,1.);
}
