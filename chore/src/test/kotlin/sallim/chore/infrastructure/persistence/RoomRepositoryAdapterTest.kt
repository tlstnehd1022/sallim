package sallim.chore.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
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

    @Autowired
    lateinit var em: TestEntityManager

    @Test
    fun `저장한 방과 배치를 다시 읽으면 값이 같다`() {
        val room = Room(RoomId.generate(), "거실")
        val placement = RoomPlacement(room.id, x = 26, y = 38, w = 74, h = 50, z = 1)

        adapter.save(room, placement)
        em.flush()
        em.clear()

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
        em.flush()
        em.clear()

        val placements = adapter.findAll().map { it.second }
        val floorPlan = FloorPlan.of(placements)
        floorPlan.placements shouldHaveSize 2
    }

    @Test
    fun `저장한 방을 id로 조회하면 값이 같다`() {
        val room = Room(RoomId.generate(), "거실")
        val placement = RoomPlacement(room.id, x = 26, y = 38, w = 74, h = 50, z = 1)
        adapter.save(room, placement)
        em.flush()
        em.clear()

        val found = adapter.findById(room.id)

        found.shouldNotBeNull()
        found.first.id shouldBe room.id
        found.second shouldBe placement
    }

    @Test
    fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
        adapter.findById(RoomId.generate()) shouldBe null
    }

    @Test
    fun `삭제하면 findAll에서 사라진다`() {
        val room = Room(RoomId.generate(), "거실")
        val placement = RoomPlacement(room.id, x = 26, y = 38, w = 74, h = 50, z = 1)
        adapter.save(room, placement)
        em.flush()
        em.clear()

        adapter.deleteById(room.id)
        em.flush()
        em.clear()

        adapter.findAll() shouldHaveSize 0
    }
}
