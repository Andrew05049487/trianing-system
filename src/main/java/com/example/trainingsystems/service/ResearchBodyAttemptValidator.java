package com.example.trainingsystems.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Versioned image-plane geometry only. SimCC scores are not probabilities. */
public final class ResearchBodyAttemptValidator {
    public static final String DEFINITION="standing-knee-raise-body-v2", EXTRACTOR="standing-knee-raise-aspect-2d-v2", INPUT="body-attempt-features-v1", SCORES="simcc_peak_mean_uncalibrated";
    private static final Set<String> ROOT=Set.of("sampleId","subjectId","sessionId","attemptId","schemaVersion","modality","source","platform","exerciseType","exerciseId","actionId","actionDefinitionVersion","extractorVersion","modelInputVersion","poseModelVersion","coordinateTransformVersion","streamSessionId","frameId","timestampOrigin","movementSide","cameraView","capturedAt","featureNames","features","featuresStatus","duration","terminationReason","setIndex","completedRepsBefore","completedRepsAfter","intendedRepetition","trackingQuality","frames");
    private static final Set<String> FRAME=Set.of("frameId","streamSessionId","timestampMs","timestampOrigin","captureTimestamp","imageWidth","imageHeight","source","poseModelVersion","coordinateTransformVersion","mirrored","rotationDegrees","scoreSemantics","keypoints","scores","validity","angles");
    private final ObjectMapper mapper;
    public ResearchBodyAttemptValidator(ObjectMapper mapper){this.mapper=mapper;}
    public ResearchSampleValidator.ValidatedSample validate(JsonNode n,boolean recent){
        only(n,ROOT);
        if(n.path("schemaVersion").asInt(-1)!=3 || !"body".equals(text(n,"modality")) || !"standing_knee_raise".equals(text(n,"actionId")) || !DEFINITION.equals(text(n,"actionDefinitionVersion")) || !EXTRACTOR.equals(text(n,"extractorVersion")) || !INPUT.equals(text(n,"modelInputVersion"))) fail();
        choice(n,"source","phone","tv_pi");choice(n,"platform","android_phone","android_tv");choice(n,"exerciseType","DEFAULT","CUSTOM");choice(n,"movementSide","left","right");choice(n,"cameraView","front","rear");choice(n,"timestampOrigin","tv_receive_monotonic","phone_receive_monotonic");
        if("tv_pi".equals(text(n,"source")) && (!"android_tv".equals(text(n,"platform")) || !"tv_receive_monotonic".equals(text(n,"timestampOrigin")))) fail();
        if("phone".equals(text(n,"source")) && (!"android_phone".equals(text(n,"platform")) || !"phone_receive_monotonic".equals(text(n,"timestampOrigin")))) fail();
        for(String k:List.of("sampleId","sessionId","attemptId","exerciseId","streamSessionId")) if(!text(n,k).matches("[A-Za-z0-9_-]{1,100}")) fail();
        if(!"rtmpose-wholebody-133-v1".equals(text(n,"poseModelVersion")) || !"rtmpose-image-normalized-v1".equals(text(n,"coordinateTransformVersion"))) fail();
        choice(n,"terminationReason","RETURNED_TO_BASELINE","USER_FINISHED","INTERRUPTED","TRACKING_LOST","TIMEOUT");
        integer(n.get("setIndex"),1,10000);int before=integer(n.get("completedRepsBefore"),0,100000);
        integer(n.get("completedRepsAfter"),before,100000);if(integer(n.get("intendedRepetition"),1,100001)!=before+1) fail();
        Instant capture;try{capture=Instant.parse(text(n,"capturedAt"));}catch(DateTimeParseException e){throw invalid();}
        if(recent && (capture.isAfter(Instant.now().plusSeconds(300)) || capture.isBefore(Instant.now().minusSeconds(365L*86400)))) fail();
        JsonNode frames=n.path("frames");if(!frames.isArray() || frames.isEmpty() || frames.size()>200) fail();
        long prevTime=-1,prevId=-1,first=-1;List<double[]> values=new ArrayList<>();
        for(JsonNode f:frames){
            only(f,FRAME);long time=nonnegative(f.get("timestampMs")),id=nonnegative(f.get("frameId"));
            if(time<=prevTime || id<=prevId) fail();if(first<0) first=time;prevTime=time;prevId=id;
            for(String k:List.of("streamSessionId","timestampOrigin","source","poseModelVersion","coordinateTransformVersion")) if(!text(n,k).equals(text(f,k))) fail();
            if(!f.has("captureTimestamp") || !f.get("captureTimestamp").isNull() || !f.path("mirrored").isBoolean() || !SCORES.equals(text(f,"scoreSemantics"))) fail();
            int r=integer(f.get("rotationDegrees"),0,270);if(!Set.of(0,90,180,270).contains(r)) fail();
            integer(f.get("imageWidth"),1,8192);integer(f.get("imageHeight"),1,8192);
            JsonNode points=f.path("keypoints"),scores=f.path("scores"),mask=f.path("validity");
            if(!points.isArray() || points.size()!=17 || !scores.isArray() || scores.size()!=17 || !mask.isArray() || mask.size()!=17) fail();
            for(int i=0;i<17;i++){
                JsonNode p=points.get(i),s=scores.get(i);
                if(!p.isNull() && (!p.isArray() || p.size()!=2 || !finite(p.get(0),0,1) || !finite(p.get(1),0,1))) fail();
                if(!s.isNull() && !finite(s,0,1e6)) fail();
                boolean expected=!p.isNull() && !s.isNull() && s.asDouble()>=0.3;
                if(!mask.get(i).isBoolean() || mask.get(i).asBoolean()!=expected) fail();
            }
            double[] v=frameFeatures(f,text(n,"movementSide"));JsonNode angles=f.get("angles");
            if(v==null){if(angles==null || !angles.isNull()) fail();}
            else{only(angles,Set.of("legHeight","hipDeg","kneeDeg","trunkLeanDeg"));int i=0;
                for(String k:List.of("legHeight","hipDeg","kneeDeg","trunkLeanDeg")) if(!close(angles.get(k),v[i++])) fail();values.add(v);}
        }
        double duration=(prevTime-first)/1000.0,ratio=(double)values.size()/frames.size();
        if(nonnegative(n.get("frameId"))!=prevId || duration>20 || !close(n.get("duration"),duration) || values.isEmpty()) fail();
        only(n.path("trackingQuality"),Set.of("validFrameRatio"));if(!close(n.path("trackingQuality").get("validFrameRatio"),ratio)) fail();
        boolean available=values.size()>=4 && ratio>=0.6 && duration>0;
        if(!(available?"available":"unavailable").equals(text(n,"featuresStatus"))) fail();
        JsonNode names=n.path("featureNames"),features=n.path("features");
        if(!names.isArray() || names.size()!=5 || !features.isArray() || features.size()!=5) fail();
        double[] expected={0,180,180,0,duration};
        for(double[] v:values){expected[0]=Math.max(expected[0],v[0]);expected[1]=Math.min(expected[1],v[1]);expected[2]=Math.min(expected[2],v[2]);expected[3]=Math.max(expected[3],Math.abs(v[3]));}
        for(int i=0;i<5;i++) if(!ResearchActionRegistry.STANDING_DEFINITION.featureNames().get(i).equals(names.get(i).asText()) || (available?!close(features.get(i),expected[i]):!features.get(i).isNull())) fail();
        ObjectNode safe=(ObjectNode)canonical(n);safe.remove("subjectId");
        return new ResearchSampleValidator.ValidatedSample(text(n,"sampleId"),text(n,"movementSide"),text(n,"cameraView"),capture,safe);
    }
    static double[] frameFeatures(JsonNode f,String side){
        int sh=side.equals("left")?5:6,h=side.equals("left")?11:12,k=side.equals("left")?13:14,a=side.equals("left")?15:16;
        for(int i:new int[]{5,6,11,12,sh,h,k,a}) if(!f.path("validity").path(i).asBoolean()) return null;
        double[] top=mid(point(f,5),point(f,6)),bottom=mid(point(f,11),point(f,12)),hip=point(f,h),knee=point(f,k);
        double torso=Math.hypot(top[0]-bottom[0],top[1]-bottom[1]);if(torso<=1e-6) return null;
        double ha=angle(point(f,sh),hip,knee),ka=angle(hip,knee,point(f,a));if(!Double.isFinite(ha) || !Double.isFinite(ka)) return null;
        return new double[]{Math.max(0,(hip[1]-knee[1])/torso),ha,ka,Math.toDegrees(Math.atan2(top[0]-bottom[0],bottom[1]-top[1]))};
    }
    private static double[] point(JsonNode f,int i){JsonNode p=f.path("keypoints").get(i);return new double[]{p.get(0).asDouble()*f.path("imageWidth").asDouble(),p.get(1).asDouble()*f.path("imageHeight").asDouble()};}
    private static double[] mid(double[] a,double[] b){return new double[]{(a[0]+b[0])/2,(a[1]+b[1])/2};}
    private static double angle(double[] a,double[] b,double[] c){double ux=a[0]-b[0],uy=a[1]-b[1],vx=c[0]-b[0],vy=c[1]-b[1],d=Math.hypot(ux,uy)*Math.hypot(vx,vy);return d<1e-6?Double.NaN:Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,(ux*vx+uy*vy)/d))));}
    private JsonNode canonical(JsonNode n){if(n.isObject()){ObjectNode out=mapper.createObjectNode();List<String> keys=new ArrayList<>();n.fieldNames().forEachRemaining(keys::add);Collections.sort(keys);keys.forEach(k->out.set(k,canonical(n.get(k))));return out;}if(n.isArray()){var out=mapper.createArrayNode();n.forEach(v->out.add(canonical(v)));return out;}return n.deepCopy();}
    private static void only(JsonNode n,Set<String> keys){if(n==null || !n.isObject()) fail();n.fieldNames().forEachRemaining(k->{if(!keys.contains(k)) fail();});}
    private static String text(JsonNode n,String k){JsonNode v=n.get(k);return v!=null && v.isTextual()?v.asText():"";}
    private static void choice(JsonNode n,String k,String... choices){if(!Set.of(choices).contains(text(n,k))) fail();}
    private static int integer(JsonNode n,int min,int max){if(n==null || !n.isIntegralNumber() || !n.canConvertToInt() || n.asInt()<min || n.asInt()>max) fail();return n.asInt();}
    private static long nonnegative(JsonNode n){if(n==null || !n.isIntegralNumber() || !n.canConvertToLong() || n.asLong()<0) fail();return n.asLong();}
    private static boolean finite(JsonNode n,double min,double max){return n!=null && n.isNumber() && Double.isFinite(n.asDouble()) && n.asDouble()>=min && n.asDouble()<=max;}
    private static boolean close(JsonNode n,double v){return finite(n,-1e9,1e9) && Math.abs(n.asDouble()-v)<=1e-5;}
    private static void fail(){throw invalid();}
    private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_BODY_ATTEMPT_SAMPLE");}
}
