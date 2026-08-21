package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ChoreInstanceJpaRepository : JpaRepository<ChoreInstanceEntity, String>
