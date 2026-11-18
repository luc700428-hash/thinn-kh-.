package io.atmos.weather.support

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorLinkTest {
    @Test fun onlyEcpayHttpsPaymentPagesAreOpened() {
        assertTrue(Sponsor.isAllowed("https://p.ecpay.com.tw/8C1BF68"))
        assertTrue(Sponsor.isAllowed(" https://payment.ecpay.com.tw/QuickCollect/PayData?abc=1 "))
        assertFalse(Sponsor.isAllowed("http://p.ecpay.com.tw/8C1BF68"))
        assertFalse(Sponsor.isAllowed("https://p.ecpay.com.tw.evil.example/8C1BF68"))
        assertFalse(Sponsor.isAllowed("https://evil.example/?u=https://p.ecpay.com.tw"))
        assertFalse(Sponsor.isAllowed("https://user@p.ecpay.com.tw/8C1BF68"))
        assertFalse(Sponsor.isAllowed("https://p.ecpay.com.tw:8443/8C1BF68"))
        assertFalse(Sponsor.isAllowed("javascript:alert(1)"))
        assertFalse(Sponsor.isAllowed(""))
    }

    @Test fun everyTierOpensItsOwnEcpayPageWithAReadablePrice() {
        val tiers = Sponsor.tiers
        assertTrue(tiers.size == 6)
        assertTrue(tiers.all { Sponsor.isAllowed(it.link) })
        assertTrue("Each tier needs its own page", tiers.map { it.link }.toSet().size == tiers.size)
        assertTrue(tiers.map { it.id }.toSet().size == tiers.size)
        assertTrue(Sponsor.ready)
        org.junit.Assert.assertEquals("NT$100", Sponsor.price(tiers.first { it.id == "coffee" }))
        org.junit.Assert.assertEquals("NT$299／月", Sponsor.price(tiers.first { it.monthly }))
        org.junit.Assert.assertEquals("自訂金額", Sponsor.price(tiers.first { it.amount == null }))
        org.junit.Assert.assertEquals("NT$1,000", Sponsor.price(tiers.first { it.amount == 1000 }))
        org.junit.Assert.assertEquals("NT$299", Sponsor.amountLabel(tiers.first { it.monthly }))
        // Amounts the merchant set up in the ECPay back office.
        org.junit.Assert.assertEquals(listOf(100, 200, 299, 500, 1000, null), tiers.map { it.amount })
    }

    @Test fun theAppNeverCarriesEcpaySigningKeys() {
        // HashKey / HashIV sign payment requests; they must live only in the ECPay back office or a server.
        val root = java.io.File("src/main/java/io/atmos/weather/support")
        val source = root.walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertFalse(Regex("""HashKey\s*[=:]\s*"[^"]+"""").containsMatchIn(source))
        assertFalse(Regex("""HashIV\s*[=:]\s*"[^"]+"""").containsMatchIn(source))
        assertFalse(source.contains("MerchantID"))
    }
}
