package com.example.calendaralarmscheduler.ui

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.calendaralarmscheduler.ui.preview.EventPreviewViewModel
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
 * Simplified unit tests for EventPreviewViewModel
 * Tests basic initialization and error handling
 */
@ExperimentalCoroutinesApi
class EventPreviewViewModelTest {

    @get:TestRule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var mockApplication: Application

    private lateinit var viewModel: EventPreviewViewModel
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
        
        // Note: We can't easily mock Room database in unit tests
        // so we'll focus on testing methods that don't require it
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModelInitialization() {
        // This test mainly verifies that the ViewModel can be created
        // without crashing during initialization
        try {
            viewModel = EventPreviewViewModel(mockApplication)
            // If we get here without exception, initialization succeeded
            assertTrue("ViewModel should initialize without exception", true)
        } catch (e: Exception) {
            fail("ViewModel initialization should not throw exception: ${e.message}")
        }
    }

    @Test
    fun testIsLoadingInitialState() {
        viewModel = EventPreviewViewModel(mockApplication)
        
        // Check that loading state is properly initialized
        assertNotNull("isLoading should not be null", viewModel.isLoading.value)
        assertFalse("Should not be loading initially", viewModel.isLoading.value ?: true)
    }

    @Test
    fun testErrorMessageInitialState() {
        viewModel = EventPreviewViewModel(mockApplication)
        
        // Check that error message is properly initialized
        assertNull("Error message should be null initially", viewModel.errorMessage.value)
    }

    @Test
    fun testClearError() {
        viewModel = EventPreviewViewModel(mockApplication)
        
        // Test the clearError method
        viewModel.clearError()
        
        // Verify error message is cleared
        assertNull("Error message should be null after clearError", viewModel.errorMessage.value)
    }

    @Test
    fun testCurrentFilterInitialState() = runTest {
        viewModel = EventPreviewViewModel(mockApplication)
        
        // Test that current filter is properly initialized
        val initialFilter = viewModel.currentFilter.value
        assertNotNull("Current filter should not be null", initialFilter)
        assertNull("Initial filter should have no rule filter", initialFilter.ruleId)
        assertNull("Initial filter should have no calendar filter", initialFilter.calendarId)
        assertFalse("Initial filter should not show past events", initialFilter.showPastEvents)
    }

    @Test
    fun testUpdateFilter() = runTest {
        viewModel = EventPreviewViewModel(mockApplication)
        
        // Create a new filter
        val newFilter = viewModel.currentFilter.value.copy(
            ruleId = "test-rule-id",
            calendarId = 123L,
            showPastEvents = true
        )
        
        // Update the filter
        viewModel.updateFilter(newFilter)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify the filter was updated
        val updatedFilter = viewModel.currentFilter.value
        assertEquals("Rule filter should be updated", "test-rule-id", updatedFilter.ruleId)
        assertEquals("Calendar filter should be updated", 123L, updatedFilter.calendarId)
        assertTrue("Show past events should be updated", updatedFilter.showPastEvents)
    }

    @Test
    fun testClearFilter() = runTest {
        viewModel = EventPreviewViewModel(mockApplication)
        
        // First set some filter values
        val newFilter = viewModel.currentFilter.value.copy(
            ruleId = "test-rule-id",
            calendarId = 123L,
            showPastEvents = true
        )
        viewModel.updateFilter(newFilter)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Now clear the filter
        viewModel.clearFilter()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify the filter was cleared
        val clearedFilter = viewModel.currentFilter.value
        assertNull("Rule filter should be cleared", clearedFilter.ruleId)
        assertNull("Calendar filter should be cleared", clearedFilter.calendarId)
        assertFalse("Show past events should be cleared", clearedFilter.showPastEvents)
    }

    @Test
    fun testToggleShowPastEvents() = runTest {
        viewModel = EventPreviewViewModel(mockApplication)
        
        // Initial state should be false
        assertFalse("Initial show past events should be false", 
                   viewModel.currentFilter.value.showPastEvents)
        
        // Toggle it
        viewModel.toggleShowPastEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Should now be true
        assertTrue("Show past events should be toggled to true", 
                  viewModel.currentFilter.value.showPastEvents)
        
        // Toggle again
        viewModel.toggleShowPastEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Should be false again
        assertFalse("Show past events should be toggled back to false", 
                   viewModel.currentFilter.value.showPastEvents)
    }

    @Test
    fun testSetRuleFilter() = runTest {
        viewModel = EventPreviewViewModel(mockApplication)
        
        val testRuleId = "test-rule-123"
        
        // Set rule filter
        viewModel.setRuleFilter(testRuleId)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify rule filter is set
        assertEquals("Rule filter should be set", testRuleId, viewModel.currentFilter.value.ruleId)
        
        // Clear rule filter
        viewModel.setRuleFilter(null)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify rule filter is cleared
        assertNull("Rule filter should be cleared", viewModel.currentFilter.value.ruleId)
    }

    @Test
    fun testSetCalendarFilter() = runTest {
        viewModel = EventPreviewViewModel(mockApplication)
        
        val testCalendarId = 456L
        
        // Set calendar filter
        viewModel.setCalendarFilter(testCalendarId)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify calendar filter is set
        assertEquals("Calendar filter should be set", testCalendarId, viewModel.currentFilter.value.calendarId)
        
        // Clear calendar filter
        viewModel.setCalendarFilter(null)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify calendar filter is cleared
        assertNull("Calendar filter should be cleared", viewModel.currentFilter.value.calendarId)
    }
}