package app.plano.domain.groups

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupsTest {
    @Test
    fun names_are_trimmed_and_checked() {
        assertEquals("Les copains du lycée", GroupName.normalize("  Les  copains\tdu lycée "))
        assertEquals(GroupError.NAME_MISSING, GroupName.validate("   "))
        assertEquals(GroupError.NAME_TOO_LONG, GroupName.validate("a".repeat(GroupName.MAX + 1)))
        assertNull(GroupName.validate("Escalade"))
    }

    @Test
    fun invite_limits_default_to_the_prd_values_and_stay_in_range() {
        assertEquals(InviteLimits(10, 7), InviteLimits())
        assertNull(InviteLimits().validate())
        assertEquals(GroupError.MAX_USES_OUT_OF_RANGE, InviteLimits(maxUses = 0).validate())
        assertEquals(GroupError.MAX_USES_OUT_OF_RANGE, InviteLimits(maxUses = 101).validate())
        assertEquals(GroupError.EXPIRY_OUT_OF_RANGE, InviteLimits(expiresInDays = 31).validate())
    }
}
