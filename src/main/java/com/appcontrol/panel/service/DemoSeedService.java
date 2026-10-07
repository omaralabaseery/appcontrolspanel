package com.appcontrol.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Explicit admin operation; never runs on application startup. */
@Service
public class DemoSeedService {
    private static final String BATCH = "panel-demo-v1";
    private final DatabaseService database;
    private final ObjectMapper mapper;
    private final String projectId;
    public DemoSeedService(DatabaseService database, ObjectMapper mapper,
        @Value("${panel.firebase.project-id:${FIREBASE_PROJECT_ID:insta-11358}}") String projectId) {
        this.database=database; this.mapper=mapper; this.projectId=projectId;
    }
    public record SeedRecord(String path, Map<String,Object> data) {}
    List<SeedRecord> records(long now) throws Exception {
        try(var stream=new ClassPathResource("database/demo-seed.json").getInputStream()) {
            List<SeedRecord> records=mapper.readValue(stream,new TypeReference<List<SeedRecord>>(){});
            for(var record:records) {
                if(!record.path().matches("(users|installations|events|notifications)/seed_v1_[A-Za-z0-9_]+"))
                    throw new IllegalStateException("Invalid seed path");
                for(String field:List.of("createdAt","lastSeen"))
                    if(record.data().get(field) instanceof Number offset) record.data().put(field,now+offset.longValue());
            }
            return records;
        }
    }
    public Map<String,Object> apply(String actor,String confirmedProject,boolean remove) throws Exception {
        if(!projectId.equals(confirmedProject)) throw new IllegalArgumentException("Confirm the configured Firebase project ID");
        PanelService.id(actor);
        var records=records(System.currentTimeMillis()); var db=database.db();
        var audit=db.collection("audit").document();
        var changed=db.runTransaction(t -> {
            // Complete every read before writes; conflicts retry atomically.
            var snapshots=new ArrayList<DocumentSnapshot>();
            for(var record:records) snapshots.add(t.get(db.document(record.path())).get());
            if(!remove) for(var snapshot:snapshots)
                if(snapshot.exists() && (!BATCH.equals(snapshot.getString("seedBatch")) || !Boolean.TRUE.equals(snapshot.getBoolean("testData"))))
                    throw new IllegalStateException("Seed ID collision; existing data will not be overwritten");
            int count=0;
            for(int i=0;i<records.size();i++) {
                var snapshot=snapshots.get(i); var record=records.get(i); var ref=db.document(record.path());
                if(remove) {
                    if(snapshot.exists() && BATCH.equals(snapshot.getString("seedBatch")) && Boolean.TRUE.equals(snapshot.getBoolean("testData"))) {t.delete(ref);count++;}
                } else if(!snapshot.exists()) {t.create(ref,record.data());count++;}
            }
            if(count>0) t.create(audit,Map.of("action",remove?"database.demo.remove":"database.demo.seed","actor",actor,"createdAt",System.currentTimeMillis(),"documentsChanged",count,"seedBatch",BATCH));
            return count;
        }).get();
        return Map.of("projectId",projectId,"documentsChanged",changed,"seedBatch",BATCH,"operation",remove?"remove":"seed");
    }
}
