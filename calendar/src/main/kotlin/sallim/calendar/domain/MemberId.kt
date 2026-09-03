package sallim.calendar.domain

import sallim.common.domain.Identifier
import java.util.UUID

/** `calendar` 컨텍스트 로컬 복제본 — household/chore의 MemberId를 참조하지 않는다(의도적 중복). */
class MemberId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): MemberId = MemberId(UUID.randomUUID())
    }
}
