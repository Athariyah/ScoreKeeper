package update

/** Сравнение версий вида «1.2.0», «v1.10», «1.3.0-beta» (суффикс после числовой части игнорируется). */
object Versions {
    private val numeric = Regex("""^[vV]?(\d+(?:\.\d+)*)""")

    fun parse(raw: String): List<Int>? {
        val match = numeric.find(raw.trim()) ?: return null
        val parts = ArrayList<Int>()
        for (part in match.groupValues[1].split('.')) {
            parts.add(part.toIntOrNull() ?: return null)
        }
        return parts
    }

    /** true, если [remote] строго новее [local]. Нераспознанная версия никогда не считается новой. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote) ?: return false
        val l = parse(local) ?: return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
