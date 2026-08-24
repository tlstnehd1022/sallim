package sallim.chore.api

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sallim.chore.application.ChoreDefinitionService
import sallim.chore.application.ChoreInstanceService
import java.time.LocalDate
import java.time.ZoneId

@Component
class ChoreInstanceScheduler(
    private val choreDefinitionService: ChoreDefinitionService,
    private val choreInstanceService: ChoreInstanceService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ChoreInstanceScheduler::class.java)
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun generateDueInstances() {
        val created = choreInstanceService.generateDueInstances(
            choreDefinitionService.list(), LocalDate.now(ZoneId.of("Asia/Seoul"))
        )
        logger.info("Generated {} chore instances", created.size)
    }
}
