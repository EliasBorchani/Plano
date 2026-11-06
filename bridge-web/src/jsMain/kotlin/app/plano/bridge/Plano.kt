package app.plano.bridge

import app.plano.contract.Routes
import app.plano.data.auth.di.authDataModule
import app.plano.data.events.di.eventsDataModule
import app.plano.data.polls.di.pollsDataModule
import app.plano.features.event.EventViewModel
import app.plano.features.event.eventFeatureModule
import app.plano.features.events.create.CreateEventViewModel
import app.plano.features.events.eventsFeatureModule
import app.plano.features.events.list.EventListViewModel
import app.plano.features.signin.SignInViewModel
import app.plano.features.signin.signInFeatureModule
import app.plano.utilities.network.NetworkConfig
import app.plano.utilities.network.RefreshConfig
import app.plano.utilities.network.SessionEvents
import app.plano.utilities.network.createHttpClient
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

private var koin: Koin? = null

private fun requireKoin(): Koin = checkNotNull(koin) { "Call startPlano() first" }

/** Starts the shared layer once. [apiBaseUrl] is absolute and ends with `/`. */
@JsExport
fun startPlano(apiBaseUrl: String) {
    if (koin != null) return
    val network = module {
        single { SessionEvents() }
        single<HttpClient> {
            createHttpClient(
                NetworkConfig(
                    baseUrl = apiBaseUrl,
                    refresh = RefreshConfig(refreshPath = Routes.REFRESH, skipPrefix = Routes.AUTH_PREFIX),
                ),
                sessionEvents = get(),
            )
        }
    }
    koin = startKoin {
        modules(
            network,
            authDataModule,
            eventsDataModule,
            pollsDataModule,
            signInFeatureModule,
            eventsFeatureModule,
            eventFeatureModule,
        )
    }.koin
}

@JsExport
fun sessionController(): SessionController = SessionController(requireKoin().get<SignInViewModel>())

@JsExport
fun eventListController(): EventListController = EventListController(requireKoin().get<EventListViewModel>())

@JsExport
fun createEventController(): CreateEventController = CreateEventController(requireKoin().get<CreateEventViewModel>())

@JsExport
fun eventController(eventId: String, joinOnOpen: Boolean): EventController =
    EventController(requireKoin().get<EventViewModel> { parametersOf(eventId, joinOnOpen) })
