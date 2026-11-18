package io.atmos.weather.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

/**
 * Sponsorship goes through an ECPay payment page created in the ECPay merchant back office
 * (收款連結 or 實況主收款). The app only opens that public link: payment, amount and receipts
 * happen on ECPay's own page.
 *
 * Never put the merchant HashKey / HashIV in this app or this repository. They sign payment
 * requests; anyone who reads them out of an APK or a public repo could forge them. If the app
 * ever needs to create orders itself, that signing must happen on a server.
 */
object Sponsor {
    /**
     * One ECPay payment page per way of supporting, created in the ECPay back office. [amount] is
     * null for the page where the supporter types their own amount; [monthly] marks the plan that
     * is billed every month.
     */
    data class Tier(val id: String, val title: String, val detail: String, val amount: Int?, val monthly: Boolean, val link: String)

    val tiers = listOf(
        Tier("coffee", "一杯咖啡", "請開發者喝杯咖啡", 100, false, "https://p.ecpay.com.tw/F5220AC"),
        Tier("snack", "每月一份點心", "讓深夜修 bug 有點力氣", 200, false, "https://p.ecpay.com.tw/42C534E"),
        Tier("feed", "定期餵食計畫", "開發者的每月固定支持", 299, true, "https://p.ecpay.com.tw/EC1637D"),
        Tier("meal", "每月一餐好料", "支撐推播與資料服務", 500, false, "https://p.ecpay.com.tw/95D8E8E"),
        Tier("build", "每月持續開發", "讓新功能持續推出", 1000, false, "https://p.ecpay.com.tw/BB0A46A"),
        Tier("custom", "持續運行與開發", "金額由你決定", null, false, "https://p.ecpay.com.tw/0128D3C"),
    )
    val defaultTier: Tier get() = tiers.first()

    /** NT$100, NT$1,000, NT$299／月, or 自訂金額. */
    fun price(t: Tier): String = when {
        t.amount == null -> "自訂金額"
        t.monthly -> "NT\$${String.format(java.util.Locale.US, "%,d", t.amount)}／月"
        else -> "NT\$${String.format(java.util.Locale.US, "%,d", t.amount)}"
    }

    /** The amount alone, for the row (its 每月 label already says how often). */
    fun amountLabel(t: Tier): String = t.amount?.let { "NT\$${String.format(java.util.Locale.US, "%,d", it)}" } ?: "自訂金額"

    /** Kept for the settings card: the first tier's page. */
    val LINK: String get() = defaultTier.link

    private val hosts = setOf("p.ecpay.com.tw", "payment.ecpay.com.tw")

    /** Only https links on ECPay's own payment hosts are ever opened. */
    fun isAllowed(url: String): Boolean = runCatching {
        val u = URI(url.trim())
        u.scheme.equals("https", ignoreCase = true) && u.host?.lowercase() in hosts &&
            u.userInfo == null && (u.port == -1 || u.port == 443)
    }.getOrDefault(false)

    val ready: Boolean get() = tiers.all { isAllowed(it.link) }

    /** Opens the ECPay page in the browser. False if no valid link is set or nothing can open it. */
    fun open(ctx: Context, link: String = LINK): Boolean {
        if (!isAllowed(link)) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.trim()))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
    }
}
