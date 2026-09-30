package com.garfiec.librechat.core.data.pdf

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The same synthetic fixtures as the Android suite (see `androidInstrumentedTest/resources/pdf/README.md`),
 * inlined because Kotlin/Native tests have no classpath resources.
 */
@OptIn(ExperimentalEncodingApi::class)
class PdfKitPdfNormalizerTest {

    private val normalizer = PdfKitPdfNormalizer()

    @Test
    fun rc4CopyProtectedPdfIsDecryptedAndKeepsItsText() = assertDecryptedWithText(Fixtures.RC4_EMPTY_USER)

    @Test
    fun aes128CopyProtectedPdfIsDecryptedAndKeepsItsText() = assertDecryptedWithText(Fixtures.AES128_EMPTY_USER)

    @Test
    fun aes256CopyProtectedPdfIsDecryptedAndKeepsItsText() = assertDecryptedWithText(Fixtures.AES256_EMPTY_USER)

    @Test
    fun pdfWithARealUserPasswordIsReportedNotStripped() {
        assertEquals(PdfNormalizeResult.NeedsPassword, normalizer.normalize(Base64.decode(Fixtures.REAL_PASSWORD)))
    }

    @Test
    fun passwordProtectedPdfIsDecryptedWithTheTypedPassword() {
        for (fixture in listOf(Fixtures.REAL_PASSWORD, Fixtures.AES256_REAL_PASSWORD)) {
            assertDecryptedWithText(normalizer.normalize(Base64.decode(fixture), password = "userpw"))
        }
    }

    @Test
    fun wrongPasswordStillNeedsPassword() {
        for (fixture in listOf(Fixtures.REAL_PASSWORD, Fixtures.AES256_REAL_PASSWORD)) {
            assertEquals(PdfNormalizeResult.NeedsPassword, normalizer.normalize(Base64.decode(fixture), password = "wrong"))
        }
    }

    @Test
    fun unencryptedPdfIsUnchanged() {
        assertEquals(PdfNormalizeResult.Unchanged, normalizer.normalize(Base64.decode(Fixtures.PLAIN)))
    }

    @Test
    fun unparseablePdfFailsWithoutThrowing() {
        assertIs<PdfNormalizeResult.Failed>(normalizer.normalize("%PDF-1.4\n/Encrypt garbage".encodeToByteArray()))
    }

    private fun assertDecryptedWithText(fixture: String) {
        val input = Base64.decode(fixture)
        assertTrue(failsServerEncryptionScan(input))
        assertDecryptedWithText(normalizer.normalize(input))
    }

    private fun assertDecryptedWithText(normalized: PdfNormalizeResult) {
        val result = assertIs<PdfNormalizeResult.Decrypted>(normalized)

        assertFalse(failsServerEncryptionScan(result.bytes))
        assertFalse(hasEncryptKey(result.bytes))
        val reopened = assertNotNull(runCatching { PDFDocument(data = result.bytes.toNSData()) }.getOrNull())
        assertFalse(reopened.isEncrypted())
        assertTrue(reopened.string.orEmpty().contains(FIXTURE_TEXT), "text lost: ${reopened.string}")
    }

