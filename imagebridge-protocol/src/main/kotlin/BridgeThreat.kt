package party.qwer.iris

import party.qwer.iris.generated.GeneratedBridgeProtocolContract

/** bridge 권한이 노출하는 위협 분류. */
enum class BridgeThreat(
    val id: String,
    val description: String,
) {
    UNSOLICITED_MESSAGE(
        GeneratedBridgeProtocolContract.THREAT_UNSOLICITED_MESSAGE,
        "탈취된 토큰이나 결함이 있는 호출자가 임의의 방에 메시지를 보낼 수 있다.",
    ),
    CONTENT_SPOOFING(
        GeneratedBridgeProtocolContract.THREAT_CONTENT_SPOOFING,
        "마크다운/이미지 첨부가 신뢰된 발신자를 사칭하거나 오해를 유발하는 내용을 표시할 수 있다.",
    ),
    PATH_TRAVERSAL(
        GeneratedBridgeProtocolContract.THREAT_PATH_TRAVERSAL,
        "이미지 경로 입력이 의도한 디렉토리 밖의 파일을 읽도록 유도될 수 있다.",
    ),
    NAVIGATION_HIJACK(
        GeneratedBridgeProtocolContract.THREAT_NAVIGATION_HIJACK,
        "방 열기 조작이 KakaoTalk UI를 공격자가 고른 방으로 강제 전환시킬 수 있다.",
    ),
    NOTIFICATION_ACTION(
        GeneratedBridgeProtocolContract.THREAT_NOTIFICATION_ACTION,
        "알림 action 조작이 KakaoTalk 읽음 상태 같은 사용자-visible 상태를 변경할 수 있다.",
    ),
    MEMBER_DATA_EXPOSURE(
        GeneratedBridgeProtocolContract.THREAT_MEMBER_DATA_EXPOSURE,
        "멤버 스냅샷이 방 참여자 식별자/닉네임을 호출자에게 노출한다.",
    ),
    ROOM_METADATA_EXPOSURE(
        GeneratedBridgeProtocolContract.THREAT_ROOM_METADATA_EXPOSURE,
        "방 조회가 방 식별·상태 메타데이터를 호출자에게 노출한다.",
    ),
    HEALTH_INFO_DISCLOSURE(
        GeneratedBridgeProtocolContract.THREAT_HEALTH_INFO_DISCLOSURE,
        "health 응답이 hook 설치 상태·내부 진단 수치를 인증 없이 드러낸다.",
    ),
    RESOURCE_EXHAUSTION(
        GeneratedBridgeProtocolContract.THREAT_RESOURCE_EXHAUSTION,
        "무제한 요청이 in-process sender나 UDS 세션 자원을 고갈시킬 수 있다.",
    ),
}
