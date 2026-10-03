package cz.pocitas.atcollector.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.native
import okhttp3.OkHttpClient
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import no.nordicsemi.kotlin.ble.core.android.AndroidEnvironment
import no.nordicsemi.kotlin.ble.environment.android.NativeAndroidEnvironment

@Module
@InstallIn(ActivityRetainedComponent::class)
object NativeEnvironmentModule {

    @ActivityRetainedScoped
    @Provides
    fun provideEnvironment(
        @ApplicationContext context: Context,
        lifecycle: ActivityRetainedLifecycle,
    ): NativeAndroidEnvironment =
        NativeAndroidEnvironment.getInstance(context, isNeverForLocationFlagSet = true)
            .also { environment ->
                lifecycle.addOnClearedListener { environment.close() }
            }
}

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class AndroidEnvironmentModule {

    @Binds
    abstract fun bindEnvironment(environment: NativeAndroidEnvironment): AndroidEnvironment
}

@Module
@InstallIn(ActivityRetainedComponent::class)
object CentralManagerModule {

    @ActivityRetainedScoped
    @Provides
    fun provideCentralManager(
        environment: NativeAndroidEnvironment,
        lifecycle: ActivityRetainedLifecycle,
    ): CentralManager {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        lifecycle.addOnClearedListener { scope.cancel() }
        return CentralManager.native(environment, scope)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Singleton
    @Provides
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()
}