    private companion object {
        const val FIXTURE_TEXT = "photosynthesis converts light into chemical energy"
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }

private object Fixtures {
    const val PLAIN =
        "JVBERi0xLjQKJb/3ov4KMSAwIG9iago8PCAvUGFnZXMgMiAwIFIgL1R5cGUgL0NhdGFsb2cgPj4KZW5kb2JqCjIgMCBvYmoK" +
        "PDwgL0NvdW50IDEgL0tpZHMgWyAzIDAgUiBdIC9UeXBlIC9QYWdlcyA+PgplbmRvYmoKMyAwIG9iago8PCAvQ29udGVudHMg" +
        "NCAwIFIgL01lZGlhQm94IFsgMCAwIDYxMiA3OTIgXSAvUGFyZW50IDIgMCBSIC9SZXNvdXJjZXMgPDwgL0ZvbnQgPDwgL0Yx" +
        "IDUgMCBSID4+ID4+IC9UeXBlIC9QYWdlID4+CmVuZG9iago0IDAgb2JqCjw8IC9MZW5ndGggMTA0ID4+CnN0cmVhbQpCVCAv" +
        "RjEgMTQgVGYgNzIgNzIwIFRkIChTd2l0Y2hib2FyZCBmaXh0dXJlOiBwaG90b3N5bnRoZXNpcyBjb252ZXJ0cyBsaWdodCBp" +
        "bnRvIGNoZW1pY2FsIGVuZXJneS4pIFRqIEVUCmVuZHN0cmVhbQplbmRvYmoKNSAwIG9iago8PCAvQmFzZUZvbnQgL0hlbHZl" +
        "dGljYSAvU3VidHlwZSAvVHlwZTEgL1R5cGUgL0ZvbnQgPj4KZW5kb2JqCnhyZWYKMCA2CjAwMDAwMDAwMDAgNjU1MzUgZiAK" +
        "MDAwMDAwMDAxNSAwMDAwMCBuIAowMDAwMDAwMDY0IDAwMDAwIG4gCjAwMDAwMDAxMjMgMDAwMDAgbiAKMDAwMDAwMDI1MSAw" +
        "MDAwMCBuIAowMDAwMDAwNDA1IDAwMDAwIG4gCnRyYWlsZXIgPDwgL1Jvb3QgMSAwIFIgL1NpemUgNiAvSUQgWzwxZjFhMDhk" +
        "NTlmYTJmMWI1MDUxY2YzMWI0Nzk1MWZlNT48MWYxYTA4ZDU5ZmEyZjFiNTA1MWNmMzFiNDc5NTFmZTU+XSA+PgpzdGFydHhy" +
        "ZWYKNDc1CiUlRU9GCg=="

    const val RC4_EMPTY_USER =
        "JVBERi0xLjQKJb/3ov4KMSAwIG9iago8PCAvUGFnZXMgMiAwIFIgL1R5cGUgL0NhdGFsb2cgPj4KZW5kb2JqCjIgMCBvYmoK" +
        "PDwgL0NvdW50IDEgL0tpZHMgWyAzIDAgUiBdIC9UeXBlIC9QYWdlcyA+PgplbmRvYmoKMyAwIG9iago8PCAvQ29udGVudHMg" +
        "NCAwIFIgL01lZGlhQm94IFsgMCAwIDYxMiA3OTIgXSAvUGFyZW50IDIgMCBSIC9SZXNvdXJjZXMgPDwgL0ZvbnQgPDwgL0Yx" +
        "IDUgMCBSID4+ID4+IC9UeXBlIC9QYWdlID4+CmVuZG9iago0IDAgb2JqCjw8IC9MZW5ndGggMTA1IC9GaWx0ZXIgL0ZsYXRl" +
        "RGVjb2RlID4+CnN0cmVhbQqdGKrKnHzc0vhPdu7g8eAv+i9uurcSeeCImxwMsORL/gBX0ZNMBqacM9UIPPO2Qq6UubJxl3N8" +
        "mhu0VUjaHZX3+jk4okJh3f7+Z3YZgfOe6d1QU/PaMTPAVrgnCtDFrTA6+9p4nmAXcyhlbmRzdHJlYW0KZW5kb2JqCjUgMCBv" +
        "YmoKPDwgL0Jhc2VGb250IC9IZWx2ZXRpY2EgL1N1YnR5cGUgL1R5cGUxIC9UeXBlIC9Gb250ID4+CmVuZG9iago2IDAgb2Jq" +
        "Cjw8IC9GaWx0ZXIgL1N0YW5kYXJkIC9MZW5ndGggMTI4IC9PIDwwZDk0Y2RiZmYzMDk2YTQ1OWQ3ZjAwMjlkMTE2OTFiZDNm" +
        "NjM0MTk5Y2M3OGUzZGFlODUwMzBlMWI2NTQ1Yzg5PiAvUCAtMTM0MCAvUiAzIC9VIDxkNjBlZmUwMDM1N2VkYzI3OWQwZjZl" +
        "OGU0MWRmMDRlMTAwMjE0NDY5OTBiOWU0MTE0MDcxYTRkOTEwNDk4NGMxPiAvViAyID4+CmVuZG9iagp4cmVmCjAgNwowMDAw" +
        "MDAwMDAwIDY1NTM1IGYgCjAwMDAwMDAwMTUgMDAwMDAgbiAKMDAwMDAwMDA2NCAwMDAwMCBuIAowMDAwMDAwMTIzIDAwMDAw" +
        "IG4gCjAwMDAwMDAyNTEgMDAwMDAgbiAKMDAwMDAwMDQyNyAwMDAwMCBuIAowMDAwMDAwNDk3IDAwMDAwIG4gCnRyYWlsZXIg" +
        "PDwgL1Jvb3QgMSAwIFIgL1NpemUgNyAvSUQgWzwxZjFhMDhkNTlmYTJmMWI1MDUxY2YzMWI0Nzk1MWZlNT48MzQ3NDY1ZTc4" +
        "ZWFmNzdhMDkzNWRjNGIzYzRjZjAzYmY+XSAvRW5jcnlwdCA2IDAgUiA+PgpzdGFydHhyZWYKNzA3CiUlRU9GCg=="

