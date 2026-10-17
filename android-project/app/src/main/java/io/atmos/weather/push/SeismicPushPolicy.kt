package io.atmos.weather.push

/** Pure decision logic; the input is already authenticated by FCM transport and server ACLs.
 * Do NOT treat this client policy as proof of upstream official EEW origin.
 */
data class PushEnvelope(
    val eventId:String, val revision:Int, val issuedAtMillis:Long,
    val expiresAtMillis:Long, val region:String, val source:String,
    val magnitude:Double?, val title:String, val detail:String
)
enum class PushDecision { ACCEPT, DUPLICATE, EXPIRED, FUTURE, INVALID, UNTRUSTED_SOURCE }
object SeismicPushPolicy {
    private val sources=setOf("CWA_OFFICIAL", "EXPTECH_VERIFIED")
    private val regions=setOf("TAIWAN", "TAIPEI", "NEW_TAIPEI", "TAOYUAN", "TAICHUNG", "TAINAN", "KAOHSIUNG", "KEELUNG", "HSINCHU", "CHIAYI", "MIAOLI", "CHANGHUA", "NANTOU", "YUNLIN", "PINGTUNG", "YILAN", "HUALIEN", "TAITUNG", "PENGHU", "KINMEN", "LIENCHIANG")
    fun check(e:PushEnvelope, now:Long, previousRevision:Int?):PushDecision {
        if(e.source !in sources) return PushDecision.UNTRUSTED_SOURCE
        // A broad TAIWAN routing topic alone does not prove that a Japan event
        // has impact in Taiwan. Require explicit Taiwanese impact wording, or a
        // dedicated Taiwan county routing key. Only trusted sources pass above.
        val foreign = listOf("日本", "沖繩", "琉球", "Japan", "Okinawa").any {
            e.title.contains(it,ignoreCase=true)
        }
        if(foreign && e.region == "TAIWAN") return PushDecision.INVALID
        // Overseas events require a verified, specific Taiwan county routing key;
        // the broad TAIWAN topic or generic text such as "台灣無影響" is not proof.
        if(e.eventId.length !in 5..128 || !e.eventId.all { it.isLetterOrDigit() || it in "_-." }) return PushDecision.INVALID
        if(e.region !in regions || e.revision !in 0..999 || e.title.length !in 3..120 || e.detail.length > 400) return PushDecision.INVALID
        if(e.magnitude != null && (!e.magnitude.isFinite() || e.magnitude !in 0.0..10.0)) return PushDecision.INVALID
        if(e.issuedAtMillis > now + 30_000L) return PushDecision.FUTURE
        if(e.expiresAtMillis <= now || now-e.issuedAtMillis > 120_000L || e.expiresAtMillis-e.issuedAtMillis !in 1L..180_000L) return PushDecision.EXPIRED
        if(previousRevision != null && previousRevision >= e.revision) return PushDecision.DUPLICATE
        return PushDecision.ACCEPT
    }
}
