package com.doculens.app.di

import android.content.Context
import com.doculens.app.data.repository.DocumentRepository

object ServiceLocator {

    @Volatile
    private var repository: DocumentRepository? = null

    fun repository(context: Context): DocumentRepository {
        return repository ?: synchronized(this) {
            repository ?: DocumentRepository(context.applicationContext).also { repository = it }
        }
    }
}
