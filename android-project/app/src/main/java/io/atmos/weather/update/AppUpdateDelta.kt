package io.atmos.weather.update


/** Actual notes from the verified target release; never invent an OS version or changelog. */
object AppUpdateDelta {
    const val MARKER = "<!-- THINN_INCREMENTAL_CHANGES -->"

    internal fun version(value: String): List<Int> = value.trim().removePrefix("v")
        .split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    internal fun compare(a: String, b: String): Int {
        val left=version(a); val right=version(b)
        for (index in 0 until maxOf(left.size,right.size)) {
            val difference=left.getOrElse(index){0}.compareTo(right.getOrElse(index){0})
            if (difference!=0) return difference
        }
        return 0
    }

    internal fun changes(notes: String): List<String>? {
        val start=notes.indexOf(MARKER)
        if(start<0) return null
        val block=notes.substring(start+MARKER.length)
            .substringBefore("<!-- END_THINN_INCREMENTAL_CHANGES -->")
        return block.lineSequence().map { it.trim() }
            .filter { it.startsWith("- ") }.map { it.removePrefix("- ").replace("**", "") }
            .filter(String::isNotBlank).toList()
    }

    internal fun displayNotes(notes:String):String {
        val start=notes.indexOf(MARKER)
        val selected=if(start>=0) notes.substring(start+MARKER.length)
            .substringBefore("<!-- END_THINN_INCREMENTAL_CHANGES -->") else notes
        return selected.replace(Regex("<!--[^>]*-->",RegexOption.DOT_MATCHES_ALL),"").trim()
    }

    /** Only the target release's own content is shown; previous releases aren't inferred. */
    suspend fun describe(installed:String,latest:ReleaseInfo):String {
        val details=displayNotes(latest.notes)
        val heading=if(compare(latest.version,installed)>0) "$installed → ${latest.version}" else "已安裝 ${latest.version}"
        return heading+"\n\n"+
            if(details.isNotBlank()) details else "此版本尚未提供更新說明，請查看發行頁面。"
    }
}
