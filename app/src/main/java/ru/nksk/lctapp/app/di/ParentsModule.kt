package ru.nksk.lctapp.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.nksk.lctapp.app.parents.LocalParentReportRepository
import ru.nksk.lctapp.feature.parents.pin.DataStorePinRepository
import ru.nksk.lctapp.feature.parents.pin.PinRepository
import ru.nksk.lctapp.feature.parents.report.ParentReportRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ParentsModule {
    @Binds @Singleton abstract fun pin(implementation: DataStorePinRepository): PinRepository
    @Binds @Singleton abstract fun report(implementation: LocalParentReportRepository): ParentReportRepository
}
