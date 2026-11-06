package app.plano.data.events.di

import app.plano.data.events.HttpEventRepository
import app.plano.domain.events.repository.EventRepository
import org.koin.dsl.module

val eventsDataModule = module {
    single<EventRepository> { HttpEventRepository(get()) }
}
