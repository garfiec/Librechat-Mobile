package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.acl.toAclError
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.model.acl.AclError
import com.garfiec.librechat.core.model.acl.PermissionUpdateFailure
import com.garfiec.librechat.core.network.api.PermissionsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The audited permission update fails closed with retryable codes (v0.8.9-rc1, `permissionErrorCode`). */
class PermissionUpdateFailureTest {

    private val api = mockk<PermissionsApi>()
    private val repository = PermissionsRepositoryImpl(api)
    private val body = UpdateResourcePermissionsRequest()

    private fun refused(status: Int, code: String) = ApiException(
        statusCode = status,
        message = "Failed to update permissions",
        body = """{"error":"Failed to update permissions","details":"x","code":"$code"}""",
    )

    private suspend fun failureFor(e: Exception): Result.Error {
        coEvery { api.updateResourcePermissions("agent", "a1", body) } throws e
        return repository.updateResourcePermissions("agent", "a1", body) as Result.Error
    }

    @Test
    fun `each approved code is read whatever the status`() = runTest {
        assertThat(failureFor(refused(409, "RESOURCE_PERMISSIONS_BUSY")).permissionUpdateFailure())
            .isEqualTo(PermissionUpdateFailure.BUSY)
        assertThat(failureFor(refused(503, "RESOURCE_PERMISSIONS_UNAVAILABLE")).permissionUpdateFailure())
            .isEqualTo(PermissionUpdateFailure.UNAVAILABLE)
        assertThat(failureFor(refused(500, "PERMISSION_AUDIT_UNAVAILABLE")).permissionUpdateFailure())
            .isEqualTo(PermissionUpdateFailure.AUDIT_UNAVAILABLE)
    }

    @Test
    fun `an unapproved code or no code is an ordinary failure`() = runTest {
        assertThat(failureFor(refused(500, "EOTHER")).permissionUpdateFailure()).isNull()
        val plain =
            ApiException(statusCode = 400, message = "Bad", body = """{"error":"Failed to update permissions"}""")
        assertThat(failureFor(plain).permissionUpdateFailure()).isNull()
    }

    @Test
    fun `the shared access editor words each refusal`() = runTest {
        assertThat(failureFor(refused(409, "RESOURCE_PERMISSIONS_BUSY")).toAclError()).isEqualTo(AclError.BUSY)
        assertThat(failureFor(refused(503, "RESOURCE_PERMISSIONS_UNAVAILABLE")).toAclError()).isEqualTo(AclError.UNAVAILABLE)
        assertThat(failureFor(refused(500, "PERMISSION_AUDIT_UNAVAILABLE")).toAclError()).isEqualTo(AclError.AUDIT_UNAVAILABLE)
    }
}
