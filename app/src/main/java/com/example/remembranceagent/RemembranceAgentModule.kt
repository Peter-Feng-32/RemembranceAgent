package com.example.remembranceagent

import com.example.remembranceagent.retrieval.Indexer
import com.example.remembranceagent.retrieval.Retriever
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemembranceAgentModule {

    @Provides
    @Singleton
    fun provideRetriever() : Retriever{
        return Retriever()
    }
}