    const val AES128_EMPTY_USER =
        "JVBERi0xLjYKJb/3ov4KMSAwIG9iago8PCAvUGFnZXMgMiAwIFIgL1R5cGUgL0NhdGFsb2cgPj4KZW5kb2JqCjIgMCBvYmoK" +
        "PDwgL0NvdW50IDEgL0tpZHMgWyAzIDAgUiBdIC9UeXBlIC9QYWdlcyA+PgplbmRvYmoKMyAwIG9iago8PCAvQ29udGVudHMg" +
        "NCAwIFIgL01lZGlhQm94IFsgMCAwIDYxMiA3OTIgXSAvUGFyZW50IDIgMCBSIC9SZXNvdXJjZXMgPDwgL0ZvbnQgPDwgL0Yx" +
        "IDUgMCBSID4+ID4+IC9UeXBlIC9QYWdlID4+CmVuZG9iago0IDAgb2JqCjw8IC9MZW5ndGggMTI4IC9GaWx0ZXIgL0ZsYXRl" +
        "RGVjb2RlID4+CnN0cmVhbQqUr9KEeVMKuGZdraMvYKYpfyzHj5Sw7xEldYz8WtdnMhpIYJV9Pyndfbw5zpeT+fCBVGFMs0Lx" +
        "+lnNudYeDwLOa2mSlnjb/83OfV2zsjvRcjy+Ujr/7n/lY5/tMJRBAv88AuIXWVDupTXGbakC4eUb3Unmf4EG+0xUW6pj3Ixy" +
        "q2VuZHN0cmVhbQplbmRvYmoKNSAwIG9iago8PCAvQmFzZUZvbnQgL0hlbHZldGljYSAvU3VidHlwZSAvVHlwZTEgL1R5cGUg" +
        "L0ZvbnQgPj4KZW5kb2JqCjYgMCBvYmoKPDwgL0NGIDw8IC9TdGRDRiA8PCAvQXV0aEV2ZW50IC9Eb2NPcGVuIC9DRk0gL0FF" +
        "U1YyIC9MZW5ndGggMTYgPj4gPj4gL0ZpbHRlciAvU3RhbmRhcmQgL0xlbmd0aCAxMjggL08gPDBkOTRjZGJmZjMwOTZhNDU5" +
        "ZDdmMDAyOWQxMTY5MWJkM2Y2MzQxOTljYzc4ZTNkYWU4NTAzMGUxYjY1NDVjODk+IC9PRSA8PiAvUCAtMTM0MCAvUiA0IC9T" +
        "dG1GIC9TdGRDRiAvU3RyRiAvU3RkQ0YgL1UgPGQ2MGVmZTAwMzU3ZWRjMjc5ZDBmNmU4ZTQxZGYwNGUxMDAyMTQ0Njk5MGI5" +
        "ZTQxMTQwNzFhNGQ5MTA0OTg0YzE+IC9VRSA8PiAvViA0ID4+CmVuZG9iagp4cmVmCjAgNwowMDAwMDAwMDAwIDY1NTM1IGYg" +
        "CjAwMDAwMDAwMTUgMDAwMDAgbiAKMDAwMDAwMDA2NCAwMDAwMCBuIAowMDAwMDAwMTIzIDAwMDAwIG4gCjAwMDAwMDAyNTEg" +
        "MDAwMDAgbiAKMDAwMDAwMDQ1MCAwMDAwMCBuIAowMDAwMDAwNTIwIDAwMDAwIG4gCnRyYWlsZXIgPDwgL1Jvb3QgMSAwIFIg" +
        "L1NpemUgNyAvSUQgWzwxZjFhMDhkNTlmYTJmMWI1MDUxY2YzMWI0Nzk1MWZlNT48MDViZTdmZjVmMWM5OTM2ZTMyMGQzNTky" +
        "YzlhNWY2ZmE+XSAvRW5jcnlwdCA2IDAgUiA+PgpzdGFydHhyZWYKODM2CiUlRU9GCg=="

