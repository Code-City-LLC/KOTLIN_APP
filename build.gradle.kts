// Top-level build file. Per-module config lives in app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // ⚠️ MUST be declared here, and BEFORE crashlytics. Without it the
    // Google Services plugin never lands on the build classpath, and the
    // Crashlytics plugin then fails to create
    // `uploadCrashlyticsMappingFile<Variant>` — which takes the whole
    // `minify<Variant>WithR8` task graph down with it:
    //
    //   Could not determine the dependencies of task ':app:minifyProdReleaseWithR8'.
    //   > Could not create task ':app:uploadCrashlyticsMappingFileProdRelease'.
    //      > Google-Services plugin not found.
    //
    // Debug builds never noticed: the mapping-upload task only exists for
    // MINIFIED release variants, so every day-to-day build passed while
    // `bundleProdRelease` — the only artifact Play accepts — could not
    // configure at all.
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
