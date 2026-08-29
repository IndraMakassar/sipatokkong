package com.antoniusindra.sipatokkong.core.push

import android.os.Build

/**
 * Whether to put the notification-permission dialog in front of someone.
 *
 * The decision is split from the asking on purpose. `Build.VERSION.SDK_INT` reads as 0 under plain
 * JUnit and `checkSelfPermission` needs a `Context`, so neither can be covered by this suite — the
 * same wall `MediaDownloadRulesTest` documents. What is worth getting right is the *rule*, so it
 * lives here as three booleans and is tested directly.
 */
object PushPermission {

    /**
     * True only where `POST_NOTIFICATIONS` exists at all — API 33 and up.
     *
     * This guard prevents a real bug rather than saving a call. `minSdk` is 27, and on 27–32
     * `checkSelfPermission` returns DENIED for a permission that does not exist, `launcher.launch`
     * shows no dialog, and the result callback reports `granted = false` — which would put an
     * apologetic Indonesian toast on the screen of every single device below 33, every launch,
     * about a permission it never needed. The mirror image of `MediaSaver.needsLegacyPermission`.
     */
    val isRequired: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Ask once, and only where it means something.
     *
     * [alreadyAsked] is what stops the nagging: after two system-level denials Android returns from
     * `launch()` immediately with `false` and shows nothing, so asking again is not a second chance,
     * it is just a second toast. The user can still turn notifications on in system settings, which
     * is where a permanently denied permission belongs.
     */
    fun shouldAsk(isRequired: Boolean, alreadyGranted: Boolean, alreadyAsked: Boolean): Boolean =
        isRequired && !alreadyGranted && !alreadyAsked
}
