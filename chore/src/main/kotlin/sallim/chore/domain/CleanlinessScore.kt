package sallim.chore.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CleanlinessScore {
    private const val DELAY_COEFFICIENT = 0.15

    fun compute(instances: List<ChoreInstance>, referenceDate: LocalDate): Double =
        instances
            .filter { !it.completed && !it.scheduledDate.isAfter(referenceDate) }
            .sumOf { instance ->
                val overdueDays = ChronoUnit.DAYS.between(instance.scheduledDate, referenceDate)
                1 + overdueDays * DELAY_COEFFICIENT
            }
}
