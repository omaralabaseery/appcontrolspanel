package com.appcontrol.panel.service;

import com.google.cloud.firestore.SetOptions;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.*;
import static com.appcontrol.panel.dto.Models.*;

/** Scheduled app events (national days, holidays) the mobile app uses to adjust its icon, greeting and accents. */
@Service
public class AppEventService {
    static final int MAX_APP_EVENTS = 100;
    private static final long CACHE_MS = 60000;
    private final PanelService panel;
    private List<Map<String,Object>> cached;
    private long expiresAt;
    AppEventService(PanelService panel) { this.panel=panel; }

    static void validate(AppEvent o) {
        if(o.endAt()<=o.startAt()) throw new IllegalArgumentException("End time must be after start time");
        if(o.bannerUrl()==null) return;
        URI uri;
        try { uri=URI.create(o.bannerUrl()); } catch(Exception e) { throw new IllegalArgumentException("Invalid banner URL"); }
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)
            throw new IllegalArgumentException("Banner URL must be an https:// address");
    }
    static boolean activeAt(Map<String,Object> row,long now) {
        return Boolean.TRUE.equals(row.get("enabled")) && number(row,"startAt")<=now && now<number(row,"endAt");
    }
    private static long number(Map<String,Object> row,String field) { return row.get(field) instanceof Number n?n.longValue():0; }
    /** Mobile view: one language, no administrative fields. */
    static Map<String,Object> present(Map<String,Object> row,boolean ar) {
        var out=new LinkedHashMap<String,Object>();
        out.put("id",row.get("id")); out.put("title",row.get(ar?"titleAr":"titleEn")); out.put("message",row.get(ar?"messageAr":"messageEn"));
        for(String field:List.of("iconKey","accentColor","bannerUrl","startAt","endAt")) out.put(field,row.get(field));
        return out;
    }

    // The mobile route is public, so reads are cached to bound Firestore usage. Writes on this instance clear it.
    private synchronized List<Map<String,Object>> rows() throws Exception {
        long now=System.currentTimeMillis();
        if(cached!=null && now<expiresAt) return cached;
        var docs=panel.db().collection("appEvents").limit(MAX_APP_EVENTS).get().get().getDocuments();
        cached=docs.stream().map(panel::row).sorted(Comparator.comparingLong(r->number(r,"startAt"))).toList();
        expiresAt=now+CACHE_MS;
        return cached;
    }
    private synchronized void changed() { cached=null; }

    public Map<String,Object> list() throws Exception {
        changed();
        return Map.of("items",rows(),"nextCursor","");
    }
    public Map<String,Object> active(String language) throws Exception {
        boolean ar=language.toLowerCase(Locale.ROOT).startsWith("ar"); long now=System.currentTimeMillis();
        var items=rows().stream().filter(r->activeAt(r,now)).map(r->present(r,ar)).toList();
        return Map.of("language",ar?"ar":"en","serverTime",now,"events",items);
    }
    public Map<String,Object> save(String id,AppEvent o,String actor) throws Exception {
        validate(o);
        var db=panel.db(); var collection=db.collection("appEvents"); long now=System.currentTimeMillis();
        var data=panel.map(o); data.put("updatedAt",now);
        var ref=id==null?collection.document():collection.document(PanelService.id(id));
        if(id==null) {
            if(panel.count(collection)>=MAX_APP_EVENTS) throw new IllegalArgumentException("Maximum "+MAX_APP_EVENTS+" events; delete an old one first");
            data.put("createdAt",now);
        } else if(!ref.get().get().exists()) throw new IllegalArgumentException("Event not found");
        var batch=db.batch();
        // Merge keeps createdAt; every optional field is present in data (possibly null), so cleared values are overwritten.
        batch.set(ref,data,SetOptions.merge());
        batch.set(db.collection("audit").document(),Map.of("action",id==null?"appEvent.create":"appEvent.update","actor",actor,"createdAt",now,"appEventId",ref.getId(),"appEvent",panel.map(o)));
        batch.commit().get(); changed();
        return panel.row(ref.get().get());
    }
    public void delete(String id,String actor) throws Exception {
        var db=panel.db(); var ref=db.collection("appEvents").document(PanelService.id(id));
        if(!ref.get().get().exists()) throw new IllegalArgumentException("Event not found");
        var batch=db.batch();
        batch.delete(ref);
        batch.set(db.collection("audit").document(),Map.of("action","appEvent.delete","actor",actor,"createdAt",System.currentTimeMillis(),"appEventId",id));
        batch.commit().get(); changed();
    }
}
