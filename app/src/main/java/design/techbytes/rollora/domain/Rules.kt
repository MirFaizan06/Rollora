package design.techbytes.rollora.domain

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

enum class Attendance(val label: String) { P("Present"), A("Absent"), L("Leave") }
object Rules {
    val courseTypes = listOf("Major", "Minor", "MDC", "AEC", "Skill", "VAC 1", "VAC 2")
    fun text(value: String, label: String, max: Int = 100): String {
        val s = value.trim()
        require(s.isNotEmpty() && s.length <= max && s.none { it.isISOControl() }) { "$label must contain 1–$max readable characters." }
        return s
    }
    fun roll(value: String): String {
        val s = text(value, "Roll number", 40)
        require(s.matches(Regex("""[\p{L}\p{N}._/-]+"""))) { "Roll number: use letters, digits, dot, dash, slash or underscore." }
        return s
    }
    fun rollKey(value: String) = roll(value).uppercase(Locale.ROOT)
    fun date(value: String): LocalDate = try { LocalDate.parse(value) } catch (_: Exception) { error("Enter a valid date as YYYY-MM-DD.") }
    fun time(value: String): String {
        require(value.matches(Regex("[0-9]{2}:[0-9]{2}"))) { "Enter time as HH:mm (24-hour)." }
        return try { LocalTime.parse(value).toString() } catch (_: Exception) { error("Invalid time.") }
    }
    fun percentage(present: Int, absent: Int): Double? =
        if (present + absent == 0) null else present * 100.0 / (present + absent)
    fun eligible(day: String, joined: String, left: String?) = day >= joined && (left == null || day < left)
    fun complete(roster: Set<String>, marks: Map<String, String>) {
        require(roster.isNotEmpty()) { "Add students eligible for this date first." }
        require(marks.keys == roster && marks.values.all { it in listOf("P", "A", "L") }) { "Mark every student Present, Absent or Leave." }
    }
    fun validateRange(from: String, to: String) {
        require(!date(to).isBefore(date(from))) { "End date must follow start date." }
        require(java.time.temporal.ChronoUnit.DAYS.between(date(from), date(to)) <= 730) { "Choose a range of at most two years." }
    }
}
