package com.example.calendaralarmscheduler.ui

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.calendaralarmscheduler.ui.rules.RuleListViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule as TestRule
import org.junit.Test
import org.junit.Assert.*

/**
 * Simplified unit tests for RuleListViewModel
 */
@ExperimentalCoroutinesApi
class RuleListViewModelTest {

    @get:TestRule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var mockApplication: Application
    private lateinit var viewModel: RuleListViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Create mock application
        mockApplication = mockk(relaxed = true)
        
        // Mock application context and system services
        every { mockApplication.getSystemService(any()) } returns mockk(relaxed = true)
        every { mockApplication.packageName } returns "com.example.calendaralarmscheduler"
        every { mockApplication.applicationContext } returns mockApplication
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModelInitialization() {
        try {
            viewModel = RuleListViewModel(mockApplication)
            assertTrue("ViewModel should initialize without exception", true)
        } catch (e: Exception) {
            fail("ViewModel initialization should not throw exception: ${e.message}")
        }
    }
}