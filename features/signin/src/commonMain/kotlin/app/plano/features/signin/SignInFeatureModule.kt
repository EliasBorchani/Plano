package app.plano.features.signin

import org.koin.dsl.module

val signInFeatureModule = module {
    factory { SignInViewModel(get()) }
}
