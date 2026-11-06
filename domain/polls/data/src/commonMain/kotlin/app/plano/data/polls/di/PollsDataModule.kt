package app.plano.data.polls.di

import app.plano.data.polls.HttpPollRepository
import app.plano.domain.polls.repository.PollRepository
import org.koin.dsl.module

val pollsDataModule = module {
    single<PollRepository> { HttpPollRepository(get()) }
}
