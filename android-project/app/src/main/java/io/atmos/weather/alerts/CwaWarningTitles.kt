package io.atmos.weather.alerts

import java.net.URI

/** CWA's shared RSS also publishes earthquake reports; those are not weather warning areas. */
internal object CwaWarningTitles {
    fun display(title:String)=title.substringAfter("發布",title).trim()
    fun earthquakeReport(title:String,url:String?=null):Boolean {
        val path=runCatching{URI(url.orEmpty()).path.orEmpty()}.getOrDefault("")
        return "/E/EQ/" in path || ("地震" in title && "海嘯" !in title)
    }

    fun weatherNotice(title:String,url:String):Boolean = !earthquakeReport(title,url) &&
        (runCatching{URI(url).path.orEmpty().contains("/P/Warning/")}.getOrDefault(false) ||
            listOf("颱風","海嘯","豪雨","大雨","強風","低溫","高溫","長浪","雷雨","濃霧").any{it in title})

    private fun product(title:String):String? = listOf("陸上強風","強風","低溫","高溫","長浪","雷雨","濃霧","颱風","海嘯")
        .firstOrNull{it in title}?.let{if(it=="陸上強風")"強風" else it}
        ?:if("豪雨" in title||"大雨" in title)"降雨" else null

    /** Headline timestamps and “發布” do not change the product being selected. */
    fun matches(first:String,second:String):Boolean {
        if(first.isBlank()||second.isBlank()||earthquakeReport(first)||earthquakeReport(second))return false
        val a=product(first);val b=product(second)
        return if(a!=null&&b!=null)a==b else first.contains(second)||second.contains(first)
    }
}
