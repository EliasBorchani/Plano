package app.plano.features.signin

import app.plano.features.signin.guest.GuestEntryViewModel
import org.koin.dsl.module

val signInFeatureModule = module {
    factory { SignInViewModel(get()) }
    factory { (eventId: String) -> GuestEntryViewModel(eventId = eventId, auth = get()) }
}