    const val AES256_EMPTY_USER =
        "JVBERi0xLjcKJb/3ov4KMSAwIG9iago8PCAvRXh0ZW5zaW9ucyA8PCAvQURCRSA8PCAvQmFzZVZlcnNpb24gLzEuNyAvRXh0" +
        "ZW5zaW9uTGV2ZWwgOCA+PiA+PiAvUGFnZXMgMiAwIFIgL1R5cGUgL0NhdGFsb2cgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL0Nv" +
        "dW50IDEgL0tpZHMgWyAzIDAgUiBdIC9UeXBlIC9QYWdlcyA+PgplbmRvYmoKMyAwIG9iago8PCAvQ29udGVudHMgNCAwIFIg" +
        "L01lZGlhQm94IFsgMCAwIDYxMiA3OTIgXSAvUGFyZW50IDIgMCBSIC9SZXNvdXJjZXMgPDwgL0ZvbnQgPDwgL0YxIDUgMCBS" +
        "ID4+ID4+IC9UeXBlIC9QYWdlID4+CmVuZG9iago0IDAgb2JqCjw8IC9MZW5ndGggMTI4IC9GaWx0ZXIgL0ZsYXRlRGVjb2Rl" +
        "ID4+CnN0cmVhbQo4s2yC9GJn/wEM2k1T1wcn8e4L7dg+7vWarjXSIOQoQe6+BxVqBVRww6lxM63xcBETdhHQtTbWg2iN6ATv" +
        "3SDVJv9Rpb9FRcrCvRBo66wNSeleauMmyBzxKvYf+0DXcg7F3KLggyk9JKCccE+ZcJS1HhxfEO/AHb41nvL38xuk9GVuZHN0" +
        "cmVhbQplbmRvYmoKNSAwIG9iago8PCAvQmFzZUZvbnQgL0hlbHZldGljYSAvU3VidHlwZSAvVHlwZTEgL1R5cGUgL0ZvbnQg" +
        "Pj4KZW5kb2JqCjYgMCBvYmoKPDwgL0NGIDw8IC9TdGRDRiA8PCAvQXV0aEV2ZW50IC9Eb2NPcGVuIC9DRk0gL0FFU1YzIC9M" +
        "ZW5ndGggMzIgPj4gPj4gL0ZpbHRlciAvU3RhbmRhcmQgL0xlbmd0aCAyNTYgL08gPGIwNmRkODZlZTU0ZGE3MzM0OGVlYmE1" +
        "YTMzMDI0ZjJjMjBlZDM0YzUwY2E5Nzg2OGI1N2I3ZWJmZTczOGUxZDI5ZjUyN2NiNGEyOGI2YzA5YWQ5OGU4NjAwNmEzOWVk" +
        "ZT4gL09FIDw1ODMxYTFmYzcwMGY3M2UyNmQwNjdkMmU1YmE5Yzc1ZTFiYTFhZmExZDQ1NmUxOTUxY2QzM2IzMzI5OTU1Nzlk" +
        "PiAvUCAtMTM0MCAvUGVybXMgPGY0OTAyMGVkYjI5YTE0YWIyYTllNzhlOWM5NGQ2MTk2PiAvUiA2IC9TdG1GIC9TdGRDRiAv" +
        "U3RyRiAvU3RkQ0YgL1UgPDg3NzQ2ZTI4NjczNmE5ODIyMDhkMWFlYmViZjAwNDY0MGMyODdiM2Y1NWNjZWJmMTJjNjZmNTI5" +
        "Y2VkYmI0MDg0YmRiMmRjZWYwZDQ0Nzc5NzBiOGUyNTVlMzg4ZWJhMD4gL1VFIDw2ZTg3MTg1ZjViMmU1NGUxODM1MGUwNTAx" +
        "MDI4OTE2MDU4M2RiNjU0MGUxNDUyODc5YmIyOGYxNjU4NzMyYWM2PiAvViA1ID4+CmVuZG9iagp4cmVmCjAgNwowMDAwMDAw" +
        "MDAwIDY1NTM1IGYgCjAwMDAwMDAwMTUgMDAwMDAgbiAKMDAwMDAwMDEzMCAwMDAwMCBuIAowMDAwMDAwMTg5IDAwMDAwIG4g" +
        "CjAwMDAwMDAzMTcgMDAwMDAgbiAKMDAwMDAwMDUxNiAwMDAwMCBuIAowMDAwMDAwNTg2IDAwMDAwIG4gCnRyYWlsZXIgPDwg" +
        "L1Jvb3QgMSAwIFIgL1NpemUgNyAvSUQgWzwxZjFhMDhkNTlmYTJmMWI1MDUxY2YzMWI0Nzk1MWZlNT48NmQ3NTg4MWRlYzBi" +
        "MWU2MDc0NjAyN2NlNWM1NTQ5OTI+XSAvRW5jcnlwdCA2IDAgUiA+PgpzdGFydHhyZWYKMTEzNgolJUVPRgo="

