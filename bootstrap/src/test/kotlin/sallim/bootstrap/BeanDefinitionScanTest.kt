package sallim.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class BeanDefinitionScanTest {

    @Test
    fun `sallim 패키지 전체를 스캔해도 빈 이름이 충돌하지 않는다`() {
        // scan()만 호출하고 refresh()는 호출하지 않는다 — 빈 정의 등록/이름 충돌 검증에는
        // DB 연결이 필요한 빈 인스턴스화(refresh)가 필요 없다. Docker 없이도 실행 가능.
        AnnotationConfigApplicationContext().apply {
            scan("sallim")
        }
    }
}
