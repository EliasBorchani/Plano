package app.plano.features.events

import app.plano.features.events.create.CreateEventViewModel
import app.plano.features.events.list.EventListViewModel
import org.koin.dsl.module

val eventsFeatureModule = module {
    factory { EventListViewModel(get()) }
    factory { CreateEventViewModel(get()) }
}
