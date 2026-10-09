package com.appcontrol.panel.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import com.google.firebase.messaging.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PushRequestServiceTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test @SuppressWarnings("unchecked") void acceptsNumericIdsLegacyAndRejectsMixedBroadcast() throws Exception {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var service=new PushRequestService(mock(PanelService.class),mock(ObjectProvider.class),mapper,factory.getValidator());
            assertEquals("0",service.parse(mapper.readTree("[{\"id\":0,\"title\":\"t\",\"message\":\"m\"}]")).get(0).id());
            assertEquals("1",service.parse(mapper.readTree("[{\"id\":1,\"title\":\"t\",\"message\":\"m\"}]")).get(0).id());
            assertEquals("u",service.parse(mapper.readTree("{\"userIds\":[\"u\"],\"title\":\"t\",\"body\":\"m\"}")).get(0).id());
            for(String json:List.of("[]","[{\"id\":0,\"title\":\"t\",\"message\":\"m\"},{\"id\":1,\"title\":\"t\",\"message\":\"m\"}]","[{\"id\":1,\"title\":\"\",\"message\":\"m\"}]","[{\"id\":1,\"title\":\"t\",\"message\":\"m\"},{\"id\":1,\"title\":\"t\",\"message\":\"m\"}]"))
                assertThrows(IllegalArgumentException.class,()->service.parse(mapper.readTree(json)));
        }
    }
    @Test @SuppressWarnings("unchecked") void personalizedSendStoresRecipientsAndFcmResultsAndDoesNotResendSameKey() throws Exception {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var panel=mock(PanelService.class);var db=mock(Firestore.class);when(panel.db()).thenReturn(db);
            var fcm=mock(FirebaseMessaging.class);ObjectProvider<FirebaseMessaging> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(fcm);
            var notices=mock(CollectionReference.class);var ref=mock(DocumentReference.class);var recipientCollection=mock(CollectionReference.class);
            when(db.collection("notifications")).thenReturn(notices);when(notices.document(anyString())).thenReturn(ref);when(ref.getId()).thenReturn("request-id");when(ref.collection("recipients")).thenReturn(recipientCollection);
            var recipientRef=mock(DocumentReference.class);when(recipientCollection.document(anyString())).thenReturn(recipientRef);
            var snapshot=mock(DocumentSnapshot.class);when(snapshot.exists()).thenReturn(true);when(ref.get()).thenReturn(ApiFutures.immediateFuture(snapshot));when(panel.row(snapshot)).thenReturn(new HashMap<>(Map.of("id","request-id")));
            when(panel.page(anyString(),anyString(),isNull())).thenReturn(Map.of("items",List.of(),"nextCursor",""));
            var tx=mock(Transaction.class);var absent=mock(DocumentSnapshot.class);when(tx.get(ref)).thenReturn(ApiFutures.immediateFuture(absent));
            when(db.runTransaction(any(Transaction.Function.class))).thenAnswer(i->ApiFutures.immediateFuture(((Transaction.Function<Object>)i.getArgument(0)).updateCallback(tx)));
            when(ref.update(anyMap())).thenReturn(ApiFutures.immediateFuture(null));
            var batch=mock(WriteBatch.class);when(db.batch()).thenReturn(batch);when(batch.commit()).thenReturn(ApiFutures.immediateFuture(List.of()));
            var users=mock(CollectionReference.class);var userRef=mock(DocumentReference.class);when(db.collection("users")).thenReturn(users);when(users.document(anyString())).thenReturn(userRef);when(userRef.get()).thenReturn(ApiFutures.immediateFuture(snapshot));
            var devices=mock(CollectionReference.class);var query=mock(Query.class);var querySnapshot=mock(QuerySnapshot.class);var device=mock(QueryDocumentSnapshot.class);
            when(db.collection("installations")).thenReturn(devices);when(devices.whereEqualTo(eq("uid"),anyString())).thenReturn(query);when(query.get()).thenReturn(ApiFutures.immediateFuture(querySnapshot));when(querySnapshot.getDocuments()).thenReturn(List.of(device));when(device.getString("fcmToken")).thenReturn("secret-fcm-token");when(device.getId()).thenReturn("device-1");
            SendResponse result=org.springframework.test.util.ReflectionTestUtils.invokeMethod(SendResponse.class,"fromMessageId","fcm-message-1");var results=mock(BatchResponse.class);when(results.getResponses()).thenReturn(List.of(result,result));when(fcm.sendEach(anyList())).thenReturn(results);
            var service=new PushRequestService(panel,provider,mapper,factory.getValidator());
            var payload=mapper.readTree("[{\"id\":\"u1\",\"title\":\"First\",\"message\":\"one\"},{\"id\":\"u2\",\"title\":\"Second\",\"message\":\"two\"}]");
            service.send(payload,"admin","same-key");
            var messages=org.mockito.ArgumentCaptor.forClass(List.class);verify(fcm).sendEach(messages.capture());assertEquals(2,messages.getValue().size());
            verify(ref).update(argThat((Map<String,Object> data)->"accepted".equals(data.get("status"))&&Long.valueOf(2).equals(data.get("accepted"))));
            verify(batch,atLeastOnce()).set(eq(recipientRef),argThat((Map<String,Object> data)->"u1".equals(data.get("uid"))&&"First".equals(data.get("title"))&&!data.toString().contains("secret-fcm-token")));
            when(absent.exists()).thenReturn(true);when(absent.getString("fingerprint")).thenReturn(PanelService.hash(mapper.writeValueAsString(service.parse(payload))));
            service.send(payload,"admin","same-key");verify(fcm,times(1)).sendEach(anyList());
            // Indeterminate FCM outcomes are recorded, never reported as accepted.
            when(absent.exists()).thenReturn(false);
            when(fcm.sendEach(anyList())).thenThrow(new RuntimeException("provider timeout"));
            service.send(payload,"admin","unknown-key");
            verify(ref).update(argThat((Map<String,Object> data)->"unknown".equals(data.get("status"))&&Long.valueOf(2).equals(data.get("unknown"))));
            // Broadcast must walk subsequent user pages, even beyond the first loaded audience.
            when(querySnapshot.getDocuments()).thenReturn(List.of());
            var userQuery=mock(Query.class);when(users.orderBy(FieldPath.documentId())).thenReturn(userQuery);when(userQuery.limit(100)).thenReturn(userQuery);when(userQuery.startAfter(anyString())).thenReturn(userQuery);
            var page1=mock(QuerySnapshot.class);var page2=mock(QuerySnapshot.class);var empty=mock(QuerySnapshot.class);
            var u1=mock(QueryDocumentSnapshot.class);var u2=mock(QueryDocumentSnapshot.class);when(u1.getId()).thenReturn("user-A");when(u2.getId()).thenReturn("user-B");
            when(page1.getDocuments()).thenReturn(List.of(u1));when(page2.getDocuments()).thenReturn(List.of(u2));when(empty.getDocuments()).thenReturn(List.of());
            when(userQuery.get()).thenReturn(ApiFutures.immediateFuture(page1),ApiFutures.immediateFuture(page2),ApiFutures.immediateFuture(empty));
            service.send(mapper.readTree("[{\"id\":0,\"title\":\"All\",\"message\":\"Broadcast\"}]"),"admin","broadcast-key");
            verify(userQuery).startAfter("user-A");verify(userQuery).startAfter("user-B");
            verify(ref).update(argThat((Map<String,Object> data)->"no_devices".equals(data.get("status"))&&Long.valueOf(2).equals(data.get("recipientCount"))));
        }
    }
}
