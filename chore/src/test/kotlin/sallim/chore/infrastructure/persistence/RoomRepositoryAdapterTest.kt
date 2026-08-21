package sallim.chore.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import sallim.chore.domain.FloorPlan
import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RoomRepositoryAdapter::class)
class RoomRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: RoomRepositoryAdapter

    @Test
    fun `저장한 방과 배치를 다시 읽으면 값이 같다`() {
        val room = Room(RoomId.generate(), "거실")
        val placement = RoomPlacement(room.id, x = 26, y = 38, w = 74, h = 50, z = 1)

        adapter.save(room, placement)

        val found = adapter.findAll()
        found shouldHaveSize 1
        val (foundRoom, foundPlacement) = found[0]
        foundRoom.id shouldBe room.id
        foundRoom.name shouldBe room.name
        foundPlacement shouldBe placement
    }

    @Test
    fun `여러 방을 저장하면 findAll 결과로 FloorPlan을 조립할 수 있다`() {
        val living = Room(RoomId.generate(), "거실")
        val livingPlacement = RoomPlacement(living.id, x = 26, y = 38, w = 74, h = 50, z = 1)
        val kitchen = Room(RoomId.generate(), "주방")
        val kitchenPlacement = RoomPlacement(kitchen.id, x = 26, y = 8, w = 36, h = 30, z = 2)

        adapter.save(living, livingPlacement)
        adapter.save(kitchen, kitchenPlacement)

        val placements = adapter.findAll().map { it.second }
        val floorPlan = FloorPlan.of(placements)
        floorPlan.placements shouldHaveSize 2
    }
}
