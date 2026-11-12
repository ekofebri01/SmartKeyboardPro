package com.smartkeyboard.pro

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the rules that decide whether a downloaded APK may be handed to the installer:
 * same package name, strictly newer versionCode and an identical signing certificate.
 */
class ApkUpdateVerificationTest {

    private val appPackage = "com.smartkeyboard.pro"
    private val productionCert = "3f8a…production"
    private val attackerCert = "91bc…attacker"

    private fun evaluate(
        archivePackage: String? = appPackage,
        installedVersionCode: Long = 146,
        archiveVersionCode: Long = 147,
        installedSigners: Set<String> = setOf(productionCert),
        archiveSigners: Set<String> = setOf(productionCert)
    ): String? = UpdateChecker.evaluateApkUpdate(
        installedPackageName = appPackage,
        archivePackageName = archivePackage,
        installedVersionCode = installedVersionCode,
        archiveVersionCode = archiveVersionCode,
        installedSigners = installedSigners,
        archiveSigners = archiveSigners
    )

    @Test
    fun `matching package version and certificate is accepted`() {
        assertNull(evaluate())
    }

    @Test
    fun `latest version check is not accepted as newer`() {
        assertTrue(evaluate(archiveVersionCode = 146)?.contains("tidak lebih baru") == true)
        assertTrue(evaluate(archiveVersionCode = 145)?.contains("tidak lebih baru") == true)
    }

    @Test
    fun `apk from another package is rejected`() {
        assertTrue(evaluate(archivePackage = "com.example.malware")?.contains("paket lain") == true)
    }

    @Test
    fun `unsigned apk is rejected`() {
        assertTrue(evaluate(archiveSigners = emptySet())?.contains("tidak memiliki tanda tangan") == true)
    }

    @Test
    fun `unreadable installed signature is rejected`() {
        assertTrue(evaluate(installedSigners = emptySet())?.contains("tidak dapat dibaca") == true)
    }

    @Test
    fun `apk signed with a different certificate is rejected`() {
        assertTrue(evaluate(archiveSigners = setOf(attackerCert))?.contains("tidak cocok") == true)
    }

    @Test
    fun `apk carrying the rotated plus current certificate is accepted`() {
        assertNull(evaluate(archiveSigners = setOf(productionCert, attackerCert)))
    }

    @Test
    fun `package identity is checked before the signature`() {
        val error = evaluate(
            archivePackage = "com.example.malware",
            archiveVersionCode = 1,
            archiveSigners = setOf(attackerCert)
        )
        assertTrue(error?.contains("paket lain") == true)
    }
}
