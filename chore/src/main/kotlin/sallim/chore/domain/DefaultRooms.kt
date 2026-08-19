package sallim.chore.domain

object DefaultRooms {
    val ME: MemberId = MemberId.generate()
    val PARTNER: MemberId = MemberId.generate()

    private fun chore(
        roomId: RoomId,
        label: String,
        assignee: MemberId,
        recurrence: RecurrencePolicy,
        steps: List<String>,
        videoQuery: String
    ) = ChoreDefinition(
        id = ChoreDefinitionId.generate(),
        roomId = roomId,
        label = label,
        assigneeId = assignee,
        recurrence = recurrence,
        howToSteps = steps,
        videoQuery = videoQuery
    )

    private val kveranda = Room(RoomId.generate(), "주방 베란다")
    private val kid = Room(RoomId.generate(), "아이방")
    private val kitchen = Room(RoomId.generate(), "주방")
    private val myroom = Room(RoomId.generate(), "컴퓨터방")
    private val bath = Room(RoomId.generate(), "공용욕실")
    private val living = Room(RoomId.generate(), "거실")
    private val entry = Room(RoomId.generate(), "현관")
    private val master = Room(RoomId.generate(), "안방")
    private val mbath = Room(RoomId.generate(), "안방욕실")
    private val lveranda = Room(RoomId.generate(), "거실 베란다")

    val rooms: List<Room> = listOf(kveranda, kid, kitchen, myroom, bath, living, entry, master, mbath, lveranda)

    val floorPlan: FloorPlan = FloorPlan.of(
        listOf(
            RoomPlacement(kveranda.id, x = 26, y = 0, w = 36, h = 8, z = 2),
            RoomPlacement(kid.id, x = 0, y = 8, w = 26, h = 30, z = 2),
            RoomPlacement(kitchen.id, x = 26, y = 8, w = 36, h = 30, z = 2),
            RoomPlacement(myroom.id, x = 62, y = 8, w = 38, h = 30, z = 2),
            RoomPlacement(bath.id, x = 0, y = 38, w = 26, h = 15, z = 2),
            RoomPlacement(living.id, x = 26, y = 38, w = 74, h = 50, z = 1),
            RoomPlacement(entry.id, x = 78, y = 38, w = 22, h = 14, z = 3),
            RoomPlacement(master.id, x = 0, y = 53, w = 26, h = 35, z = 1),
            RoomPlacement(mbath.id, x = 0, y = 53, w = 15, h = 13, z = 3),
            RoomPlacement(lveranda.id, x = 0, y = 88, w = 100, h = 12, z = 2)
        )
    )

    val choreDefinitions: List<ChoreDefinition> = listOf(
        chore(
            kveranda.id, "분리수거", PARTNER, WeeklyNTimes(2),
            listOf("플라스틱은 라벨 떼고 한 번 헹구기", "종이 상자는 테이프·송장 제거", "금·일 저녁에 한 번에 배출"),
            "분리수거 제대로 하는 법"
        ),
        chore(
            kid.id, "장난감 정리", PARTNER, Daily,
            listOf("바구니 3개로 종류별 분류", "아이와 5분 타이머 걸고 같이", "안 쓰는 건 상자에 격리 보관"),
            "아이방 장난감 수납"
        ),
        chore(
            kid.id, "침구 정리", PARTNER, Daily,
            listOf("이불은 발끝부터 반듯하게", "베개 털어 각 세우기", "주 1회 커버 세탁"),
            "침구 정리 습관"
        ),
        chore(
            kitchen.id, "설거지", ME, Daily,
            listOf("기름기 없는 그릇부터 먼저", "수세미는 주 1회 교체", "마지막에 싱크볼까지 닦기"),
            "설거지 순서 팁"
        ),
        chore(
            kitchen.id, "음식물 쓰레기", PARTNER, Daily,
            listOf("물기를 최대한 짜기", "신문지로 한 번 감싸기", "저녁 산책 나갈 때 같이"),
            "음식물 쓰레기 냄새 잡기"
        ),
        chore(
            kitchen.id, "가스레인지 닦기", ME, WeeklyNTimes(2),
            listOf("식은 뒤 베이킹소다 뿌리기", "5분 두고 마른 천으로 밀기", "틈새는 면봉으로 마무리"),
            "가스레인지 기름때 제거"
        ),
        chore(
            myroom.id, "책상 정리", ME, Daily,
            listOf("책상 위 물건을 전부 내리기", "자주 쓰는 것만 다시 올리기", "서류는 트레이 한 곳에"),
            "책상 정리 루틴"
        ),
        chore(
            myroom.id, "케이블 정리", ME, Monthly,
            listOf("전원 뽑고 전부 분리", "케이블 타이로 묶기", "라벨 붙여 구분"),
            "책상 밑 케이블 정리"
        ),
        chore(
            bath.id, "변기 청소", PARTNER, WeeklyNTimes(1),
            listOf("세정제 뿌리고 5분 방치", "솔로 테두리 안쪽부터", "물내림 버튼·손잡이 소독"),
            "변기 청소하는 법"
        ),
        chore(
            bath.id, "세면대 닦기", ME, WeeklyNTimes(2),
            listOf("배수구 머리카락 먼저 제거", "구연산수로 물때 녹이기", "수전은 마른 천으로 광내기"),
            "세면대 물때 제거"
        ),
        chore(
            living.id, "바닥 청소기", PARTNER, Daily,
            listOf("바닥 물건 먼저 치우기", "창가에서 문 쪽으로 밀기", "소파 밑은 3초 더"),
            "빠른 바닥 청소"
        ),
        chore(
            living.id, "소파 위 옷 치우기", ME, Daily,
            listOf("입을 옷 / 빨래 두 더미로", "빨래는 바로 세탁기로", "5분 넘기지 않기"),
            "옷 쌓임 방지 정리"
        ),
        chore(
            living.id, "테이블 닦기", ME, Daily,
            listOf("컵·리모컨 제자리로", "물티슈 후 마른 천으로", "컵받침 깔아두기"),
            "거실 테이블 정리"
        ),
        chore(
            entry.id, "신발 정리", PARTNER, WeeklyNTimes(1),
            listOf("오늘 신은 것만 밖에 두기", "나머지는 신발장 안으로", "현관 바닥 물걸레"),
            "현관 신발 수납"
        ),
        chore(
            master.id, "이불 정리", ME, Daily,
            listOf("일어나자마자 걷어 환기", "10분 뒤 반듯하게 펴기", "주 1회 이불 털기"),
            "침대 정리 30초"
        ),
        chore(
            master.id, "옷 정리", PARTNER, WeeklyNTimes(1),
            listOf("의자 위 옷부터 처리", "계절 아닌 옷은 상단칸", "안 입는 옷 3벌 비우기"),
            "옷장 정리 방법"
        ),
        chore(
            mbath.id, "거울 닦기", ME, WeeklyNTimes(1),
            listOf("물 스프레이 후 스퀴지", "마른 극세사로 마무리", "세면대 튄 자국까지"),
            "거울 얼룩 없이 닦기"
        ),
        chore(
            lveranda.id, "빨래 개기", ME, Daily,
            listOf("드라마 한 편 = 바구니 하나", "옷장 칸별로 쌓기", "갠 즉시 넣기"),
            "빨래 빨리 개는 법"
        )
    )
}
