package com.appcontrol.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import com.google.firebase.messaging.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.appcontrol.panel.dto.Models.*;

@Service
public class PushRequestService {
    private final PanelService panel;
    private final ObjectProvider<FirebaseMessaging> messaging;
    private final ObjectMapper mapper;
    private final Validator validator;
    PushRequestService(PanelService panel,ObjectProvider<FirebaseMessaging> messaging,ObjectMapper mapper,Validator validator) {
        this.panel=panel;this.messaging=messaging;this.mapper=mapper;this.validator=validator;
    }
    List<PushItem> parse(JsonNode payload) {
        List<PushItem> requests;
        try {
            if(payload.isArray()) requests=mapper.convertValue(payload,new TypeReference<List<PushItem>>(){});
            else {
                var legacy=mapper.treeToValue(payload,Push.class);
                if(!validator.validate(legacy).isEmpty()) throw new IllegalArgumentException("Invalid push request");
                requests=legacy.userIds().stream().map(uid->new PushItem(uid,legacy.title(),legacy.body())).toList();
            }
        } catch(Exception ex) {throw new IllegalArgumentException("Use [{id,title,message}] or {userIds,title,body}");}
        if(requests==null || requests.isEmpty() || requests.size()>100) throw new IllegalArgumentException("Provide 1–100 push requests");
        var ids=new HashSet<String>();
        for(var request:requests) {
            if(request==null || !validator.validate(request).isEmpty()) throw new IllegalArgumentException("Each request requires id, title and message");
            PanelService.id(request.id());
            if(!ids.add(request.id())) throw new IllegalArgumentException("Duplicate recipient ID");
        }
        if(ids.contains("0") && requests.size()!=1) throw new IllegalArgumentException("All-users ID 0 must be the only request");
        return requests;
    }
    public Map<String,Object> send(JsonNode payload,String actor,String key) throws Exception {
        PanelService.id(key);var requests=parse(payload); boolean all=requests.get(0).id().equals("0");
        var db=panel.db();var ref=db.collection("notifications").document(PanelService.hash(actor+":"+key));
        String fingerprint=PanelService.hash(mapper.writeValueAsString(requests));
        var initial=new HashMap<String,Object>();initial.put("actor",actor);initial.put("createdAt",System.currentTimeMillis());
        initial.put("requests",mapper.convertValue(requests,new TypeReference<List<Map<String,Object>>>(){}));
        initial.put("audience",all?"all":"selected");initial.put("userIds",all?List.of():requests.stream().map(PushItem::id).toList());
        initial.put("title",requests.get(0).title());initial.put("body",requests.get(0).message());initial.put("fingerprint",fingerprint);
        initial.put("status","processing");initial.put("recipientCount",0);initial.put("accepted",0);initial.put("failed",0);initial.put("unknown",0);
        boolean claimed=db.runTransaction(t->{
            var existing=t.get(ref).get();
            if(existing.exists()) {
                if(!fingerprint.equals(existing.getString("fingerprint"))) throw new IllegalArgumentException("Idempotency key already used for different content");
                return false;
            }
            t.create(ref,initial);return true;
        }).get();
        if(!claimed) return details(ref.getId());
        var totals=new HashMap<String,Long>();for(String name:List.of("recipientCount","accepted","failed","unknown","noDevices","missingUsers")) totals.put(name,0L);
        try {
            if(all) {
                String cursor="";
                while(true) {
                    Query query=db.collection("users").orderBy(FieldPath.documentId()).limit(100);
                    if(!cursor.isEmpty()) query=query.startAfter(cursor);
                    var docs=query.get().get().getDocuments();if(docs.isEmpty()) break;
                    var chunk=docs.stream().map(d->new PushItem(d.getId(),requests.get(0).title(),requests.get(0).message())).toList();
                    deliver(ref,chunk,totals);cursor=docs.get(docs.size()-1).getId();
                }
            } else deliver(ref,requests,totals);
            String status=totals.get("unknown")>0?"unknown":totals.get("failed")>0||totals.get("missingUsers")>0?"partial":totals.get("accepted")>0?"accepted":"no_devices";
            var summary=new HashMap<String,Object>(totals);summary.put("status",status);summary.put("completedAt",System.currentTimeMillis());
            ref.update(summary).get();
        } catch(Exception ex) {
            var summary=new HashMap<String,Object>(totals);summary.put("status","unknown");
            ref.update(summary).get();
            // Never retry automatically: FCM may already have accepted messages.
        }
        return details(ref.getId());
    }
    private record Target(String uid,String deviceId,Message message) {}
    private void deliver(DocumentReference request,List<PushItem> chunk,Map<String,Long> totals) throws Exception {
        var db=panel.db();var rows=new LinkedHashMap<String,Map<String,Object>>();var targets=new ArrayList<Target>();
        var resultLists=new HashMap<String,List<Map<String,Object>>>();
        for(var item:chunk) {
            var row=new HashMap<String,Object>();row.put("uid",item.id());row.put("title",item.title());row.put("message",item.message());
            var results=new ArrayList<Map<String,Object>>();resultLists.put(item.id(),results);row.put("fcmResults",results);
            row.put("status","processing");row.put("accepted",0);row.put("failed",0);row.put("unknown",0);
            rows.put(item.id(),row);totals.merge("recipientCount",1L,Long::sum);
            if(!db.collection("users").document(item.id()).get().get().exists()) {row.put("status","user_not_found");totals.merge("missingUsers",1L,Long::sum);continue;}
            var seen=new HashSet<String>();
            for(var device:db.collection("installations").whereEqualTo("uid",item.id()).get().get().getDocuments()) {
                String token=device.getString("fcmToken");if(token==null||token.isBlank()||!seen.add(token)) continue;
                targets.add(new Target(item.id(),device.getId(),Message.builder().setToken(token)
                    .setNotification(Notification.builder().setTitle(item.title()).setBody(item.message()).build()).build()));
            }
            if(targets.stream().noneMatch(t->t.uid().equals(item.id()))) {row.put("status","no_devices");totals.merge("noDevices",1L,Long::sum);}
        }
        // Persist recipient intent before contacting FCM. Tokens never enter history documents.
        persist(request,rows);
        for(int start=0;start<targets.size();start+=500) {
            var batch=targets.subList(start,Math.min(start+500,targets.size()));
            try {
                var responses=messaging.getObject().sendEach(batch.stream().map(Target::message).toList()).getResponses();
                for(int i=0;i<batch.size();i++) {
                    var target=batch.get(i);var response=responses.get(i);var result=new HashMap<String,Object>();result.put("deviceId",target.deviceId());
                    if(response.isSuccessful()) {result.put("status","accepted");result.put("messageId",response.getMessageId());}
                    else {result.put("status","failed");result.put("errorCode",response.getException().getMessagingErrorCode()==null?"FCM_ERROR":response.getException().getMessagingErrorCode().name());}
                    resultLists.get(target.uid()).add(result);
                }
            } catch(Exception ex) {
                for(var target:batch) resultLists.get(target.uid()).add(Map.of("deviceId",target.deviceId(),"status","unknown","errorCode","FCM_RESULT_UNKNOWN"));
            }
        }
        for(var row:rows.values()) {
            var results=resultLists.get(row.get("uid"));
            for(String outcome:List.of("accepted","failed","unknown")) {
                long count=results.stream().filter(r->outcome.equals(r.get("status"))).count();row.put(outcome,count);totals.merge(outcome,count,Long::sum);
            }
            if(!results.isEmpty()) row.put("status",((Number)row.get("unknown")).longValue()>0?"unknown":((Number)row.get("failed")).longValue()>0?"partial":"accepted");
        }
        persist(request,rows);request.update(new HashMap<String,Object>(totals)).get();
    }
    private void persist(DocumentReference request,Map<String,Map<String,Object>> rows) throws Exception {
        var batch=panel.db().batch();for(var entry:rows.entrySet()) batch.set(request.collection("recipients").document(entry.getKey()),entry.getValue());
        batch.commit().get();
    }
    public Map<String,Object> details(String id) throws Exception {
        var ref=panel.db().collection("notifications").document(PanelService.id(id));var doc=ref.get().get();
        if(!doc.exists()) throw new IllegalArgumentException("Push request not found");
        var data=panel.row(doc);data.put("recipients",recipients(id,""));return data;
    }
    public Map<String,Object> recipients(String id,String cursor) throws Exception {
        return panel.page("notifications/"+PanelService.id(id)+"/recipients",cursor,null);
    }
}
