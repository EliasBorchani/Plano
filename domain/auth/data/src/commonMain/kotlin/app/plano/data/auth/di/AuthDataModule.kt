package app.plano.data.auth.di

import app.plano.data.auth.HttpAuthRepository
import app.plano.domain.auth.repository.AuthRepository
import org.koin.dsl.module

val authDataModule = module {
    single<AuthRepository> { HttpAuthRepository(get(), get()) }
}
