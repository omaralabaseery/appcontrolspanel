package com.appcontrol.panel.service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.appcontrol.panel.dto.Models.*;
@Service
public class PanelService {
    private final ObjectProvider<Firestore> dbProvider;
    private final ObjectProvider<FirebaseMessaging> messaging;
    private final ObjectProvider<FirebaseAuth> auth;
    private final ObjectMapper mapper;
    PanelService(ObjectProvider<Firestore> db,ObjectProvider<FirebaseMessaging> messaging,ObjectProvider<FirebaseAuth> auth,ObjectMapper mapper) {
        this.dbProvider=db; this.messaging=messaging; this.auth=auth; this.mapper=mapper;
    }
    Firestore db() {
        var db=dbProvider.getIfAvailable();
        if(db==null) throw new IllegalStateException("Firebase is not configured. Set server credentials to enable live mode.");
        return db;
    }
    static String id(String id) {
        if(id==null || id.isBlank() || id.length()>128 || id.contains("/") || id.equals(".") || id.equals("..")) throw new IllegalArgumentException("Invalid identifier");
        return id;
    }
    Map<String,Object> map(Object o) { return mapper.convertValue(o,new TypeReference<Map<String,Object>>(){}); }
    Map<String,Object> row(DocumentSnapshot d) {
        var m=new HashMap<String,Object>(d.getData()==null?Map.of():d.getData()); m.put("id",d.getId()); m.remove("fcmToken");
        for(String field:List.of("input","output")) if(m.containsKey(field+"Json")) {
            Object json=m.remove(field+"Json");
            try {m.put(field,json==null?null:mapper.readTree(json.toString()));}
            catch(Exception ex) {m.put(field,null);}
        }
        return m;
    }
    long count(Query q) throws Exception { return q.count().get().get().getCount(); }
    public Map<String,Object> stats() throws Exception {
        var db=db(); long now=System.currentTimeMillis();
        return Map.of("users",count(db.collection("users")),"online",count(db.collection("users").whereGreaterThan("lastSeen",now-300000)),
            "installations",count(db.collection("installations")),"multiDevice",count(db.collection("users").whereGreaterThan("deviceCount",1)),
            "requests",count(db.collection("events")),"errors",count(db.collection("events").whereGreaterThanOrEqualTo("status",400)),"updatedAt",now);
    }
    public Map<String,Object> page(String collection,String cursor,String uid) throws Exception {
        Query q=db().collection(collection);
        if(uid!=null && !uid.isBlank()) q=q.whereEqualTo("uid",id(uid));
        q=q.orderBy(FieldPath.documentId());
        if(cursor!=null && !cursor.isBlank()) q=q.startAfter(id(cursor));
        var docs=q.limit(51).get().get().getDocuments(); boolean more=docs.size()>50;
        var items=docs.stream().limit(50).map(this::row).toList();
        return Map.of("items",items,"nextCursor",more?docs.get(49).getId():"");
    }
    public Map<String,Object> user(String uid) throws Exception {
        var doc=db().collection("users").document(id(uid)).get().get();
        if(!doc.exists()) throw new IllegalArgumentException("User not found");
        var devices=db().collection("installations").whereEqualTo("uid",uid).get().get().getDocuments().stream().map(this::row).toList();
        return Map.of("user",row(doc),"devices",devices);
    }
    public Policy policy() throws Exception {
        var d=db().collection("settings").document("application").get().get();
        return d.exists()?mapper.convertValue(d.getData(),Policy.class):PolicyService.defaults();
    }
    public Policy savePolicy(Policy p,String uid) throws Exception {
        PolicyService.validate(p); var batch=db().batch();
        batch.set(db().collection("settings").document("application"),map(p));
        batch.set(db().collection("audit").document(),Map.of("action","policy.update","actor",uid,"createdAt",System.currentTimeMillis(),"policy",map(p)));
        batch.commit().get(); return p;
    }
    public void register(String uid,Registration r) throws Exception {
        var db=db(); var u=db.collection("users").document(id(uid)); var installation=db.collection("installations").document(id(r.installationId()));
        var profile=auth.getObject().getUser(uid); long now=System.currentTimeMillis();
        db.runTransaction(t -> {
            var old=t.get(installation).get(); var user=t.get(u).get();
            String previous=old.exists()?old.getString("uid"):null;
            DocumentReference previousRef=previous!=null&&!previous.isBlank()&&!previous.equals(uid)?db.collection("users").document(previous):null;
            DocumentSnapshot previousUser=previousRef!=null?t.get(previousRef).get():null;
            long devices=user.exists()&&user.getLong("deviceCount")!=null?user.getLong("deviceCount"):0;
            boolean newOwner=!uid.equals(previous);
            if(newOwner && devices>=20) throw new IllegalArgumentException("Maximum 20 devices per user; revoke an old device first");
            var data=map(r); data.remove("installationId"); data.put("uid",uid); data.put("lastSeen",now);
            data.put("createdAt",old.exists()?old.get("createdAt"):now);
            // Never retain another account's token when ownership changes or consent is revoked.
            if(r.fcmToken()==null) data.put("fcmToken","");
            t.set(installation,data);
            t.set(u,Map.of("name",Optional.ofNullable(profile.getDisplayName()).orElse("App user"),"email",Optional.ofNullable(profile.getEmail()).orElse(""),
                "language",r.language(),"lastSeen",now,"createdAt",user.exists()?user.get("createdAt"):now,"deviceCount",devices+(newOwner?1:0)),SetOptions.merge());
            if(previousUser!=null && previousUser.exists()) t.update(previousRef,"deviceCount",Math.max(0,Optional.ofNullable(previousUser.getLong("deviceCount")).orElse(0L)-1));
            return null;
        }).get();
    }
    public void heartbeat(String uid,String installationId,boolean remove) throws Exception {
        var db=db(); var d=db.collection("installations").document(id(installationId)); var u=db.collection("users").document(id(uid));
        db.runTransaction(t -> {
            var device=t.get(d).get(); var user=t.get(u).get();
            if(!device.exists() || !uid.equals(device.getString("uid"))) throw new IllegalArgumentException("Device not registered to this user");
            if(remove) {
                t.update(d,Map.of("uid","","fcmToken","","lastSeen",0));
                t.update(u,"deviceCount",Math.max(0,Optional.ofNullable(user.getLong("deviceCount")).orElse(0L)-1));
            } else {
                long now=System.currentTimeMillis(); t.update(d,"lastSeen",now); t.update(u,"lastSeen",now);
            }
            return null;
        }).get();
    }
    static String hash(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    public void event(String uid,Event event) throws Exception {
        var db=db(); var ref=db.collection("events").document(hash(uid+":"+event.eventId()));
        db.runTransaction(t -> {
            if(t.get(ref).get().exists()) return null;
            var data=map(event); data.remove("input");data.remove("output");
            data.put("inputJson",mapper.writeValueAsString(TelemetryPayload.normalize(event.input(),mapper)));
            data.put("outputJson",mapper.writeValueAsString(TelemetryPayload.normalize(event.output(),mapper)));
            data.put("uid",uid); data.put("createdAt",System.currentTimeMillis());
            t.set(ref,data); return null;
        }).get();
    }
    public Map<String,Object> push(Push p,String actor,String key) throws Exception {
        id(key); var db=db(); var ref=db.collection("notifications").document(hash(actor+":"+key));
        String fingerprint=hash(mapper.writeValueAsString(p));
        var initial=new HashMap<String,Object>(map(p)); initial.put("actor",actor); initial.put("createdAt",System.currentTimeMillis());
        initial.put("status","processing"); initial.put("fingerprint",fingerprint);
        boolean claimed=db.runTransaction(t -> {
            var existing=t.get(ref).get();
            if(existing.exists()) {
                if(!fingerprint.equals(existing.getString("fingerprint"))) throw new IllegalArgumentException("Idempotency key already used for different content");
                return false;
            }
            t.set(ref,initial); return true;
        }).get();
        if(!claimed) return row(ref.get().get());
        try {
            Set<String> tokens=new LinkedHashSet<>();
            for(String uid:new LinkedHashSet<>(p.userIds())) {
                for(var d:db.collection("installations").whereEqualTo("uid",id(uid)).get().get().getDocuments()) {
                    String token=d.getString("fcmToken"); if(token!=null&&!token.isBlank()) tokens.add(token);
                }
            }
            var all=new ArrayList<>(tokens); int accepted=0,failed=0;
            for(int i=0;i<all.size();i+=500) {
                var batch=messaging.getObject().sendEachForMulticast(MulticastMessage.builder()
                    .addAllTokens(all.subList(i,Math.min(i+500,all.size())))
                    .setNotification(Notification.builder().setTitle(p.title()).setBody(p.body()).build()).build());
                accepted+=batch.getSuccessCount(); failed+=batch.getFailureCount();
            }
            ref.update(Map.of("status",tokens.isEmpty()?"no_devices":failed>0?"partial":"accepted","accepted",accepted,"failed",failed)).get();
        } catch(Exception ex) {
            // Never automatically retry a possibly delivered batch: callers inspect this record first.
            ref.update("status","unknown").get(); throw ex;
        }
        return row(ref.get().get());
    }
}
