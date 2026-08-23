package sallim.chore.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import java.time.LocalDate

@Service
class ChoreInstanceService(private val choreInstanceRepository: ChoreInstanceRepository) {
    @Transactional(readOnly = true)
    fun listByDate(date: LocalDate): List<ChoreInstance> =
        choreInstanceRepository.findAll().filter { it.scheduledDate == date }

    @Transactional
    fun complete(id: ChoreInstanceId, completedBy: MemberId): ChoreInstance {
        val instance = choreInstanceRepository.findById(id) ?: throw NotFoundException("chore instance not found: $id")
        instance.complete(completedBy)
        return choreInstanceRepository.save(instance)
    }
}
