import io.atmos.weather.push.*
fun main() {
    val now=1_800_000_000_000L
    val e=PushEnvelope("CWA_2026_1001",1,now-1000,now+45000,"TAIWAN","CWA_OFFICIAL",5.5,"地震速報","請立即掩護")
    check(SeismicPushPolicy.check(e,now,null)==PushDecision.ACCEPT)
    check(SeismicPushPolicy.check(e,now,1)==PushDecision.DUPLICATE)
    check(SeismicPushPolicy.check(e.copy(revision=2),now,1)==PushDecision.ACCEPT)
    check(SeismicPushPolicy.check(e.copy(expiresAtMillis=now-1),now,null)==PushDecision.EXPIRED)
    check(SeismicPushPolicy.check(e.copy(issuedAtMillis=now+40000),now,null)==PushDecision.FUTURE)
    check(SeismicPushPolicy.check(e.copy(source="FAKE"),now,null)==PushDecision.UNTRUSTED_SOURCE)
    check(SeismicPushPolicy.check(e.copy(eventId="!!"),now,null)==PushDecision.INVALID)
    check(SeismicPushPolicy.check(e.copy(magnitude=12.0),now,null)==PushDecision.INVALID)
    println("8 seismic push policy tests PASS")
}