    const val AES256_REAL_PASSWORD =
        "JVBERi0xLjcKJb/3ov4KMSAwIG9iago8PCAvRXh0ZW5zaW9ucyA8PCAvQURCRSA8PCAvQmFzZVZlcnNpb24gLzEuNyAvRXh0" +
        "ZW5zaW9uTGV2ZWwgOCA+PiA+PiAvUGFnZXMgMiAwIFIgL1R5cGUgL0NhdGFsb2cgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL0Nv" +
        "dW50IDEgL0tpZHMgWyAzIDAgUiBdIC9UeXBlIC9QYWdlcyA+PgplbmRvYmoKMyAwIG9iago8PCAvQ29udGVudHMgNCAwIFIg" +
        "L01lZGlhQm94IFsgMCAwIDYxMiA3OTIgXSAvUGFyZW50IDIgMCBSIC9SZXNvdXJjZXMgPDwgL0ZvbnQgPDwgL0YxIDUgMCBS" +
        "ID4+ID4+IC9UeXBlIC9QYWdlID4+CmVuZG9iago0IDAgb2JqCjw8IC9MZW5ndGggMTI4IC9GaWx0ZXIgL0ZsYXRlRGVjb2Rl" +
        "ID4+CnN0cmVhbQrqNlUwY2Oy3FoNaZxw3yzWHp15MqHN+F9pkaJEX/S7ojp2ZFiADUF3Gnx01/7hLVrFGFu5WL8N8WPMufhu" +
        "vGqDp8A9u8fDHy93uUyxgP1ywMZWjf2dP8WZdTSVL+9VyBN/FXVBu5EkzhKe+2e15ibPOHTGRB69Lpv6UDG8F//Qc2VuZHN0" +
        "cmVhbQplbmRvYmoKNSAwIG9iago8PCAvQmFzZUZvbnQgL0hlbHZldGljYSAvU3VidHlwZSAvVHlwZTEgL1R5cGUgL0ZvbnQg" +
        "Pj4KZW5kb2JqCjYgMCBvYmoKPDwgL0NGIDw8IC9TdGRDRiA8PCAvQXV0aEV2ZW50IC9Eb2NPcGVuIC9DRk0gL0FFU1YzIC9M" +
        "ZW5ndGggMzIgPj4gPj4gL0ZpbHRlciAvU3RhbmRhcmQgL0xlbmd0aCAyNTYgL08gPDhiZDhhNzRlYmMzM2Q1MzA2MDE0NjRm" +
        "ODMzMjY0NWM0YTMxNWIxMzJiYjg1Njk5YWI2NDFlNWRmOGQyN2Y2MTc5MzkwNjA4Yjg4Y2U5YjgwODhlZDFjOWYwMTM1OGRi" +
        "OT4gL09FIDxkYjM3ZDVkMWZiYmNkNmRjMmU5NWEzY2E4Y2Y2MDUxZWYwNWZmOTYxZTM1YzEzMGZmYmJjYTY1NzRiNWFmNTM1" +
        "PiAvUCAtNCAvUGVybXMgPGY0NmFmZWVlYWQ4OTQ2MGYxYmQyYzFhZjU3Y2RjMGM2PiAvUiA2IC9TdG1GIC9TdGRDRiAvU3Ry" +
        "RiAvU3RkQ0YgL1UgPDgzMzQ5ZjZiM2FkNWVkOGI3ZGQ4OTUzZDU0OGJmZDQ4NDE3ODhkMTJiZDhhOTlkMWRkZTM2ZTE1OTc0" +
        "ZjgwODNlZjdmMTAwZmVjY2YzYjIxYmJiYzgzNDg3ZDljZTUyZT4gL1VFIDwwODYxZjhhNGE2OWVhNTQ1YzM0NDM1YzRiYmRk" +
        "YjUxODg4ODM4MzVhMDY0ZTFjNmMwOWJiOWE2NDc1ZTAwZGZhPiAvViA1ID4+CmVuZG9iagp4cmVmCjAgNwowMDAwMDAwMDAw" +
        "IDY1NTM1IGYgCjAwMDAwMDAwMTUgMDAwMDAgbiAKMDAwMDAwMDEzMCAwMDAwMCBuIAowMDAwMDAwMTg5IDAwMDAwIG4gCjAw" +
        "MDAwMDAzMTcgMDAwMDAgbiAKMDAwMDAwMDUxNiAwMDAwMCBuIAowMDAwMDAwNTg2IDAwMDAwIG4gCnRyYWlsZXIgPDwgL1Jv" +
        "b3QgMSAwIFIgL1NpemUgNyAvSUQgWzwxZjFhMDhkNTlmYTJmMWI1MDUxY2YzMWI0Nzk1MWZlNT48MzIwZmJlYWUwN2JiOTBi" +
        "YTJlM2Y5ZmI1OWUwNWQ4ODA+XSAvRW5jcnlwdCA2IDAgUiA+PgpzdGFydHhyZWYKMTEzMwolJUVPRgo="

