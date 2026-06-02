package party.qwer.iris

/*
 * 권한(capability) 단위로 본 bridge 위협 모델 schema.
 *
 * 이 파일은 LSPosed bridge가 KakaoTalk 프로세스 안에서 수행하는 action을 권한 단위로
 * 분해하고, 각 권한이 무엇을 할 수 있으며 어떤 위협을 노출하는지 정적으로 기술한다.
 * 런타임 health 보고용 ImageBridgeCapability/ImageBridgeCapabilities와 달리 wire에
 * 실리지 않는 설계 모델이며, 기존 protocol/health wire format을 바꾸지 않는 additive schema다.
 */

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

/**
 * bridge가 행사하는 권한 단위.
 *
 * @property id 안정적인 권한 식별자.
 * @property description 권한이 부여하는 능력.
 * @property hasSideEffect KakaoTalk 상태(메시지 송신·화면 전환)를 변경하면 true, 읽기 전용이면 false.
 * @property requiresAuthToken 호출 시 bridge 인증 토큰을 요구하면 true.
 * @property threats 이 권한이 노출하는 위협.
 */
enum class BridgeCapability(
    val id: String,
    val description: String,
    val hasSideEffect: Boolean,
    val requiresAuthToken: Boolean,
    val threats: Set<BridgeThreat>,
) {
    SEND_IMAGE_REPLY(
        "send-image-reply",
        "지정한 방에 이미지 첨부 메시지를 송신한다.",
        hasSideEffect = true,
        requiresAuthToken = true,
        threats = setOf(BridgeThreat.UNSOLICITED_MESSAGE, BridgeThreat.PATH_TRAVERSAL),
    ),
    SEND_TEXT_REPLY(
        "send-text-reply",
        "지정한 방에 평문 텍스트 메시지를 송신한다.",
        hasSideEffect = true,
        requiresAuthToken = true,
        threats = setOf(BridgeThreat.UNSOLICITED_MESSAGE),
    ),
    SEND_MARKDOWN_REPLY(
        "send-markdown-reply",
        "지정한 방에 마크다운 렌더링 메시지를 송신한다.",
        hasSideEffect = true,
        requiresAuthToken = true,
        threats = setOf(BridgeThreat.UNSOLICITED_MESSAGE, BridgeThreat.CONTENT_SPOOFING),
    ),
    OPEN_CHATROOM(
        "open-chatroom",
        "KakaoTalk UI를 지정한 방으로 전환한다.",
        hasSideEffect = true,
        requiresAuthToken = true,
        threats = setOf(BridgeThreat.NAVIGATION_HIJACK),
    ),
    INSPECT_CHATROOM(
        "inspect-chatroom",
        "방 식별·상태 메타데이터를 조회한다.",
        hasSideEffect = false,
        requiresAuthToken = true,
        threats = setOf(BridgeThreat.ROOM_METADATA_EXPOSURE),
    ),
    SNAPSHOT_CHATROOM_MEMBERS(
        "snapshot-chatroom-members",
        "방 멤버 스냅샷(식별자/닉네임)을 조회한다.",
        hasSideEffect = false,
        requiresAuthToken = true,
        threats = setOf(BridgeThreat.MEMBER_DATA_EXPOSURE),
    ),
    REPORT_HEALTH(
        "report-health",
        "bridge readiness와 hook 진단을 보고한다.",
        hasSideEffect = false,
        requiresAuthToken = false,
        threats = setOf(BridgeThreat.HEALTH_INFO_DISCLOSURE, BridgeThreat.RESOURCE_EXHAUSTION),
    ),
}

/**
 * wire protocol action과 그 action이 요구하는 [BridgeCapability]의 매핑.
 *
 * [wireName]은 [ImageBridgeProtocol]의 `ACTION_*` 상수와 일치한다.
 */
enum class BridgeAction(
    val wireName: String,
    val requiredCapability: BridgeCapability,
) {
    SEND_IMAGE(ImageBridgeProtocol.ACTION_SEND_IMAGE, BridgeCapability.SEND_IMAGE_REPLY),
    SEND_TEXT(ImageBridgeProtocol.ACTION_SEND_TEXT, BridgeCapability.SEND_TEXT_REPLY),
    SEND_MARKDOWN(ImageBridgeProtocol.ACTION_SEND_MARKDOWN, BridgeCapability.SEND_MARKDOWN_REPLY),
    OPEN_CHATROOM(ImageBridgeProtocol.ACTION_OPEN_CHATROOM, BridgeCapability.OPEN_CHATROOM),
    INSPECT_CHATROOM(ImageBridgeProtocol.ACTION_INSPECT_CHATROOM, BridgeCapability.INSPECT_CHATROOM),
    SNAPSHOT_CHATROOM_MEMBERS(
        ImageBridgeProtocol.ACTION_SNAPSHOT_CHATROOM_MEMBERS,
        BridgeCapability.SNAPSHOT_CHATROOM_MEMBERS,
    ),
    HEALTH(ImageBridgeProtocol.ACTION_HEALTH, BridgeCapability.REPORT_HEALTH),
    ;

    companion object {
        private val BY_WIRE_NAME: Map<String, BridgeAction> = entries.associateBy { it.wireName }

        /** wire action 문자열을 [BridgeAction]으로 해석한다. 알 수 없으면 null. */
        fun fromWireName(wireName: String): BridgeAction? = BY_WIRE_NAME[wireName]
    }
}

/** capability matrix의 한 행: action이 요구하는 권한과 그 권한이 노출하는 위협. */
data class BridgeCapabilityMatrixRow(
    val action: BridgeAction,
    val capability: BridgeCapability,
    val threats: Set<BridgeThreat>,
)

/** action 단위 capability matrix. threat model 문서와 같은 표를 코드로 표현한다. */
object BridgeCapabilityMatrix {
    val rows: List<BridgeCapabilityMatrixRow> =
        BridgeAction.entries.map { action ->
            BridgeCapabilityMatrixRow(
                action = action,
                capability = action.requiredCapability,
                threats = action.requiredCapability.threats,
            )
        }
}
