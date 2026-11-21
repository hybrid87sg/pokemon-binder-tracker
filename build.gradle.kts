// Top-level build file. "apply false" means: make these plugins available,
// but only switch them on inside the module that needs them (app/).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.room) apply false
}
