package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface RoomJpaRepository : JpaRepository<RoomEntity, String>
