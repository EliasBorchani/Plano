package app.plano.features.event

import org.koin.dsl.module

val eventFeatureModule = module {
    factory { (eventId: String, joinOnOpen: Boolean) ->
        EventViewModel(eventId = eventId, joinOnOpen = joinOnOpen, eventRepository = get(), pollRepository = get(), auth = get())
    }
}
