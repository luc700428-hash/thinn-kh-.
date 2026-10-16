package io.atmos.weather.update


/** Delta-only release notes. Do not present a cumulative history as new features.
 * A release without a dedicated incremental block is reported as undocumented.
 */
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
            .filter(String::isNotBlank).take(8).toList()
    }

    /** The signed Release manifest carries notes for the *latest* release.
     * Do not fabricate intermediate-version changes or call rate-limited APIs.
     */
    suspend fun describe(installed: String, latest: ReleaseInfo): String {
        val result = mutableListOf("$installed → ${latest.version}")
        val details = changes(latest.notes)
        if (!details.isNullOrEmpty()) {
            result.add("本次最新版本的變更：")
            result.addAll(details.map { "- $it" })
        } else {
            result.add("最新版本未提供獨立的更新差異清單。")
        }
        result.add("跨多個版本的完整變更請參閱 GitHub Releases。")
        return result.joinToString("\n")
    }
}
