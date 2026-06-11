package party.qwer.iris.imagebridge.runtime.room.memberextract

import kotlin.test.Test
import kotlin.test.assertEquals

class MemberExtractionDiagnosticsTest {
    @Test
    fun `diagnostic samples redact raw primitive values`() {
        assertEquals(
            "message=<string:19 chars>",
            redactedDiagnosticSample("message", PrimitiveValue.StringValue("secret@example.test")),
        )
        assertEquals(
            "userId=<long>",
            redactedDiagnosticSample("userId", PrimitiveValue.LongValue(90_002L)),
        )
    }
}
