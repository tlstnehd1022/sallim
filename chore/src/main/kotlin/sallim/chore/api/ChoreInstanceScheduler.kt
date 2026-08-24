package sallim.chore.api

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sallim.chore.application.ChoreDefinitionService
import sallim.chore.application.ChoreInstanceService
import java.time.LocalDate

@Component
class ChoreInstanceScheduler(
    private val choreDefinitionService: ChoreDefinitionService,
    private val choreInstanceService: ChoreInstanceService
) {
    @Scheduled(cron = "0 0 0 * * *")
    fun generateDueInstances() {
        choreInstanceService.generateDueInstances(choreDefinitionService.list(), LocalDate.now())
    }
}
