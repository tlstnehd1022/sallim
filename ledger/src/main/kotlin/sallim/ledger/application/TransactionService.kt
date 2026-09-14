package sallim.ledger.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import sallim.ledger.domain.TransactionRepository
import java.time.LocalDateTime

@Service
class TransactionService(private val repository: TransactionRepository) {

    @Transactional(readOnly = true)
    fun list(from: LocalDateTime, to: LocalDateTime, memberId: MemberId?, category: String?): List<Transaction> {
        require(!from.isAfter(to)) { "from must not be after to: $from > $to" }
        require(to.year < 9999) { "to must be a reasonable calendar year: $to" }
        return repository.findByOccurredAtBetween(from, to)
            .filter { memberId == null || it.memberId == memberId }
            .filter { category == null || it.category == category }
    }

    @Transactional
    fun create(memberId: MemberId, amount: Long, category: String, memo: String?, occurredAt: LocalDateTime): Transaction =
        repository.save(Transaction(TransactionId.generate(), memberId, amount, category, memo, occurredAt))

    @Transactional
    fun update(
        id: TransactionId, memberId: MemberId, amount: Long, category: String, memo: String?, occurredAt: LocalDateTime
    ): Transaction {
        repository.findById(id) ?: throw NotFoundException("transaction not found: $id")
        return repository.save(Transaction(id, memberId, amount, category, memo, occurredAt))
    }

    @Transactional
    fun delete(id: TransactionId) {
        repository.findById(id) ?: throw NotFoundException("transaction not found: $id")
        repository.deleteById(id)
    }
}
