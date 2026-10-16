package io.atmos.weather.push

import org.junit.Assert.assertEquals
import org.junit.Test

class SeismicPushPolicyTest {
 private val now=1800000000000L
 private val event=PushEnvelope("CWA_2026_1001",1,now-1000,now+45000,"TAIWAN","CWA_OFFICIAL",5.5,"地震速報","請立即掩護")
 @Test fun freshEvent(){assertEquals(PushDecision.ACCEPT,SeismicPushPolicy.check(event,now,null))}
 @Test fun duplicate(){assertEquals(PushDecision.DUPLICATE,SeismicPushPolicy.check(event,now,1))}
 @Test fun revision(){assertEquals(PushDecision.ACCEPT,SeismicPushPolicy.check(event.copy(revision=2),now,1))}
 @Test fun stale(){assertEquals(PushDecision.EXPIRED,SeismicPushPolicy.check(event.copy(expiresAtMillis=now-1),now,null))}
 @Test fun invalidSource(){assertEquals(PushDecision.UNTRUSTED_SOURCE,SeismicPushPolicy.check(event.copy(source="FAKE"),now,null))}
 @Test fun invalidFuture(){assertEquals(PushDecision.FUTURE,SeismicPushPolicy.check(event.copy(issuedAtMillis=now+40000),now,null))}
 @Test fun invalidRegion(){assertEquals(PushDecision.INVALID,SeismicPushPolicy.check(event.copy(region="WILD"),now,null))}
 @Test fun invalidMagnitude(){assertEquals(PushDecision.INVALID,SeismicPushPolicy.check(event.copy(magnitude=12.0),now,null))}
}
