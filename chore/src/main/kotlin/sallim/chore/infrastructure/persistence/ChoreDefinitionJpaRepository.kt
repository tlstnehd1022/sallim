package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ChoreDefinitionJpaRepository : JpaRepository<ChoreDefinitionEntity, String>
