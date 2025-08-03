package dev.dgset.cajasva.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dgset.cajasva.data.local.ml.MoneyClassifier
import dev.dgset.cajasva.data.local.ml.MoneyDetector
import dev.dgset.cajasva.data.local.ml.ObjectDetector
import dev.dgset.cajasva.data.repository.CashRepositoryImpl
import dev.dgset.cajasva.domain.repository.CashRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCashRepository(
        cashRepositoryImpl: CashRepositoryImpl
    ): CashRepository

    companion object {

        @Provides
        @Singleton
        fun provideObjectDetector(@ApplicationContext context: Context): ObjectDetector {
            return ObjectDetector(
                context = context,
                modelPath = "detector.tflite",
                confidenceThreshold = 0.5f,
                numThreads = 4
            )
        }

        @Provides
        @Singleton
        fun provideMoneyClassifier(@ApplicationContext context: Context): MoneyClassifier {
            return MoneyClassifier(
                context = context,
                modelPath = "classifier_model_float32.tflite",
                labelPath = "classifier_labels.txt"
            )
        }

        @Provides
        @Singleton
        fun provideMoneyDetector(
            @ApplicationContext context: Context,
            objectDetector: ObjectDetector,
            moneyClassifier: MoneyClassifier
        ): MoneyDetector {
            return MoneyDetector(
                context = context,
                objectDetector = objectDetector,
                moneyClassifier = moneyClassifier
            )
        }
    }
}
