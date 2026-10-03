package com.example.trainingsystems.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Image proxies matching Dart/Python, NOT clinical angles, depth or pinch force. */
public final class ResearchHandFeatures {
    private ResearchHandFeatures() {}
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid hand contract"); }
    private static double v(JsonNode p, int i, int axis) { return p.get(i).get(axis).asDouble(); }
    private static double distance(JsonNode a, JsonNode b) { return Math.hypot(a.get(0).asDouble()-b.get(0).asDouble(), a.get(1).asDouble()-b.get(1).asDouble()); }
    private static double range(List<Double> list) { return list.stream().mapToDouble(Double::doubleValue).max().orElseThrow()-list.stream().mapToDouble(Double::doubleValue).min().orElseThrow(); }
    private static double bearing(JsonNode p) { return Math.toDegrees(Math.atan2(v(p,9,1)-v(p,0,1),v(p,9,0)-v(p,0,0))); }
    private static boolean finite(JsonNode n, double min, double max) { return n.isNumber() && Double.isFinite(n.asDouble()) && n.asDouble()>=min && n.asDouble()<=max; }

    public static double[] extract(JsonNode sample) {
        require(sample.path("schemaVersion").asInt(-1)==2 &&
            "mediapipe_hand_21".equals(sample.path("landmarkSource").asText()) &&
            "hand-image-proxy-v1".equals(sample.path("extractorVersion").asText()) &&
            "hand-features-v1".equals(sample.path("modelInputVersion").asText()) &&
            "channel-arrival-stopwatch".equals(sample.path("timestampOrigin").asText()) &&
            "unknown".equals(sample.path("movementSide").asText()) &&
            "unknown".equals(sample.path("anatomicalSide").asText()));
        JsonNode frames=sample.path("frames");
        require(frames.isArray() && frames.size()>=4 && frames.size()<=200);
        List<JsonNode> points=new ArrayList<>();
        long previous=-1,first=-1;
        for (JsonNode frame:frames) {
            require(frame.isObject() && frame.size()==2 && frame.has("landmarks") && frame.has("timestampMs"));
            JsonNode time=frame.path("timestampMs"), p=frame.path("landmarks");
            require(time.isIntegralNumber() && time.canConvertToLong() && time.asLong()>=0 && time.asLong()>previous &&
                (previous<0 || time.asLong()-previous<=350) && p.isArray() && p.size()==21);
            previous=time.asLong(); if(first<0) first=previous;
            for(JsonNode point:p) require(point.isArray() && point.size()==3 && finite(point.get(0),0,1) && finite(point.get(1),0,1) && finite(point.get(2),-5,5));
            double scale=distance(p.get(0),p.get(9));
            require(scale>=0.02 && scale<=0.8 && distance(p.get(5),p.get(17))/scale>=0.1);
            points.add(p);
        }
        JsonNode segment=sample.path("segment");
        double duration=(previous-first)/1000.0;
        require(duration>=0.3 && duration<=20 && sample.path("timestampMs").isIntegralNumber() && sample.path("timestampMs").asLong()==previous &&
            segment.isObject() && segment.size()==4 && segment.path("startMs").asLong(-1)==first && segment.path("endMs").asLong(-1)==previous &&
            "rule-rep-boundaries".equals(segment.path("kind").asText()) && segment.path("completedReps").asInt(-1)==1);
        List<Double> axis=new ArrayList<>(); axis.add(0.0);
        double step=0;
        for(int i=1;i<points.size();i++) {
            double delta=(bearing(points.get(i))-bearing(points.get(i-1))+540)%360-180;
            axis.add(axis.get(i-1)+delta); step+=Math.abs(delta);
        }
        step/=points.size()-1;
        String action=sample.path("actionId").asText();
        require(Set.of("turnPalm","sidePinch","wristExtension","wristSideBend").contains(action));
        if("sidePinch".equals(action)) {
            List<Double> ratios=new ArrayList<>(); double travel=0;
            JsonNode start=points.get(0); double startScale=distance(start.get(0),start.get(9));
            for(JsonNode p:points) {
                ratios.add(distance(p.get(4),p.get(6))/distance(p.get(0),p.get(9)));
                travel=Math.max(travel,distance(p.get(0),start.get(0))/startScale);
            }
            require(range(ratios)>=0.01);
            return new double[]{ratios.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),ratios.stream().mapToDouble(Double::doubleValue).max().orElseThrow(),range(ratios),travel,duration};
        }
        require(range(axis)>=1);
        if("turnPalm".equals(action)) {
            List<Double> xs=new ArrayList<>(), normals=new ArrayList<>();
            for(JsonNode p:points) {
                xs.add((v(p,9,0)-v(p,0,0))/distance(p.get(0),p.get(9)));
                double ux=v(p,5,0)-v(p,0,0),uy=v(p,5,1)-v(p,0,1),uz=v(p,5,2)-v(p,0,2);
                double vx=v(p,17,0)-v(p,0,0),vy=v(p,17,1)-v(p,0,1),vz=v(p,17,2)-v(p,0,2);
                double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,length=Math.sqrt(nx*nx+ny*ny+nz*nz);
                require(length>=1e-8); normals.add(nz/length);
            }
            return new double[]{range(xs),range(normals),range(axis),step,duration};
        }
        return new double[]{axis.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),axis.stream().mapToDouble(Double::doubleValue).max().orElseThrow(),range(axis),step,duration};
    }
}
