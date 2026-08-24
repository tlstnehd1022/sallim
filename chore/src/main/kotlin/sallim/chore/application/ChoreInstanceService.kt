package sallim.chore.application

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import java.time.LocalDate

@Service
class ChoreInstanceService(
    private val choreInstanceRepository: ChoreInstanceRepository,
    private val eventPublisher: ApplicationEventPublisher = ApplicationEventPublisher { }
) {
    @Transactional(readOnly = true)
    fun listByDate(date: LocalDate): List<ChoreInstance> =
        choreInstanceRepository.findAll().filter { it.scheduledDate == date }

    @Transactional
    fun complete(id: ChoreInstanceId, completedBy: MemberId): ChoreInstance {
        val instance = choreInstanceRepository.findById(id) ?: throw NotFoundException("chore instance not found: $id")
        instance.complete(completedBy)
        val saved = choreInstanceRepository.save(instance)
        instance.domainEvents.forEach { eventPublisher.publishEvent(it) }
        instance.clearEvents()
        return saved
    }

    @Transactional
    fun generateDueInstances(definitions: List<ChoreDefinition>, today: LocalDate): List<ChoreInstance> {
        val allInstances = choreInstanceRepository.findAll()
        return definitions.flatMap { definition ->
            val latest = allInstances
                .filter { it.choreDefinitionId == definition.id }
                .maxByOrNull { it.scheduledDate } ?: return@flatMap emptyList()

            val created = mutableListOf<ChoreInstance>()
            var next = definition.recurrence.nextOccurrence(latest.scheduledDate)
            while (!next.isAfter(today)) {
                created += choreInstanceRepository.save(ChoreInstance.schedule(definition.id, next))
                next = definition.recurrence.nextOccurrence(next)
            }
            created
        }
    }
}
