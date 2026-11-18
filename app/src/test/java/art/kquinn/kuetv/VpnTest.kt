package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Reading OpenVPN files with the engine built into KueTV, and keeping the VPN details. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VpnTest {
    /** Like a free VPN company's file: a server in Dublin, a login, and the company's certificate inline. */
    private val file = """
        client
        dev tun
        proto tcp
        remote ie1.example-vpn.com 1194
        resolv-retry infinite
        nobind
        persist-key
        persist-tun
        auth-user-pass
        cipher AES-256-CBC
        verb 3
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIBszCCAVmgAwIBAgIUQ2VydGlmaWNhdGVGb3JUZXN0aW5nMAoGCCqGSM49BAMC
        -----END CERTIFICATE-----
        </ca>
    """.trimIndent()

    @Test fun anOpenVpnFileIsRead() {
        val reading = Vpn.read(file)
        assertEquals("ie1.example-vpn.com", reading.server)
        assertTrue("the file asks for a username and password", reading.wantsLogin)
    }

    @Test fun aPageThatIsntAnOpenVpnFileIsRefused() {
        for (bad in listOf("<html><body>Please sign in</body></html>", "", "client\ndev tun\n")) {
            try {
                Vpn.read(bad)
                fail("should have been refused: " + bad.take(30))
            } catch (e: Vpn.VpnProblem) {
                assertTrue(e.message.orEmpty(), e.message.orEmpty().isNotBlank())
            }
        }
    }

    @Test fun theFileIsTakenOutOfAZip() {
        val zipped = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt")); zip.write("hello".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("ie1/IE1.ovpn")); zip.write(file.toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        assertEquals(file, Vpn.fromFile(zipped))
        assertEquals(file, Vpn.fromFile(file.toByteArray()))
    }

    @Test fun detailsAreKeptAndForgotten() {
        val app = RuntimeEnvironment.getApplication()
        Vpn.keep(app, file, " kevin-vpn ", "secret")
        val kept = Vpn.details(app)
        assertEquals("kevin-vpn", kept.user)
        assertEquals("secret", kept.pass)
        assertEquals(file, kept.config)
        Vpn.forget(app)
        assertTrue(Vpn.details(app).isEmpty)
    }

    @Test fun theFilesOptionsAreListedWithoutItsCertificates() {
        val listed = Vpn.options(file)
        assertTrue(listed.toString(), "remote ie1.example-vpn.com 1194" in listed && "cipher AES-256-CBC" in listed && "auth-user-pass" in listed)
        assertTrue("no certificate line is listed: " + listed, listed.none { it.contains("CERTIFICATE") || it.startsWith("MII") || it.startsWith("<") })
    }
}
