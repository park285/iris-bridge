package party.qwer.iris

/** bridge 권한이 노출하는 위협 분류. */
enum class BridgeThreat(
    val id: String,
    val description: String,
) {
    UNSOLICITED_MESSAGE(
        "unsolicited-message",
        "탈취된 토큰이나 결함이 있는 호출자가 임의의 방에 메시지를 보낼 수 있다.",
    ),
    CONTENT_SPOOFING(
        "content-spoofing",
        "마크다운/이미지 첨부가 신뢰된 발신자를 사칭하거나 오해를 유발하는 내용을 표시할 수 있다.",
    ),
    PATH_TRAVERSAL(
        "path-traversal",
        "이미지 경로 입력이 의도한 디렉토리 밖의 파일을 읽도록 유도될 수 있다.",
    ),
    NAVIGATION_HIJACK(
        "navigation-hijack",
        "방 열기 조작이 KakaoTalk UI를 공격자가 고른 방으로 강제 전환시킬 수 있다.",
    ),
    MEMBER_DATA_EXPOSURE(
        "member-data-exposure",
        "멤버 스냅샷이 방 참여자 식별자/닉네임을 호출자에게 노출한다.",
    ),
    ROOM_METADATA_EXPOSURE(
        "room-metadata-exposure",
        "방 조회가 방 식별·상태 메타데이터를 호출자에게 노출한다.",
    ),
    HEALTH_INFO_DISCLOSURE(
        "health-info-disclosure",
        "health 응답이 hook 설치 상태·내부 진단 수치를 인증 없이 드러낸다.",
    ),
    RESOURCE_EXHAUSTION(
        "resource-exhaustion",
        "무제한 요청이 in-process sender나 UDS 세션 자원을 고갈시킬 수 있다.",
    ),
}
