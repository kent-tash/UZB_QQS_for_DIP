package com.example.uzb_qqs_for_dip.ui

import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.R
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.PaymentType
import com.example.uzb_qqs_for_dip.data.model.ReceiptOwner
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.repository.ReceiptRepository
import com.example.uzb_qqs_for_dip.data.session.SessionManager
import com.example.uzb_qqs_for_dip.network.ParsedReceipt
import com.example.uzb_qqs_for_dip.network.ReceiptParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZeroVatScanLogicTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockApp: QqsApp
    private lateinit var mockContainer: AppContainer
    private lateinit var mockReceiptRepository: ReceiptRepository
    private lateinit var mockSessionManager: SessionManager
    private lateinit var mockReceiptParser: ReceiptParser

    private lateinit var viewModel: ScanViewModel

    private val receiptFlow = MutableStateFlow<List<ReceiptWithUser>>(emptyList())
    private val currentUserIdFlow = MutableStateFlow<Long?>(1L)

    private val sampleZeroVatReceipt = ParsedReceipt(
        qrUrl = "https://ofd.soliq.uz/check?t=123",
        purchasedAt = 1729700000000L,
        sellerName = "Korzinka Zero",
        totalAmountTiyin = 10000000L,
        vatAmountTiyin = 0L,
        paymentType = PaymentType.CARD,
        fiscalSign = "FS123",
        rawSnippet = null
    )

    private val sampleNullVatReceipt = ParsedReceipt(
        qrUrl = "https://ofd.soliq.uz/check?t=124",
        purchasedAt = 1729700000000L,
        sellerName = "No Vat Shop",
        totalAmountTiyin = 5000000L,
        vatAmountTiyin = null,
        paymentType = PaymentType.CASH,
        fiscalSign = "FS124",
        rawSnippet = null
    )

    private val sampleNormalReceipt = ParsedReceipt(
        qrUrl = "https://ofd.soliq.uz/check?t=125",
        purchasedAt = 1729700000000L,
        sellerName = "Korzinka VAT",
        totalAmountTiyin = 11200000L,
        vatAmountTiyin = 1200000L,
        paymentType = PaymentType.CARD,
        fiscalSign = "FS125",
        rawSnippet = null
    )

    @Before
    fun setup() {
        mockApp = mockk(relaxed = true)
        mockContainer = mockk(relaxed = true)
        mockReceiptRepository = mockk(relaxed = true)
        mockSessionManager = mockk(relaxed = true)
        mockReceiptParser = mockk(relaxed = true)

        every { mockApp.container } returns mockContainer
        every { mockContainer.receiptRepository } returns mockReceiptRepository
        every { mockContainer.sessionManager } returns mockSessionManager
        every { mockContainer.receiptParser } returns mockReceiptParser

        every { mockReceiptRepository.receipts } returns receiptFlow
        every { mockSessionManager.currentUserId } returns currentUserIdFlow
        coEvery { mockReceiptRepository.findOwner(any(), any(), any(), any()) } returns null

        viewModel = ScanViewModel(mockApp)
    }

    @Test
    fun handleScan_whenZeroVat_emitsZeroVatState_andDoesNotInsert() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(any()) } returns Result.success(sampleZeroVatReceipt)

        viewModel.handleScan(sampleZeroVatReceipt.qrUrl)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("State should be ZeroVat", state is ScanState.ZeroVat)
        assertEquals(sampleZeroVatReceipt, (state as ScanState.ZeroVat).parsed)
        coVerify(exactly = 0) { mockReceiptRepository.insert(any()) }
    }

    @Test
    fun handleScan_whenNullVat_emitsZeroVatState() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(any()) } returns Result.success(sampleNullVatReceipt)

        viewModel.handleScan(sampleNullVatReceipt.qrUrl)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("State should be ZeroVat", state is ScanState.ZeroVat)
        assertEquals(sampleNullVatReceipt, (state as ScanState.ZeroVat).parsed)
        coVerify(exactly = 0) { mockReceiptRepository.insert(any()) }
    }

    @Test
    fun handleScan_whenPositiveVat_emitsParsedState() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(any()) } returns Result.success(sampleNormalReceipt)

        viewModel.handleScan(sampleNormalReceipt.qrUrl)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("State should be Parsed", state is ScanState.Parsed)
        assertEquals(sampleNormalReceipt, (state as ScanState.Parsed).parsed)
    }

    @Test
    fun saveCurrent_whenZeroVat_setsErrorState() = runTest {
        // Manually trigger saveCurrent when state has a receipt with 0 VAT
        // (e.g. if state was somehow Parsed with 0 VAT)
        coEvery { mockReceiptParser.fetchAndParse(any()) } returns Result.success(sampleNormalReceipt)
        viewModel.handleScan(sampleNormalReceipt.qrUrl)
        advanceUntilIdle()

        // Replace state with zero VAT parsed state to test guard
        val zeroVatParsed = sampleZeroVatReceipt
        // Directly test saveForUser
        val result = viewModel.saveForUser(zeroVatParsed, 1L)
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { mockReceiptRepository.insert(any()) }
    }

    @Test
    fun processSingleQr_whenZeroVat_returnsWarning_withoutInsert() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(any()) } returns Result.success(sampleZeroVatReceipt)

        val result = viewModel.processSingleQr(sampleZeroVatReceipt.qrUrl)

        assertEquals(CameraScanStatus.WARNING, result.status)
        assertEquals(R.string.scanner_zero_vat_not_added, result.message)
        assertNull(result.completedReceiptId)
        coVerify(exactly = 0) { mockReceiptRepository.insert(any()) }
    }

    @Test
    fun prepareSheetFromUrls_whenZeroVat_assignsZeroVatStatus_andLocksToggle() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(sampleZeroVatReceipt.qrUrl) } returns Result.success(sampleZeroVatReceipt)
        coEvery { mockReceiptParser.fetchAndParse(sampleNormalReceipt.qrUrl) } returns Result.success(sampleNormalReceipt)

        viewModel.prepareSheetFromUrls(listOf(sampleZeroVatReceipt.qrUrl, sampleNormalReceipt.qrUrl))
        advanceUntilIdle()

        val items = viewModel.sheetPreviewItems.value
        assertEquals(2, items.size)

        val zeroVatItem = items[0]
        assertEquals(SheetItemStatus.ZERO_VAT, zeroVatItem.status)
        assertFalse(zeroVatItem.selected)

        val normalItem = items[1]
        assertEquals(SheetItemStatus.NEW, normalItem.status)
        assertTrue(normalItem.selected)

        // Try toggling zero VAT item
        viewModel.toggleSheetItem(0)
        assertFalse("ZERO_VAT item should not toggle", viewModel.sheetPreviewItems.value[0].selected)

        // Confirm selection
        coEvery { mockReceiptRepository.insert(any()) } returns Result.success(10L)
        viewModel.confirmSheetSelection()
        advanceUntilIdle()

        val summary = viewModel.sheetSummary.value
        assertEquals(2, summary?.scanned)
        assertEquals(1, summary?.saved)
        assertEquals(1, summary?.skipped) // zero vat item skipped
    }

    @Test
    fun handleScan_whenDuplicateSameUser_emitsParsedWithSameUserOwner() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(sampleNormalReceipt.qrUrl) } returns Result.success(sampleNormalReceipt)
        coEvery { mockReceiptRepository.findOwner(any(), any(), any(), any()) } returns ReceiptOwner(
            receiptId = 55L,
            userId = 1L,
            fullName = "Current User"
        )

        viewModel.handleScan(sampleNormalReceipt.qrUrl)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("State should be Parsed", state is ScanState.Parsed)
        val parsedState = state as ScanState.Parsed
        assertEquals(ExistingOwner.SameUser, parsedState.existingOwner)
    }

    @Test
    fun handleScan_whenDuplicateOtherUser_emitsParsedWithOtherUserOwner() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(sampleNormalReceipt.qrUrl) } returns Result.success(sampleNormalReceipt)
        coEvery { mockReceiptRepository.findOwner(any(), any(), any(), any()) } returns ReceiptOwner(
            receiptId = 56L,
            userId = 2L,
            fullName = "Ivan Ivanov"
        )

        viewModel.handleScan(sampleNormalReceipt.qrUrl)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("State should be Parsed", state is ScanState.Parsed)
        val parsedState = state as ScanState.Parsed
        assertEquals(ExistingOwner.OtherUser("Ivan Ivanov"), parsedState.existingOwner)
    }

    @Test
    fun processSingleQr_whenPositiveVat_returnsSuccess() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(sampleNormalReceipt.qrUrl) } returns Result.success(sampleNormalReceipt)
        coEvery { mockReceiptRepository.findOwner(any(), any(), any(), any()) } returns null
        coEvery { mockReceiptRepository.insert(any()) } returns Result.success(777L)

        val result = viewModel.processSingleQr(sampleNormalReceipt.qrUrl)

        assertEquals(CameraScanStatus.SUCCESS, result.status)
        assertEquals(R.string.scanner_added, result.message)
        assertEquals(777L, result.completedReceiptId)
    }

    @Test
    fun processSingleQr_whenDuplicateSameUser_returnsDuplicate() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(sampleNormalReceipt.qrUrl) } returns Result.success(sampleNormalReceipt)
        coEvery { mockReceiptRepository.findOwner(any(), any(), any(), any()) } returns ReceiptOwner(
            receiptId = 88L,
            userId = 1L,
            fullName = "Current User"
        )

        val result = viewModel.processSingleQr(sampleNormalReceipt.qrUrl)

        assertEquals(CameraScanStatus.DUPLICATE, result.status)
        assertEquals(R.string.scanner_duplicate, result.message)
        assertNull(result.completedReceiptId)
    }

    @Test
    fun processSingleQr_whenDuplicateOtherUser_returnsErrorWithOwner() = runTest {
        coEvery { mockReceiptParser.fetchAndParse(sampleNormalReceipt.qrUrl) } returns Result.success(sampleNormalReceipt)
        coEvery { mockReceiptRepository.findOwner(any(), any(), any(), any()) } returns ReceiptOwner(
            receiptId = 89L,
            userId = 99L,
            fullName = "Other Colleague"
        )

        val result = viewModel.processSingleQr(sampleNormalReceipt.qrUrl)

        assertEquals(CameraScanStatus.ERROR, result.status)
        assertEquals(R.string.scanner_owner, result.message)
        assertEquals(listOf("Other Colleague"), result.args)
        assertNull(result.completedReceiptId)
    }

    @Test
    fun prepareSheetFromUrls_whenDuplicateReceipts_setsAlreadyThisAndOtherOwner() = runTest {
        val sameUserUrl = "https://ofd.soliq.uz/check?t=same"
        val otherUserUrl = "https://ofd.soliq.uz/check?t=other"

        val sameReceipt = sampleNormalReceipt.copy(qrUrl = sameUserUrl)
        val otherReceipt = sampleNormalReceipt.copy(qrUrl = otherUserUrl)

        coEvery { mockReceiptParser.fetchAndParse(sameUserUrl) } returns Result.success(sameReceipt)
        coEvery { mockReceiptParser.fetchAndParse(otherUserUrl) } returns Result.success(otherReceipt)

        coEvery {
            mockReceiptRepository.findOwner(qrUrl = sameUserUrl, any(), any(), any())
        } returns ReceiptOwner(receiptId = 1L, userId = 1L, fullName = "Current User")

        coEvery {
            mockReceiptRepository.findOwner(qrUrl = otherUserUrl, any(), any(), any())
        } returns ReceiptOwner(receiptId = 2L, userId = 2L, fullName = "Other User")

        viewModel.prepareSheetFromUrls(listOf(sameUserUrl, otherUserUrl))
        advanceUntilIdle()

        val items = viewModel.sheetPreviewItems.value
        assertEquals(2, items.size)

        val sameItem = items[0]
        assertEquals(SheetItemStatus.ALREADY_THIS, sameItem.status)
        assertFalse(sameItem.selected)

        val otherItem = items[1]
        assertEquals(SheetItemStatus.OTHER_OWNER, otherItem.status)
        assertFalse(otherItem.selected)

        // Verify other owner cannot be toggled
        viewModel.toggleSheetItem(1)
        assertFalse(viewModel.sheetPreviewItems.value[1].selected)

        // Confirm
        viewModel.confirmSheetSelection()
        advanceUntilIdle()

        val summary = viewModel.sheetSummary.value
        assertEquals(2, summary?.scanned)
        assertEquals(0, summary?.saved)
        assertEquals(1, summary?.alreadyVerified)
        assertEquals(1, summary?.conflicts)
    }
}
