package com.example.uzb_qqs_for_dip.ui

import android.content.Context
import android.net.Uri
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.data.repository.AuditorRepository
import com.example.uzb_qqs_for_dip.data.repository.ReceiptRepository
import com.example.uzb_qqs_for_dip.data.repository.UserRepository
import com.example.uzb_qqs_for_dip.data.repository.UserReceiptStats
import com.example.uzb_qqs_for_dip.data.session.SessionManager
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuditorVerifyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockApp: QqsApp
    private lateinit var mockContainer: AppContainer
    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockReceiptRepository: ReceiptRepository
    private lateinit var mockAuditorRepository: AuditorRepository
    private lateinit var mockSessionManager: SessionManager
    private lateinit var mockReceiptParser: ReceiptParser

    private lateinit var viewModel: AuditorVerifyViewModel

    private val userFlow = MutableStateFlow<List<User>>(emptyList())
    private val receiptFlow = MutableStateFlow<List<ReceiptWithUser>>(emptyList())
    private val currentUserIdFlow = MutableStateFlow<Long?>(null)

    @Before
    fun setup() {
        mockApp = mockk(relaxed = true)
        mockContainer = mockk(relaxed = true)
        mockUserRepository = mockk(relaxed = true)
        mockReceiptRepository = mockk(relaxed = true)
        mockAuditorRepository = mockk(relaxed = true)
        mockSessionManager = mockk(relaxed = true)
        mockReceiptParser = mockk(relaxed = true)

        every { mockApp.container } returns mockContainer
        every { mockContainer.userRepository } returns mockUserRepository
        every { mockContainer.receiptRepository } returns mockReceiptRepository
        every { mockContainer.auditorRepository } returns mockAuditorRepository
        every { mockContainer.sessionManager } returns mockSessionManager
        every { mockContainer.receiptParser } returns mockReceiptParser

        every { mockUserRepository.users } returns userFlow
        every { mockReceiptRepository.receipts } returns receiptFlow
        every { mockSessionManager.currentUserId } returns currentUserIdFlow
    }

    @Test
    fun `submitManualEntry inserts receipt successfully when photoUri is null`() = runTest {
        viewModel = AuditorVerifyViewModel(mockApp)
        
        val user = User(id = 1L, fullName = "Test User", position = "Pos", initialsSurname = "Test", role = UserRole.EMPLOYEE)
        
        // Mock get stats and declarations
        coEvery { mockReceiptRepository.getUserPeriodStats(any(), any(), any()) } returns UserReceiptStats(0, 0, 0L, 0L, 0L, 0L)
        coEvery { mockAuditorRepository.getDeclaration(any(), any(), any()) } returns null
        coEvery { mockReceiptRepository.insert(any()) } returns Result.success(1L)
        
        viewModel.selectEmployee(user)
        advanceUntilIdle()

        viewModel.submitManualEntry(
            context = mockApp,
            storeName = "Test Store",
            dateMs = 123456789L,
            totalTiyin = 100000L,
            vatTiyin = 12000L,
            photoUri = null
        )
        advanceUntilIdle()

        coVerify { 
            mockReceiptRepository.insert(withArg { receipt ->
                assertEquals(1L, receipt.userId)
                assertEquals("Test Store", receipt.sellerName)
                assertEquals(123456789L, receipt.purchasedAt)
                assertEquals(100000L, receipt.totalAmountTiyin)
                assertEquals(12000L, receipt.vatAmountTiyin)
                assertTrue(receipt.isManual)
                assertEquals(null, receipt.manualPhotoUri)
            })
        }
        val result = viewModel.verifyResult.value
        assertTrue(result is VerifyResult.Success)
        assertEquals(false, (result as VerifyResult.Success).markedVerified)
    }

    @Test
    fun `submitManualEntry emits error when insert fails`() = runTest {
        viewModel = AuditorVerifyViewModel(mockApp)
        
        val user = User(id = 1L, fullName = "Test User", position = "Pos", initialsSurname = "Test", role = UserRole.EMPLOYEE)
        
        coEvery { mockReceiptRepository.getUserPeriodStats(any(), any(), any()) } returns UserReceiptStats(0, 0, 0L, 0L, 0L, 0L)
        coEvery { mockAuditorRepository.getDeclaration(any(), any(), any()) } returns null
        
        // Mock insert to return failure
        coEvery { mockReceiptRepository.insert(any()) } returns Result.failure(Exception("DB error"))
        
        viewModel.selectEmployee(user)
        advanceUntilIdle()

        val mockUri = mockk<Uri>(relaxed = true)
        viewModel.submitManualEntry(
            context = mockApp,
            storeName = "Test Store",
            dateMs = 123456789L,
            totalTiyin = 100000L,
            vatTiyin = 12000L,
            photoUri = mockUri
        )
        advanceUntilIdle()

        val result = viewModel.verifyResult.value
        assertTrue(result is VerifyResult.Error)
        assertEquals("DB error", (result as VerifyResult.Error).message)
    }
}
