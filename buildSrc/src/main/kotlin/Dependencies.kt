object Dependencies {

    val coreKtx by lazy { "androidx.core:core-ktx:${Version.coreKtx}" }
    val appCompatKtx by lazy { "androidx.appcompat:appcompat:${Version.appCompatKtx}" }
    val lifeCycleKtx by lazy { "androidx.lifecycle:lifecycle-runtime-ktx:${Version.lifeCycleKtx}" }
    val activityKtx by lazy { "androidx.activity:activity-compose:${Version.activityKtx}" }
    val composeKtx by lazy { "androidx.compose:compose-bom:${Version.composeKtx}" }
    val uiKtx by lazy { "androidx.compose.ui:ui" }
    val uiGraphicsKtx by lazy { "androidx.compose.ui:ui-graphics" }
    val uiToolingKtx by lazy { "androidx.compose.ui:ui-tooling-preview" }
    val materialKtx by lazy { "androidx.compose.material3:material3" }
    val jUnitKtx by lazy { "junit:junit:${Version.jUnitKtx}" }
    val jUnitTestKtx by lazy { "androidx.test.ext:junit:${Version.jUnitKtx}" }
    val espressoKtx by lazy { "androidx.test.espresso:espresso-core:${Version.espressoKtx}" }
    val composeBomKtx by lazy { "androidx.compose:compose-bom:${Version.composeBomKtx}" }
    val uiTestKtx by lazy { "androidx.compose.ui:ui-test-junit4" }
    val debugToolingKtx by lazy { "androidx.compose.ui:ui-tooling" }
    val manifestKtx by lazy { "androidx.compose.ui:ui-test-manifest" }
    val googleMaterial by lazy { "com.google.android.material:material:${Version.googleMaterial}" }
    val hiltAndroid by lazy { "com.google.dagger:hilt-android:${Version.hilt}" }
    val hiltAndroidCompiler by lazy { "com.google.dagger:hilt-android-compiler:${Version.hilt}" }
    val hiltCompiler by lazy { "androidx.hilt:hilt-compiler:${Version.hiltCompiler}" }
    val hiltNavigationCompose by lazy { "androidx.hilt:hilt-navigation-compose:${Version.hiltNavigationCompose}" }

    val retrofit by lazy { "com.squareup.retrofit2:retrofit:${Version.retrofit}" }
    val okhttp by lazy { "com.squareup.okhttp3:okhttp:${Version.okhttp}" }
    val gsonConvertor by lazy { "com.squareup.retrofit2:converter-gson:${Version.gsonConvertor}" }
    val moshiConvertor by lazy { "com.squareup.moshi:moshi-kotlin:${Version.moshi}" }
    val moshi by lazy { "com.squareup.retrofit2:converter-moshi:${Version.moshiConvertor}" }
    val interceptor by lazy { "com.squareup.okhttp3:logging-interceptor:${Version.interceptor}" }

    val coroutinesCore by lazy { "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Version.coroutines}" }
    val coroutinesAndroid by lazy { "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Version.coroutines}" }

    //val splashScreen by lazy { "androidx.core:core-splashscreen:${Version.splashScreen}" }
    val coil by lazy { "io.coil-kt:coil-compose:${Version.coil}" }


}

object Modules {
    const val utility = ":utility"
}