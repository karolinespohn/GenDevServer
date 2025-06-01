package org.gendev25.project.dataRetrieval

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

abstract class DataRetriever {


    internal val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

