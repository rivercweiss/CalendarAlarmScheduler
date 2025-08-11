Got it — you want a one-time $2.00 in-app purchase that unlocks showing the event title in the alarm UI instead of just "Alarm."
Here’s how I’d think through and implement it with minimal friction while keeping the feature robust and secure.
1. Product & Billing Model

    Type: Managed (non-consumable) product in Google Play Console.

        Name: alarm_event_title_unlock

        Price: $2.00 USD (localized automatically by Google Play).

        Managed products persist across reinstalls and devices.

    Reason: This ensures once a user pays, they never pay again — the entitlement is stored in their Google Play account.

2. Play Billing Setup

    Use Google Play Billing Library v6+ (or latest stable).

    Add dependency in build.gradle:

    implementation "com.android.billingclient:billing:<latest_version>"

    Initialize BillingClient in your app's startup path (e.g., ViewModel for Settings screen).

3. Purchase Flow

    Trigger purchase from a "Unlock Event Titles" button in settings or when a user tries to enable the feature.

    Call:

billingClient.launchBillingFlow(activity, billingFlowParams)

On success:

    Verify purchase with Play Billing's built-in signature validation.

    Consume? No — since it’s non-consumable, just acknowledge:

        billingClient.acknowledgePurchase(params, listener)

4. Entitlement Storage & Sync

    Store entitlement in two places:

        Local: SharedPreferences or a small Room table → fast checks.

        Play Store: Always trust Play Billing query on cold start to restore purchases (queryPurchasesAsync).

    On every app start:

        Query BillingClient.queryPurchasesAsync() for alarm_event_title_unlock.

        Update local flag if purchased.

    This covers:

        New device installs.

        Reinstalls.

        Refunds (if refunded, remove entitlement locally).

5. Feature Gate

    Wrap any code that shows event titles in:

    if (isEventTitleUnlocked) {
        showEventTitle()
    } else {
        showGenericAlarm()
    }

    isEventTitleUnlocked is determined from the entitlement storage above.

6. User Experience

    Settings Screen:

        Locked: Greyed out toggle + padlock icon + short “Unlock for $2.00” button.

        Unlocked: Normal toggle to enable/disable event titles.

    During alarm trigger:

        If locked: Always "Alarm".

        If unlocked: Show event name fetched from stored event data.

7. Testing

    Use Google Play’s license testing accounts for free purchase simulations.

    Test flows:

        First-time purchase.

        Restore purchase on reinstall.

        Refund behavior (should revert entitlement).

        Network failure during purchase.

    Test on real devices since Play Billing doesn’t work in emulators for live testing.

8. Security Notes

    Do not hardcode “purchased” flag only locally — always sync with Play Billing on startup.

    Don’t rely on UI hiding — always check entitlement in backend logic before showing event title.