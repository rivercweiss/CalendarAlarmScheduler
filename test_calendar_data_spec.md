# Test Calendar Data Specification

This document defines the exact calendar events that should exist in the emulator snapshot for comprehensive E2E testing.

## Design Principles

1. **Predictable Timing**: All events scheduled relative to a "test baseline time" 
2. **Keyword Coverage**: Events designed to match specific test rules
3. **Comprehensive Scenarios**: Cover all app functionality including edge cases
4. **Deterministic**: Same events every test run for reliable results

## Test Baseline Time

All event times are relative to: **Monday 9:00 AM** (when tests begin)
- Tests should always run as if it's Monday 9:00 AM baseline
- Events scheduled at specific offsets from this time

## Calendar Events Specification

### IMMEDIATE EVENTS (0-2 hours from baseline - within alarm window)
1. **"Important Client Call"**
   - Time: Monday 10:00 AM (+1 hour from baseline)
   - Duration: 1 hour
   - Purpose: Test "Important" keyword rule matching
   - Should trigger 30min alarm at 9:30 AM

2. **"Doctor Appointment Follow-up"** 
   - Time: Monday 11:00 AM (+2 hours from baseline)
   - Duration: 1 hour
   - Purpose: Test "Doctor" keyword rule matching
   - Should trigger 30min alarm at 10:30 AM

### SAME DAY EVENTS (later today)
3. **"Team Meeting Weekly"**
   - Time: Monday 2:00 PM (+5 hours from baseline)
   - Duration: 1 hour
   - Purpose: Test "Meeting" keyword rule matching

4. **"Important Project Review"**
   - Time: Monday 4:00 PM (+7 hours from baseline) 
   - Duration: 2 hours
   - Purpose: Test "Important" keyword, longer duration event

5. **"Lunch Break"**
   - Time: Monday 12:00 PM (+3 hours from baseline)
   - Duration: 1 hour
   - Purpose: Event that should NOT match any rules

### NEXT DAY EVENTS (24-48 hours from baseline)
6. **"Morning Standup Meeting"**
   - Time: Tuesday 9:00 AM (+24 hours from baseline)
   - Duration: 30 minutes
   - Purpose: Test "Meeting" keyword, short duration

7. **"Doctor Visit Annual"**
   - Time: Tuesday 2:00 PM (+29 hours from baseline)
   - Duration: 1 hour
   - Purpose: Test "Doctor" keyword on day 2

8. **"Important Conference Call"**
   - Time: Tuesday 6:00 PM (+33 hours from baseline)
   - Duration: 1 hour
   - Purpose: Test "Important" keyword, late day event

### DAY 3 EVENTS (48-72 hours - edge of 2-day lookahead window)
9. **"Weekly Team Meeting"**
   - Time: Wednesday 10:00 AM (+49 hours from baseline)
   - Duration: 1 hour
   - Purpose: Test edge of 2-day window, "Meeting" keyword

10. **"Important Budget Meeting"**
    - Time: Wednesday 3:00 PM (+54 hours from baseline)
    - Duration: 2 hours
    - Purpose: Test edge of 2-day window, "Important" keyword

### FUTURE EVENTS (beyond 2-day lookahead - for background refresh testing)
11. **"Doctor Consultation"**
    - Time: Friday 9:00 AM (+120 hours from baseline)
    - Duration: 1 hour
    - Purpose: Test background refresh, outside initial window

12. **"Important Quarterly Review"**
    - Time: Next Monday 10:00 AM (+169 hours from baseline)
    - Duration: 3 hours
    - Purpose: Test long-term background refresh

### ALL-DAY EVENTS
13. **"Conference Day Important"**
    - Date: Thursday (all-day)
    - Purpose: Test all-day event alarm logic with "Important" keyword

14. **"Training Workshop"**
    - Date: Next Tuesday (all-day)  
    - Purpose: Test all-day event without matching keywords

### MULTI-KEYWORD EVENTS (test multiple rule matching)
15. **"Important Meeting Doctor Review"**
    - Time: Tuesday 11:00 AM (+26 hours from baseline)
    - Duration: 1 hour
    - Purpose: Should match ALL THREE rules (Important + Meeting + Doctor)

16. **"Important Doctor Consultation Meeting"**
    - Time: Wednesday 11:00 AM (+50 hours from baseline) 
    - Duration: 1.5 hours
    - Purpose: Another triple-match event for comprehensive testing

### NON-MATCHING EVENTS (should not trigger any alarms)
17. **"Gym Session"**
    - Time: Tuesday 7:00 PM (+34 hours from baseline)
    - Duration: 1 hour
    - Purpose: Ensure rules don't over-match

18. **"Grocery Shopping"**
    - Time: Wednesday 5:00 PM (+56 hours from baseline)
    - Duration: 1 hour
    - Purpose: Another non-matching event

19. **"Regular Lunch"**
    - Time: Thursday 12:00 PM (+75 hours from baseline)
    - Duration: 1 hour
    - Purpose: Baseline non-matching event

### STRESS TEST EVENTS (20 additional events for performance testing)
20-39. **"Stress Test Event [N]"** (where N = 01-20)
    - Times: Various times spread across Monday-Wednesday
    - Duration: 30 minutes each
    - Purpose: Test app performance with many events
    - Pattern: Every 2 hours starting Monday 1:00 PM

### PAST EVENTS (for cleanup and historical data testing)
40. **"Past Important Event"**
    - Time: Yesterday 10:00 AM
    - Duration: 1 hour
    - Purpose: Test past event handling

41. **"Past Meeting"**
    - Time: Yesterday 2:00 PM  
    - Duration: 1 hour
    - Purpose: Test past event cleanup

## Test Rule Configuration

Based on these events, tests should create these rules:

1. **"Important Events Rule"**
   - Pattern: "Important" 
   - Lead time: 30 minutes
   - Expected matches: Events 1, 4, 8, 10, 12, 13, 15, 16, 40

2. **"Meeting Reminders Rule"**
   - Pattern: "Meeting"
   - Lead time: 15 minutes  
   - Expected matches: Events 3, 6, 9, 10, 15, 16, 41

3. **"Doctor Appointments Rule"**
   - Pattern: "Doctor"
   - Lead time: 60 minutes
   - Expected matches: Events 2, 7, 11, 15, 16

## Validation Criteria

After setup, the emulator should have:
- **Total events**: 41 events
- **Events in next 2 days**: ~25 events (within app's lookahead window)
- **Events matching "Important"**: 9 events
- **Events matching "Meeting"**: 6 events  
- **Events matching "Doctor"**: 5 events
- **All-day events**: 2 events
- **Multi-keyword events**: 2 events (should generate multiple alarms)
- **Non-matching events**: 3 events
- **Stress test events**: 20 events

This provides comprehensive coverage for all testing scenarios while maintaining predictable, deterministic test data.