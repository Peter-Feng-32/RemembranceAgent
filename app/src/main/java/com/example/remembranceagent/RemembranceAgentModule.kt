package com.example.remembranceagent

import android.content.Context
import android.content.SharedPreferences
import com.example.remembranceagent.retrieval.Indexer
import com.example.remembranceagent.retrieval.Retriever
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemembranceAgentModule {
    val sharedPreferenceKey = "RemembranceAgentPreferences"
    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(sharedPreferenceKey, Context.MODE_PRIVATE)
    }


}
