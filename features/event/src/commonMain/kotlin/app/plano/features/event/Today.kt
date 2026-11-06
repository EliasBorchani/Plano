package app.plano.features.event

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

fun interface Today {
    fun date(): LocalDate
}

object SystemToday : Today {
    override fun date(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
