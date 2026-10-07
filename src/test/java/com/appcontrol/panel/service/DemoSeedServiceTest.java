package com.appcontrol.panel.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class DemoSeedServiceTest {
    @Test void datasetHasConsistentRelationsAndNoPushTokens() throws Exception {
        var service=new DemoSeedService(mock(DatabaseService.class),new ObjectMapper(),"test-project");
        var records=service.records(1000000000000L);
        assertEquals(46,records.size());
        var users=new HashSet<String>();
        records.stream().filter(r->r.path().startsWith("users/")).forEach(r->users.add(r.path().substring(6)));
        assertEquals(8,users.size());
        for(var r:records) {
            assertEquals(true,r.data().get("testData"));
            assertFalse(r.data().containsKey("fcmToken"));
            if(r.data().containsKey("uid")) assertTrue(users.contains(r.data().get("uid")));
            if(r.path().startsWith("users/")) assertEquals(((Number)r.data().get("deviceCount")).longValue(),records.stream().filter(d->d.path().startsWith("installations/")&&r.path().substring(6).equals(d.data().get("uid"))).count());
        }
    }
    @Test void requiresCorrectProjectBeforeAccessingDatabase() throws Exception {
        var database=mock(DatabaseService.class);
        var service=new DemoSeedService(database,new ObjectMapper(),"test-project");
        assertThrows(IllegalArgumentException.class,()->service.apply("admin","other",false));
        verifyNoInteractions(database);
    }
    @Test @SuppressWarnings("unchecked") void seedIsIdempotentAndCleanupOnlyDeletesMarkedRecords() throws Exception {
        var database=mock(DatabaseService.class); var db=mock(Firestore.class);var t=mock(Transaction.class);
        when(database.db()).thenReturn(db);
        var refs=new HashMap<String,DocumentReference>();
        when(db.document(anyString())).thenAnswer(i->refs.computeIfAbsent(i.getArgument(0),k->mock(DocumentReference.class)));
        var audit=mock(CollectionReference.class);when(db.collection("audit")).thenReturn(audit);when(audit.document()).thenReturn(mock(DocumentReference.class));
        var snapshot=mock(DocumentSnapshot.class);
        when(t.get(any(DocumentReference.class))).thenReturn(ApiFutures.immediateFuture(snapshot));
        when(db.runTransaction(any(Transaction.Function.class))).thenAnswer(i->ApiFutures.immediateFuture(((Transaction.Function<Object>)i.getArgument(0)).updateCallback(t)));
        var service=new DemoSeedService(database,new ObjectMapper(),"test-project");
        assertEquals(46,service.apply("admin","test-project",false).get("documentsChanged"));
        when(snapshot.exists()).thenReturn(true);when(snapshot.getString("seedBatch")).thenReturn("panel-demo-v1");when(snapshot.getBoolean("testData")).thenReturn(true);
        clearInvocations(t);
        assertEquals(0,service.apply("admin","test-project",false).get("documentsChanged"));verify(t,never()).create(any(),any());
        assertEquals(46,service.apply("admin","test-project",true).get("documentsChanged"));
        when(snapshot.getBoolean("testData")).thenReturn(false);clearInvocations(t);
        assertEquals(0,service.apply("admin","test-project",true).get("documentsChanged"));verify(t,never()).delete(any());
        assertThrows(IllegalStateException.class,()->service.apply("admin","test-project",false));
    }
}
