package party.qwer.iris.imagebridge.runtime.memberextract

import party.qwer.iris.ImageBridgeProtocol

internal class MemberExtractionPlanMapper {
    fun toInternalPlan(plan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan): ExtractionPlan =
        ExtractionPlan(
            containerPath = plan.containerPath,
            sourceClassName = plan.sourceClassName,
            userIdPath = plan.userIdPath,
            nicknamePath = plan.nicknamePath,
            rolePath = plan.rolePath,
            profileImagePath = plan.profileImagePath,
        )

    fun toProtocolPlan(plan: ExtractionPlan): ImageBridgeProtocol.ChatRoomMemberExtractionPlan =
        ImageBridgeProtocol.ChatRoomMemberExtractionPlan(
            containerPath = plan.containerPath,
            sourceClassName = plan.sourceClassName,
            userIdPath = plan.userIdPath,
            nicknamePath = plan.nicknamePath,
            rolePath = plan.rolePath,
            profileImagePath = plan.profileImagePath,
            fingerprint = plan.fingerprint(),
        )
}