    const val REAL_PASSWORD =
        "JVBERi0xLjQKJb/3ov4KMSAwIG9iago8PCAvUGFnZXMgMiAwIFIgL1R5cGUgL0NhdGFsb2cgPj4KZW5kb2JqCjIgMCBvYmoK" +
        "PDwgL0NvdW50IDEgL0tpZHMgWyAzIDAgUiBdIC9UeXBlIC9QYWdlcyA+PgplbmRvYmoKMyAwIG9iago8PCAvQ29udGVudHMg" +
        "NCAwIFIgL01lZGlhQm94IFsgMCAwIDYxMiA3OTIgXSAvUGFyZW50IDIgMCBSIC9SZXNvdXJjZXMgPDwgL0ZvbnQgPDwgL0Yx" +
        "IDUgMCBSID4+ID4+IC9UeXBlIC9QYWdlID4+CmVuZG9iago0IDAgb2JqCjw8IC9MZW5ndGggMTA1IC9GaWx0ZXIgL0ZsYXRl" +
        "RGVjb2RlID4+CnN0cmVhbQqDvMEBclfMvkjFGk353Gc6r2VDUrmJ3fRbGbirXtV+amS7Ov5SL44IGYnaVcLT4z1CDOMJ0a2S" +
        "0PC3TKuheEQMhxvv7+pzQwYIqGtH4Jd7THBV7s34kn0jn9TH1S5Owwxjme5OZMuC5IdlbmRzdHJlYW0KZW5kb2JqCjUgMCBv" +
        "YmoKPDwgL0Jhc2VGb250IC9IZWx2ZXRpY2EgL1N1YnR5cGUgL1R5cGUxIC9UeXBlIC9Gb250ID4+CmVuZG9iago2IDAgb2Jq" +
        "Cjw8IC9GaWx0ZXIgL1N0YW5kYXJkIC9MZW5ndGggMTI4IC9PIDw1MDU4ZTY5M2NkMGJjOGJiYjcyMTAwMGFhNGFkZjRiNTVm" +
        "MWJiZWQ1MWQxOGYzNzRjN2VhNDk3N2VjODcxYWZmPiAvUCAtNCAvUiAzIC9VIDxjOTMzODViYWJmZjI0MmQ3OWIzZTAxZjI0" +
        "NmFkYjk5ZjAwMjE0NDY5OTBiOWU0MTE0MDcxYTRkOTEwNDk4NGMxPiAvViAyID4+CmVuZG9iagp4cmVmCjAgNwowMDAwMDAw" +
        "MDAwIDY1NTM1IGYgCjAwMDAwMDAwMTUgMDAwMDAgbiAKMDAwMDAwMDA2NCAwMDAwMCBuIAowMDAwMDAwMTIzIDAwMDAwIG4g" +
        "CjAwMDAwMDAyNTEgMDAwMDAgbiAKMDAwMDAwMDQyNyAwMDAwMCBuIAowMDAwMDAwNDk3IDAwMDAwIG4gCnRyYWlsZXIgPDwg" +
        "L1Jvb3QgMSAwIFIgL1NpemUgNyAvSUQgWzwxZjFhMDhkNTlmYTJmMWI1MDUxY2YzMWI0Nzk1MWZlNT48ZWQ2YTI1NDMwZmYw" +
        "MTJjN2IxZDQyY2I4ZmE1OTE0YjQ+XSAvRW5jcnlwdCA2IDAgUiA+PgpzdGFydHhyZWYKNzA0CiUlRU9GCg=="
}
